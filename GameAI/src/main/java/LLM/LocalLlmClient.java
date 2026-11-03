package LLM;

public interface LocalLlmClient extends AutoCloseable {

    /**
     * Загружает модель в память.
     * Обязательно выполнять в фоновом потоке.
     */
    void load() throws Exception;

    /**
     * Генерирует текст по готовому промпту.
     * Обязательно выполнять в фоновом потоке.
     */
    String generate(String prompt) throws Exception;

    boolean isReady();

    @Override
    void close();
}
