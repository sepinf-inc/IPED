package dpf.sp.gpinf.indexer.util;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public class IconUtil {
    private static final Map<String, Icon> memoIcon = new HashMap<String, Icon>();

   /* public static final Icon getIcon(String name, String resPath) {
        return getIcon(name, resPath, 0);
    }*/

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
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
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
}
