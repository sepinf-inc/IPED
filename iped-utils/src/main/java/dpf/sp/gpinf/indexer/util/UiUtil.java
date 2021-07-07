package dpf.sp.gpinf.indexer.util;

import java.awt.Color;

import javax.swing.UIManager;

public class UiUtil {
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

    public static String getUIEmptyHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\""); //$NON-NLS-1$
        Color background = UIManager.getColor("Viewer.background"); //$NON-NLS-1$
        if (background != null) {
            sb.append("background-color:").append(getHexRGB(background)).append(";"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append("\"></body></html>"); //$NON-NLS-1$
        return sb.toString();
    }

    public static String getUIHtmlStyle() {
        StringBuilder sb = new StringBuilder();
        sb.append("data:,.ipedtheme { "); //$NON-NLS-1$
        Color background = UIManager.getColor("Viewer.background"); //$NON-NLS-1$
        if (background != null) {
            sb.append("background-color:").append(getHexRGB(background)).append(" !important;"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        Color foreground = UIManager.getColor("Viewer.foreground"); //$NON-NLS-1$
        if (foreground != null) {
            sb.append("color:").append(getHexRGB(foreground)).append(" !important;"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append("}"); //$NON-NLS-1$
        Color htmlLink = UIManager.getColor("Viewer.htmlLink"); //$NON-NLS-1$
        if (htmlLink != null) {
            sb.append(" .ipedtheme a:link, a:visited {color:").append(getHexRGB(htmlLink)).append(" !important;}"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return sb.toString();
    }
}
