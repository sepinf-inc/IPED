package iped.app.home.config;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.lang.SystemUtils;

/*
 * @created 07/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.configurables.AutoCalcSpinnerModel;
import iped.app.home.style.StyleManager;
import iped.app.home.utils.CasePathManager;
import iped.app.ui.Messages;
import iped.configuration.IConfigurationDirectory;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.config.LocaleConfig;
import iped.engine.config.PluginConfig;
import iped.engine.config.ProfileManager;

/**
 * Her we'll manage IPED configuration the main config file is LocalConfig.txt
 */
public class ConfigPanel extends DefaultPanel {

    private JSpinner spinnerThreads;
    private JButton buttonSetDefaultThread;
    private JTextField textFieldIndexTemp;
    private JButton buttonChangeIndexTempPath;
    private JButton buttonSetDefaultIndexTempPath;

    private JCheckBox checkBoxIndexTempOnSSD;
    private JTextField textFieldHashesDB;
    private JButton buttonChangeHashDB;
    private JCheckBox checkBoxEnableDisableHashDb;
    private JTextField textFieldTskJarPath;
    private JButton buttonChangeTskJarPath;

    private JTextField textFieldPluginFolder;
    private JComboBox<Languages> comboBoxLocale;
    private JButton buttonChangePluginConfig;
    private Boolean RUNNIG_ON_WINDOWS;

    private boolean isEnableHashDB;

    private ConfigurationManager defaultConfigurationManager;

