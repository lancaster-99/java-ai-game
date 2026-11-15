# Как установить OpenCode и работать локально

Сразу честно: **“меня” локально установить нельзя**. Я не существую как отдельный `.exe`, который ты можешь скачать и поставить. Можно установить локально клиент OpenCode и подключить к нему локальную модель.

## 1. Установка OpenCode на Windows

На Windows package manager не поддерживается, поэтому проще всего:

### Вариант A: Desktop app

Скачай Windows build:

```text
https://opencode.ai/update/api/latest/desktop/opencode/opencode-desktop-win-x64.exe
```

Установи и запусти.

### Вариант B: CLI

Скачай standalone CLI:

```text
https://opencode.ai/update/api/latest/cli/opencode/opencode-windows-x64.zip
```

Распакуй, например в:

```text
C:\Tools\opencode\
```

Добавь `C:\Tools\opencode\` в PATH или запускай полный путь:

```powershell
C:\Tools\opencode\opencode.exe "C:\Users\Arseniy\Desktop\Курсова Java"
```

Проверка:

```powershell
opencode --version
opencode service status
```

## 2. Как “создать проект” в OpenCode

В OpenCode проект не создаётся кнопкой. Проект = папка на диске.

Правильно:

```powershell
cd "C:\Users\Arseniy\Desktop\Курсова Java"
opencode
```

Или лучше открыть именно игру:

```powershell
opencode "C:\Users\Arseniy\Desktop\Курсова Java\GameAI"
```

Если OpenCode открыт не в той папке, проще закрыть и запустить из нужной папки.

## 3. Минимальные файлы проекта

В корне проекта должны быть:

```text
opencode.jsonc
AGENTS.md
PROJECT_MEMORY.md
```

Для твоего случая ещё:

```text
план.txt
GameAI\pom.xml
Phi-3.5-mini-instruct-onnx\genai_config.json
```

## 4. Локальная модель для OpenCode

OpenCode автоматически обнаруживает локальные модели от:

- Ollama — `http://127.0.0.1:11434`
- LM Studio — `http://127.0.0.1:1234/v1`
- vLLM — `http://127.0.0.1:8000/v1`

Пример через Ollama:

```powershell
ollama serve
ollama pull qwen3:8b
```

Потом выбери модель в OpenCode:

```text
/models
```

Или задай в `opencode.jsonc`:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "model": "ollama/qwen3:8b"
}
```

Если хочешь другой OpenAI-compatible сервер:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "model": "local/coder",
  "providers": {
    "local": {
      "name": "Local server",
      "package": "@opencode/ai/providers/openai-compatible",
      "settings": {
        "baseURL": "http://127.0.0.1:1234/v1"
      },
      "models": {
        "coder": {
          "modelID": "model-name-on-server",
          "capabilities": {
            "tools": true,
            "input": ["text"],
            "output": ["text"]
          },
          "limit": {
            "context": 32768,
            "output": 8192
          }
        }
      }
    }
  }
}
```

## 5. Где тут твоя Phi?

Твоя `Phi-3.5-mini-instruct-onnx` — это модель для игры, а не обязательно модель для OpenCode.

Для OpenCode проще использовать Ollama/LM Studio/vLLM.

Для игры используй свою ONNX Phi через `OnnxGenAiPhiClient`.

Это два разных канала:

```text
OpenCode assistant  -> помогает писать код
Phi в игре          -> генерирует игровые режимы для NPC
```

## 6. Если хочешь максимально локально

- OpenCode CLI/Desktop ставится локально.
- Модель для OpenCode выбирай через Ollama/LM Studio/vLLM.
- Phi для игры уже локальная.
- Интернет нужен только для скачивания OpenCode, моделей и зависимостей.
- После скачивания можно работать офлайн, если модель и зависимости уже на диске.
