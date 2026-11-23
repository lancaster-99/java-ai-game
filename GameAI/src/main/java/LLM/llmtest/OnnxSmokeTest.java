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
                    Мне нужно, чтобы ты запомнил, какое имя и параметры поведения я тебе дал. Другие нейросети для такого могут обращаться в файл, в котором записано их поведение и нужная информация. как это сделать с использованием файла с тобой используя Java?<|end|>
                    <|assistant|>
                    """;

            String result = client.generate(prompt);
            System.out.println("Кейн:");
            System.out.println(result);
        }
    }
}