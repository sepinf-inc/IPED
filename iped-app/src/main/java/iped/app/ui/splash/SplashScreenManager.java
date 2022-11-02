package iped.app.ui.splash;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.SplashScreen;
import java.awt.geom.Rectangle2D;

import iped.engine.Version;

public class SplashScreenManager {

    public void run() {
        SplashScreen screen = SplashScreen.getSplashScreen();
        if (screen == null) {
            return;
        }

        Thread threadUpdate = new Thread() {
            public void run() {
                try {
                    String msg = "Operação GENESIS - Equipe STS-14";
                    String version = Version.APP_VERSION;

                    Font font = new Font("Arial", Font.BOLD, 24);
                    Color c1 = new Color(15, 45, 60);
                    Color c2 = new Color(240, 240, 245);
                    Color c3 = new Color(60, 120, 150);

                    Dimension d = screen.getSize();
                    Graphics2D g = screen.createGraphics();
                    g.setFont(font);

                    // Version
                    int x = 572;
                    int y = 234;
                    Rectangle2D r = g.getFontMetrics().getStringBounds(version, g);
                    x = (int) (x - r.getWidth());
                    g.setColor(c1);
                    g.drawString(version, x + 1, y + 1);
                    g.setColor(c2);
                    g.drawString(version, x, y);

                    // Custom Message, if defined
                    if (msg != null) {
                        r = g.getFontMetrics().getStringBounds(msg, g);
                        x = (int) (d.getWidth() - r.getWidth()) / 2;
                        y = 340;
                        g.setColor(c1);
                        g.drawString(msg, x + 1, y + 1);
                        g.setColor(c2);
                        g.drawString(msg, x, y);
                    }

                    // Empty progress
                    Rectangle rc = new Rectangle(20, 286, (int) d.getWidth() - 40, 14);
                    g.setColor(c1);
                    g.translate(1, 1);
                    g.draw(rc);
                    g.translate(-1, -1);
                    g.setColor(c3);
                    g.fill(rc);

                    screen.update();

                    int i = 0;
                    g.setColor(c2);
                    String pid = "";
                    while (screen.isVisible()) {
                        Thread.sleep(100);

                        // Update progress
                        int size = StartControl.getCurrentProcessSize();
                        i++;

                        g.setColor(c3);
                        g.fillRect(10, 300, 400, 50);
                        g.setColor(c2);
                        g.drawString(i + " : " + pid + " : " + size, 20, 330);

                        // double pct = Math.min(1, i / 100.0);
                        // Rectangle rcFill = new Rectangle(rc.x + 1, rc.y + 1, (int) ((rc.width - 2) *
                        // pct),
                        // rc.height - 2);
                        // g.fill(rcFill);
                        screen.update();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        threadUpdate.setDaemon(true);
        threadUpdate.start();
    }
}
