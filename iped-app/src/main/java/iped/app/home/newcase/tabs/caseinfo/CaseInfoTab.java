package iped.app.home.newcase.tabs.caseinfo;/*
                                            * @created 08/09/2022
                                            * @project IPED
                                            * @author Thiago S. Figueiredo
                                            */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.JTextComponent;

import org.apache.commons.lang3.StringUtils;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.newcase.model.ExistentCaseOptions;
import iped.app.home.newcase.model.IPEDProcess;
import iped.app.home.style.StyleManager;
import iped.app.home.utils.CasePathManager;
import iped.app.home.utils.JTextAreaTabFocus;
import iped.app.ui.Messages;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.data.ReportInfo;

/**
 * Case info TAB
 */
public class CaseInfoTab extends DefaultPanel {

    private LocalConfig localConfig;
    private IPEDProcess ipedProcess;

    private JTextField textFieldCaseOutput;
    private JCheckBox checkBoxOutputOnSSD;
    private JButton buttonSelectCaseOutput;
    private JButton buttonCheckCaseOutput;
    private JTextField textFieldReportNumber;
    private JTextField textFieldReportDate;
    private JTextField textFieldReportTitle;
    private JTextArea textAreaInvestigatedNames;
    private JTextField textFieldRequestDate;
    private JTextField textFieldCaseNumber;
    private JTextField textFieldLabCaseNumber;
    private JTextField textFieldLabCaseDate;
    private JTextField textFieldRequestForm;
    private JTextField textFieldRequester;
    private JTextField textFieldOrganization;
    private JTextArea textAreaExaminerNames;
    private JTextField textFieldContact;
    private JTextArea textAreaCaseNotes;
    private JPanel panelCaseOutputOptions;
    private JRadioButton radioCaseOutAdd;
    private JRadioButton radioCaseOutContinue;
    private JRadioButton radioCaseOutRestart;
    private ButtonGroup buttonGroupCaseOutOptions;
    private FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("caseinfo.json", new String[] { "json", "JSON" });
    private ArrayList<JTextComponent> caseInfoInputList;

