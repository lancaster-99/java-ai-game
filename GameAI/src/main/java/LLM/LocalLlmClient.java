package LLM; // пакет клиентов локальной нейросети

public interface LocalLlmClient extends AutoCloseable { // общий контракт для mock и реального ONNX-клиента; AutoCloseable даёт close()

    // Загружает модель в память. Обязательно вызывать в фоновом потоке.
    void load() throws Exception; // throws Exception — загрузка может падать (нет файлов, не хватило памяти)

    // Генерирует текст по готовому промпту. Обязательно вызывать в фоновом потоке.
    String generate(String prompt) throws Exception; // возвращает сырой текст ответа модели

    boolean isReady(); // true, если модель загружена и готова к generate()

    @Override
    void close(); // освобождает ресурсы модели; без throws, чтобы не требовать try/catch
}
