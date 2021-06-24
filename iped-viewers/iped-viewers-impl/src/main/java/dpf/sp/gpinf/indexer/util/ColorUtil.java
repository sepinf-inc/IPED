package dpf.sp.gpinf.indexer.util;

import java.awt.Color;

public class ColorUtil {
    public static String getHexRGB(Color c) {
        return "#" + Integer.toHexString(c.getRGB() & 0xFFFFFF);
    }

    public static Color mix(Color c1, Color c2, double weight) {
        if (weight < 0)
            return c2;
        if (weight > 1)
            return c1;

        int r = (int) Math.round(c1.getRed() * weight + c2.getRed() * (1 - weight));
        int g = (int) Math.round(c1.getGreen() * weight + c2.getGreen() * (1 - weight));
        int b = (int) Math.round(c1.getBlue() * weight + c2.getBlue() * (1 - weight));
        return new Color(r, g, b);
    }
}
