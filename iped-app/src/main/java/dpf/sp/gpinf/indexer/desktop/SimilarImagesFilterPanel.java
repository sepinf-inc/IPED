package dpf.sp.gpinf.indexer.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import iped3.IItem;

public class SimilarImagesFilterPanel extends JPanel {
    private static final long serialVersionUID = -6323740427378842045L;
    private BufferedImage img;
    private String refName;
    private boolean isRefExternal;
    private int xc;

    public SimilarImagesFilterPanel() {
        setPreferredSize(new Dimension(75, 50));
        setMaximumSize(getPreferredSize());
        String removeMsg = Messages.getString("ImageSimilarity.RemoveFilter");
        String tooltipTitle = Messages.getString("ImageSimilarity.FilterTipTitle");
        String tooltipDescInternal = Messages.getString("ImageSimilarity.FilterTipInternal");
        String tooltipDescExternal = Messages.getString("ImageSimilarity.FilterTipExternal");
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getX() >= xc) {
                    firePropertyChange("close", false, true);
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                if (e.getX() < xc) {
                    setToolTipText("<HTML>" + tooltipTitle + "<br>" + (isRefExternal ? tooltipDescExternal
                            : tooltipDescInternal) + ": <b>" + refName + "</b></HTML>");
                } else {
                    setToolTipText(removeMsg);
                }
            }
        });
    }

    public void setCurrentItem(IItem currentItem, boolean external) {
        img = null;
        refName = "";
        if (currentItem != null) {
            byte[] thumb = currentItem.getThumb();
            if (thumb != null) {
                refName = currentItem.getName();
                isRefExternal = external;
                ByteArrayInputStream bais = new ByteArrayInputStream(thumb);
                try {
                    img = ImageIO.read(bais);
                } catch (Exception e) {
                }
            }
        }
        repaint();
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (img != null) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int pw = getWidth();
            int ph = getHeight();
            int dx = 4;
            g2.setColor(Color.white);
            g2.fillRect(dx, 0, pw - dx, ph);

            double zoom = ph / (double) Math.max(img.getWidth(), img.getHeight());
            int w = (int) Math.round(img.getWidth() * zoom);
            int h = (int) Math.round(img.getHeight() * zoom);
            int x = (w >= ph ? 0 : (ph - w) / 2) + dx;
            int y = h >= ph ? 0 : (ph - h) / 2;
            xc = x + w;
            g2.drawImage(img, x, y, w, h, null);

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Color.red);
            g2.drawRect(dx, 0, pw - dx - 1, ph - 1);
            g2.drawRect(dx + 1, 1, pw - dx - 3, ph - 3);

            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL));
            g2.setColor(Color.darkGray);
            x = (xc + pw - 3) / 2;
            y = ph / 2;
            g2.drawLine(x - 4, y - 4, x + 4, y + 4);
            g2.drawLine(x + 4, y - 4, x - 4, y + 4);
        }
    }
}
