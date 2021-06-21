package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;
import java.awt.Graphics2D;

import javax.swing.JComponent;
import javax.swing.Painter;
import javax.swing.UIManager;

public abstract class Theme {
    public abstract String getName();

    public abstract void apply();

    @Override
    public String toString() {
        return getName();
    }

    public void put(Object key, Object value) {
        //UIManager.put(key, value);
        UIManager.getLookAndFeelDefaults().put(key, value);
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
