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

    private static final int MAX_ATTEMPTS = 2; // сколько раз просим LLM исправить ответ, прежде чем сдаться

    // Системный промпт: жёстко ограничивает поведение модели (комментарий нельзя ставить на строке с """).
    private static final String SYSTEM_PROMPT = """
            Ты — локальный генератор игровых режимов для 3D-игры на jMonkeyEngine.
             Каждый элемент JSON-объекта должен иметь вид "ключ": значение. Отдельные числа между полями запрещены.

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
            - description должно быть непустой строкой.
            - objective.targetCount должно быть не меньше 1 (для survive используй 1).

            Образец структуры JSON.
            Сохраняй структуру и названия полей, но значения выбирай по запросу игрока.
            Не копируй значения примера, если они противоречат запросу.
            Все перечисленные поля обязательны.
            
            {
              "schemaVersion": 1,
              "name": "Сбор предметов",
              "description": "Соберите 10 предметов под дождём на закате.",
              "world": {
                "gravityY": -9.81,
                "timeOfDay": "sunset",
                "weather": "rain"
              },
              "player": {
                "moveSpeed": 7.0,
                "jumpForce": 7.0,
                "maxHealth": 100
              },
              "objective": {
                "type": "collect",
                "targetCount": 10,
                "timeLimitSec": 300
              },
              "spawns": [
                {
                  "type": "item",
                  "position": [0.0, 1.0, 0.0],
                  "count": 10,
                  "speed": 0.0,
                  "damage": 0.0
                }
              ],
              "rules": [
                {
                  "event": "onObjectiveComplete",
                  "action": "endWin",
                  "delaySec": 0.0
                }
              ],
              "difficulty": 3
            }
            
            Разрешённые значения:
            - world.timeOfDay: day, night, sunset.
            - world.weather: clear, rain, fog.
            - objective.type: survive, collect, reach, defend.
            - spawns[].type: enemy, item, checkpoint, trap.
            - rules[].event: onPlayerDeath, onObjectiveComplete, onTimeExpired, onScoreChanged.
            - rules[].action: respawn, endWin, endLose, spawnWave, healPlayer.
            - difficulty: целое число от 1 до 10.
            - Не добавляй поля, которых нет в образце.
            - Не вставляй числа без имени поля.
            - Обычная скорость игрока равна 5.0. Если просят повышенную скорость, используй значение больше 5.0.
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
            String rawResponse = "";
            try {
                String fullPrompt = buildChatPrompt(SYSTEM_PROMPT, userPrompt); // собираем чат-промпт Phi-3.5
                rawResponse = client.generate(fullPrompt); // зовём нейросеть (в фоновом потоке!)
                System.out.println("[LLM] Сырой ответ, попытка " + (attempt + 1) + ":\n" + rawResponse);
                String json = extractJson(rawResponse);           // вырезаем из ответа чистый JSON-объект
                System.out.println("[LLM] Json для парсинга:" + "\n" + json);
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
                Твой предыдущий ответ не прошёл проверку.
                Ошибка: %s
        
                Твой предыдущий ответ:
                %s
        
                Исправь этот ответ по указанной ошибке и по образцу из системного сообщения.
                Сохрани остальные корректные значения.
                Не добавляй новые поля.
                Не вставляй числа или другие значения без имени поля.
                difficulty должно быть целым числом от 1 до 10.
                Проверь синтаксис JSON и все значения перед ответом.
        
                Верни ровно один полный валидный JSON-объект.
                Ответ должен начинаться с { и заканчиваться }.
                Без markdown, комментариев, объяснений и текста до или после JSON.
        
                Исходный запрос игрока:
                %s
                """.formatted(
                        e.getMessage(), // конкретная ошибка парсинга или валидации
                        rawResponse,    // предыдущий ответ, который нужно исправить
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
