package LLM.GameMode; // пакет игровых режимов

import com.jme3.app.SimpleApplication; // приложение jME (нужен доступ к rootNode и assetManager)
import com.jme3.material.Material;     // материал для окраски объектов
import com.jme3.math.ColorRGBA;        // цвет
import com.jme3.math.Vector3f;         // позиция объекта
import com.jme3.scene.Geometry;        // видимый объект сцены
import com.jme3.scene.Node;            // узел-контейнер сцены
import com.jme3.scene.shape.Box;       // примитив "куб" для заглушек

public class GameModeApplier { // применяет GameModeSpec к сцене: строит объекты режима

    private final SimpleApplication app; // ссылка на приложение jME
    private final Node modeNode = new Node("GeneratedMode"); // отдельный узел для всего, что создаёт режим (удобно чистить целиком)

    public GameModeApplier(SimpleApplication app) { // конструктор: цепляем узел режима к сцене
        this.app = app; // запоминаем приложение
        app.getRootNode().attachChild(modeNode); // modeNode живёт под корнем сцены
    }

    // Этот метод нужно вызывать только из главного потока обновления jME.
    public void apply(GameModeSpec spec) { // применяет спецификацию режима
        clearPreviousMode(); // сначала полностью убираем объекты предыдущего режима

        System.out.println("[GameMode] Applying: " + spec.name);        // логируем имя режима
        System.out.println("[GameMode] Description: " + spec.description); // логируем описание

        for (GameModeSpec.Spawn spawn : spec.spawns) { // проходим по всем группам объектов из спецификации
            for (int i = 0; i < spawn.count; i++) {    // создаём count штук объектов этой группы
                Geometry entity = createPlaceholder(spawn.type); // куб-заглушка с цветом по типу

                float x = spawn.position[0] + i * 1.5f;        // разводим объекты по оси X с шагом 1.5
                float y = spawn.position[1];                     // высота из спецификации
                float z = spawn.position[2] + (i % 3) * 1.5f;    // небольшой зигзаг по Z, чтобы не слипались

                entity.setLocalTranslation(new Vector3f(x, y, z)); // ставим объект на его позицию
                modeNode.attachChild(entity);                      // добавляем объект в узел режима
            }
        }
    }

    private void clearPreviousMode() { // полностью убирает предыдущий режим
        modeNode.detachAllChildren();  // отцепляем все объекты узла режима от сцены
    }

    private Geometry createPlaceholder(String type) { // создаёт куб-заглушку с цветом по типу объекта
        Geometry geometry = new Geometry(type, new Box(0.5f, 0.5f, 0.5f)); // куб 1x1x1 м, имя = тип

        Material material = new Material( // новый неосвещаемый материал
                app.getAssetManager(),
                "Common/MatDefs/Misc/Unshaded.j3md"
        );

        switch (type) { // цвет зависит от типа объекта
            case "enemy" -> material.setColor("Color", ColorRGBA.Red);       // враг — красный
            case "item" -> material.setColor("Color", ColorRGBA.Green);      // предмет — зелёный
            case "checkpoint" -> material.setColor("Color", ColorRGBA.Cyan); // чекпоинт — бирюзовый
            case "trap" -> material.setColor("Color", ColorRGBA.Orange);     // ловушка — оранжевый
            default -> material.setColor("Color", ColorRGBA.White);          // неизвестный тип — белый
        }

        geometry.setMaterial(material); // назначаем материал кубу
        return geometry;                // возвращаем готовый объект
    }
}
