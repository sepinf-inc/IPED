package iped.app.home.config;

/*
 * @created 07/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.style.StyleManager;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.config.LocaleConfig;
import iped.engine.config.PluginConfig;

import javax.swing.*;
import java.awt.*;

/**
 * Her we'll manage IPED configuration
 * the main config file is LocalConfig.txt
 */
public class ConfigPanel extends DefaultPanel {

    private JTextField textFieldNumThreads;
    private JTextField textFieldIndexTemp;
    private JCheckBox checkBoxSSD;
    private JTextField textFieldHashesDB;
    private JTextField textFieldTskJarPath;
    private JTextField textFieldMplayerPath;
    private JTextField textFieldPluginFolder;
    private JTextField textFieldRegripperFolder;
    private JComboBox comboBoxLocale;

    private LocaleConfig localeConfig;
    private PluginConfig pluginConfig;
    private LocalConfig localConfig;

    public ConfigPanel(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI(){
        this.setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );
        this.add(createTitlePanel());
        this.add(createFormPanel());
        this.add(Box.createVerticalGlue());
        this.add(createButtonsPanel());
        this.loadLocalConfigFile();
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Configurações de ambiente");
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private void createFormComponentInstances(){
        comboBoxLocale = new JComboBox();
        comboBoxLocale.setModel( new DefaultComboBoxModel<Languages>( Languages.values() ) );
        comboBoxLocale.setRenderer(new LanguageComboBoxCellRender());

        textFieldNumThreads = new JTextField();
        textFieldIndexTemp = new JTextField();
        checkBoxSSD = new JCheckBox("É um SSD?");
        checkBoxSSD.setBackground(Color.white);
        textFieldHashesDB = new JTextField();
        textFieldTskJarPath = new JTextField();
        textFieldMplayerPath = new JTextField();
        textFieldPluginFolder = new JTextField();
        textFieldRegripperFolder = new JTextField();
    }

    /**
     * Create a new JPanel instance containing all inputs
     * @return JPanel - A JPanel containing all data input form itens
     */
    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel(new GridBagLayout());
        panelForm.setBackground(Color.white);

        createFormComponentInstances();

        int labelCellColumnWidth = 1;
        int inputCellColumnWidth = 3;
        double labelsWeightx = 0;
        double inputsWeightx = 1.0;
        int linha = 0;

        panelForm.add(new JLabel("Idioma:"), getGridBagConstraints(0, linha, 1, labelsWeightx));
        panelForm.add(comboBoxLocale, getGridBagConstraints(1, linha, 1, inputsWeightx));

        panelForm.add(new JLabel("N de Threads:"), getGridBagConstraints(2, linha, 1, labelsWeightx));
        panelForm.add(textFieldNumThreads, getGridBagConstraints(3, linha, 1, 0));

        linha++;
        panelForm.add(new JLabel("Diretório para temporários:"), getGridBagConstraints(0, linha, 1, labelsWeightx));
        panelForm.add(textFieldIndexTemp, getGridBagConstraints(1, linha, 2, 0.5));
        GridBagConstraints c = getGridBagConstraints(3, linha, 1, 0);
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_END;
        panelForm.add(checkBoxSSD, c);

        linha++;
        panelForm.add(new JLabel("Banco de dados de Hash:"), getGridBagConstraints(0, linha, labelCellColumnWidth, labelsWeightx));
        panelForm.add(textFieldHashesDB, getGridBagConstraints(1, linha, inputCellColumnWidth, inputsWeightx));

        if (System.getProperty("os.name").startsWith("Linux")) {
            linha++;
            panelForm.add(new JLabel("JAR do SleuthKit:"), getGridBagConstraints(0, linha, labelCellColumnWidth, labelsWeightx));
            panelForm.add(textFieldTskJarPath, getGridBagConstraints(1, linha, inputCellColumnWidth, inputsWeightx));
        }

        linha++;
        panelForm.add(new JLabel("Executável do MPlayer:"), getGridBagConstraints(0, linha, labelCellColumnWidth, labelsWeightx));
        panelForm.add(textFieldMplayerPath, getGridBagConstraints(1, linha, inputCellColumnWidth, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("Diretório de Plugins opcionais:"), getGridBagConstraints(0, linha, labelCellColumnWidth, labelsWeightx));
        panelForm.add(textFieldPluginFolder, getGridBagConstraints(1, linha, inputCellColumnWidth, inputsWeightx));

        linha++;
        panelForm.add(new JLabel("Diretório do RegRipper:"), getGridBagConstraints(0, linha, labelCellColumnWidth, labelsWeightx));
        panelForm.add(textFieldRegripperFolder, getGridBagConstraints(1, linha, inputCellColumnWidth, inputsWeightx));

        return panelForm;
    }

    /**
     * Create a new GridBagConstraints to be used on this page Form
     * @param tableColumnIndex - The index number of table column
     * @param tableLineIndex - The index number of table line
     * @param cellWidth - The table Cell Width
     * @param weightX - Cell Weight X
     * @return GridBagConstraints - a new GridBagConstraints instance containing all parameter passed
     */
    private GridBagConstraints getGridBagConstraints(int tableColumnIndex, int tableLineIndex, int cellWidth, double weightX) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = weightX;
        c.gridx = tableColumnIndex;
        c.gridy = tableLineIndex;
        c.gridwidth = cellWidth;
        c.gridheight = 1;
        c.insets = new Insets(2, 10,2, 10);
        return c;
    }

    /**
     * A JPanel containing "Save" and "Cancel" buttons
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttonSave = new JButton("Salvar");
        buttonSave.addActionListener( e -> saveConfiguration());
        JButton buttonCancel = new JButton("Cancelar");
        buttonCancel.addActionListener( e -> mainFrame.showPanel(MainFrameCardsNames.HOME));
        panelButtons.add(buttonSave);
        panelButtons.add(buttonCancel);
        return panelButtons;
    }

    public void loadLocalConfigFile(){
        //Load configs Files
        localeConfig = ConfigurationManager.get().findObject(LocaleConfig.class);
        pluginConfig = ConfigurationManager.get().findObject(PluginConfig.class);
        localConfig = ConfigurationManager.get().findObject(LocalConfig.class);

        //set values to input texts
        String languageTag = (localeConfig.getLocale() != null )? localeConfig.getLocale().toLanguageTag() : "";
        comboBoxLocale.setSelectedItem(Languages.getByLanguageTag(languageTag));
        textFieldNumThreads.setText( String.valueOf(localConfig.getNumThreads()) );
        textFieldIndexTemp.setText( (localConfig.getIndexTemp() == null ) ? "Default" : localConfig.getIndexTemp().getAbsolutePath());
        checkBoxSSD.setSelected( localConfig.isIndexTempOnSSD() );
        textFieldHashesDB.setText( (localConfig.getHashDbFile() == null ) ? "" : localConfig.getHashDbFile().getAbsolutePath() );
        textFieldTskJarPath.setText( (pluginConfig.getTskJarFile() != null )? pluginConfig.getTskJarFile().getAbsolutePath() : "" );
        textFieldPluginFolder.setText( (pluginConfig.getPluginFolder() != null )? pluginConfig.getPluginFolder().getPath() : "" );
        //FIXME Waiting issue #1320
        textFieldMplayerPath.setText( localConfig.getMplayerWinPath() );
        //FIXME Waiting issue #331
        textFieldRegripperFolder.setText( "" );

    }

    private void saveConfiguration(){

        //TODO save properties on file


        mainFrame.showPanel(MainFrameCardsNames.HOME);
    }

}
