package iped.app.home;

/*
 * @created 05/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.Objects;

/**
 * This is the Home Panel - the App start page
 * here we got the app logo and first options
 */
public class HomePanel extends DefaultPanel {

    public HomePanel(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI(){
        this.setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
        this.add(createAppLogoPanel());
        this.add(createOptionsButtonsPanel());
    }

    /**
     * Create and setup IPED Application Logo panel
     * @return JPanel
     */
    private JPanel createAppLogoPanel(){
        JPanel panelLogo = new JPanel();
        panelLogo.setBackground(Color.white);
        JLabel labelLogo = new JLabel();
        URL image = getClass().getResource("IPED-logo_lupa.png");
        assert image != null;
        ImageIcon imgLogo = new ImageIcon(image);
        labelLogo.setIcon(imgLogo);
        panelLogo.add(labelLogo);
        panelLogo.setMinimumSize(new Dimension(851, 174 ));
        panelLogo.setMaximumSize(new Dimension(851, 174 ));
        return panelLogo;
    }

    /**
     * Create and setup the application japnel buttons (new case and config button)
     * @return JPanel
     */
    private JPanel createOptionsButtonsPanel(){
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout( new BoxLayout( optionsPanel, BoxLayout.X_AXIS ) );
        optionsPanel.setBackground(Color.white);
        optionsPanel.add(createNewCasePanelButton());
        optionsPanel.add(Box.createRigidArea(new Dimension(30,0)));
        optionsPanel.add(createConfigPanelButton());
        return optionsPanel;
    }

    /**
     * Create and setup "Star New Case" Button
     * @return JPanel - A JPanel containing a JButton
     */
    private JPanel createNewCasePanelButton(){
        JPanel newCasePanel = new JPanel(new BorderLayout());
        newCasePanel.setMinimumSize( new Dimension(382,382));
        newCasePanel.setMaximumSize( new Dimension(382,382));
        JButton newCaseButton = getNewOptionsButton("INICIAR NOVO CASO", "newcase.png", MainFrameCardsNames.NEW_CASE);
        newCasePanel.add(newCaseButton, BorderLayout.CENTER);
        return newCasePanel;
    }

    /**
     * Create and setup "Config" Button
     * @return - JPanel - A JPanel containing a JButton
     */
    private JPanel createConfigPanelButton(){
        JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.setMaximumSize( new Dimension(382,382));
        JButton configButton = getNewOptionsButton("CONFIGURAÇÕES", "config.png", MainFrameCardsNames.CONFIG);
        configPanel.add(configButton, BorderLayout.CENTER);
        return configPanel;
    }

    /**
     * Create a new JButton with default style and configuration for HomePanel
     * @param buttonText - The text to be displayed on JButton
     * @param iconName - The icon filename  to be used on JButton
     * @param destination - The card destination of button @see MainFrameCardsNames
     * @return - a new JButton to be used as HomePanel Button
     */
    private JButton getNewOptionsButton(String buttonText, String iconName , MainFrameCardsNames destination){
        JButton optionButton = new JButton(buttonText);
        optionButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        optionButton.setOpaque(false);
        optionButton.setContentAreaFilled(false);
        optionButton.setBorderPainted(false);
        optionButton.setFont(new Font("Arial Bold", Font.PLAIN, 28));

        optionButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        optionButton.setHorizontalTextPosition(SwingConstants.CENTER);

        optionButton.setIcon( createNewButtonIcon(iconName) );
        optionButton.addActionListener( e -> mainFrame.showPanel(destination));
        return optionButton;
    }

    /**
     * create a new ImageIcon instance with proper size to the HomePanel
     * @param imageFilename - The icon filename  to be used on JButton
     * @return ImageIcon - A new ImageIcon instance with proper size to the HomePanel
     */
    private ImageIcon createNewButtonIcon(String imageFilename){
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(imageFilename)));
        Image resizedImage = icon.getImage().getScaledInstance( 182, 182, java.awt.Image.SCALE_SMOOTH);
        return new ImageIcon(resizedImage);
    }

}
