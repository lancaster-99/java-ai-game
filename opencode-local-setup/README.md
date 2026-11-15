# OpenCode local setup для курсовой GameAI

Эта папка — стартовый набор файлов для OpenCode и “памяти” проекта.

## Куда копировать файлы

Скопируй эти файлы в корень проекта:

```text
C:\Users\Arseniy\Desktop\Курсова Java\
├── opencode.jsonc
├── AGENTS.md
├── PROJECT_MEMORY.md
└── план.txt
```

После этого запускай OpenCode из корня проекта:

```powershell
cd "C:\Users\Arseniy\Desktop\Курсова Java"
opencode
```

Или сразу открой нужную папку:

```powershell
opencode "C:\Users\Arseniy\Desktop\Курсова Java"
```

## Важно про “создать проект в OpenCode”

В OpenCode обычно не “создают проект” внутри приложения. Проект — это обычная папка на диске, желательно с `.git`, `pom.xml`, `README.md`, `AGENTS.md`.

Правильный сценарий:

1. Создай папку проекта на диске.
2. Положи туда код.
3. Положи туда `AGENTS.md`, `opencode.jsonc`, `PROJECT_MEMORY.md`.
4. Запусти `opencode` из этой папки.
5. Внутри OpenCode работай с сессиями: одна сессия = одна большая задача.

Для твоей курсовой лучше открывать не весь `Курсова Java`, а именно:

```powershell
opencode "C:\Users\Arseniy\Desktop\Курсова Java\GameAI"
```

Но тогда `AGENTS.md` и `opencode.jsonc` лучше положить и в `GameAI` тоже.

## Можно ли установить “тебя” локально?

Меня локально установить нельзя как `.exe` или файл. Я работаю через клиент/провайдера, который у тебя настроен.

Что можно сделать локально:

- установить OpenCode локально;
- запускать OpenCode из локальной папки;
- подключить локальную LLM через Ollama / LM Studio / vLLM;
- для игры использовать свою локальную `Phi-3.5-mini-instruct-onnx` через ONNX Runtime GenAI.

OpenCode может автоматически обнаруживать локальные модели от Ollama, LM Studio и vLLM.

Пример с Ollama:

```powershell
ollama serve
ollama pull qwen3:8b
```

Потом в `opencode.jsonc`:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "model": "ollama/qwen3:8b"
}
```

Подробности смотри в `INSTALL_LOCAL.md`.

## Главная идея “памяти”

OpenCode может забывать старые части длинного диалога. Поэтому важное храним в файлах:

- `AGENTS.md` — постоянные правила проекта;
- `PROJECT_MEMORY.md` — текущее состояние и следующие задачи;
- `план.txt` — долгосрочный план;
- `opencode.jsonc` — настройки compaction, instructions, commands.

Перед перерывом проси:

```text
Обнови PROJECT_MEMORY.md: что сделано, что решено, что дальше.
```
