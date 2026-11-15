package LLM; // пакет клиентов локальной нейросети

public class MockLocalLlm implements LocalLlmClient { // заглушка: имитирует нейросеть, возвращая готовый JSON без модели

    private volatile boolean ready = false; // флаг готовности; volatile — читается из разных потоков

    @Override
    public void load() { // "загрузка" модели — мгновенная
        ready = true; // помечаем готовность
        System.out.println("[MockLLM] Model loaded"); // логируем для отладки
    }

    @Override
    public String generate(String prompt) { // "генерация": всегда возвращаем один и тот же валидный JSON режима
        return """
                {
                  "schemaVersion": 1,
                  "name": "Mock Mode",
                  "description": "Временный режим без реального ONNX-инференса.",
                  "world": {
                    "gravityY": -6.0,
                    "timeOfDay": "night",
                    "weather": "fog"
                  },
                  "player": {
                    "moveSpeed": 6.0,
                    "jumpForce": 8.0,
                    "maxHealth": 100
                  },
                  "objective": {
                    "type": "survive",
                    "targetCount": 1,
                    "timeLimitSec": 60
                  },
                  "spawns": [
                    {
                      "type": "enemy",
                      "position": [4, 1, -4],
                      "count": 3,
                      "speed": 2.0,
                      "damage": 8
                    },
                    {
                      "type": "item",
                      "position": [-4, 1, 4],
                      "count": 2,
                      "speed": 0,
                      "damage": 0
                    }
                  ],
                  "rules": [
                    {
                      "event": "onPlayerDeath",
                      "action": "respawn",
                      "delaySec": 2
                    },
                    {
                      "event": "onTimeExpired",
                      "action": "endWin",
                      "delaySec": 0
                    }
                  ],
                  "difficulty": 2
                }
                """; // текстовый блок Java 21: JSON спецификации тестового режима
    }

    @Override
    public boolean isReady() { // сообщаем, готова ли "модель"
        return ready; // просто возвращаем флаг
    }

    @Override
    public void close() { // "освобождение" ресурсов
        ready = false; // сбрасываем флаг готовности
    }
}
