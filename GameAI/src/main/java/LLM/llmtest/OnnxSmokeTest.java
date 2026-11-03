package LLM.llmtest;

import LLM.OnnxGenAiPhiClient;

import java.nio.file.Path;

public class OnnxSmokeTest {

    public static void main(String[] args) throws Exception {
        String modelPath = System.getProperty(
                "phi.model.dir",
                "C:\\Users\\Arseniy\\Desktop\\Курсова Java\\Phi-3.5-mini-instruct-onnx"
        );

        System.out.println("Testing model dir: " + modelPath);

        try (OnnxGenAiPhiClient client = new OnnxGenAiPhiClient(Path.of(modelPath))) {
            client.load();

            String prompt = """
                    <|user|>
                    И так, какое имя я тебе дал?<|end|>
                    <|assistant|>
                    """;

            String result = client.generate(prompt);
            System.out.println("MODEL RESPONSE:");
            System.out.println(result);
        }
    }
}