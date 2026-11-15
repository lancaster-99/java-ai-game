package LLM.GameMode; // пакет игровых режимов

import java.util.ArrayList; // список для сбора ошибок валидации
import java.util.List;      // интерфейс списка

public class GameModeSpecValidator { // проверяет спецификацию ДО нормализации, чтобы отсеять мусорные ответы LLM (например, пустой "{}")

    public List<String> validate(GameModeSpec spec) { // возвращает список ошибок; пустой список = спецификация валидна
        List<String> errors = new ArrayList<>(); // сюда копим тексты ошибок

        if (spec == null) { // Jackson вернул null — ответ вообще не JSON-объект
            errors.add("spec == null: ответ LLM не является JSON-объектом");
            return errors; // дальше проверять нечего
        }

        if (spec.name == null || spec.name.isBlank()) { // у режима должно быть имя
            errors.add("name пустое");
        }

        if (spec.description == null || spec.description.isBlank()) { // описание обязательно; у пустого "{}" оно пустое — так отсеиваем "{}"
            errors.add("description пустое");
        }

        if (spec.world == null) { // секция мира обязательна
            errors.add("world == null");
        } else {
            if (!GameModeSpec.SUPPORTED_TIMES.contains(spec.world.timeOfDay)) { // время суток строго из списка
                errors.add("world.timeOfDay недопустимо: " + spec.world.timeOfDay);
            }
            if (!GameModeSpec.SUPPORTED_WEATHERS.contains(spec.world.weather)) { // погода строго из списка
                errors.add("weather недопустимо: " + spec.world.weather);
            }
        }

        if (spec.player == null) { // секция игрока обязательна
            errors.add("player == null");
        } else {
            if (spec.player.moveSpeed <= 0 || spec.player.moveSpeed > 20) { // скорость в разумных пределах
                errors.add("player.moveSpeed вне диапазона: " + spec.player.moveSpeed);
            }
            if (spec.player.jumpForce <= 0 || spec.player.jumpForce > 20) { // сила прыжка в разумных пределах
                errors.add("player.jumpForce вне диапазона: " + spec.player.jumpForce);
            }
            if (spec.player.maxHealth < 1 || spec.player.maxHealth > 1000) { // здоровье 1..1000
                errors.add("player.maxHealth вне диапазона: " + spec.player.maxHealth);
            }
        }

        if (spec.objective == null) { // цель режима обязательна
            errors.add("objective == null");
        } else {
            if (!GameModeSpec.SUPPORTED_OBJECTIVE_TYPES.contains(spec.objective.type)) { // тип цели строго из списка
                errors.add("objective.type недопустим: " + spec.objective.type);
            }
            if (spec.objective.targetCount < 1) { // целей должна быть хотя бы одна
                errors.add("objective.targetCount < 1");
            }
            if (spec.objective.timeLimitSec < 10) { // лимит времени не меньше 10 секунд
                errors.add("objective.timeLimitSec < 10");
            }
        }

        if (spec.spawns == null || spec.spawns.isEmpty()) { // без объектов режим бессмыслен; у пустого "{}" список пуст
            errors.add("spawns пуст: режим без объектов не имеет смысла");
        } else {
            for (int i = 0; i < spec.spawns.size(); i++) { // проверяем каждую группу объектов
                GameModeSpec.Spawn s = spec.spawns.get(i); // текущая группа
                if (s == null) { // null-элемент в списке
                    errors.add("spawns[" + i + "] == null");
                    continue; // к следующему
                }
                if (!GameModeSpec.SUPPORTED_SPAWN_TYPES.contains(s.type)) { // тип объекта строго из списка
                    errors.add("spawns[" + i + "].type недопустим: " + s.type);
                }
                if (s.position == null || s.position.length != 3) { // позиция — ровно 3 числа
                    errors.add("spawns[" + i + "].position должна содержать 3 числа");
                }
                if (s.count < 1 || s.count > 50) { // количество 1..50
                    errors.add("spawns[" + i + "].count вне диапазона: " + s.count);
                }
            }
        }

        if (spec.rules != null) { // правила необязательны, но если есть — проверяем
            for (int i = 0; i < spec.rules.size(); i++) { // каждое правило
                GameModeSpec.Rule r = spec.rules.get(i); // текущее правило
                if (r == null) { // null-элемент
                    errors.add("rules[" + i + "] == null");
                    continue;
                }
                if (!GameModeSpec.SUPPORTED_EVENTS.contains(r.event)) { // событие строго из списка
                    errors.add("rules[" + i + "].event недопустимо: " + r.event);
                }
                if (!GameModeSpec.SUPPORTED_ACTIONS.contains(r.action)) { // действие строго из списка
                    errors.add("rules[" + i + "].action недопустимо: " + r.action);
                }
            }
        }

        if (spec.difficulty < 1 || spec.difficulty > 10) { // сложность 1..10
            errors.add("difficulty вне диапазона: " + spec.difficulty);
        }

        return errors; // отдаём все найденные ошибки
    }
}
