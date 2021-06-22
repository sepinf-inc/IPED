package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.Painter;
import javax.swing.UIManager;

public abstract class Theme {
    private final Map<String, Color> dockMap = new HashMap<String, Color>();

    public abstract String getName();

    public abstract void apply();

    @Override
    public String toString() {
        return getName();
    }

    public void put(Object key, Object value) {
        UIManager.put(key, value);
        UIManager.getLookAndFeelDefaults().put(key, value);
    }

    public void putDock(String key, Color value) {
        dockMap.put(key, value);
    }
    
    public Map<String, Color> getDockSettings() {
        return Collections.unmodifiableMap(dockMap);
    }

    class ColorPainter implements Painter<JComponent> {

        private final Color color;

        ColorPainter(Color c) {
            color = c;
        }

        public void paint(Graphics2D g, JComponent comp, int w, int h) {
            g.setColor(color);
            g.fillRect(0, 0, w - 1, h - 1);
        }
    }
}
