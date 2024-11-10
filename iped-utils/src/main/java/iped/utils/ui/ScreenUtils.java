package iped.utils.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @created 05/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 *
 * Utility class to manage frame positioning on multiple monitors
 */
public class ScreenUtils {

    /**
     * Place frame on the given Monitor/Screen
     * @param screen - Number of target Monitor/Screen
     * @param frame - JFrame references to be managed
     */
    public static void showOnScreen(int screen, JFrame frame ) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gd = ge.getScreenDevices();
        int width, height;
        if( screen > -1 && screen < gd.length ) {
            width = gd[screen].getDefaultConfiguration().getBounds().width;
            height = gd[screen].getDefaultConfiguration().getBounds().height;
            frame.setLocation(
                ((width / 2) - (frame.getSize().width / 2)) + gd[screen].getDefaultConfiguration().getBounds().x,
                ((height / 2) - (frame.getSize().height / 2)) + gd[screen].getDefaultConfiguration().getBounds().y
            );
            frame.setVisible(true);
        } else {
            throw new RuntimeException( "No Screens/Monitors Found" );
        }
    }

    /**
     * Place frame on the given Monitor/Screen with margins
     * @param screen - Number of target Monitor/Screen
     * @param frame - JFrame references to be managed
     * @param marginx - Horizontal margin
     * @param marginy - Vertical margin
     */
    public static void showOnScreen(int screen, JFrame frame, int marginx, int marginy) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gd = ge.getScreenDevices();
        int width, height;
        if( screen > -1 && screen < gd.length ) {
            width = gd[screen].getDefaultConfiguration().getBounds().width;
            height = gd[screen].getDefaultConfiguration().getBounds().height;
            frame.setLocation(
                ((width / 2) - (frame.getSize().width / 2) + marginx) + gd[screen].getDefaultConfiguration().getBounds().x,
                ((height / 2) - (frame.getSize().height / 2) + marginy) + gd[screen].getDefaultConfiguration().getBounds().y
            );
            frame.setVisible(true);
        } else {
            throw new RuntimeException( "No Screens/Monitors Found" );
        }
    }

}
