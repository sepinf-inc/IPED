package iped.app.timelinegraph;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JSplitPane;

public class IpedSplitPane extends JSplitPane {

    Component divider = null;
    private int filterMask = InputEvent.SHIFT_DOWN_MASK;
    int lastX = -1;
    int newOrientation = -1;

    public IpedSplitPane() {
        super();

        setResizeWeight(0.85d);

        divider = this.getComponents()[2];

        DividerMouseListener dl = new DividerMouseListener();
        divider.addMouseListener(dl);
        divider.addMouseMotionListener(dl);
        divider.addKeyListener(dl);

    }

    class DividerMouseListener implements MouseListener, MouseMotionListener, KeyListener {

        @Override
        public void mouseClicked(MouseEvent e) {
            int mods = e.getModifiersEx();
            if ((mods & filterMask) == filterMask) {
                if (getOrientation() == JSplitPane.HORIZONTAL_SPLIT) {
                    setOrientation(JSplitPane.VERTICAL_SPLIT);
                    setDividerLocation(getSize().height - 60);
                } else if (getOrientation() == JSplitPane.VERTICAL_SPLIT) {
                    setOrientation(JSplitPane.HORIZONTAL_SPLIT);
                    setDividerLocation(getSize().width - 160);
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }

        @Override
        public void mouseMoved(MouseEvent e) {

        }

        @Override
        public void keyTyped(KeyEvent e) {
            // TODO Auto-generated method stub

        }

        @Override
        public void keyPressed(KeyEvent e) {
        }

        @Override
        public void keyReleased(KeyEvent e) {
        }

    }

}
