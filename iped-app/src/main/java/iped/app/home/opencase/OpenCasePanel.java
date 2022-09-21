package iped.app.home.opencase;
/*
 * @created 21/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.style.StyleManager;

import javax.swing.*;
import java.awt.*;

/**
 * A panel to manage IPED cases to be opened
 */
public class OpenCasePanel extends DefaultPanel {


    public OpenCasePanel(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    @Override
    protected void createAndShowGUI() {
        this.setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
        this.add(createTitlePanel());
        this.add(Box.createVerticalGlue());
        this.add(createButtonsPanel());
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Abrir Casos");
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    /**
     * A JPanel containing "open" and "Cancel" buttons
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttonOpen = new JButton("Abrir");
        buttonOpen.addActionListener( e -> JOptionPane.showMessageDialog(this, "Abrir o iped search"));
        JButton buttonCancel = new JButton("Cancelar");
        buttonCancel.addActionListener( e -> mainFrame.showPanel(MainFrameCardsNames.HOME));
        panelButtons.add(buttonOpen);
        panelButtons.add(buttonCancel);
        return panelButtons;
    }

}
