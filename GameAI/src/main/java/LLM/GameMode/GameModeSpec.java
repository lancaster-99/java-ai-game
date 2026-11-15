package LLM.GameMode; // пакет игровых режимов

import java.util.ArrayList; // изменяемый список для spawns/rules
import java.util.List;      // интерфейс списка
import java.util.Locale;    // для регистронезависимого сравнения строк (Locale.ROOT)
import java.util.Set;       // множество допустимых значений

public class GameModeSpec { // строгая спецификация игрового режима: ровно то, что LLM имеет право вернуть

    public static final Set<String> SUPPORTED_TIMES = Set.of( // допустимые значения времени суток (нужны валидатору и normalize)
            "day",    // день
            "night",  // ночь
            "sunset"  // закат
    );

    public static final Set<String> SUPPORTED_WEATHERS = Set.of( // допустимые значения погоды (нужны валидатору и normalize)
            "clear", // ясно
            "rain",  // дождь
            "fog"    // туман
    );

    public static final Set<String> SUPPORTED_OBJECTIVE_TYPES = Set.of( // допустимые типы цели режима
            "survive", // выжить заданное время
            "collect", // собрать предметы
            "reach",   // добраться до точки
            "defend"   // оборонять позицию
    );

    public static final Set<String> SUPPORTED_SPAWN_TYPES = Set.of( // допустимые типы создаваемых объектов
            "enemy",      // враг
            "item",       // предмет для сбора
            "checkpoint", // контрольная точка
            "trap"        // ловушка
    );

    public static final Set<String> SUPPORTED_EVENTS = Set.of( // допустимые события для правил
            "onPlayerDeath",       // игрок погиб
            "onObjectiveComplete", // цель выполнена
            "onTimeExpired",       // время вышло
            "onScoreChanged"       // изменился счёт
    );

    public static final Set<String> SUPPORTED_ACTIONS = Set.of( // допустимые действия для правил
            "respawn",    // возродить игрока
            "endWin",     // завершить победой
            "endLose",    // завершить поражением
            "spawnWave",  // создать волну врагов
            "healPlayer"  // вылечить игрока
    );

    public int schemaVersion = 1;                // версия схемы JSON (на будущее, для миграций)
    public String name = "Generated Mode";       // имя режима
    public String description = "";              // текстовое описание режима

    public World world = new World();         // параметры мира
    public Player player = new Player();      // параметры игрока
    public Objective objective = new Objective(); // цель режима

    public List<Spawn> spawns = new ArrayList<>(); // какие объекты создать на сцене
    public List<Rule> rules = new ArrayList<>();   // правила реакции на события

    public int difficulty = 1; // сложность 1..10

    public static class World {      // секция "мир"
        public float gravityY = -9.81f;    // вертикальная гравитация
        public String timeOfDay = "day";   // время суток
        public String weather = "clear";   // погода
    }

    public static class Player {         // секция "игрок"
        public float moveSpeed = 5.0f; // скорость перемещения
        public float jumpForce = 7.0f; // сила прыжка
        public int maxHealth = 100;    // максимальное здоровье
    }

    public static class Objective {        // секция "цель"
        public String type = "survive"; // тип цели
        public int targetCount = 1;     // сколько нужно (собрать/убить/...)
        public int timeLimitSec = 120;  // лимит времени в секундах
    }

    public static class Spawn {                          // группа создаваемых объектов одного типа
        public String type = "item";                     // тип объекта
        public float[] position = new float[]{0f, 1f, 0f}; // базовая позиция [x, y, z]
        public int count = 1;                            // сколько объектов создать
        public float speed = 0f;                         // скорость объекта (для врагов)
        public float damage = 0f;                        // урон объекта (для врагов/ловушек)
    }

    public static class Rule {                    // правило "событие -> действие"
        public String event = "onTimeExpired"; // событие-триггер
        public String action = "endWin";       // действие-реакция
        public float delaySec = 0f;            // задержка перед действием, секунды
    }

