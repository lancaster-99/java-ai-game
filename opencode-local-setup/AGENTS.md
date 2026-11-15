# AGENTS.md

Проект: курсовая GameAI на Java + jMonkeyEngine.

Цель: NPC через локальную Phi-3.5-mini ONNX генерирует игровые режимы по текстовому запросу игрока.

## Главные правила

- LLM не управляет сценой, физикой или памятью игры напрямую.
- LLM возвращает только строгий JSON GameModeSpec.
- Любой ответ LLM считается недоверенным: parse -> validate -> normalize -> apply.
- `generate()` нельзя вызывать в render-потоке jME.
- Модель загружается один раз при старте и переиспользуется.
- Перед применением нового режима старый режим полностью очищается.
- Для MVP сначала используем MockLocalLlm, реальную Phi включаем отдельным флагом.

## Память

- `opencode-local-setup/PROJECT_MEMORY.md` — сохранение проекта: сделано, решения, проблемы, следующие шаги.
- `план.txt` — долгосрочный план курсовой (читать при необходимости, в инструкции не грузится).
- Перед перерывом: `/save-context`. После перерыва: `/resume`.
- Долгие сессии не требуют пересоздания: контекст сжимается автоматически (`compaction` в `opencode.jsonc`).

## Пути

- Корень курсовой: `C:\Users\Arseniy\Desktop\Курсова Java`
- Игра: `C:\Users\Arseniy\Desktop\Курсова Java\GameAI`
- Модель: `C:\Users\Arseniy\Desktop\Курсова Java\Phi-3.5-mini-instruct-onnx`
- План: `C:\Users\Arseniy\Desktop\Курсова Java\план.txt`

## Технологии

- Java 21
- Maven (в системе пока не установлен, `mvn`/`mvnw` отсутствуют)
- jMonkeyEngine 3.10 beta
- Jackson
- ONNX Runtime / ONNX Runtime GenAI
- Phi-3.5-mini-instruct ONNX

## Критичные текущие задачи

1. Удалить NPE-код из `GameModeApplier`.
2. Сделать `OnnxGenAiPhiClient implements LocalLlmClient`.
3. Добавить `isReady()` в ONNX-клиент.
4. Уменьшить `maxNewTokens` до 512–1024.
5. Добавить `GameModeSpecValidator`.
6. Сделать NPC interaction, HUD и fallback-режим.

## Стиль работы

- Маленькие проверяемые изменения.
- После изменения кода: компиляция -> запуск -> лог -> короткий вывод.
- Не хранить секреты в файлах проекта.
- Не коммитить модель, `target`, `.idea`, большие бинарники.
