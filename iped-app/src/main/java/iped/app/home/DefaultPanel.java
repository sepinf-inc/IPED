package iped.app.home;/*
 * @created 07/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import javax.swing.*;
import java.awt.*;

public abstract class DefaultPanel extends JPanel {
    protected MainFrame mainFrame;

    public DefaultPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.setBackground(Color.white);
        this.createAndShowGUI();
    }

    protected abstract void createAndShowGUI();

}
