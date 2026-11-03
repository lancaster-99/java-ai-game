package LLM;

import ai.onnxruntime.genai.Generator;
import ai.onnxruntime.genai.GeneratorParams;
import ai.onnxruntime.genai.GenAIException;
import ai.onnxruntime.genai.Model;
import ai.onnxruntime.genai.Tokenizer;
import ai.onnxruntime.genai.TokenizerStream;
import ai.onnxruntime.genai.Sequences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class OnnxGenAiPhiClient implements AutoCloseable {

    private final Path modelDir;
    private Model model;
    private Tokenizer tokenizer;

    private int maxNewTokens = 128;
    private double temperature = 0.0;

    public OnnxGenAiPhiClient(Path modelDir) {
        this.modelDir = modelDir;
    }

    public void load() throws GenAIException {
        Path dir = modelDir.toAbsolutePath().normalize();

        if (!Files.isDirectory(dir))
            throw new IllegalArgumentException("Каталог модели не найден: " + dir);
        if (!Files.exists(dir.resolve("genai_config.json")))
            throw new IllegalStateException("В каталоге нет genai_config.json: " + dir);

        System.out.println("[ONNX] Loading model from: " + dir);
        model = new Model(dir.toString());
        tokenizer = new Tokenizer(model);
        System.out.println("[ONNX] Model loaded");
    }

    public String generate(String prompt) throws GenAIException {
        return generate(prompt, maxNewTokens, null);
    }

    public String generate(String prompt, int newTokensLimit, Consumer<String> onPiece)
            throws GenAIException {

        ensureLoaded();

        Sequences promptSeq = tokenizer.encode(prompt);
        int[] promptTokens = promptSeq.getSequence(0);

        StringBuilder out = new StringBuilder();

        try (GeneratorParams params = new GeneratorParams(model)) {
            params.setSearchOption("max_length", (double) (promptTokens.length + newTokensLimit));

            if (temperature > 0.0) {
                params.setSearchOption("do_sample", true);
                params.setSearchOption("temperature", temperature);
                params.setSearchOption("top_p", 0.9);
            } else {
                params.setSearchOption("do_sample", false);
            }

            // setInput УБРАН. Промпт передаётся в Generator:
            try (Generator generator = new Generator(model, params);
                 TokenizerStream stream = tokenizer.createStream()) {

                generator.appendTokens(promptTokens);   // <-- вот здесь задаётся промпт

                while (!generator.isDone()) {
                    generator.generateNextToken();
                    int token = generator.getLastTokenInSequence(0);
                    String piece = stream.decode(token);
                    if (piece != null && !piece.isEmpty()) {
                        out.append(piece);
                        if (onPiece != null) onPiece.accept(piece);
                    }
                }
            }
        }
        return cleanup(out.toString());
    }

    private static String cleanup(String s) {
        return s.replace("<|end|>", "")
                .replace("<|endoftext|>", "")
                .replace("<|assistant|>", "")
                .replace("<|user|>", "")
                .trim();
    }

    private void ensureLoaded() {
        if (model == null || tokenizer == null)
            throw new IllegalStateException("Модель не загружена: сначала вызовите load()");
    }

    public void setMaxNewTokens(int v) { this.maxNewTokens = v; }
    public void setTemperature(double t) { this.temperature = t; }

    @Override
    public void close() {
        if (tokenizer != null) { tokenizer.close(); tokenizer = null; }
        if (model != null) { model.close(); model = null; }
    }
}