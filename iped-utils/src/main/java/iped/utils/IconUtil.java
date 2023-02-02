package iped.utils;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

public class IconUtil {
    private static final Map<String, Icon> memoIcon = new HashMap<String, Icon>();
    private static final int toolbarIconSize = 16;

    public static final Icon getToolbarIcon(String name, String resPath) {
        return getIcon(name, resPath, toolbarIconSize);
    }

    public static final Icon getIcon(String name, String resPath, int iconSize) {
        String key = resPath + "/" + name + "/" + iconSize;
        synchronized (memoIcon) {
            if (memoIcon.containsKey(key)) {
                return memoIcon.get(key);
            }
        }
        try {
            ImageIcon orgIcon = new ImageIcon(IconUtil.class.getResource(resPath + name + ".png"));
            Icon resizedIcon = orgIcon;
            if (iconSize > 0) {
                double zoom = Math.min(iconSize / (double) orgIcon.getIconWidth(),
                        iconSize / (double) orgIcon.getIconHeight());
                int w = (int) Math.round(zoom * orgIcon.getIconWidth());
                int h = (int) Math.round(zoom * orgIcon.getIconHeight());
                resizedIcon = new Icon() {
                    @Override
                    public void paintIcon(Component c, Graphics g, int x, int y) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.drawImage(orgIcon.getImage(), x, y, w, h, null);
                    }

                    @Override
                    public int getIconWidth() {
                        return w;
                    }

                    @Override
                    public int getIconHeight() {
                        return h;
                    }
                };
            }
            synchronized (memoIcon) {
                memoIcon.put(key, resizedIcon);
            }
            return resizedIcon;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR));
    }

    /**
     * Create a list of images with different resolutions, from a source image. This
     * method is intended to get images used as applications icons, passing the
     * return list to JFrame.setIconImages().
     */
    public static List<BufferedImage> getIconImages(String name, String resPath) {
        try {
            URL icon = IconUtil.class.getResource(resPath + "/" + name + ".png");
            BufferedImage src = ImageIO.read(icon);
            List<BufferedImage> targets = new ArrayList<BufferedImage>();
            targets.add(src);
            // Create images for all even values up to 16
            int start = (src.getWidth() & ~1) - 2;
            for (int size = start; size >= 16; size -= 2) {
                BufferedImage img = null;
                try {
                    icon = IconUtil.class.getResource(resPath + "/" + name + size + ".png");
                    img = ImageIO.read(icon);
                } catch (Exception e) {
                    img = ImageUtil.resizeImage(src, size, size);
                }
                targets.add(img);
            }
            Collections.reverse(targets);
            return targets;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
