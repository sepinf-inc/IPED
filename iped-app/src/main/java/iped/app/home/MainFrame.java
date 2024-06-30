package iped.app.home;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.WindowConstants;

import iped.app.home.config.ConfigPanel;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.opencase.OpenCasePanel;
import iped.app.home.processmanager.ProcessManagerContainer;
import iped.app.home.style.StyleManager;
import iped.app.home.utils.CasePathManager;
import iped.app.ui.Messages;
import iped.app.ui.utils.UiScale;
import iped.engine.Version;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocaleConfig;
import iped.engine.task.ParsingTask;
import iped.engine.util.Util;
import iped.exception.UIException;
import iped.utils.ui.ScreenUtils;

/**
 * @created 02/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 *
 *         Home frame for IPED config and case start
 */
public class MainFrame extends JFrame {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static MainFrame instance;

    private final JPanel cardsContentPanel = new JPanel();
    private ProcessManagerContainer pmc;

    public static synchronized MainFrame getInstance() {
        if (instance == null) {
            instance = new MainFrame();
        }
        return instance;
    }

    /**
     * Class constructor
     */
    private MainFrame() {
        super(Version.APP_NAME);
    }

    /**
     * Validate and prepare application frame to be displayed
     */
    private void createAndShowGUI() {

        try {
            checkAppPreRequisites();
            setupLayout();
        } catch (Exception | UIException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // show frame on first screen (if multiple monitors)
        ScreenUtils.showOnScreen(0, this);
    }

    /**
     * Adjust layout configurations, sizes and behaviors
     */
    private void setupLayout() throws Exception {
        UiScale.loadUserSetting();
        this.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets = StyleManager.getDefaultPanelInsets();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.BOTH;
        cardsContentPanel.setBackground(Color.white);
        cardsContentPanel.setLayout(new CardLayout());
        this.add(cardsContentPanel, gbc);

        // Load configurables files
        Configuration configuration = Configuration.getInstance();
        Configuration.getInstance().saveIpedRoot(CasePathManager.getInstance().getConfigPath().getAbsolutePath());
        configuration.loadConfigurables(CasePathManager.getInstance().getConfigPath().getAbsolutePath(), true);

        // Add panels to cardlayout
        cardsContentPanel.add(new HomePanel(this), MainFrameCardsNames.HOME.getName());
        cardsContentPanel.add(new ConfigPanel(this), MainFrameCardsNames.CONFIG.getName());
        cardsContentPanel.add(new NewCaseContainerPanel(this), MainFrameCardsNames.NEW_CASE.getName());
        cardsContentPanel.add(new OpenCasePanel(this), MainFrameCardsNames.OPEN_CASE.getName());

        ParsingTask.setupParsingOptions(ConfigurationManager.get());

        setHomeFrameSize();
        setFrameIcon();

        // set tooltip delay
        ToolTipManager.sharedInstance().setDismissDelay(15000);

        // Dark Theme is still not working properly with config UI...
        // ThemeManager.getInstance().setLookAndFeel();

        // Set the locale used for docking frames, so texts and tool tips are localized
        // (if available)
        LocaleConfig localeConfig = ConfigurationManager.get().findObject(LocaleConfig.class);
        // Set the locale used by JFileChooser's
        JFileChooser.setDefaultLocale(localeConfig.getLocale());
    }

    /**
     * Check if all requisites to show frame are present
     */
    private void checkAppPreRequisites() throws UIException {
        // check JavaFX
        if (!Util.isJavaFXPresent()) {
            throw new UIException(Messages.get("NoJavaFX.Error"));
        }
    }

    /**
     * Set application frame Icon
     */
    private void setFrameIcon() {
        // set frame icon
        URL image = getClass().getResource("search.png"); //$NON-NLS-1$
        assert image != null;
        this.setIconImage(new ImageIcon(image).getImage());
    }

    /**
     * Set Home frame size
     */
    private void setHomeFrameSize() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension preferredSize = new Dimension(1280, 1024);
        Dimension minimumSize = new Dimension(1024, 768);
        this.setPreferredSize(preferredSize);
        this.setMinimumSize(minimumSize);
        this.setMaximumSize(screenSize);
    }

    /**
     * Manage the panel to be displayed on MainFrame based on card name this method
     * is used by other class to navigate, don't change de public accessor method
     * 
     * @param cardName
     *            - Card name of JPanel to be displayed
     */
    public void showPanel(MainFrameCardsNames cardName) {
        ((CardLayout) cardsContentPanel.getLayout()).show(cardsContentPanel, cardName.getName());
    }

    /**
     * Application Start point
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame main = MainFrame.getInstance();
            main.createAndShowGUI();
            main.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        });
    }

}
