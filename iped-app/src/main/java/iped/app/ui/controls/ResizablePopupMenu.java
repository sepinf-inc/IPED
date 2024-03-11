package iped.app.ui.controls;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ResizablePopupMenu extends JPopupMenu {
    private static final long serialVersionUID = 4807545296165600782L;

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
                    Dimension newDim = new Dimension(Math.max(minDim.width, startSize.width + p.x - mouseStart.x),
                            Math.max(minDim.height, startSize.height + p.y - mouseStart.y));
                    setPopupSize(newDim);
                }
            }
        });
    }

    @Override
    public void paintChildren(Graphics g) {
        super.paintChildren(g);
        int x = getWidth() - 2;
        int y = getHeight() - 2;
        drawDot(g, x - 2, y - 2);
        drawDot(g, x - 6, y - 2);
        drawDot(g, x - 2, y - 6);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        c1 = UIManager.getColor("nimbusLightBackground");
        if (c1 == null) {
            c1 = Color.white;
        }
        c2 = UIManager.getColor("activeCaption");
        if (c2 == null) {
            c2 = Color.lightGray;
        }
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
        setCursor(Cursor.getPredefinedCursor(
                isCorner(e.getPoint()) || isResizing ? Cursor.SE_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
    }
}
