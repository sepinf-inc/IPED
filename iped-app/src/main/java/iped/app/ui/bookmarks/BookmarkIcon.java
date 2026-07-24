package iped.app.ui.bookmarks;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import iped.app.ui.IconManager;
import iped.data.IMultiBookmarks;

public class BookmarkIcon implements Icon {
    public static final String columnName = "$BookmarkIcon";

    private static final Map<Color, Icon> iconPerColor = new HashMap<Color, Icon>();
    private static final Map<List<Color>, Icon> iconPerColorList = new HashMap<List<Color>, Icon>();
    private static final Stroke strokeBorder = new BasicStroke(1f);
    private static final Stroke strokeChecked = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final RenderingHints renderingHints;

    static {
        Map<Key, Object> hints = new HashMap<Key, Object>();
        hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        renderingHints = new RenderingHints(hints);
    }

    private final Color color, checkedColor;
    private final Color[] colors;
    private Boolean checked;

    public static Icon getIcon(IMultiBookmarks bookmarks, String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        if (str.indexOf(" | ") < 0) {
            str = str.replace("||", "|");
            return getIcon(bookmarks.getBookmarkColor(str));
        }
        // Multiple bookmarks
        String[] bookmarkNames = str.split(" \\| ");
        List<Color> l = new ArrayList<Color>(bookmarkNames.length);
        for (String name : bookmarkNames) {
            name = name.replace("||", "|");
            l.add(bookmarks.getBookmarkColor(name));
        }
        synchronized (iconPerColorList) {
            Icon icon = iconPerColorList.get(l);
            if (icon == null) {
                icon = new BookmarkIcon(l);
                iconPerColorList.put(l, icon);
            }
            return icon;
        }
    }

    public static synchronized Icon getIcon(Color color) {
        Icon icon = iconPerColor.get(color);
        if (icon == null) {
            icon = new BookmarkIcon(color);
            iconPerColor.put(color, icon);
        }
        return icon;
    }

    public static BookmarkIcon getIcon(Color color, boolean checked) {
        return new BookmarkIcon(color, checked);
    }

    private BookmarkIcon(Color color) {
        this.color = color;
        this.colors = null;
        this.checkedColor = null;
    }

    private BookmarkIcon(List<Color> colors) {
        this.color = null;
        this.colors = colors.toArray(new Color[0]);
        this.checkedColor = null;
    }

    private BookmarkIcon(Color color, Boolean checked) {
        this.color = color;
        this.colors = null;
        this.checked = checked;
        this.checkedColor = BookmarkColorsUtil.getForeground(color);
    }

    @Override
    public void paintIcon(Component comp, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g;
        RenderingHints saveHints = g2.getRenderingHints();
        g2.setRenderingHints(renderingHints);

        int size = getIconWidth();
        int arc = size / 3 + 2;

        if (colors == null) {
            Color c = color == null ? BookmarkStandardColors.defaultColor : color;
            g2.setColor(c);
            g2.fillRoundRect(x + 1, y + 1, size - 2, size - 2, arc, arc);
            if (checked == Boolean.TRUE) {
                g2.setColor(checkedColor);
                g2.setStroke(strokeChecked);
                GeneralPath gp = new GeneralPath();
                gp.moveTo(x + 1 + size / 5.0, y + 1 + size * 2 / 5.0);
                gp.lineTo(x + size / 2.0, y - 1 + size * 4 / 5.0);
                gp.lineTo(x - 1 + size * 4 / 5.0, y + 1 + size / 5.0);
                g2.draw(gp);
            }
        } else {
            double w = (size - 2) / (double) colors.length;
            double d = x + 1;
            Shape saveClip = g2.getClip();
            for (Color c : colors) {
                g2.clip(new Rectangle2D.Double(d, y, w, size));
                g2.setColor(c == null ? BookmarkStandardColors.defaultColor : c);
                g2.fillRoundRect(x + 1, y + 1, size - 2, size - 2, arc, arc);
                g2.setClip(saveClip);
                d += w;
            }
        }

        Color colorBorder = comp.getForeground();
        if (colorBorder == null) {
            colorBorder = Color.gray;
        }
        colorBorder = new Color(colorBorder.getRed(), colorBorder.getGreen(), colorBorder.getBlue(), 64);

        g2.setStroke(strokeBorder);
        g2.setColor(colorBorder);
        g2.drawRoundRect(x + 1, y + 1, size - 2, size - 2, arc, arc);

        g2.setRenderingHints(saveHints);
    }

    @Override
    public int getIconWidth() {
        return IconManager.getIconSize() + (checked != null ? 2 : -2);
    }

    @Override
    public int getIconHeight() {
        return getIconWidth();
    }

    public Color getColor() {
        return color;
    }

    public Boolean getChecked() {
        return checked;
    }

    public void setChecked(Boolean checked) {
        this.checked = checked;
    }
}
