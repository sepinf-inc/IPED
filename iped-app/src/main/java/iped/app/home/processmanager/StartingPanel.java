package iped.app.home.processmanager;/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class StartingPanel extends DefaultPanel {


    public StartingPanel(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        ImageIcon imgLogo = createNewButtonIcon("plug-in.png", new Dimension(400,400));
        JLabel labelLogo = new JLabel(imgLogo, JLabel.CENTER);
        this.add(labelLogo);
    }

    private ImageIcon createNewButtonIcon(String imageFilename, Dimension iconDimension){
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(imageFilename)));
        Image resizedImage = icon.getImage().getScaledInstance( iconDimension.width, iconDimension.height, java.awt.Image.SCALE_SMOOTH);
        return new ImageIcon(resizedImage);
    }
}
