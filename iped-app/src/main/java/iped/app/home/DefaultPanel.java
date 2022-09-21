package iped.app.home;/*
 * @created 07/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.style.StyleManager;

import javax.swing.*;
import java.awt.*;

public abstract class DefaultPanel extends JPanel {
    protected MainFrame mainFrame;
    private static final Insets defaultInsets = StyleManager.getDefaultPanelInsets();

    public DefaultPanel(MainFrame mainFrame) {
        super();
        this.setBorder(BorderFactory.createEmptyBorder(defaultInsets.top, defaultInsets.left, defaultInsets.bottom, defaultInsets.right));
        this.mainFrame = mainFrame;
        this.setBackground(Color.white);
        this.createAndShowGUI();
    }

    protected abstract void createAndShowGUI();

}
