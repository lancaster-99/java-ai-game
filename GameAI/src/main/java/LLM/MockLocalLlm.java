package LLM;

public class MockLocalLlm implements LocalLlmClient {

    private volatile boolean ready = false;

    @Override
    public void load() {
        ready = true;
        System.out.println("[MockLLM] Model loaded");
    }

    @Override
    public String generate(String prompt) {
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
                """;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void close() {
        ready = false;
    }
}