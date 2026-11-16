package iped.app.ui;

import static org.junit.Assert.assertEquals;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.junit.Test;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import sun.misc.Unsafe;

public class AppPreviewCloseTest {
    // Avoid constructing a JFrame: these tests model shutdown before initialization.
    private App partialApp() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (App) ((Unsafe) field.get(null)).allocateInstance(App.class);
    }

    private void set(App app, String name, Object value) throws Exception {
        Field field = App.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(app, value);
    }

    private void cleanup(App app) throws Exception {
        Method method = App.class.getDeclaredMethod("removeAllDockables");
        method.setAccessible(true);
        method.invoke(app);
    }

    @Test
    public void closeBeforeDockInitialization() throws Exception {
        App app = partialApp();
        cleanup(app);
        cleanup(app);
    }

    @Test
    public void closeEmptyAndPopulatedDocksRepeatedly() throws Exception {
        App app = partialApp();
        DefaultSingleCDockable empty = new DefaultSingleCDockable("empty");
        DefaultSingleCDockable populated = new DefaultSingleCDockable("populated", new JPanel());
        List<DefaultSingleCDockable> viewers = new ArrayList<>();
        viewers.add(empty);
        viewers.add(populated);
        List<DefaultSingleCDockable> results = new ArrayList<>();
        results.add(new DefaultSingleCDockable("result", new JPanel()));
        set(app, "viewerDocks", viewers);
        set(app, "rsTabDock", results);
        cleanup(app);
        cleanup(app);
        assertEquals(0, populated.getContentPane().getComponentCount());
        assertEquals(0, results.size());
    }
}
