package iped.app.home.processmanager;/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class FailedPanelTab extends DefaultPanel {


    public FailedPanelTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        this.setLayout(new BorderLayout());
        ImageIcon imgLogo = createNewButtonIcon("plug_error.png", new Dimension(400,400));
        JLabel labelLogo = new JLabel(imgLogo, JLabel.CENTER);
        this.add(labelLogo, BorderLayout.CENTER);
        this.add(createButtonpanel(), BorderLayout.LINE_END);
    }

    private JPanel createButtonpanel(){
        JPanel buttonPanel = new JPanel();
        JButton buttonBack = new JButton("Back to case");
        buttonBack.addActionListener(e->{
            mainFrame.showPanel(MainFrameCardsNames.NEW_CASE);
        });
        this.add(buttonBack);
        JButton buttonShowLog = new JButton("Show error log");
        buttonShowLog.addActionListener(e->{
            //((ProcessManagerContainer) this.getParent()).get
        });
        this.add(buttonShowLog);
        return buttonPanel;
    }

    private ImageIcon createNewButtonIcon(String imageFilename, Dimension iconDimension){
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(imageFilename)));
        Image resizedImage = icon.getImage().getScaledInstance( iconDimension.width, iconDimension.height, Image.SCALE_SMOOTH);
        return new ImageIcon(resizedImage);
    }
}
