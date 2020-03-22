package dpf.sp.gpinf.indexer.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

public class IconUtil {
    private static final Map<String, ImageIcon> memoIcon = new HashMap<String, ImageIcon>();
    private static final String resPath = "/dpf/sp/gpinf/indexer/search/viewer/res/";

    public static final ImageIcon getIcon(String name) {
        return getIcon(name, 0);
    }

    public static final ImageIcon getIcon(String name, int iconSize) {
        String key = name + iconSize;
        synchronized (memoIcon) {
            if (memoIcon.containsKey(key)) {
                return memoIcon.get(key);
            }
        }
        try {
            ImageIcon icon = new ImageIcon(IconUtil.class.getResource(resPath + name + ".png"));
            if (iconSize > 0) {
                double zoom = Math.min(iconSize / (double) icon.getIconWidth(), iconSize / (double) icon.getIconHeight());
                int w = (int) Math.round(zoom * icon.getIconWidth());
                int h = (int) Math.round(zoom * icon.getIconHeight());
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
                Graphics2D g2 = (Graphics2D) img.getGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(icon.getImage(), 0, 0, w, h, null);
                icon = new ImageIcon(img);
                g2.dispose();
            }
            synchronized (memoIcon) {
                memoIcon.put(key, icon);
            }
            return icon;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR));
    }
}
