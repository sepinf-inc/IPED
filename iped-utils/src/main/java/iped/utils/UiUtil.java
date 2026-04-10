package iped.utils;

import java.awt.Color;

import javax.swing.UIManager;

public class UiUtil {
    public static String getHexRGB(Color c) {
        return "#" + Integer.toHexString(c.getRGB() & 0xFFFFFF);
    }

    public static Color shift(Color c, int delta) {
        int r = Math.max(0, Math.min(255, c.getRed() + delta));
        int g = Math.max(0, Math.min(255, c.getGreen() + delta));
        int b = Math.max(0, Math.min(255, c.getBlue() + delta));
        return new Color(r, g, b, c.getAlpha());
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
        return getUIEmptyHtml(null);
    }

    public static String getUIEmptyHtml(String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"");
        Color background = UIManager.getColor("Viewer.background");
        if (background == null) {
            background = Color.white;
        }
        sb.append("background-color:").append(getHexRGB(background)).append(";");
        sb.append("\">");
        if (msg != null && !msg.isBlank()) {
            sb.append("<div style=\"font:12px sans-serif;margin:8px;text-align:center;");
            Color foreground = UIManager.getColor("Viewer.foreground");
            if (foreground == null) {
                foreground = Color.black;
            }
            Color c = mix(foreground, background, 0.5);
            sb.append("color:");
            sb.append(getHexRGB(c));
            sb.append(";");
            sb.append("\">").append(msg).append("</p>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    public static String getUIHtmlStyle() {
        Color background = UIManager.getColor("Viewer.background"); //$NON-NLS-1$
        Color foreground = UIManager.getColor("Viewer.foreground"); //$NON-NLS-1$
        Color htmlLink = UIManager.getColor("Viewer.htmlLink"); //$NON-NLS-1$

        if (background == null) {
            background = Color.white;
        }
        if (foreground == null) {
            foreground = Color.black;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("data:,.ipedtheme { "); //$NON-NLS-1$
        sb.append("background-color:").append(getHexRGB(background)).append(" !important;"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("color:").append(getHexRGB(foreground)).append(" !important;"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("}"); //$NON-NLS-1$

        if (htmlLink != null) {
            sb.append(" .ipedtheme a:link, a:visited {color:").append(getHexRGB(htmlLink)).append(" !important;}"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return sb.toString();
    }
}
