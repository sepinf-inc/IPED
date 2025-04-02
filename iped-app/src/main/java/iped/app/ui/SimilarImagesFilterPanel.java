package iped.app.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import iped.data.IItem;
import iped.viewers.api.ClearFilterListener;

public class SimilarImagesFilterPanel extends JPanel implements ClearFilterListener {
    private static final long serialVersionUID = -6323740427378842045L;
    private BufferedImage img;
    protected String refName;
    protected boolean isRefExternal;
    private int xc;

    public SimilarImagesFilterPanel() {
        setPreferredSize(new Dimension(50, 32));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                clearFilterAndUpdate();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                if (e.getX() < xc) {
                    setToolTipText(getDefaultToolTip());
                } else {
                    setToolTipText(getRemoveToolTip());
                }
            }
        });
    }

    protected String getDefaultToolTip() {
        String tooltipTitle = Messages.getString("ImageSimilarity.FilterTipTitle");
        String tooltipDescInternal = Messages.getString("ImageSimilarity.FilterTipInternal");
        String tooltipDescExternal = Messages.getString("ImageSimilarity.FilterTipExternal");
        return buildDefaultToolTip(tooltipTitle, tooltipDescInternal, tooltipDescExternal);
    }

    protected String buildDefaultToolTip(String tooltipTitle, String tooltipDescInternal, String tooltipDescExternal) {
        return "<HTML>" + tooltipTitle + "<br>" + (isRefExternal ? tooltipDescExternal : tooltipDescInternal) + ": <b>" + refName + "</b></HTML>";
    }

    protected String getRemoveToolTip() {
        return Messages.getString("ImageSimilarity.RemoveFilter");
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
                    e.printStackTrace();
                }
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (img != null) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int pw = getWidth();
            int ph = getHeight();
            RoundRectangle2D rc = new RoundRectangle2D.Double(1, 1, pw - 3, ph - 2, 4, 4);
            g2.setColor(new Color(245, 245, 245));
            g2.fill(rc);

            double zoom = ph / (double) Math.max(img.getWidth(), img.getHeight());
            int w = (int) Math.round(img.getWidth() * zoom);
            int h = (int) Math.round(img.getHeight() * zoom);
            int x = w >= ph ? 0 : (ph - w) / 2;
            int y = h >= ph ? 0 : (ph - h) / 2;

            Shape clip = g2.getClip();
            g2.setClip(rc);
            g2.drawImage(img, x, y, w, h, null);
            g2.setClip(clip);

            g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Color.red);
            g2.draw(rc);

            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL));
            g2.setColor(Color.darkGray);

            xc = x + w;
            x = (xc + pw) / 2 - 1;
            y = ph / 2;
            int closeSize = 3;
            g2.drawLine(x - closeSize, y - closeSize, x + closeSize, y + closeSize);
            g2.drawLine(x + closeSize, y - closeSize, x - closeSize, y + closeSize);
        }
    }

    protected void clearFilterAndUpdate() {
        SimilarImagesFilterActions.clear(true);
    }

    @Override
    public void clearFilter() {
        SimilarImagesFilterActions.clear(false);
    }
}
