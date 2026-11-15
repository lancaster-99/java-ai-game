package LLM.GameMode; // пакет игровых режимов

import LLM.LocalLlmClient; // клиент локальной нейросети (mock или реальный ONNX)
import com.fasterxml.jackson.core.json.JsonReadFeature;          // дополнительные послабления при чтении JSON
import com.fasterxml.jackson.databind.DeserializationFeature;    // настройки десериализации Jackson
import com.fasterxml.jackson.databind.ObjectMapper;              // парсер JSON -> объекты Java
import com.fasterxml.jackson.databind.json.JsonMapper;           // билдер ObjectMapper для JSON

import java.util.List; // список ошибок валидации

public class GameModeGenerator { // превращает текстовый запрос игрока в проверенный GameModeSpec

    private final LocalLlmClient client;            // кто генерирует текст (нейросеть или заглушка)
    private final ObjectMapper objectMapper;        // парсер JSON
    private final GameModeSpecValidator validator = new GameModeSpecValidator(); // проверяет спеку до нормализации

    private static final int MAX_ATTEMPTS = 3; // сколько раз просим LLM исправить ответ, прежде чем сдаться

    // Системный промпт: жёстко ограничивает поведение модели (комментарий нельзя ставить на строке с """).
    private static final String SYSTEM_PROMPT = """
            Ты — локальный генератор игровых режимов для 3D-игры на jMonkeyEngine.

            Ты не пишешь объяснений.
            Ты не пишешь код.
            Ты не используешь markdown.
            Ты возвращаешь только один валидный JSON-объект.

            Требования:
            - Только JSON.
            - Без текста до и после.
            - Без комментариев.
            - Без лишних полей.
            - Все числа должны быть числами, а не строками.
            - Используй только разрешённые значения.
            - Если запрос неполный, используй безопасные значения по умолчанию.

            Схема:
            {
              "schemaVersion": 1,
              "name": "string",
              "description": "string",
              "world": {
                "gravityY": number,
                "timeOfDay": "day|night|sunset",
                "weather": "clear|rain|fog"
              },
              "player": {
                "moveSpeed": number,
                "jumpForce": number,
                "maxHealth": integer
              },
              "objective": {
                "type": "survive|collect|reach|defend",
                "targetCount": integer,
                "timeLimitSec": integer
              },
              "spawns": [
                {
                  "type": "enemy|item|checkpoint|trap",
                  "position": [number, number, number],
                  "count": integer,
                  "speed": number,
                  "damage": number
                }
              ],
              "rules": [
                {
                  "event": "onPlayerDeath|onObjectiveComplete|onTimeExpired|onScoreChanged",
                  "action": "respawn|endWin|endLose|spawnWave|healPlayer",
                  "delaySec": number
                }
              ],
              "difficulty": integer
            }
            """;

    public GameModeGenerator(LocalLlmClient client) { // конструктор: получает готового клиента LLM
        this.client = client; // запоминаем клиента

        this.objectMapper = JsonMapper.builder() // собираем парсер JSON
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // неизвестные поля LLM просто игнорируем
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA) // прощаем висячую запятую (частая ошибка LLM)
                .build(); // готовый ObjectMapper
    }

    public GameModeSpec generate(String userRequest) { // главный метод: запрос игрока -> GameModeSpec
        String userPrompt = buildUserPrompt(userRequest); // первый пользовательский промпт из запроса
        Exception lastError = null; // последняя ошибка (понадобится, если все попытки провалятся)

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) { // до 3 попыток получить валидный ответ
            try {
                String fullPrompt = buildChatPrompt(SYSTEM_PROMPT, userPrompt); // собираем чат-промпт Phi-3.5
                String rawResponse = client.generate(fullPrompt); // зовём нейросеть (в фоновом потоке!)
                String json = extractJson(rawResponse);           // вырезаем из ответа чистый JSON-объект

                GameModeSpec spec = objectMapper.readValue(json, GameModeSpec.class); // parse: JSON -> объект

                List<String> errors = validator.validate(spec); // validate: проверяем до нормализации (отсекаем "{}", мусор)
                if (!errors.isEmpty()) { // спека мусорная — считаем попытку неудачной
                    throw new IllegalArgumentException( // бросаем, чтобы сработал retry с текстом ошибок
                            "Невалидный GameModeSpec: " + String.join("; ", errors));
                }

                return spec.normalize(); // normalize: зажимаем значения в безопасные пределы и возвращаем

            } catch (Exception e) { // любая ошибка: парсинг, валидация, сбой LLM
                lastError = e; // запоминаем для финального исключения

                // Просим модель исправить свой ответ, показывая ей текст ошибки.
                userPrompt = """
                        Твой предыдущий ответ был неверным.
                        Ошибка: %s

                        Верни только исправленный валидный JSON-объект.
                        Без объяснений.
                        Без текста до и после.

                        Исходный запрос игрока:
                        %s
                        """.formatted(
                        e.getMessage(), // текст ошибки (парсинга или валидации)
                        userRequest     // исходный запрос игрока
                );
            }
        }

        throw new RuntimeException("Не удалось получить корректный игровой режим", lastError); // все попытки исчерпаны
    }

    private String buildUserPrompt(String userRequest) { // собирает пользовательскую часть промпта
        return """
                Запрос игрока:
                %s

                Верни только один валидный JSON-объект по схеме.
                Начни ответ символом { и закончи символом }.
                """.formatted(userRequest);
    }

    // Шаблон чата для Phi-3/Phi-3.5.
    private String buildChatPrompt(String system, String user) { // склеивает system и user в чат-формате Phi
        return """
                <|system|>
                %s<|end|>
                <|user|>
                %s<|end|>
                <|assistant|>
                """.formatted(system, user);
    }

    private String extractJson(String raw) { // вырезает первый сбалансированный JSON-объект из сырого ответа модели
        if (raw == null) { // модель вернула null — подставляем пустой объект (его отсеет валидатор)
            return "{}";
        }

        String text = raw.trim(); // убираем пробелы по краям

        if (text.startsWith("```")) { // модель обернула ответ в markdown-блок — снимаем обёртку
            text = text
                    .replaceAll("```[a-zA-Z]*", "") // убираем открывающий ```json / ``` и т.п.
                    .replaceAll("```", "")          // убираем закрывающий ```
                    .trim();                        // снова чистим края
        }

        int start = text.indexOf('{'); // ищем начало первого JSON-объекта
        if (start < 0) { // объекта нет вообще — вернём как есть, парсер выбросит ошибку
            return text;
        }

        int depth = 0;          // глубина вложенности фигурных скобок
        boolean inString = false; // мы внутри строки "..."?
        boolean escape = false;   // предыдущий символ был обратным слэшем?

        for (int i = start; i < text.length(); i++) { // идём по символам от первой '{'
            char c = text.charAt(i); // текущий символ

            if (inString) { // внутри строки скобки не считаем
                if (escape) { // предыдущий символ был '\' — этот экранирован
                    escape = false; // сбрасываем флаг экранирования
                } else if (c == '\\') { // началось экранирование
                    escape = true;
                } else if (c == '"') { // строка закончилась
                    inString = false;
                }
            } else { // вне строки
                if (c == '"') { // началась строка
                    inString = true;
                } else if (c == '{') { // вложенность выросла
                    depth++;
                } else if (c == '}') { // вложенность упала
                    depth--;
                    if (depth == 0) { // объект закрылся — это конец JSON
                        return text.substring(start, i + 1); // возвращаем ровно один объект
                    }
                }
            }
        }

        return text.substring(start); // закрывающей скобки не нашли — берём до конца (парсер выбросит ошибку)
    }
}
