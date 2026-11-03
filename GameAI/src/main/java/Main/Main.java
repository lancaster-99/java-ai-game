package Main;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;

public class Main extends SimpleApplication {

    public static void main(String[] args) {
        new Main().start();
    }

    @Override
    public void simpleInitApp() {
        Box box = new Box(1f, 1f, 1f);
        Box box1 = new Box(2f, 1f, 1f);
        Geometry geom = new Geometry("Box", box);
        Geometry geom1 = new Geometry("Box", box1);
        Material mat = new Material(
                assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md"
        );
        mat.setColor("Color", ColorRGBA.Blue);
        geom.setMaterial(mat);
        rootNode.attachChild(geom);
        
        mat.setColor("Color", ColorRGBA.Red);
        geom1.setMaterial(mat);
        rootNode.attachChild(geom1);
    }
}