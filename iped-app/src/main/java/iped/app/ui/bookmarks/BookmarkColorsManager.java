package iped.app.ui.bookmarks;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BookmarkColorsManager {
    private static final Map<Color, Color> backgroundToForeground = Collections
            .synchronizedMap(new HashMap<Color, Color>());
    static {
        for (int i = 0; i < BookmarkStandardColors.numStandardColors; i++) {
            Color foreground = i * 2 >= BookmarkStandardColors.numStandardColors ? Color.black : Color.white;
            backgroundToForeground.put(BookmarkStandardColors.colors[i], foreground);
        }
    }

    public static Color getInitialColor(Set<Color> usedColors, String name) {
        int off = name.toLowerCase().hashCode() % BookmarkStandardColors.numStandardColors;
        Color ret = BookmarkStandardColors.colors[off];
        for (int i = 0; i < BookmarkStandardColors.numStandardColors; i++) {
            int idx = (off + i) % BookmarkStandardColors.numStandardColors;
            Color c = BookmarkStandardColors.colors[idx];
            if (!usedColors.contains(c)) {
                ret = c;
                break;
            }
        }
        return ret;
    }

    static Color getForeground(Color background) {
        Color foreground = backgroundToForeground.get(background);
        if (foreground == null) {
            int[] c = new int[] { background.getRed(), background.getGreen(), background.getBlue() };
            Arrays.sort(c);
            foreground = c[0] >= 100 && c[1] >= 150 && c[2] >= 200 ? Color.black : Color.white;
            backgroundToForeground.put(background, foreground);
        }
        return foreground;
    }
}
