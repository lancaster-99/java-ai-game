package LLM; // пакет клиентов локальной нейросети

import ai.onnxruntime.genai.Generator;       // генератор токенов ONNX Runtime GenAI
import ai.onnxruntime.genai.GeneratorParams; // параметры генерации (max_length, temperature и т.д.)
import ai.onnxruntime.genai.GenAIException;  // исключение ONNX GenAI
import ai.onnxruntime.genai.Model;           // загруженная ONNX-модель
import ai.onnxruntime.genai.Tokenizer;       // токенизатор модели
import ai.onnxruntime.genai.TokenizerStream; // потоковый декодер токенов в текст
import ai.onnxruntime.genai.Sequences;       // результат токенизации (набор последовательностей)

import java.nio.file.Files;                  // проверка существования файлов/каталогов
import java.nio.file.Path;                   // путь к каталогу модели
import java.util.function.Consumer;          // колбэк для потокового вывода кусочков текста

public class OnnxGenAiPhiClient implements LocalLlmClient { // реальный клиент Phi-3.5; реализует общий контракт LocalLlmClient

    private final Path modelDir;        // каталог с файлами модели (genai_config.json, model.onnx и др.)
    private Model model;                // загруженная модель (null до load())
    private Tokenizer tokenizer;        // токенизатор (null до load())
    private volatile boolean ready = false; // флаг готовности; volatile — читается из разных потоков
    private int maxNewTokens = 768;     // лимит новых токенов: JSON-спека короткая, 768 хватает с запасом
    private double temperature = 0.0;   // 0 = детерминированная генерация (важно для строгого JSON)

    public OnnxGenAiPhiClient(Path modelDir) { // конструктор: просто запоминает путь
        this.modelDir = modelDir; // сама загрузка — в load()
    }

    @Override
    public void load() throws GenAIException { // загружает модель и токенизатор в память
        Path dir = modelDir.toAbsolutePath().normalize(); // нормализуем путь (убираем . и ..)

        if (!Files.isDirectory(dir)) // каталога нет — дальше нет смысла
            throw new IllegalArgumentException("Каталог модели не найден: " + dir);
        if (!Files.exists(dir.resolve("genai_config.json"))) // без genai_config.json GenAI модель не откроет
            throw new IllegalStateException("В каталоге нет genai_config.json: " + dir);

        System.out.println("[ONNX] Загрузка модели " + dir); // логируем начало загрузки
        model = new Model(dir.toString());       // нативная загрузка ONNX-модели (тяжёлая операция)
        tokenizer = new Tokenizer(model);        // создаём токенизатор для этой модели
        ready = true;                            // помечаем готовность только после полной загрузки
        System.out.println("[ONNX] Модель загружена успешно");
    }

    @Override
    public String generate(String prompt) throws GenAIException { // простая генерация без потокового вывода
        return generate(prompt, maxNewTokens, null); // делегируем в расширенную версию с лимитом по умолчанию
    }

    public String generate(String prompt, int newTokensLimit, Consumer<String> onPiece) // расширенная генерация: свой лимит токенов + колбэк на каждый кусочек текста
            throws GenAIException {

        ensureLoaded(); // проверяем, что load() уже вызван

        Sequences promptSeq = tokenizer.encode(prompt); // текст промпта -> последовательности токенов
        int[] promptTokens = promptSeq.getSequence(0);  // берём первую (единственную) последовательность

        StringBuilder out = new StringBuilder(); // сюда накапливаем сгенерированный текст

        try (GeneratorParams params = new GeneratorParams(model)) { // параметры генерации; AutoCloseable
            params.setSearchOption("max_length", (double) (promptTokens.length + newTokensLimit)); // max_length = длина промпта + лимит новых токенов

            if (temperature > 0.0) { // если задана температура — включаем сэмплирование
                params.setSearchOption("do_sample", true);       // вероятностный выбор токенов
                params.setSearchOption("temperature", temperature); // степень "случайности"
                params.setSearchOption("top_p", 0.9);            // nucleus sampling: 90% вероятностной массы
            } else {
                params.setSearchOption("do_sample", false);      // жадный поиск: всегда самый вероятный токен
            }

            try (Generator generator = new Generator(model, params); // генератор токенов; AutoCloseable
                 TokenizerStream stream = tokenizer.createStream()){ // потоковый декодер токен->текст; AutoCloseable

                generator.appendTokens(promptTokens); // скармливаем генератору токены промпта

                while (!generator.isDone()) { // генерируем, пока модель не закончит (EOS или max_length)
                    generator.generateNextToken();                    // один шаг генерации = один новый токен
                    int token = generator.getLastTokenInSequence(0);  // забираем только что созданный токен
                    String piece = stream.decode(token);              // декодируем токен в кусочек текста
                    if (piece != null && !piece.isEmpty()) {          // бывают пустые/служебные кусочки
                        out.append(piece);                            // накапливаем текст
                        if (onPiece != null) onPiece.accept(piece);   // отдаём кусочек подписчику (потоковый вывод)
                    }
                }
            }
        }
        return cleanup(out.toString()); // чистим служебные токены и возвращаем текст
    }

    private static String cleanup(String s) { // убирает служебные токены чат-шаблона из ответа
        return s.replace("<|end|>", "")          // токен конца реплики
                .replace("<|endoftext|>", "")    // токен конца текста
                .replace("<|assistant|>", "")    // маркер роли ассистента
                .replace("<|user|>", "")         // маркер роли пользователя
                .trim();                         // обрезаем пробелы по краям
    }

    private void ensureLoaded() { // защита от вызова generate() до load()
        if (model == null || tokenizer == null) // модель или токенизатор ещё не созданы
            throw new IllegalStateException("Модель не загружена: сначала вызовите load()");
    }

    @Override
    public boolean isReady() { // сообщаем, готова ли модель к генерации
        return ready; // флаг выставляется в конце load()
    }

    public void setMaxNewTokens(int v) { this.maxNewTokens = v; } // позволяет изменить лимит токенов извне
    public void setTemperature(double t) { this.temperature = t; } // позволяет включить сэмплирование извне

    @Override
    public void close() { // освобождает нативные ресурсы
        ready = false; // модель больше не готова
        if (tokenizer != null) { tokenizer.close(); tokenizer = null; } // закрываем токенизатор и обнуляем ссылку
        if (model != null) { model.close(); model = null; }             // закрываем модель и обнуляем ссылку
    }
}