    /**
     * Default Constructor
     * 
     * @param mainFrame
     *            - Application MainFrame Reference
     */
    public ConfigPanel(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI() {
        ProfileManager.get().addObject(ProfileManager.get().getDefaultProfile());
        defaultConfigurationManager = ConfigurationManager.get();
        // A listener to force components to load values from file. So if user change
        // values but do not save, whe will load the original properties and discard
        // user changes
        this.addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                updateComponentsState();
            }
        });

        this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        this.add(createTitlePanel());
        this.add(createFormPanel());
        this.add(Box.createVerticalGlue());
        this.add(createButtonsPanel());
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * 
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel() {
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(super.getCurrentBackGroundColor());

        JLabel labelTitle = new JLabel(Messages.get("Home.LocalEnviromentConfiguration"));
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    /**
     * Create instance to form components and set default behavior
     */
    private void createFormComponentInstances() {
        // Default icons to use on JFileChooser
        String ICON_OPEN_FOLDER = "cat/Folders.png";
        String ICON_SET_DEFAULT = "cat/Internet History.png";
        // A variable to check if the app is runnong on Windows. You can use to test the
        // Linux behavior
        RUNNIG_ON_WINDOWS = SystemUtils.IS_OS_WINDOWS;

        // Create combobox to select Languages
        comboBoxLocale = new JComboBox<>(new DefaultComboBoxModel<>(Languages.values()));
        comboBoxLocale.setRenderer(new LanguageComboBoxCellRender());

        // Create JSpinner to change the number of Threads and a button to set the
        // default thread value
        spinnerThreads = new JSpinner();
        spinnerThreads.setModel(new AutoCalcSpinnerModel(spinnerThreads, LocalConfig.DEFAULT_VAL));
        ((JSpinner.DefaultEditor) spinnerThreads.getEditor()).getTextField().setEditable(false);
        buttonSetDefaultThread = getNewIconButton(ICON_SET_DEFAULT);
        buttonSetDefaultThread.setToolTipText(Messages.get("Home.DefaultThreadValue"));
        buttonSetDefaultThread.addActionListener(e -> spinnerThreads.setValue(Runtime.getRuntime().availableProcessors()));

        // Create a TextField to show the IndexTemp Path, a JButton to select a new
        // IndexPath location and a JButton to set the Default indexPath Value
        textFieldIndexTemp = new JTextField();
        textFieldIndexTemp.setEditable(false);
        buttonChangeIndexTempPath = getNewIconButton(ICON_OPEN_FOLDER);
        buttonChangeIndexTempPath.addActionListener(e -> {
            File selectedFile = showDirectoryFileChooser(Messages.get("Home.ChooseDefaultIndexTempFolder"), null);
            textFieldIndexTemp.setText(selectedFile != null ? selectedFile.toPath().toString() : "");
        });
        buttonSetDefaultIndexTempPath = getNewIconButton(ICON_SET_DEFAULT);
        buttonSetDefaultIndexTempPath.setToolTipText(Messages.get("Home.DefaultIndexTempToolTip"));
        buttonSetDefaultIndexTempPath.addActionListener(e -> textFieldIndexTemp.setText("default"));

        // Create a CheckBox to set indexTempOnSSD propertie (Only true or false)
        checkBoxIndexTempOnSSD = new JCheckBox(Messages.get("Home.IndexTempOnSSDLabel"));
        checkBoxIndexTempOnSSD.setBackground(super.getCurrentBackGroundColor());

        // Create a TextField to show the HashDBFile Path, a JButton to select a new
        // HashDBFile location and a CheckBox to enable or disable this option
        textFieldHashesDB = new JTextField();
        textFieldHashesDB.setEditable(false);
        buttonChangeHashDB = getNewIconButton(ICON_OPEN_FOLDER);
        buttonChangeHashDB.addActionListener(e -> {
            File selectedFile = showFileChooser(Messages.get("Home.InformHashDBFile"), "hashes.db", new String[] { "db", "DB" }, Messages.get("Home.Select"));
            textFieldHashesDB.setText(selectedFile != null ? selectedFile.getAbsolutePath() : textFieldHashesDB.getText());
        });
        checkBoxEnableDisableHashDb = new JCheckBox(Messages.get("Home.Active"));
        checkBoxEnableDisableHashDb.setBackground(super.getCurrentBackGroundColor());
        checkBoxEnableDisableHashDb.addItemListener(e -> {
            isEnableHashDB = (e.getStateChange() == ItemEvent.SELECTED);
            checkBoxEnableDisableHashDb.setText(isEnableHashDB ? Messages.get("Home.Active") : Messages.get("Home.Inactive"));
            buttonChangeHashDB.setVisible(isEnableHashDB);
            Color tfBackground = textFieldHashesDB.getBackground();
            textFieldHashesDB.setEnabled(isEnableHashDB);
            textFieldHashesDB.setBackground(tfBackground);
            defaultConfigurationManager.reloadConfigurable(LocalConfig.class);
        });

        // Create a TextField to show TSKJar Path and a button to select a new TSKJar
        // Path
        // Attention -- This TextField can be showed or hide based on operational system
        // in use. This behavior is controlled by createFormPanel() method
        textFieldTskJarPath = new JTextField();
        textFieldTskJarPath.setEditable(false);
        buttonChangeTskJarPath = getNewIconButton(ICON_OPEN_FOLDER);
        buttonChangeTskJarPath.addActionListener(e -> {
            File selectedFile = showFileChooser(Messages.get("Home.TSKJarDialogTtitle"), "sleuthkit-x.x.x.jar", new String[] { "jar", "JAR" }, Messages.get("Home.Select"));
            textFieldTskJarPath.setText(selectedFile != null ? selectedFile.getAbsolutePath() : "");
        });

        // Create a TextField to show the Option Plugin Path and a Button to select a
        // new one
        // Attention -- When user select a new path, we save the relative path and not
        // the absolute path
        textFieldPluginFolder = new JTextField();
        textFieldPluginFolder.setEditable(false);
        buttonChangePluginConfig = getNewIconButton(ICON_OPEN_FOLDER);
        // A listener to convert Absolute path in relative path based on Iped App root
        buttonChangePluginConfig.addActionListener(e -> {
            String appRoot = System.getProperty(IConfigurationDirectory.IPED_APP_ROOT);
            Path basePath = Paths.get(appRoot);
            File selectedFile = showDirectoryFileChooser(Messages.get("Home.OptPluginDialogTtitle"), basePath.getParent().toFile());
            if (selectedFile == null)
                return;
            Path relativePath = basePath.relativize(selectedFile.toPath());
            PluginConfig pluginConfig = defaultConfigurationManager.findObject(PluginConfig.class);
            pluginConfig.setPluginFolder(relativePath.toString());
            textFieldPluginFolder.setText(relativePath.toString());
        });

    }

    /**
     * A form JButton factory
     * 
     * @param iconResourceString
     *            - Icon resource reference
     * @return A JButton instance with the icon
     */
    private JButton getNewIconButton(String iconResourceString) {
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(iped.app.ui.App.class.getResource(iconResourceString)));
        Image resizedImage = icon.getImage().getScaledInstance(15, 15, java.awt.Image.SCALE_SMOOTH);
        JButton button = new JButton();
        button.setIcon(new ImageIcon(resizedImage));
        return button;
    }

    /**
     * A JFiledChoose Factory with the proper behavior to select Directories only
     * 
     * @param title
     *            - File Chooser Title
     * @param baseDirectory
     *            - The base Path to be showed
     * @return - A File instance containing the path selected by user
     */
    private File showDirectoryFileChooser(String title, File baseDirectory) {
        JFileChooser fileChooserDestino = new JFileChooser(baseDirectory != null ? baseDirectory : FileSystemView.getFileSystemView().getHomeDirectory());
        fileChooserDestino.setDialogTitle(title);
        fileChooserDestino.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooserDestino.setAcceptAllFileFilterUsed(false);
        if (fileChooserDestino.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return fileChooserDestino.getSelectedFile();
        }
        return null;
    }

    /**
     * @param title
     *            - File Chooser Title
     * @param fileFilterName
     *            - Name of the filter
     * @param filters
     *            - The filters values
     * @param buttonName
     *            - Select file button name
     * @return The File selected by the user
     */
    private File showFileChooser(String title, String fileFilterName, String[] filters, String buttonName) {
        JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        fileChooser.setDialogTitle(title);
        fileChooser.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter filter = new FileNameExtensionFilter(fileFilterName, filters);
        fileChooser.addChoosableFileFilter(filter);
        int returnValue = fileChooser.showDialog(this, buttonName);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }
        return null;
    }

    /**
     * Create a new JPanel instance containing all inputs
     * 
     * @return JPanel - A JPanel containing all data input form itens
     */
    private JPanel createFormPanel() {
        JPanel panelForm = new JPanel(new GridBagLayout());
        panelForm.setBackground(super.getCurrentBackGroundColor());

        createFormComponentInstances();

        int labelCellColumnWidth = 1;
        double labelsWeightsX = 0;
        double inputsWeightsX = 1.0;
        int linha = 0;

        panelForm.add(new JLabel(Messages.get("Home.Language") + ":"), getGridBagConstraints(0, linha, 1, labelsWeightsX));
        panelForm.add(comboBoxLocale, getGridBagConstraints(1, linha, 1, inputsWeightsX));

        panelForm.add(new JLabel(Messages.get("Home.NumberOfThreads") + ":"), getGridBagConstraints(2, linha, 1, labelsWeightsX));
        JPanel buttonsPanel = getComponentsPanel(spinnerThreads, buttonSetDefaultThread);
        panelForm.add(buttonsPanel, getGridBagConstraints(3, linha, 1, 0));

        linha++;
        panelForm.add(new JLabel(Messages.get("Home.TempFolder") + ":"), getGridBagConstraints(0, linha, 1, labelsWeightsX));
        panelForm.add(textFieldIndexTemp, getGridBagConstraints(1, linha, 2, 0.5));
        buttonsPanel = getComponentsPanel(buttonChangeIndexTempPath, buttonSetDefaultIndexTempPath);
        panelForm.add(buttonsPanel, getGridBagConstraints(3, linha, 1, 0));

        linha++;
        panelForm.add(checkBoxIndexTempOnSSD, getGridBagConstraints(0, linha, 3, labelsWeightsX));

        linha++;
        panelForm.add(new JLabel(Messages.get("Home.HashDatabase") + ":"), getGridBagConstraints(0, linha, labelCellColumnWidth, labelsWeightsX));
        panelForm.add(textFieldHashesDB, getGridBagConstraints(1, linha, 2, inputsWeightsX));
        buttonsPanel = getComponentsPanel(buttonChangeHashDB, checkBoxEnableDisableHashDb);
        panelForm.add(buttonsPanel, getGridBagConstraints(3, linha, 1, 0));

        // On Linux whe need to set the TSK Jar Path
        if (!RUNNIG_ON_WINDOWS) {
            linha++;
            panelForm.add(new JLabel(Messages.get("Home.TSKJar") + ":"), getGridBagConstraints(0, linha, labelCellColumnWidth, labelsWeightsX));
            panelForm.add(textFieldTskJarPath, getGridBagConstraints(1, linha, 2, inputsWeightsX));
            buttonsPanel = getComponentsPanel(buttonChangeTskJarPath);
            panelForm.add(buttonsPanel, getGridBagConstraints(3, linha, 1, 0));
        }

        linha++;
        panelForm.add(new JLabel(Messages.get("Home.OptionalPluginsPath") + ":"), getGridBagConstraints(0, linha, labelCellColumnWidth, labelsWeightsX));
        panelForm.add(textFieldPluginFolder, getGridBagConstraints(1, linha, 2, inputsWeightsX));
        buttonsPanel = getComponentsPanel(buttonChangePluginConfig);
        panelForm.add(buttonsPanel, getGridBagConstraints(3, linha, 1, 0));

        return panelForm;
    }

    /**
     * A simple method to create a JPanel to wrapp all components
     * 
     * @param components
     *            - List of components to be added on JPanel
     * @return A Jpanel with all components
     */
    private JPanel getComponentsPanel(JComponent... components) {
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setBackground(super.getCurrentBackGroundColor());
        for (JComponent button : components)
            buttonsPanel.add(button);
        return buttonsPanel;
    }

    /**
     * Create a new GridBagConstraints to be used on this page Form
     * 
     * @param tableColumnIndex
     *            - The index number of table column
     * @param tableLineIndex
     *            - The index number of table line
     * @param cellWidth
     *            - The table Cell Width
     * @param weightX
     *            - Cell Weight X
     * @return GridBagConstraints - a new GridBagConstraints instance containing all
     *         parameter passed
     */
    private GridBagConstraints getGridBagConstraints(int tableColumnIndex, int tableLineIndex, int cellWidth, double weightX) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = weightX;
        c.gridx = tableColumnIndex;
        c.gridy = tableLineIndex;
        c.gridwidth = cellWidth;
        c.gridheight = 1;
        c.insets = new Insets(2, 10, 2, 10);
        return c;
    }

    /**
     * A JPanel containing "Save" and "Cancel" buttons
     * 
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(super.getCurrentBackGroundColor());
        JButton buttonSave = new JButton(Messages.get("Home.Save"));
        buttonSave.addActionListener(e -> saveConfiguration());
        JButton buttonCancel = new JButton(Messages.get("Home.Cancel"));
        buttonCancel.addActionListener(e -> mainFrame.showPanel(MainFrameCardsNames.HOME));
        panelButtons.add(buttonSave);
        panelButtons.add(buttonCancel);
        return panelButtons;
    }

    public void updateComponentsState() {
        updateLocaleConfigComponentsState();
        updateLocalConfigComponentsState();
        updatePluginConfigComponentsState();
    }

    private void updateLocaleConfigComponentsState() {
        // Load configs Files. Please do not create a global variable for Configurables
        // objects
        LocaleConfig localeConfig = defaultConfigurationManager.findObject(LocaleConfig.class);
        // set values to input texts
        String languageTag = (localeConfig.getLocale() != null) ? localeConfig.getLocale().toLanguageTag() : "";
        comboBoxLocale.setSelectedItem(Languages.getByLanguageTag(languageTag));
    }

    private void updateLocalConfigComponentsState() {
        defaultConfigurationManager.reloadConfigurable(LocalConfig.class);
        LocalConfig localConfig = defaultConfigurationManager.findObject(LocalConfig.class);
        spinnerThreads.setValue(localConfig.getPropertie().getProperty(LocalConfig.NUM_THREADS));
        textFieldIndexTemp.setText((localConfig.getPropertie().getProperty(LocalConfig.IPED_TEMP) == null) ? "Default" : localConfig.getPropertie().getProperty(LocalConfig.IPED_TEMP));
        checkBoxIndexTempOnSSD.setSelected(localConfig.isIndexTempOnSSD());
        File hashDbFile = localConfig.getHashDbFile();
        boolean isHashDBEnabled = hashDbFile != null;
        if (hashDbFile == null) {
            // load commented out hashedDb path from txt file
            localConfig.getPropertie().enableOrDisablePropertie(CasePathManager.getInstance().getLocalConfigFile(), LocalConfig.HASH_DB, false);
            defaultConfigurationManager.reloadConfigurable(LocalConfig.class);
            localConfig = defaultConfigurationManager.findObject(LocalConfig.class);
            hashDbFile = localConfig.getHashDbFile();
            localConfig.getPropertie().enableOrDisablePropertie(CasePathManager.getInstance().getLocalConfigFile(), LocalConfig.HASH_DB, true);
        }
        textFieldHashesDB.setText(hashDbFile != null ? hashDbFile.getAbsolutePath() : "");
        Color tfBackground = textFieldHashesDB.getBackground();
        textFieldHashesDB.setEnabled(isEnableHashDB);
        textFieldHashesDB.setBackground(tfBackground);

        checkBoxEnableDisableHashDb.setSelected(isHashDBEnabled);
        checkBoxEnableDisableHashDb.setText(isHashDBEnabled ? Messages.get("Home.Active") : Messages.get("Home.Inactive"));
        buttonChangeHashDB.setVisible(isHashDBEnabled);
    }

    private void updatePluginConfigComponentsState() {
        PluginConfig pluginConfig = defaultConfigurationManager.findObject(PluginConfig.class);
        if (!RUNNIG_ON_WINDOWS) {
            if (!pluginConfig.isTskJarPathEnabled()) {
                pluginConfig.getPropertie().enableOrDisablePropertie(CasePathManager.getInstance().getLocalConfigFile(), PluginConfig.TSK_JAR_PATH, false);
                defaultConfigurationManager.reloadConfigurable(PluginConfig.class);
                pluginConfig = defaultConfigurationManager.findObject(PluginConfig.class);
            }
            textFieldTskJarPath.setText((pluginConfig.getTskJarFile() != null) ? pluginConfig.getTskJarFile().getAbsolutePath() : "");
        }
        textFieldPluginFolder.setText((pluginConfig.getRelativePluginFolder() != null) ? pluginConfig.getRelativePluginFolder() : "");
    }

    /**
     * Save all changes made by user
     */
    private void saveConfiguration() {
        try {
            // Save LocaleConfig
            LocaleConfig localeConfig = defaultConfigurationManager.findObject(LocaleConfig.class);
            Languages selectedLanguage = ((Languages) comboBoxLocale.getSelectedItem());
            if (!localeConfig.getLocale().toLanguageTag().equals(selectedLanguage.getLanguageTag())) {
                JOptionPane.showMessageDialog(this, Messages.get("Home.LanguageChangeMessage"), Messages.get("Home.LanguageChangeTitle"), JOptionPane.WARNING_MESSAGE);
            }
            localeConfig.setLocale(selectedLanguage.getLanguageTag());
            localeConfig.getPropertie().saveOnFile(CasePathManager.getInstance().getLocalConfigFile());

            // Force LocalConfig to reload possible changes made by LocaleConfig
            defaultConfigurationManager.reloadConfigurable(LocalConfig.class);
            LocalConfig localConfig = defaultConfigurationManager.findObject(LocalConfig.class);
            // Set changes on LocalConfig Configurable class
            localConfig.setIndexTempOnSSD(checkBoxIndexTempOnSSD.isSelected());
            localConfig.getPropertie().setProperty(LocalConfig.IPED_TEMP, textFieldIndexTemp.getText());
            localConfig.setNumThreads(spinnerThreads.getValue());
            localConfig.getPropertie().setProperty(LocalConfig.HASH_DB, textFieldHashesDB.getText());
            localConfig.getPropertie().enableOrDisablePropertie(CasePathManager.getInstance().getLocalConfigFile(), LocalConfig.HASH_DB, !isEnableHashDB);
            // Save LocalConfig modifications to file
            localConfig.getPropertie().saveOnFile(CasePathManager.getInstance().getLocalConfigFile());

            // Force PluginConfig to reload possible changes made by LocalConfig
            defaultConfigurationManager.reloadConfigurable(PluginConfig.class);
            PluginConfig pluginConfig = defaultConfigurationManager.findObject(PluginConfig.class);
            // Set changes on PluginConfig Configurable class
            pluginConfig.setPluginFolder(textFieldPluginFolder.getText());
            if (!RUNNIG_ON_WINDOWS) {
                if ((textFieldTskJarPath.getText() == null) || (!textFieldTskJarPath.getText().trim().isEmpty()))
                    pluginConfig.setTskJarFile(textFieldTskJarPath.getText() != null ? textFieldTskJarPath.getText().trim() : null);
            }
            pluginConfig.getPropertie().saveOnFile(CasePathManager.getInstance().getLocalConfigFile());

            // Force LocalConfig to reload changes made by PluginConfig
            defaultConfigurationManager.reloadConfigurable(LocalConfig.class);
            defaultConfigurationManager.reloadConfigurable(PluginConfig.class);

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        mainFrame.showPanel(MainFrameCardsNames.HOME);
    }

}
