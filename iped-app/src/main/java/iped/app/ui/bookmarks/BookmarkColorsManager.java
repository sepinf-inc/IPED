package iped.app.ui.bookmarks;

import java.awt.Color;
import java.util.Set;

public class BookmarkColorsManager {
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
}
