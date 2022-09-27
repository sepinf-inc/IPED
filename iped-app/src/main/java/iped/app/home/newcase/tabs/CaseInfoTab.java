package iped.app.home.newcase.tabs;/*
 * @created 08/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.model.CaseInfo;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.style.StyleManager;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;

/**
 * Case info TAB
 */
public class CaseInfoTab extends DefaultPanel {

    private LocalConfig localConfig;

    private JTextField textFieldCaseOutput;
    private JCheckBox checkBoxOutputOnSSD;
    private JButton buttonSelectCaseOutput;
    private JTextField textFieldCaseNumber;
    private JTextField textFieldCaseName;
    private JTextArea textAreaInvestigatedNames;
    private JTextField textFieldRequestDate;
    private JTextField textFieldDemandant;
    private JTextField textFieldOrganization;
    private JTextArea textAreaExaminerNames;
    private JTextField textFieldContact;
    private JTextArea textAreaCaseNotes;

    private String OutpuOnSSDTooltip = "<html>Ative se a pasta de saída do caso estiver em um SSD.<br>Se ativado, o índice é criado diretamente na pasta do caso, não em indexTemp,  portanto, você precisará de menos espaço livre na pasta temp.</html>";


    public CaseInfoTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI(){
        this.setLayout( new BorderLayout() );
        this.add(createTitlePanel(), BorderLayout.NORTH);
        this.add(createFormPanel(), BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Informações do caso");
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private void createFormComponentInstances(){
        localConfig = ConfigurationManager.get().findObject(LocalConfig.class);

        textFieldCaseNumber = new JTextField();
        textFieldCaseName = new JTextField();
        textAreaInvestigatedNames = new JTextArea();
        textAreaInvestigatedNames.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        textFieldRequestDate = new JTextField();
        textFieldDemandant = new JTextField();
        textFieldOrganization = new JTextField();
        textAreaExaminerNames = new JTextArea();
        textAreaExaminerNames.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        textFieldContact = new JTextField();
        textAreaCaseNotes = new JTextArea();
        textAreaCaseNotes.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        textAreaCaseNotes.setRows(5);
        textFieldCaseOutput = new JTextField();
        checkBoxOutputOnSSD = new JCheckBox("A pasta do caso esta em um SSD?");
        checkBoxOutputOnSSD.setToolTipText(OutpuOnSSDTooltip);
        checkBoxOutputOnSSD.setSelected(localConfig.isOutputOnSSD());
        checkBoxOutputOnSSD.setOpaque(false);
        buttonSelectCaseOutput = new JButton("...");
        buttonSelectCaseOutput.addActionListener( e -> {
            JFileChooser fileChooserDestino = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            fileChooserDestino.setDialogTitle("Selecione a pasta de destino do caso");
            fileChooserDestino.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooserDestino.setAcceptAllFileFilterUsed(false);
            if( fileChooserDestino.showOpenDialog(this) == JFileChooser.APPROVE_OPTION){
                textFieldCaseOutput.setText( fileChooserDestino.getSelectedFile().toString() );
            }
        } );
    }

    /**
     * Create a new JPanel instance containing all inputs
     * @return JPanel - A JPanel containing all data input form itens
     */
    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel(new GridBagLayout());
        panelForm.setBackground(Color.white);
        createFormComponentInstances();

        int column1 = 0;
        int column2 = 1;

        int column1Width = 1;
        int column2width = 2;

        double noWeightx = 0;
        double fullWeightx = 1.0;

        int currentLine = 0;

        panelForm.add(new JLabel("Número do caso:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(textFieldCaseNumber, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Nome do caso:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(textFieldCaseName, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Investigado(s):"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(textAreaInvestigatedNames, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Data da solicitação:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(textFieldRequestDate, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Demandante:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(textFieldDemandant, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Organização:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(textFieldOrganization, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Examinador(es):"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(textAreaExaminerNames, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Contato:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        panelForm.add(textFieldContact, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(new JLabel("Notas:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));

        panelForm.add(textAreaCaseNotes, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        GridBagConstraints c = getGridBagConstraints(column1, currentLine, column1Width+column2width, fullWeightx);
        c.insets = new Insets(10,10,10,10);
        panelForm.add(new JSeparator(), c);

        currentLine++;
        panelForm.add(new JLabel("Pasta do caso:"), getGridBagConstraints(column1, currentLine, column1Width, noWeightx));
        JPanel panelCaseFolder = new JPanel();
        panelCaseFolder.setLayout(new BoxLayout(panelCaseFolder, BoxLayout.LINE_AXIS));
        panelCaseFolder.setBackground(Color.white);
        panelCaseFolder.add(textFieldCaseOutput);
        panelCaseFolder.add(buttonSelectCaseOutput);
        panelForm.add(panelCaseFolder, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

        currentLine++;
        panelForm.add(checkBoxOutputOnSSD, getGridBagConstraints(column2, currentLine, column2width, fullWeightx));

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
        c.insets = new Insets(2,10,2,10);
        return c;
    }

    /**
     * A JPanel containing "Cancel" and "Next" buttons
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttoCancel = new JButton("Cancelar");
        buttoCancel.addActionListener( e -> NewCaseContainerPanel.getInstance().goHome());
        JButton buttonNext = new JButton("Próximo");
        buttonNext.addActionListener( e -> navigateToNextTab() );
        panelButtons.add(buttoCancel);
        panelButtons.add(buttonNext);
        return panelButtons;
    }

    private void navigateToNextTab(){
        if( textFieldCaseOutput.getText() == null || textFieldCaseOutput.getText().trim().isEmpty()  ){
            JOptionPane.showMessageDialog(this, "A pasta de destino do caso é obrigatório", "Pasta de destino do caso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File caseOutput = new File(textFieldCaseOutput.getText());
        if(! caseOutput.exists() ){
            JOptionPane.showMessageDialog(this, "A pasta de destino não é valida", "Pasta de destino do caso", JOptionPane.WARNING_MESSAGE);
            return;
        }else if(! caseOutput.canWrite() ){
            JOptionPane.showMessageDialog(this, "A pasta de destino informada não tem permissão para escrita", "Pasta de destino do caso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        populateCaseInfo();
        NewCaseContainerPanel.getInstance().goToNextTab();
    }

    private void populateCaseInfo(){
        CaseInfo caseInfo = NewCaseContainerPanel.getInstance().getIpedProcess().getCaseInfo();
        caseInfo.setCaseNumber(textFieldCaseNumber.getText());
        caseInfo.setCaseName(textFieldCaseName.getText());
        caseInfo.setInvestigatedNames(textAreaInvestigatedNames.getText());
        caseInfo.setRequestDate(textFieldRequestDate.getText());
        caseInfo.setDemandant(textFieldDemandant.getText());
        caseInfo.setOrganization(textFieldOrganization.getText());
        caseInfo.setExaminerNames(textAreaExaminerNames.getText());
        caseInfo.setContact(textFieldContact.getText());
        caseInfo.setCaseNotes(textAreaCaseNotes.getText());
    }

}
