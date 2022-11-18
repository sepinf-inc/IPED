package iped.app.ui.splash;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.awt.geom.Rectangle2D;

import iped.engine.Version;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.SplashScreenConfig;

public class SplashScreenManager {
    /**
     * This is the total progress, based on the number of the classes loaded in the
     * current (parent) process and in the child process (actual application). A
     * rough estimation is enough to show some progress while loading. This constant
     * may be adjusted in the future, if a larger number of classes are loaded
     * during startup process.
     */
    private static int maxProgress = 2300;

    /**
     * Expected initial progress (roughly).
     */
    private static int minProgress = 0;

    public void start() {
        SplashScreen sc = null;
        try {
            sc = SplashScreen.getSplashScreen();
        } catch (Exception e) {
        }
        if (sc == null) {
            return;
        }

        SplashScreen screen = sc;
        StartUpControlServer server = new StartUpControlServer();

        Thread threadUpdate = new Thread() {
            public void run() {
                try {
                    // Create font and colors used
                    int fontSize = 24;
                    Font font = new Font("Arial", Font.BOLD, fontSize);
                    Color c1 = new Color(15, 45, 60);
                    Color c2 = new Color(250, 250, 255);
                    Color c3 = new Color(60, 120, 150);

                    // Get splash screen dimension and Graphics2D to be rendered
                    Dimension d = screen.getSize();
                    Graphics2D g = screen.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setFont(font);

                    // Get IPED's version and an optional custom message
                    String version = Version.APP_VERSION;
                    String msg = null;
                    SplashScreenConfig config = ConfigurationManager.get().findObject(SplashScreenConfig.class);
                    if (config != null) {
                        msg = config.getMessage();
                    }

                    // Draw version
                    int xv = 485;
                    int yv = 220;
                    Rectangle2D r = g.getFontMetrics().getStringBounds(version, g);
                    xv = (int) (xv - r.getWidth());
                    g.setColor(c1);
                    g.drawString(version, xv + 1, yv + 1);
                    g.setColor(c2);
                    g.drawString(version, xv, yv);

                    // Draw a custom Message, if defined
                    int yp = 282;
                    int hp = 18;
                    int xp = 24;
                    if (msg != null && !msg.isBlank()) {
                        int xm = 0;
                        int ym = 0;
                        while (true) {
                            // Check if the message fits in the window width
                            r = g.getFontMetrics().getStringBounds(msg, g);
                            xm = (int) (d.getWidth() - r.getWidth()) / 2;
                            ym = 280 + (int) (r.getHeight());
                            if (xm >= xp) {
                                break;
                            }

                            // Otherwise, reduce the font size
                            if (--fontSize < 10) {
                                break;
                            }
                            font = new Font("Arial", Font.BOLD, fontSize);
                            g.setFont(font);
                        }

                        g.setColor(c1);
                        g.drawString(msg, xm + 1, ym + 1);
                        g.setColor(c2);
                        g.drawString(msg, xm, ym);
                        yp = 255;
                        hp = 14;
                    }

                    // Draw an empty progress
                    Rectangle rc = new Rectangle(xp, yp, (int) d.getWidth() - 2 * xp, hp);
                    g.setColor(c1);
                    g.translate(1, 1);
                    g.fill(rc);
                    g.translate(-1, -1);
                    g.setColor(c3);
                    g.fill(rc);

                    screen.update();

                    g.setColor(c2);
                    long startTime = System.currentTimeMillis();
                    while (screen.isVisible()) {
                        Thread.sleep(100);

                        // Has child process start up finished?
                        if (server.isFinished()) {
                            screen.close();
                            break;
                        }

                        // Estimate the progress based on the number of loaded classes
                        int progress = 0;

                        // This causes illegal reflective access message on Console
                        // progress += StartUpControl.getCurrentProcessSize();
                        progress += server.getProgress();

                        // Also increment a bit as time goes by
                        progress += (System.currentTimeMillis() - startTime) / 100;

                        // Update the current progress in the splash screen
                        double pct = (progress - minProgress) / (double) (maxProgress - minProgress);
                        if (pct < 0) {
                            pct = 0;
                        } else if (pct > 1) {
                            pct = 1;
                        }
                        Rectangle rcFill = new Rectangle(rc.x + 1, rc.y + 1, (int) ((rc.width - 2) * pct),
                                rc.height - 2);
                        g.fill(rcFill);
                        screen.update();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        threadUpdate.setDaemon(true);
        threadUpdate.start();

        server.start();
    }
}
