# PROJECT_MEMORY.md

## Objective

Сделать курсовую GameAI: Java-игру на jMonkeyEngine, где NPC по запросу игрока вызывает локальную Phi-3.5-mini и получает JSON-описание игрового режима, а игра строит режим из шаблонов.

## Done

- Найден проект `GameAI`.
- Есть `Main.java` с jME `SimpleApplication`.
- Есть `LocalLlmClient`, `MockLocalLlm`, `OnnxGenAiPhiClient`.
- Есть `GameModeSpec`, `GameModeGenerator`, `GameModeApplier`.
- Есть локальная модель `Phi-3.5-mini-instruct-onnx`.
- Создан долгосрочный `план.txt`.
- Рабочая папка OpenCode перенесена в корень курсовой.
- Развёрнута постоянная память: `opencode-local-setup/AGENTS.md`, `opencode.jsonc` (compaction + `/save-context`, `/resume`).
- Сохранения проекта и AGENTS.md перенесены в `opencode-local-setup/`, чтобы не мешать в корне (пути прописаны в `instructions` в `opencode.jsonc`).

## Decisions

- LLM генерирует только JSON-спеку режима.
- Игра валидирует JSON и строит уровень из готовых шаблонов.
- Режимы делятся на combat, sport, adventure, puzzle, sandbox.
- Для MVP достаточно: survival, football, collect adventure.
- MCP для локальной одиночной игры не обязателен; проще прямой встроенный клиент.
- Долгую сессию не пересоздаём: включено автосжатие контекста (`compaction.auto`, `keep.tokens` 24000, `buffer` 20%).
- `план.txt` не включён в `instructions`, чтобы не занимать контекст в каждом запросе; читается по необходимости.

## Current problems

- Нужен fallback-режим (когда все 3 попытки генерации провалились).
- В системе нет Maven (`mvn`/`mvnw`), но компиляция проверяется через `javac` с classpath из `~/.m2` (jme3-core 3.10.0-beta1, jackson 2.17.0, onnxruntime-genai 0.17.0).

Решено 2026-11-08 (подробности в `ИЗМЕНЕНИЯ.txt` в корне курсовой):

- В `GameModeSpec` добавлены `SUPPORTED_TIMES` и `SUPPORTED_WEATHERS` (без них валидатор не компилировался); `normalize()` использует эти константы.
- `GameModeSpecValidator` подключён в `GameModeGenerator.generate()`: цепочка parse -> validate -> normalize -> apply, при ошибках валидации — retry с текстом ошибок (до 3 попыток).
- `{}` от LLM теперь отсекается валидатором (проверено тестом).
- NPE в `GameModeApplier`, `implements LocalLlmClient`, `isReady()`, `maxNewTokens=768` — уже были в порядке.
- Подготовка к 3D-моделям из Blender: в pom добавлен `jme3-plugins` (glTF-лоадер), создана `src/main/resources/assets/Models/`, в `Main.initScene()` добавлены DirectionalLight + AmbientLight (иначе glTF с PBR-материалами чёрные). Следующий шаг по теме: в `GameModeApplier` маппить тип спавна → .glb-файл (белый список, clone(), fallback на кубики).
- В корне курсовой лежит `книги-по-нейросетям.txt` — подборка книг (нейросети, Java/C#/C++, геймдев) по запросу пользователя.

## Next moves

1. Сделать NPC interaction + HUD.
2. Добавить fallback-режим при провале всех попыток генерации.
3. Подключить реальную Phi флагом `-Dgameai.llm=onnx` и проверить скорость генерации.
4. Реализовать первый survival-режим и football-режим.

## Side task: Quartus lab11 (МИКРА, задание 3.1) — DONE 2026-11-08

- Проект `C:\qurtuslabs45\lab11`: автомат Мура светофора, иерархия `sv13_light` = `s13_light.bdf` (схема) + `v13_light.v` (Verilog), MAX II EPM240T100C5, Quartus 8.1.
- Пины: c=2, s=3, k=4; выходы sg..sy=52-55, vg..vy=56,57,58,66.
- map/fit/tan/sim все зелёные: 10 LE (4%), 11 пинов, симуляция 100% coverage, `sv13_light.vwf` (назван как верхний уровень) содержит результаты (sr/sp импульсы при k=1 на 2460-2560 и 2960-3060 нс; схема ≡ Verilog, 0/760 рассогласований).
- Уравнения: d0=s·~q2·~q0; d1=s·~q2·(q1^q0); d2=s·k·~q2·q1·q0; y=~q2·~q0, g=q0, r=p=q2.
- Подводные камни BDF 8.1 (важно для будущих лаб): rect в (text) — абсолютные координаты x1,y1,x2,y2 (не ширина!); провод, проходящий через точку порта пина, замыкает цепи; генераторы: `Temp\docconv\gen_s13.ps1`, `gen_top.ps1`, `gen_proj.ps1`.
- Quartus 8.1 читает исходники как ANSI (cp1251): кириллица в UTF-8 превращается в кракозябры. Требование пользователя: код писать БЕЗ комментариев (или только ASCII).

## Side task: Quartus ЛР11 задание 3.2 (проект sv32_light) — DONE 2026-11-08

- Второе задание той же ЛР11, лежит в той же папке `C:\qurtuslabs45\lab11` рядом с lab11 (задание 3.1). Проект/qpf = `sv32_light.qpf`, верхний уровень `sv32_light` = `s32_light.bdf` (схема) + `v32_light.v` (Verilog). Папка lab12 удалена.
- Тот же автомат светофора, вариант 2 = код Грея + T-триггеры.
- Состояния (Грей): 000=желтый, 001=зеленый, 011=желтый, 010=зеленый, 110=красный+переход. Переходы как в 3.1.
- Уравнения возбуждения T (при s=0: T_i=q_i, сброс в 000): T2=~s·q2 + s·q1·~q0·(q2+k); T1=~s·q1 + s·(~q2·~q1·q0 + q1·~q0·(q2+~k)); T0=~s·q0 + s·y. Выходы: y=~q2·~(q1^q0), g=~q2·(q1^q0), r=p=q2·q1·~q0.
- Схема: 5 NOT, 14 AND2, 1 AND3, 10 OR2, 3 TFF. Формат символа TFF взят из `LR9\s13_fsm.bdf` (геометрия как DFF, порт T на месте D).
- map/fit/tan/sim зелёные: 16 LE, 11 пинов, 100% coverage, `sv32_light.vwf` с результатами; sr/sp импульсы 2461-2561 и 2961-3061 нс; схема ≡ Verilog (0/760 рассогласований при выборке 5 нс). На sg есть ~2 нс глитч при выходе из красного (110→000 меняет 2 бита) — неизбежен для этого кода.
- Пины как в задании 3.1: c=2, s=3, k=4; sg..sy=52-55; vg,vr,vp,vy=56,57,58,66.
- Генераторы: `Temp\docconv\gen_s32.ps1`, `gen_top32.ps1`, `gen_proj32.ps1`; проверка диаграмм `dump_vwf32.ps1`. Промежуточных t_* файлов нет.

## Risks

- Phi может вернуть невалидный JSON.
- Генерация может быть долгой.
- При смене режима возможны утечки физики/памяти.
- Scope слишком большой: сначала MVP, потом расширения.
- Сжатие контекста lossy: важные решения фиксировать в этом файле.

## Useful commands

```powershell
cd "C:\Users\Arseniy\Desktop\Курсова Java\GameAI"
mvn -q -DskipTests package
```

Запуск реальной Phi должен включаться отдельным флагом/настройкой, а Mock остаётся режимом разработки.
