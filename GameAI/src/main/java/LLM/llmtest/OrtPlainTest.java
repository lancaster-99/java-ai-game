package LLM.llmtest;

public class OrtPlainTest {
    public static void main(String[] args) {
        System.out.println("ORT OK: " + ai.onnxruntime.OrtEnvironment.getEnvironment().getVersion());
    }
}
