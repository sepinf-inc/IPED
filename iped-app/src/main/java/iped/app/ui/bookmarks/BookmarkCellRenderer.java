package iped.app.ui.bookmarks;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

import iped.data.IMultiBookmarks;

public class BookmarkCellRenderer {
    private static final RenderingHints renderingHints;

    private String[] names;
    private Color[] colors, foregrounds;
    private final Map<Integer, ClippedString[]> clippedNamesMemo = new HashMap<Integer, ClippedString[]>();
    private int maxSpacing;
    private String lastStr;

    static {
        Map<Key, Object> hints = new HashMap<Key, Object>();
        try {
            @SuppressWarnings("unchecked")
            Map<Key, Object> desktopHints = (Map<Key, Object>) Toolkit.getDefaultToolkit()
                    .getDesktopProperty("awt.font.desktophints");
            if (desktopHints != null) {
                hints.putAll(desktopHints);
            }
        } catch (Exception e) {
        }
        hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        renderingHints = new RenderingHints(hints);
    }

    void setBookmark(String str, Color color) {
        boolean strChanged = false;
        if (!str.equals(lastStr)) {
            strChanged = true;
            if (names == null || names.length != 1) {
                names = new String[1];
            }
            lastStr = names[0] = str;
        }
        if (colors == null || colors.length < names.length) {
            colors = new Color[1];
            foregrounds = new Color[1];
        }
        colors[0] = color;
        foregrounds[0] = BookmarkColorsUtil.getForeground(color);
        if (strChanged) {
            maxSpacing = Integer.MAX_VALUE;
            clippedNamesMemo.clear();
        }
    }

    public void setBookmarks(IMultiBookmarks bookmarks, String str) {
        boolean strChanged = false;
        if (!str.equals(lastStr)) {
            strChanged = true;
            if (str.indexOf(" | ") < 0) {
                if (names == null || names.length != 1) {
                    names = new String[] { str };
                } else {
                    names[0] = str;
                }
            } else {
                names = str.split(" \\| ");
            }
            for (int i = 0; i < names.length; i++) {
                names[i] = names[i].replace("||", "|");
            }
            lastStr = str;
        }
        if (colors == null || colors.length < names.length) {
            colors = new Color[names.length];
            foregrounds = new Color[names.length];
        }
        for (int i = 0; i < names.length; i++) {
            Color c = bookmarks.getBookmarkColor(names[i]);
            c = colors[i] = c == null ? BookmarkStandardColors.defaultColor : c;
            foregrounds[i] = BookmarkColorsUtil.getForeground(c);
        }
        if (strChanged) {
            maxSpacing = Integer.MAX_VALUE;
            clippedNamesMemo.clear();
        }
    }

    private synchronized ClippedString[] getClippedNames(FontMetrics fm, Graphics2D g, int w) {
        if (w > maxSpacing) {
            w = maxSpacing;
        }
        ClippedString[] cs = clippedNamesMemo.get(w);
        if (cs == null) {
            cs = new ClippedString[names.length];
            int x = 0;
            for (int i = 0; i < names.length; i++) {
                String s = names[i].trim();
                Rectangle2D rc = fm.getStringBounds(s, g);
                int rw = (int) rc.getWidth() + 6;
                cs[i] = new ClippedString(s, rw);
                x += rw + 1;
            }
            x--;
            if (x <= w) {
                maxSpacing = w = x;
            } else {
                String el = "...";
                int used = x;
                int minW = 8;
                while (used > w) {
                    int idx = -1;
                    int maxW = minW;
                    for (int i = 0; i < names.length; i++) {
                        ClippedString a = cs[i];
                        if (a.w > maxW && !a.str.isEmpty()) {
                            maxW = a.w;
                            idx = i;
                        }
                    }
                    if (idx == -1) {
                        break;
                    }
                    ClippedString a = cs[idx];
                    if (a.str.length() <= 1) {
                        a.str = "";
                    } else if (a.str.endsWith(el)) {
                        if (a.str.length() <= el.length() + 1) {
                            a.str = a.str.substring(0, 1).trim();
                        } else {
                            a.str = a.str.substring(0, a.str.length() - 1 - el.length()).trim() + el;
                        }
                    } else {
                        a.str = a.str.substring(0, a.str.length() - 1).trim() + el;
                    }
                    if (a.str.isEmpty()) {
                        a.w = minW;
                    } else {
                        Rectangle2D rc = fm.getStringBounds(a.str, g);
                        int rw = (int) rc.getWidth() + 6;
                        if (rw < minW) {
                            rw = minW;
                            a.str = "";
                        }
                        a.w = rw;
                    }
                    used -= maxW - a.w;
                }
            }
            clippedNamesMemo.put(w, cs);
        }
        return cs;
    }

    public void paint(Graphics2D g, int w, int h) {
        RenderingHints saveHints = g.getRenderingHints();
        g.setRenderingHints(renderingHints);
        FontMetrics fm = g.getFontMetrics();
        ClippedString[] cn = getClippedNames(fm, g, w);
        int y = (h - fm.getHeight()) / 2 + fm.getAscent();
        int x = 0;
        for (int i = 0; i < cn.length; i++) {
            ClippedString cs = cn[i];
            g.setColor(colors[i]);
            g.fillRoundRect(x, 1, cs.w, h - 2, h / 2, h / 2);
            g.setColor(foregrounds[i]);
            g.drawString(cs.str, x + 3, y);
            x += cs.w + 1;
        }
        g.setRenderingHints(saveHints);
    }

    private class ClippedString {
        String str;
        int w;

        ClippedString(String str, int w) {
            this.str = str;
            this.w = w;
        }
    }
}
