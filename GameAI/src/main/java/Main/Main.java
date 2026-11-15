package Main; // пакет главного класса игры

import LLM.GameMode.GameModeGenerator; // генератор режима: текст игрока -> LLM -> JSON -> GameModeSpec
import LLM.LocalLlmClient;             // общий интерфейс клиента локальной нейросети
import LLM.MockLocalLlm;               // заглушка нейросети (разработка без тяжёлой модели)
import LLM.OnnxGenAiPhiClient;         // реальный клиент Phi-3.5 через ONNX Runtime GenAI
import LLM.GameMode.GameModeApplier;   // применяет готовую спецификацию режима к сцене

import com.jme3.app.SimpleApplication;        // базовый класс приложения jMonkeyEngine
import com.jme3.input.KeyInput;               // коды клавиш
import com.jme3.light.AmbientLight;           // рассеянный свет (подсвечивает тени)
import com.jme3.light.DirectionalLight;       // направленный свет ("солнце") — нужен glTF-моделям
import com.jme3.input.controls.ActionListener; // слушатель действий ввода (нажатий)
import com.jme3.input.controls.KeyTrigger;     // триггер "нажата клавиша"
import com.jme3.material.Material;             // материал (цвет/текстура) геометрии
import com.jme3.math.ColorRGBA;                // цвет RGBA
import com.jme3.math.Vector3f;                 // трёхмерный вектор
import com.jme3.scene.Geometry;                // видимый объект на сцене
import com.jme3.scene.shape.Box;               // примитив "параллелепипед"

import java.nio.file.Path;                     // путь к каталогу модели
import java.util.concurrent.Callable;          // задача, возвращаемая в render-поток через enqueue
import java.util.concurrent.CompletableFuture; // асинхронный запуск генерации
import java.util.concurrent.ExecutorService;   // пул потоков
import java.util.concurrent.Executors;         // фабрика пулов потоков

public class Main extends SimpleApplication implements ActionListener { // главный класс: jME-приложение + обработчик клавиш

    private final ExecutorService llmExecutor = Executors.newSingleThreadExecutor(); // отдельный поток для LLM, чтобы не блокировать render-поток

    private LocalLlmClient localLlm;      // клиент нейросети: mock или реальный ONNX
    private GameModeGenerator generator;  // строит GameModeSpec из текстового запроса
    private GameModeApplier applier;      // применяет GameModeSpec к сцене

    private volatile boolean generating = false;  // true, пока идёт генерация (защита от повторного запуска)
    private volatile boolean modelReady = false;  // true, когда модель загружена

    public static void main(String[] args) { // точка входа программы
        Main app = new Main();             // создаём приложение
        app.start();                       // запускаем окно jME и игровой цикл
    }

    @Override
    public void simpleInitApp() {          // одноразовая инициализация (вызывает jME при старте)
        setDisplayStatView(false);         // убираем статистику FPS из угла экрана

        initScene();                       // строим базовую сцену (пол, камера)

        // Выбор реализации LLM: по умолчанию mock; реальная Phi включается флагом -Dgameai.llm=onnx
        String llmImpl = System.getProperty("gameai.llm", "mock"); // читаем системное свойство выбора клиента
        if (llmImpl.equalsIgnoreCase("onnx")) {                    // если запрошен реальный ONNX
            String modelDir = System.getProperty(                  // путь к модели можно переопределить флагом
                    "phi.model.dir",
                    "C:\\Users\\Arseniy\\Desktop\\Курсова Java\\Phi-3.5-mini-instruct-onnx" // реальный путь к модели Phi-3.5
            );
            localLlm = new OnnxGenAiPhiClient(Path.of(modelDir));  // создаём реальный клиент Phi-3.5
        } else {
            localLlm = new MockLocalLlm();                         // создаём заглушку (режим разработки)
        }

        generator = new GameModeGenerator(localLlm); // генератор работает поверх выбранного клиента
        applier = new GameModeApplier(this);         // applier получает приложение для доступа к сцене

        inputManager.addMapping("GenerateMode1", new KeyTrigger(KeyInput.KEY_1)); // клавиша 1 -> запрос режима выживания
        inputManager.addMapping("GenerateMode2", new KeyTrigger(KeyInput.KEY_2)); // клавиша 2 -> запрос режима сбора
        inputManager.addListener(this, "GenerateMode1", "GenerateMode2");         // подписываем this на оба действия

        System.out.println("Нажмите 1 или 2 для генерации режима."); // подсказка в консоль
        System.out.println("LLM-клиент: " + llmImpl + ". Модель загружается в фоне..."); // какой клиент выбран

        loadModelAsync(); // запускаем фоновую загрузку модели
    }

