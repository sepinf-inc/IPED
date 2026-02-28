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
import iped.engine.data.Bookmarks;
import iped.app.ui.BookmarkTree;
import iped.app.ui.BookmarksManager;

public class BookmarkCellRenderer {
    private static final RenderingHints renderingHints;

    private String[] names;
    private String[] shortNames;
    private Color[] colors, foregrounds;
    private final Map<Integer, ClippedString[]> clippedNamesMemo = new HashMap<Integer, ClippedString[]>();
    private int maxSpacing;
    private String lastStr;
    private boolean lastShowShortNames;

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
        boolean showShortNames = BookmarksManager.showShortBookmarksNames();
        boolean settingChanged = (showShortNames != lastShowShortNames);
        boolean strChanged = false;        
        if (!str.equals(lastStr) || settingChanged) {
            strChanged = true;
            if (names == null || names.length != 1) {
                names = new String[1];
                shortNames = new String[1];
            }
            names[0] = str;
            shortNames[0] = transformBookmarkName(str, showShortNames);
            lastStr = str;
            lastShowShortNames = showShortNames;
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
        boolean showShortNames = BookmarksManager.showShortBookmarksNames();
        boolean settingChanged = (showShortNames != lastShowShortNames);
        boolean strChanged = false;        
        if (!str.equals(lastStr) || settingChanged) {
            strChanged = true;
            if (str.indexOf(" | ") < 0) {
                if (names == null || names.length != 1) {
                    names = new String[] { str };
                    shortNames = new String[] { str };
                } else {
                    names[0] = str;
                    shortNames[0] = str;
                }
            } else {
                names = str.split(" \\| ");
                shortNames = new String[names.length];
            }
            for (int i = 0; i < names.length; i++) {
                names[i] = names[i].replace("||", "|");
                shortNames[i] = transformBookmarkName(names[i], showShortNames);
            }
            lastStr = str;
            lastShowShortNames = showShortNames;
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

    public String[] getBookmarkNames() {
        return names;
    }

    public String getBookmarkAt(int mouseX, int w) {
        int wKey = (w > maxSpacing) ? maxSpacing : w;
        ClippedString[] cn = clippedNamesMemo.get(wKey);
        if (cn == null) return null;

        int currentX = 0;
        for (int i = 0; i < cn.length; i++) {
            if (mouseX >= currentX && mouseX <= currentX + cn[i].w) {
                return names[i];
            }
            currentX += cn[i].w + 1;
        }
        return null;
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
                String s;
                if (BookmarksManager.showShortBookmarksNames()) {
                    s = shortNames[i].trim();
                }
                else {
                    s = BookmarkTree.displayPath(names[i].trim());
                }
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

    /**
     * Transforms a bookmark name to short format if needed
     */
    private String transformBookmarkName(String name, boolean useShortNames) {
        if (!useShortNames) {
            return name;
        }
        
        StringBuilder displayName = new StringBuilder();
        int ancestorsCount = BookmarkTree.countAncestors(name);
        for (int j = 0; j < ancestorsCount; j++) {
            displayName.append(Bookmarks.PATH_SEPARATOR_DISPLAY);
        }
        if (displayName.length() > 0) {
            displayName.append(" ");
        }
        displayName.append(BookmarkTree.getNameFromPath(name));
        return displayName.toString();
    }
}
