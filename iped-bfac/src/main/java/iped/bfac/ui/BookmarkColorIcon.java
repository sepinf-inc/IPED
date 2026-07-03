package iped.bfac.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Icon;

/**
 * Colored rounded-square icon for bookmarks, matching the look of iped-app's BookmarkIcon
 * without depending on iped-app.
 */
public class BookmarkColorIcon implements Icon {

    public static final int SIZE = 16;
    public static final Color DEFAULT_COLOR = new Color(180, 180, 240);

    private static final Map<Color, Icon> iconPerColor = new HashMap<>();
    private static final Stroke strokeBorder = new BasicStroke(1f);
    private static final RenderingHints renderingHints;

    static {
        Map<RenderingHints.Key, Object> hints = new HashMap<>();
        hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        renderingHints = new RenderingHints(hints);
    }

    private final Color color;

    public static Icon getIcon(Color color) {
        Color c = color == null ? DEFAULT_COLOR : color;
        synchronized (iconPerColor) {
            Icon icon = iconPerColor.get(c);
            if (icon == null) {
                icon = new BookmarkColorIcon(c);
                iconPerColor.put(c, icon);
            }
            return icon;
        }
    }

    private BookmarkColorIcon(Color color) {
        this.color = color;
    }

    @Override
    public void paintIcon(Component comp, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g;
        RenderingHints saveHints = g2.getRenderingHints();
        g2.setRenderingHints(renderingHints);

        int size = getIconWidth();
        int arc = size / 3 + 2;

        g2.setColor(color);
        g2.fillRoundRect(x + 1, y + 1, size - 2, size - 2, arc, arc);

        Color colorBorder = comp.getForeground();
        if (colorBorder == null) {
            colorBorder = Color.GRAY;
        }
        colorBorder = new Color(colorBorder.getRed(), colorBorder.getGreen(), colorBorder.getBlue(), 64);

        g2.setStroke(strokeBorder);
        g2.setColor(colorBorder);
        g2.drawRoundRect(x + 1, y + 1, size - 2, size - 2, arc, arc);

        g2.setRenderingHints(saveHints);
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
