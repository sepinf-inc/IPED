package dpf.sp.gpinf.indexer.util;

import java.awt.Color;

public class ColorUtil {
    public static String getHexRGB(Color c) {
        return "#" + Integer.toHexString(c.getRGB() & 0xFFFFFF);
    }
}
