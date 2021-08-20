package dpf.sp.gpinf.indexer.ui.controls;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

import dpf.sp.gpinf.indexer.util.UiUtil;

/**
 * A flat button with no background or border. 
 * Border and background are only painted when the mouse is over it, 
 * and with a brighter color when it is pressed. 
 * It is "theme aware", so colors will change if a new theme is activated.
 * Currently it only displays icons, but it could handle text.  
 */
public class HoverButton extends JButton {
    private static final long serialVersionUID = 7827182813873015956L;
    private boolean inside;
    private boolean pressed;
    private Color c1, c2, b1, b2;

    @Override
    public void updateUI() {
        Color c = UIManager.getColor("control");
        if (c == null)
            c = Color.white;
        Color t = UIManager.getColor("text");
        if (t == null)
            t = Color.black;
        b1 = UiUtil.mix(c, t, 0.8);
        b2 = UiUtil.mix(c, t, 0.6);
        c1 = UiUtil.mix(c, Color.white, 0.8);
        c2 = UiUtil.mix(c, Color.white, 0.6);
        super.updateUI();
    }

    public void paint(Graphics g) {
        if (pressed || inside) {
            g.setColor(pressed ? c2 : c1);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(pressed ? b2 : b1);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 2, 2);
        }
        Icon icon = getIcon();
        if (icon != null)
            icon.paintIcon(this, g, (getWidth() - icon.getIconWidth()) / 2, (getHeight() - icon.getIconHeight()) / 2);

    }

    public HoverButton() {
        setContentAreaFilled(false);
        setFocusPainted(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                inside = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                inside = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    pressed = false;
                    repaint();
                }
            }
        });
    }
}
