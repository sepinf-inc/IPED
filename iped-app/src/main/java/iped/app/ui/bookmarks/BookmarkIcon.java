package iped.app.ui.bookmarks;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import iped.app.ui.IconManager;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;

public class BookmarkIcon implements Icon {
    public static final String columnName = "$BookmarkIcon";

    private static final Map<Color, Icon> iconPerColor = new HashMap<Color, Icon>();
    private static final Color colorShadow = new Color(64, 64, 64, 192);
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

    private final Color color;

    public static Icon getIcon(IMultiBookmarks bookmarks, IItemId id) {
        if (id == null || bookmarks == null) {
            return null;
        }
        List<String> l = bookmarks.getBookmarkList(id);
        if (l == null || l.isEmpty()) {
            return null;
        }
        if (l.size() == 1) {
            return getIcon(bookmarks.getBookmarkColor(l.get(0)));
        }
        
        //TODO: [#1866] Handle multiple bookmarks
        return getIcon(bookmarks.getBookmarkColor(l.get(0)));
    }
    
    public static synchronized Icon getIcon(Color color) {
        Icon icon = iconPerColor.get(color);
        if (icon == null) {
            icon = new BookmarkIcon(color);
            iconPerColor.put(color, icon);
        }
        return icon;
    }

    private BookmarkIcon(Color color) {
        this.color = color;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g;
        RenderingHints saveHints = g2.getRenderingHints();
        AffineTransform saveTransform = g2.getTransform();

        g2.setRenderingHints(renderingHints);
        g2.translate(x, y);

        int size = getIconWidth();
        int arc = size / 3 + 2;

        g.setColor(colorShadow);
        g.fillRoundRect(2, 2, size - 4, size - 4, arc, arc);

        g.setColor(color == null ? BookmarkStandardColors.defaultColor : color);
        g.fillRoundRect(1, 1, size - 4, size - 4, arc, arc);

        g2.setTransform(saveTransform);
        g2.setRenderingHints(saveHints);
    }

    @Override
    public int getIconWidth() {
        return IconManager.getIconSize();
    }

    @Override
    public int getIconHeight() {
        return getIconWidth();
    }
}
