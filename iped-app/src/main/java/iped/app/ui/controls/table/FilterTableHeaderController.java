package iped.app.ui.controls.table;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;

import javax.swing.table.JTableHeader;

public class FilterTableHeaderController {
    private static MouseListener[] headerMouseListeners;
    private static FilterTableHeaderRenderer renderer;

    public static synchronized void init(JTableHeader header) {
        headerMouseListeners = header.getMouseListeners();
        for (MouseListener ml : headerMouseListeners) {
            header.removeMouseListener(ml);
        }
        header.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                if (renderer != null) {
                    renderer.update(e, 0);
                }
            }
        });
        header.addMouseListener(new MouseListener() {
            public void mouseExited(MouseEvent e) {
                if (renderer != null) {
                    renderer.clear();
                }
                for (MouseListener ml : headerMouseListeners) {
                    ml.mouseExited(e);
                }
            }

            public void mouseEntered(MouseEvent e) {
                if (renderer != null) {
                    renderer.update(e, 0);
                }
                for (MouseListener ml : headerMouseListeners) {
                    ml.mouseEntered(e);
                }
            }

            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (renderer != null && renderer.update(e, 1)) {
                        e.consume();
                        return;
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    if (renderer != null && renderer.update(e, 3)) {
                        e.consume();
                        return;
                    }
                }
                for (MouseListener ml : headerMouseListeners) {
                    ml.mouseClicked(e);
                }
            }

            public void mousePressed(MouseEvent e) {
                for (MouseListener ml : headerMouseListeners) {
                    ml.mousePressed(e);
                }
            }

            public void mouseReleased(MouseEvent e) {
                for (MouseListener ml : headerMouseListeners) {
                    ml.mouseReleased(e);
                }
            }
        });
    }

    public static synchronized void setRenderer(JTableHeader header, FilterTableHeaderRenderer newRenderer) {
        renderer = newRenderer;
        MouseListener[] newMouseListeners = header.getMouseListeners();
        for (int i = 0; i < headerMouseListeners.length; i++) {
            for (MouseListener ml : newMouseListeners) {
                if (headerMouseListeners[i].getClass().equals(ml.getClass())) {
                    headerMouseListeners[i] = ml;
                    header.removeMouseListener(ml);
                    break;
                }
            }
        }
    }
}
