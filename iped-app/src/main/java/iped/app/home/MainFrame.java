package iped.app.home;

import iped.app.home.config.ConfigPanel;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.ui.Messages;
import iped.app.ui.themes.ThemeManager;
import iped.engine.Version;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocaleConfig;
import iped.engine.util.Util;
import iped.exception.UIException;
import iped.utils.ui.ScreenUtils;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * @created 02/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 *
 * Home frame for IPED config and case start
 */
public class MainFrame extends JFrame {

    private final JPanel cardsContentPanel = new JPanel();

    /**
     * Class constructor
     */
    public MainFrame() {
        super(Version.APP_NAME);
        this.createAndShowGUI();
    }

    /**
     * Validate and prepare application frame to be displayed
     */
    public void createAndShowGUI(){

        try {
            checkAppPreRequisites();
            setupLayout();
        } catch (Exception | UIException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        //show frame on first screen (if multiple monitors)
        ScreenUtils.showOnScreen(0, this);
    }

    /**
     *Adjust layout configurations, sizes and behaviors
     */
    private void setupLayout() throws Exception {
        this.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx=1.0;
        gbc.weighty=1.0;
        gbc.insets = new Insets(30,30,30,30);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.BOTH;
        cardsContentPanel.setBackground(Color.white);
        cardsContentPanel.setLayout(new CardLayout());
        this.add(cardsContentPanel, gbc);

        //Add panels to cardlayout
        cardsContentPanel.add(new HomePanel(this), MainFrameCardsNames.HOME.getName());
        cardsContentPanel.add(new ConfigPanel(this), MainFrameCardsNames.CONFIG.getName());
        cardsContentPanel.add(new NewCaseContainerPanel(this), MainFrameCardsNames.NEW_CASE.getName());

        setHomeFrameSize();
        setFrameIcon();
        //set tooltip delay
        ToolTipManager.sharedInstance().setInitialDelay(10);
        ThemeManager.getInstance().setLookAndFeel();
        //FIXME Remove hardcoded location and set properly path
        Configuration.getInstance().loadConfigurables("C:/Users/xxx/Documents/projetos/IPED/target/release/iped-4.0.2", true);

        // Set the locale used for docking frames, so texts and tool tips are localized (if available)
        LocaleConfig localeConfig = ConfigurationManager.get().findObject(LocaleConfig.class);
        // Set the locale used by JFileChooser's
        JFileChooser.setDefaultLocale(localeConfig.getLocale());
    }

    /**
     * Check if all requisites to show frame are present
     */
    private void checkAppPreRequisites() throws UIException {
        //check JavaFX
        if (!Util.isJavaFXPresent()) {
            throw new UIException(Messages.get("NoJavaFX.Error"));
        }
    }

    /**
     * Set application frame Icon
     */
    private void setFrameIcon(){
        //set frame icon
        URL image = getClass().getResource("search.png"); //$NON-NLS-1$
        assert image != null;
        this.setIconImage(new ImageIcon(image).getImage());
    }

    /**
     * Set Home frame size
     */
    private void setHomeFrameSize(){
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension preferredSize = new Dimension(1280, 1024);
        Dimension minimumSize = new Dimension(1024, 768);
        this.setPreferredSize( preferredSize );
        this.setMinimumSize(minimumSize);
        this.setMaximumSize(screenSize);
    }

    /**
     * Manage the panel to be displayed on MainFrame based on card name
     * this method is used by other class to navigate, don't change de public accessor method
     * @param cardName - Card name of JPanel to be displayed
     */
    public void showPanel(MainFrameCardsNames cardName){
        ((CardLayout) cardsContentPanel.getLayout()).show(cardsContentPanel, cardName.getName());
    }

    /**
     * Application Start point
     */
    public static void main(String[] args) {
        MainFrame main = new MainFrame();
        main.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        main.setVisible(true);
    }

}
