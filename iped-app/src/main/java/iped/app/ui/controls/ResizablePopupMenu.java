package iped.app.ui.controls;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ResizablePopupMenu extends JPopupMenu {
    private static final long serialVersionUID = 4807545296165600782L;

    private static final RenderingHints renderingHints;

    static {
        Map<Key, Object> hints = new HashMap<>();
        hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        renderingHints = new RenderingHints(hints);
    }

    private Point mouseStart = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
    private Dimension startSize;
    private boolean isResizing;
    private Color c1, c2;

    public ResizablePopupMenu() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                mouseStart = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
                isResizing = false;
                updateCursor(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                mouseStart = toScreen(e);
                startSize = getSize();
                isResizing = isCorner(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                updateCursor(e);
            }
        });

        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateCursor(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (isResizing) {
                    Point p = toScreen(e);
                    Dimension minDim = getMinimumSize();
                    Dimension newDim = new Dimension(Math.max(minDim.width, startSize.width + p.x - mouseStart.x), Math.max(minDim.height, startSize.height + p.y - mouseStart.y));
                    setPopupSize(newDim);
                }
            }
        });
    }

    @Override
    public void paintChildren(Graphics g) {
        super.paintChildren(g);
        Graphics2D g2 = (Graphics2D) g;
        RenderingHints oldHints = g2.getRenderingHints();
        g2.setRenderingHints(renderingHints);
        int x = getWidth() - 2;
        int y = getHeight() - 2;
        drawDot(g, x - 2, y - 2);
        drawDot(g, x - 6, y - 2);
        drawDot(g, x - 2, y - 6);
        g2.setRenderingHints(oldHints);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        c1 = new Color(240, 240, 240, 200);
        c2 = UIManager.getColor("Resizable.Spot");
    }

    private void drawDot(Graphics g, int x, int y) {
        g.setColor(c1);
        g.fillRect(x, y, 2, 2);
        g.setColor(c2);
        g.fillRect(x - 1, y - 1, 2, 2);
    }

    private boolean isCorner(Point point) {
        if (point == null)
            return false;
        Rectangle resizeSpot = new Rectangle(getWidth() - 8, getHeight() - 8, 8, 8);
        return resizeSpot.contains(point);
    }

    private Point toScreen(MouseEvent e) {
        Point p = e.getPoint();
        SwingUtilities.convertPointToScreen(p, e.getComponent());
        return p;
    }

    private void updateCursor(MouseEvent e) {
        setCursor(Cursor.getPredefinedCursor(isCorner(e.getPoint()) || isResizing ? Cursor.SE_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
    }
}