    // Обязательно нормализуем всё, что вернула нейросеть: LLM считается недоверенной.
    public GameModeSpec normalize() { // приводит значения к безопасным диапазонам, заменяет мусор дефолтами
        if (name == null || name.isBlank()) { // имени нет — ставим дефолт
            name = "Generated Mode";
        }

        if (description == null) { // описания нет — пустая строка
            description = "";
        }

        if (world == null) { // секции мира нет — создаём с дефолтами
            world = new World();
        }

        world.gravityY = clamp(world.gravityY, -30f, 30f); // гравитация в физически разумных пределах
        world.timeOfDay = allowed(world.timeOfDay, SUPPORTED_TIMES, "day"); // неизвестное время суток -> день
        world.weather = allowed(world.weather, SUPPORTED_WEATHERS, "clear"); // неизвестная погода -> ясно

        if (player == null) { // секции игрока нет — создаём с дефолтами
            player = new Player();
        }

        player.moveSpeed = clamp(player.moveSpeed, 1f, 20f);   // скорость 1..20
        player.jumpForce = clamp(player.jumpForce, 1f, 20f);   // прыжок 1..20
        player.maxHealth = clamp(player.maxHealth, 1, 1000);   // здоровье 1..1000

        if (objective == null) { // секции цели нет — создаём с дефолтами
            objective = new Objective();
        }

        objective.type = allowed(objective.type, SUPPORTED_OBJECTIVE_TYPES, "survive"); // неизвестная цель -> survive
        objective.targetCount = clamp(objective.targetCount, 1, 1000); // количество целей 1..1000
        objective.timeLimitSec = clamp(objective.timeLimitSec, 10, 3600); // лимит времени 10 сек..1 час

        if (spawns == null) { // списка объектов нет — пустой список
            spawns = new ArrayList<>();
        }

        spawns.removeIf(spawn -> // выкидываем null-элементы и группы с неподдерживаемым типом
                spawn == null || !SUPPORTED_SPAWN_TYPES.contains(spawn.type)
        );

        for (Spawn spawn : spawns) { // чиним каждую оставшуюся группу объектов
            if (spawn.position == null || spawn.position.length != 3) { // позиция битая — дефолт над землёй
                spawn.position = new float[]{0f, 1f, 0f};
            }

            for (int i = 0; i < 3; i++) { // каждую координату зажимаем в пределы мира
                spawn.position[i] = clamp(spawn.position[i], -500f, 500f);
            }

            spawn.count = clamp(spawn.count, 1, 50);     // 1..50 объектов в группе
            spawn.speed = clamp(spawn.speed, 0f, 20f);   // скорость 0..20
            spawn.damage = clamp(spawn.damage, 0f, 100f); // урон 0..100
        }

        if (spawns.size() > 200) { // защита от переполнения сцены: не больше 200 групп
            spawns = new ArrayList<>(spawns.subList(0, 200));
        }

        if (rules == null) { // списка правил нет — пустой список
            rules = new ArrayList<>();
        }

        rules.removeIf(rule -> // выкидываем null-правила и правила с неизвестным событием/действием
                rule == null
                        || !SUPPORTED_EVENTS.contains(rule.event)
                        || !SUPPORTED_ACTIONS.contains(rule.action)
        );

        for (Rule rule : rules) { // задержку каждого правила зажимаем в 0..60 секунд
            rule.delaySec = clamp(rule.delaySec, 0f, 60f);
        }

        difficulty = clamp(difficulty, 1, 10); // сложность 1..10

        return this; // возвращаем себя для цепочки вызовов
    }

    private static float clamp(float value, float min, float max) { // зажимает float в [min, max]
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) { // зажимает int в [min, max]
        return Math.max(min, Math.min(max, value));
    }

    private static String allowed(String value, Set<String> allowedValues, String fallback) { // возвращает value, если оно в списке допустимых, иначе fallback
        if (value == null) { // null сразу заменяем дефолтом
            return fallback;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT); // чистим пробелы и приводим к нижнему регистру
        return allowedValues.contains(normalized) ? normalized : fallback; // допустимое значение или дефолт
    }
}