    private void initScene() { // создаёт базовую сцену: серый пол и камеру
        Geometry floor = new Geometry("Floor", new Box(20, 0.1f, 20)); // пол 40x40 м толщиной 0.2 м
        Material floorMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md"); // простой неосвещаемый материал
        floorMaterial.setColor("Color", ColorRGBA.Gray); // красим пол в серый
        floor.setMaterial(floorMaterial);                // назначаем материал полу
        floor.setLocalTranslation(0, -0.1f, 0);          // верх пола ровно на уровне y=0
        rootNode.attachChild(floor);                     // добавляем пол на сцену

        cam.setLocation(new Vector3f(0, 6, 12));         // камера выше и позади центра
        cam.lookAt(new Vector3f(0, 0, 0), Vector3f.UNIT_Y); // камера смотрит в центр сцены

        // Свет нужен заранее: будущие glTF-модели из Blender идут с освещаемыми PBR-материалами,
        // без источников света они отображаются чёрными.
        DirectionalLight sun = new DirectionalLight(); // направленный свет типа "солнце"
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal()); // лучи сверху и слегка сбоку
        rootNode.addLight(sun);                        // вешаем "солнце" на сцену

        AmbientLight ambient = new AmbientLight();     // рассеянный свет: смягчает тёмные стороны объектов
        ambient.setColor(ColorRGBA.White.mult(0.3f));  // 30% яркости, чтобы тени не были абсолютно чёрными
        rootNode.addLight(ambient);                    // вешаем рассеянный свет на сцену
    }

    private void loadModelAsync() { // загружает модель в фоновом потоке
        CompletableFuture.runAsync(() -> { // выполняем в llmExecutor, не в render-потоке
            try {
                localLlm.load();           // тяжёлая загрузка модели в память
                modelReady = true;         // помечаем, что моделью можно пользоваться
                System.out.println("Модель готова к использованию.");
            } catch (Exception e) {        // любая ошибка загрузки
                System.err.println("Ошибка загрузки модели:");
                e.printStackTrace();       // печатаем стектрейс в консоль
            }
        }, llmExecutor); // выполнитель — наш отдельный поток
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) { // обработчик нажатий (jME дергает при вводе)
        if (!isPressed) { // игнорируем отпускание клавиши
            return;
        }

        if (name.equals("GenerateMode1")) { // нажата клавиша 1
            startGeneration("Сделай режим выживания ночью: туман, слабая гравитация, 5 врагов, выжить 120 секунд."); // запрос режима выживания
        }

        if (name.equals("GenerateMode2")) { // нажата клавиша 2
            startGeneration("Сделай режим сбора предметов: закат, дождь, 10 предметов, скорость игрока выше обычного."); // запрос режима сбора
        }
    }

    private void startGeneration(String request) { // запускает асинхронную генерацию режима по запросу
        if (!modelReady) { // модель ещё грузится
            System.out.println("Модель ещё не загружена. Попробуйте позже.");
            return;
        }

        if (generating) { // предыдущая генерация не завершена
            System.out.println("Генерация уже выполняется.");
            return;
        }

        generating = true; // ставим флаг "генерируем"
        System.out.println("Запрос к локальной нейросети: " + request); // логируем запрос

        CompletableFuture
                .supplyAsync(() -> generator.generate(request), llmExecutor) // generate() — в фоновом потоке, НЕ в render-потоке
                .whenComplete((spec, error) -> { // колбэк по завершении генерации
                    generating = false;          // снимаем флаг

                    if (error != null) {         // генерация упала с исключением
                        System.err.println("Ошибка генерации режима:");
                        error.printStackTrace();
                        return;
                    }

                    enqueue((Callable<Void>) () -> { // применение режима — только в render-потоке jME
                        applier.apply(spec);         // строим режим на сцене
                        return null;
                    });
                });
    }

    @Override
    public void destroy() { // вызывается при закрытии приложения
        try {
            localLlm.close(); // освобождаем ресурсы модели (память, нативные объекты)
        } catch (Exception ignored) { // ошибки при закрытии игнорируем
        }

        llmExecutor.shutdownNow(); // останавливаем фоновый поток генерации

        super.destroy(); // штатное завершение jME
    }
}
