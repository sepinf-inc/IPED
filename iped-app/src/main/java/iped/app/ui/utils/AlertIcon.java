package iped.app.ui.utils;

import javax.swing.*;
import java.awt.*;

public class AlertIcon implements Icon {
    private final Icon base;
    private final Color alertColor = Color.RED;

    public AlertIcon(Icon base) {
        this.base = base;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        // 1. Draw the original bookmark icon
        base.paintIcon(c, g, x, y);

        // 2. Prepare to draw the exclamation circle
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int size = 10; // size of the alert circle
        int posX = x + getIconWidth() - size + 2;
        int posY = y - 1;

        // Draw red circle
        g2.setColor(alertColor);
        g2.fillOval(posX, posY, size, size);

        // Draw white exclamation mark
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 8));
        g2.drawString("!", posX + size/2, posY + size - 2);

        g2.dispose();
    }

    @Override
    public int getIconWidth() { return base.getIconWidth(); }

    @Override
    public int getIconHeight() { return base.getIconHeight(); }
}