    public CaseInfoTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI() {
        ipedProcess = NewCaseContainerPanel.getInstance().getIpedProcess();
        this.setLayout(new BorderLayout());
        this.add(createTitlePanel(), BorderLayout.NORTH);
        this.add(createFormPanel(), BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * 
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel() {
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel(Messages.get("Home.CaseInformation"));
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private void createFormComponentInstances() {
        localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
        caseInfoInputList = new ArrayList<>();

        textFieldReportNumber = new JTextField();
        caseInfoInputList.add(textFieldReportNumber);
        textFieldReportDate = new JTextField();
        caseInfoInputList.add(textFieldReportDate);
        textFieldReportTitle = new JTextField();
        caseInfoInputList.add(textFieldReportTitle);
        JTextAreaTabFocus tabFocus = new JTextAreaTabFocus();
        textAreaInvestigatedNames = new JTextArea();
        caseInfoInputList.add(textAreaInvestigatedNames);
        textAreaInvestigatedNames.addKeyListener(tabFocus);
        textAreaInvestigatedNames.setBorder(BorderFactory.createLineBorder(Color.gray));
        textAreaInvestigatedNames.setLineWrap(true);
        textAreaInvestigatedNames.setWrapStyleWord(true);
        textFieldRequestDate = new JTextField();
        caseInfoInputList.add(textFieldRequestDate);
        textFieldCaseNumber = new JTextField();
        caseInfoInputList.add(textFieldCaseNumber);
        textFieldRequestForm = new JTextField();
        caseInfoInputList.add(textFieldRequestForm);
        textFieldRequester = new JTextField();
        caseInfoInputList.add(textFieldRequester);
        textFieldLabCaseNumber = new JTextField();
        caseInfoInputList.add(textFieldLabCaseNumber);
        textFieldLabCaseDate = new JTextField();
        caseInfoInputList.add(textFieldLabCaseDate);
        textFieldOrganization = new JTextField();
        caseInfoInputList.add(textFieldOrganization);
        textAreaExaminerNames = new JTextArea();
        caseInfoInputList.add(textAreaExaminerNames);
        textAreaExaminerNames.setBorder(BorderFactory.createLineBorder(Color.gray));
        textAreaExaminerNames.addKeyListener(tabFocus);
        textAreaExaminerNames.setLineWrap(true);
        textAreaExaminerNames.setWrapStyleWord(true);
        textFieldContact = new JTextField();
        caseInfoInputList.add(textFieldContact);
        textAreaCaseNotes = new JTextArea();
        caseInfoInputList.add(textAreaCaseNotes);
        textAreaCaseNotes.setBorder(BorderFactory.createLineBorder(Color.gray));
        textAreaCaseNotes.setRows(5);
        textAreaCaseNotes.addKeyListener(tabFocus);
        textAreaCaseNotes.setLineWrap(true);
        textAreaCaseNotes.setWrapStyleWord(true);

        textFieldCaseOutput = new JTextField();
        textFieldCaseOutput.setEditable(false);
        checkBoxOutputOnSSD = new JCheckBox(Messages.get("Home.NewCase.IsCaseOutputOnSSD"));
        checkBoxOutputOnSSD.setToolTipText(Messages.get("Home.NewCase.IsCaseOutputOnSSDToolTip"));
        checkBoxOutputOnSSD.setSelected(localConfig.isOutputOnSSD());
        checkBoxOutputOnSSD.setOpaque(false);
        checkBoxOutputOnSSD.addItemListener(e -> {
            try {
                Boolean isEnableOutputSSD = (e.getStateChange() == ItemEvent.SELECTED);
                LocalConfig localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
                localConfig.getPropertie().setProperty(LocalConfig.OUTPUT_ON_SSD, isEnableOutputSSD.toString());
                localConfig.getPropertie().saveOnFile(CasePathManager.getInstance().getLocalConfigFile());
                ConfigurationManager.get().reloadConfigurable(LocalConfig.class);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        buttonSelectCaseOutput = new JButton("...");
        buttonSelectCaseOutput.addActionListener(e -> {
            JFileChooser fileChooserDestino = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            fileChooserDestino.setDialogTitle(Messages.get("Home.NewCase.ChooseCaseOutput"));
            fileChooserDestino.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooserDestino.setAcceptAllFileFilterUsed(false);
            String outputStr = textFieldCaseOutput.getText();
            if (!outputStr.isBlank()) {
                fileChooserDestino.setCurrentDirectory(new File(outputStr));
            }
            if (fileChooserDestino.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                setCaseOutputValue(fileChooserDestino.getSelectedFile().toPath());
            }
        });
        buttonCheckCaseOutput = new JButton("");
        Image img = (new ImageIcon(getClass().getResource("/iped/app/ui/refresh.png"))).getImage();
        Image resizedIcon = img.getScaledInstance(15, 15, java.awt.Image.SCALE_SMOOTH);
        buttonCheckCaseOutput.setIcon(new ImageIcon(resizedIcon));
        buttonCheckCaseOutput.addActionListener(e -> {
            if (ipedProcess.getCaseOutputPath() != null)
                setCaseOutputValue(ipedProcess.getCaseOutputPath());

        });
    }

    private File showSaveCaseInfoFileChooser(String title) {
        JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        fileChooser.setDialogTitle(title);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.addChoosableFileFilter(jsonFilter);
        int returnValue = fileChooser.showSaveDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".json")) {
                file = new File(file.getParentFile(), file.getName() + ".json");
            }
            return file;
        }
        return null;
    }

    private File showLoadCaseInfoFileChooser(String title) {
        JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        fileChooser.setDialogTitle(title);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.addChoosableFileFilter(jsonFilter);
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }
        return null;
    }

    private void setCaseOutputValue(Path caseOutput) {
        // first check if exists a case on selected output folder
        boolean isExistsCase = Paths.get(caseOutput.toString(), "IPED-SearchApp.exe").toFile().exists();
        panelCaseOutputOptions.setVisible(isExistsCase);
        if (isExistsCase) {
            // check if the existent case is finished, if yes the user cannot choose the
            // continue options
            boolean isCaseFinished = Paths.get(caseOutput.toString(), "iped", "data", "processing_finished").toFile().exists();
            radioCaseOutContinue.setVisible(!isCaseFinished);
        }
        // when user reselect other path wee need to unselect the previously selected
        // radio button
        if (!panelCaseOutputOptions.isVisible()) {
            ipedProcess.setExistentCaseOption(null);
            buttonGroupCaseOutOptions.clearSelection();
        }

        IPEDProcess ipedProcess = NewCaseContainerPanel.getInstance().getIpedProcess();
        ipedProcess.setCaseOutputPath(caseOutput);
        textFieldCaseOutput.setText(caseOutput.toString());
    }

    /**
     * Create a new JPanel instance containing all inputs
     * 
     * @return JPanel - A JPanel containing all data input form itens
     */
    private JScrollPane createFormPanel() {
        JPanel panelForm = new JPanel(new GridBagLayout());
        panelForm.setBackground(super.getCurrentBackGroundColor());
        createFormComponentInstances();

        int column0 = 0;
        int column1 = 1;

        int column0Width = 1;
        int column1width = 2;
        int fullColumnWidth = column0Width + column1width;

        double noWeightx = 0;
        double fullWeightx = 1.0;

        int currentLine = 0;

        panelForm.add(new JLabel(Messages.get("ReportDialog.ReportNum") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldReportNumber, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.ReportDate") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldReportDate, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.ReportTitle") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldReportTitle, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.Examiner") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textAreaExaminerNames, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.Investigation") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldCaseNumber, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.Request") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldRequestForm, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.RequestDate") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldRequestDate, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.Requester") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldRequester, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.Record") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldLabCaseNumber, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.RecordDate") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldLabCaseDate, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.InvestigatedNames") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textAreaInvestigatedNames, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.organizationName") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldOrganization, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.contact") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textFieldContact, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(new JLabel(Messages.get("ReportDialog.caseNotes") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(textAreaCaseNotes, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        // ----------------Case data buttons-----------------
        panelForm.add(createCaseDataPanel(), getGridBagConstraints(column0, currentLine++, fullColumnWidth, fullWeightx, new Insets(10, 10, 0, 10)));

        // ----------------Line Separator-----------------
        panelForm.add(new JSeparator(), getGridBagConstraints(column0, currentLine++, fullColumnWidth, fullWeightx, new Insets(10, 10, 10, 10)));

        panelForm.add(new JLabel(Messages.get("Home.NewCase.CaseOutput") + ":"), getGridBagConstraints(column0, currentLine, column0Width, noWeightx));
        panelForm.add(createSetCaseOutputPanel(), getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelForm.add(checkBoxOutputOnSSD, getGridBagConstraints(column1, currentLine++, column1width, fullWeightx));

        panelCaseOutputOptions = createCaseOutputOptionsPanel();
        panelForm.add(panelCaseOutputOptions, getGridBagConstraints(column0, currentLine, fullColumnWidth, fullWeightx, new Insets(10, 10, 0, 10)));

        return new JScrollPane(panelForm);

    }

    /**
     * Create a JPanel with one textfield and a button to set the case output
     * 
     * @return JPanel with a textfield and a button to set case output
     */
    private JPanel createSetCaseOutputPanel() {
        JPanel panelCaseOutput = new JPanel();
        panelCaseOutput.setLayout(new BoxLayout(panelCaseOutput, BoxLayout.LINE_AXIS));
        panelCaseOutput.setBackground(Color.white);
        panelCaseOutput.add(textFieldCaseOutput);
        panelCaseOutput.add(buttonSelectCaseOutput);
        panelCaseOutput.add(buttonCheckCaseOutput);
        return panelCaseOutput;
    }

    private JPanel createCaseOutputOptionsPanel() {
        buttonGroupCaseOutOptions = new ButtonGroup();
        radioCaseOutAdd = new JRadioButton(Messages.get("Home.AppendExistentCase"), false);
        radioCaseOutAdd.setBackground(super.getCurrentBackGroundColor());
        radioCaseOutContinue = new JRadioButton(Messages.get("Home.ContinueExistentCase"), false);
        radioCaseOutContinue.setBackground(super.getCurrentBackGroundColor());
        radioCaseOutRestart = new JRadioButton(Messages.get("Home.RestartExistentCase"), false);
        radioCaseOutRestart.setBackground(super.getCurrentBackGroundColor());
        buttonGroupCaseOutOptions.add(radioCaseOutAdd);
        buttonGroupCaseOutOptions.add(radioCaseOutContinue);
        buttonGroupCaseOutOptions.add(radioCaseOutRestart);

        JPanel panel = new JPanel();
        panel.setVisible(false);
        panel.setBackground(super.getCurrentBackGroundColor());
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        JLabel labelCaseOptionMessage = new JLabel(Messages.get("Home.ExistentCaseAlert"));
        labelCaseOptionMessage.setForeground(Color.RED);
        panel.add(labelCaseOptionMessage);
        panel.add(radioCaseOutAdd);
        panel.add(radioCaseOutContinue);
        panel.add(radioCaseOutRestart);
        return panel;
    }

    /**
     * Create a JPanel with Save and load buttons
     * 
     * @return A new JPanel with Case Button
     */
    private JPanel createCaseDataPanel() {
        JPanel panelCaseData = new JPanel();
        panelCaseData.setBackground(super.getCurrentBackGroundColor());
        JButton buttonSaveCaseData = new JButton(Messages.get("Home.NewCase.SaveCaseData"));
        buttonSaveCaseData.addActionListener(e -> {
            File destinationFile = showSaveCaseInfoFileChooser(Messages.get("Home.NewCase.ChooseCaseInfoFileOutput"));
            if (destinationFile != null) {
                populateCaseInfo();
                ipedProcess.getReportInfo().saveJsonInfoFile(destinationFile);
            }
        });
        JButton buttonLoadCaseData = new JButton(Messages.get("Home.NewCase.LoadCaseData"));
        buttonLoadCaseData.addActionListener(e -> {
            if (isCaseInfoFormFilled()) {
                int selectedOption = JOptionPane.showConfirmDialog(null, Messages.get("Home.NewCase.OpenCaseFileDialogMessage"), Messages.get("Home.NewCase.OpenCaseFileDialogTitle"), JOptionPane.YES_NO_OPTION);
                if (selectedOption == 1)
                    return;
            }

            File caseInfoSourceFile = showLoadCaseInfoFileChooser(Messages.get("Home.NewCase.ChooseCaseInfoFile"));
            if (caseInfoSourceFile != null) {
                ReportInfo reportInfo = new ReportInfo();
                try {
                    reportInfo.readJsonInfoFile(caseInfoSourceFile);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return;
                }
                ipedProcess.setReportInfo(reportInfo);
                populateFormCaseInfo();
            }
        });
        panelCaseData.add(buttonSaveCaseData);
        panelCaseData.add(buttonLoadCaseData);
        return panelCaseData;
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
        GridBagConstraints gbcons = new GridBagConstraints();
        gbcons.fill = GridBagConstraints.HORIZONTAL;
        gbcons.weightx = weightX;
        gbcons.gridx = tableColumnIndex;
        gbcons.gridy = tableLineIndex;
        gbcons.gridwidth = cellWidth;
        gbcons.gridheight = 1;
        gbcons.insets = new Insets(2, 10, 2, 10);
        return gbcons;
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
     * @param insets
     *            - the Insets
     * @return GridBagConstraints - a new GridBagConstraints instance containing all
     *         parameter passed
     */
    private GridBagConstraints getGridBagConstraints(int tableColumnIndex, int tableLineIndex, int cellWidth, double weightX, Insets insets) {
        GridBagConstraints gbcons = getGridBagConstraints(tableColumnIndex, tableLineIndex, cellWidth, weightX);
        gbcons.insets = insets;
        return gbcons;
    }

    /**
     * A JPanel containing "Cancel" and "Next" buttons
     * 
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttoCancel = new JButton(Messages.get("Home.Cancel"));
        buttoCancel.addActionListener(e -> NewCaseContainerPanel.getInstance().goHome());
        JButton buttonNext = new JButton(Messages.get("Home.Next"));
        buttonNext.addActionListener(e -> navigateToNextTab());
        panelButtons.add(buttoCancel);
        panelButtons.add(buttonNext);
        return panelButtons;
    }

    private void navigateToNextTab() {
        Path casePath = ipedProcess.getCaseOutputPath();
        // check if output path exists
        if ((casePath == null) || (!Files.isDirectory(casePath))) {
            JOptionPane.showMessageDialog(this, Messages.get("Home.NewCase.CaseOutputRequired"), Messages.get("Home.NewCase.CaseOutputRequiredTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Check case output permissions
        if ((!Files.isReadable(casePath)) || (!Files.isWritable(casePath))) {
            JOptionPane.showMessageDialog(this, Messages.get("Home.NewCase.CaseOutputPermission"), Messages.get("Home.NewCase.CaseOutputPermissionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (panelCaseOutputOptions.isVisible() && (buttonGroupCaseOutOptions.getSelection() == null)) {
            JOptionPane.showMessageDialog(this, Messages.get("Home.NewCase.CaseOutPutOptionsRequired"), Messages.get("Home.NewCase.CaseOutputRequiredTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        setCaseOutputOptions();
        populateCaseInfo();
        NewCaseContainerPanel.getInstance().goToNextTab();
    }

    private void setCaseOutputOptions() {
        if (radioCaseOutAdd.isSelected())
            ipedProcess.setExistentCaseOption(ExistentCaseOptions.APPEND);
        if (radioCaseOutContinue.isSelected())
            ipedProcess.setExistentCaseOption(ExistentCaseOptions.CONTINUE);
        if (radioCaseOutRestart.isSelected())
            ipedProcess.setExistentCaseOption(ExistentCaseOptions.RESTART);
    }

    private void populateCaseInfo() {
        ReportInfo reportInfo = ipedProcess.getReportInfo();
        if (reportInfo == null) {
            reportInfo = new ReportInfo();
            ipedProcess.setReportInfo(reportInfo);
        }
        reportInfo.reportNumber = textFieldReportNumber.getText();
        reportInfo.reportDate = textFieldReportDate.getText();
        reportInfo.reportTitle = textFieldReportTitle.getText();
        reportInfo.examiners = new ArrayList<>(Arrays.asList(textAreaExaminerNames.getText().split("\n")));
        reportInfo.caseNumber = textFieldCaseNumber.getText();
        reportInfo.requestForm = textFieldRequestForm.getText();
        reportInfo.requestDate = textFieldRequestDate.getText();
        reportInfo.requester = textFieldRequester.getText();
        reportInfo.labCaseNumber = textFieldLabCaseNumber.getText();
        reportInfo.labCaseDate = textFieldLabCaseDate.getText();
        reportInfo.investigatedName = new ArrayList<>(Arrays.asList(textAreaInvestigatedNames.getText().split("\n")));
        reportInfo.organizationName = textFieldOrganization.getText();
        reportInfo.contact = textFieldContact.getText();
        reportInfo.caseNotes = textAreaCaseNotes.getText();
        reportInfo.evidences = new ArrayList<>();
    }

    /**
     * Check if there is any information filled in case info form
     * 
     * @return
     */
    private boolean isCaseInfoFormFilled() {
        for (JTextComponent currentCaseInfoInput : caseInfoInputList) {
            if (!StringUtils.isEmpty(currentCaseInfoInput.getText()))
                return true;
        }
        return false;
    }

    /**
     * Fill the form inputs with reportinfo class data
     */
    private void populateFormCaseInfo() {
        String emptyValue = "";
        ReportInfo reportInfo = ipedProcess.getReportInfo();
        textFieldReportNumber.setText(reportInfo.reportNumber != null ? reportInfo.reportNumber : emptyValue);
        textFieldReportDate.setText(reportInfo.reportDate != null ? reportInfo.reportDate : emptyValue);
        textFieldReportTitle.setText(reportInfo.reportTitle != null ? reportInfo.reportTitle : emptyValue);
        if ((reportInfo.examiners != null) && (!reportInfo.examiners.isEmpty())) {
            String examinerNames = reportInfo.examiners.stream().map(String::trim).collect(Collectors.joining("\n"));
            textAreaExaminerNames.append(examinerNames);
        } else
            textAreaExaminerNames.setText(emptyValue);
        textFieldCaseNumber.setText(reportInfo.caseNumber != null ? reportInfo.caseNumber : emptyValue);
        textFieldRequestForm.setText(reportInfo.requestForm != null ? reportInfo.requestForm : emptyValue);
        textFieldRequestDate.setText(reportInfo.requestDate != null ? reportInfo.requestDate : emptyValue);
        textFieldRequester.setText(reportInfo.requester != null ? reportInfo.requester : emptyValue);
        textFieldLabCaseNumber.setText(reportInfo.labCaseNumber != null ? reportInfo.labCaseNumber : emptyValue);
        textFieldLabCaseDate.setText(reportInfo.labCaseDate != null ? reportInfo.labCaseDate : emptyValue);
        if ((reportInfo.investigatedName != null) && (!reportInfo.investigatedName.isEmpty())) {
            String investigatedNames = reportInfo.investigatedName.stream().map(String::trim).collect(Collectors.joining("\n"));
            textAreaInvestigatedNames.setText(investigatedNames);
        } else
            textAreaInvestigatedNames.setText(emptyValue);
        textFieldOrganization.setText(reportInfo.organizationName != null ? reportInfo.organizationName : emptyValue);
        textFieldContact.setText(reportInfo.contact != null ? reportInfo.contact : emptyValue);
        textAreaCaseNotes.setText(reportInfo.caseNotes != null ? reportInfo.caseNotes : emptyValue);
    }

}
