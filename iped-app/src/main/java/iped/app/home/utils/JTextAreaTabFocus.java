package iped.app.home.utils;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class JTextAreaTabFocus extends KeyAdapter {

        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_TAB) {
                if (e.getModifiersEx() > 0) {
                    e.getComponent().transferFocusBackward();
                } else {
                    e.getComponent().transferFocus();
                }
                e.consume();
            }
        }

}
