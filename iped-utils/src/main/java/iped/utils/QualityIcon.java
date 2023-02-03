package iped.utils;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public class QualityIcon implements Icon {
    private final ImageIcon icon;
    private final BufferedImage img;
    private int w, h;

    public QualityIcon(ImageIcon icon, int size) {
        this.icon = icon;
        this.img = null;
        w = h = size;
    }

    public QualityIcon(BufferedImage img, int w, int h) {
        this.icon = null;
        this.img = img;
        this.w = w;
        this.h = h;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (img == null)
            g2.drawImage(((ImageIcon) icon).getImage(), x, y, w, h, c);
        else 
            g2.drawImage(img, x, y, w, h, c);
    }

    public int getIconWidth() {
        return w;
    }

    public int getIconHeight() {
        return h;
    }

    public void setSize(int size) {
        w = h = size;
    }
}
