package iped.app.home.opencase;
/*
 * @created 21/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.newcase.tabs.caseinfo.CaseException;
import iped.app.home.newcase.tabs.caseinfo.CaseInfoManager;
import iped.app.home.processmanager.ProcessListener;
import iped.app.home.processmanager.ProcessManager;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * A panel to manage IPED cases to be opened
 */
public class OpenCasePanel extends DefaultPanel implements ProcessListener {

    private JTable jTableCaseList;
    private CasesTableModel casesTableModel;
    private ArrayList<Path> caseList;
    private JButton buttonOpen;
    private JButton buttonCancel;

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
        this.add(createTableButtonsPanel());
        this.add(createFormPanel());
        this.add(createButtonsPanel());
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel(Messages.get("Home.OpenCase.OpenCase"));
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private JPanel createTableButtonsPanel(){
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout( buttonPanel, BoxLayout.LINE_AXIS ));
        buttonPanel.setBackground(Color.white);
        JButton addCaseButton = new JButton(Messages.get("Home.OpenCase.AddCaseToList"));
        addCaseButton.addActionListener(e->{
            JFileChooser fileChooserDestino = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            fileChooserDestino.setDialogTitle(Messages.get("Home.OpenCase.SelectCaseFolder"));
            fileChooserDestino.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooserDestino.setAcceptAllFileFilterUsed(false);
            if( fileChooserDestino.showOpenDialog(this) == JFileChooser.APPROVE_OPTION){
                Path selectedCasePath = fileChooserDestino.getSelectedFile().toPath();
                try {
                    new CaseInfoManager().validateCasePath(selectedCasePath);
                } catch (CaseException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                    return;
                }
                getCaseList().add(selectedCasePath);
                casesTableModel.fireTableDataChanged();
            }
        });
        buttonPanel.add(addCaseButton);
        JButton removeCaseButton = new JButton(Messages.get("Home.OpenCase.RemoveCaseFromList"));
        removeCaseButton.addActionListener(e->{
            caseList.remove(jTableCaseList.getSelectedRow());
            casesTableModel.fireTableDataChanged();
            jTableCaseList.doLayout();
        });
        buttonPanel.add(removeCaseButton);
        return buttonPanel;
    }

    /**
     * Create a new JPanel instance containing all inputs
     * @return JPanel - A JPanel containing all data input form itens
     */
    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout( panelForm, BoxLayout.PAGE_AXIS ));
        panelForm.setBackground(super.getCurrentBackGroundColor());
        panelForm.add( Box.createRigidArea( new Dimension(10, 10) ) );
        setupCaseTables(panelForm);
        return panelForm;
    }

    /**
     * Create and setup a JTable do manage all Evidences to be processed
     * @param panel - A JPanel to add JTable
     */
    private void setupCaseTables(JPanel panel){
        casesTableModel = new CasesTableModel(getCaseList());
        jTableCaseList = new JTable(casesTableModel);
        jTableCaseList.setFillsViewportHeight(true);
        jTableCaseList.setDefaultRenderer(JPanel.class, new CaseTableCellRender());
        StyleManager.setTableHeaderStyle(jTableCaseList);
        panel.add( new JScrollPane(jTableCaseList));
    }

    public ArrayList<Path> getCaseList() {
        if( caseList == null )
            caseList = new ArrayList<>();
        return caseList;
    }

    /**
     * A JPanel containing "open" and "Cancel" buttons
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        buttonOpen = new JButton(Messages.get("Home.OpenCase.OpenAllCasesInTheList"));
        buttonOpen.addActionListener( e -> this.openCases());
        buttonCancel = new JButton(Messages.get("Case.Cancel"));
        buttonCancel.addActionListener( e -> mainFrame.showPanel(MainFrameCardsNames.HOME));
        panelButtons.add(buttonOpen);
        panelButtons.add(buttonCancel);
        return panelButtons;
    }

    private void openCases(){
        //If user do not choose any case, show message and stop execution
        if( caseList == null || caseList.isEmpty() ){
            JOptionPane.showMessageDialog(this, Messages.get("Home.OpenCase.EmptyCaseMessage"), Messages.get("Home.OpenCase.EmptyCaseMessageTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        Thread t = new Thread(() -> {
            ProcessManager pm = new ProcessManager();
            pm.addProcessListener(OpenCasePanel.this);

            //If user choose a single case, will open iped search single case
            if (caseList.size() == 1) {
                try {
                    pm.openSingleCase(caseList.get(0));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(OpenCasePanel.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                return;
            }

            //Open iped search multicase mode
            //first create on temp folder a txt file containing the cases to be opened
            File multiCaseFile = Paths.get(System.getProperty("user.home"), ".iped", "multicasetemp.txt").toFile();
            try (FileWriter fileWriter = new FileWriter(multiCaseFile, StandardCharsets.UTF_8); PrintWriter printWriter = new PrintWriter(fileWriter)) {
                for (Path currentPath : caseList)
                    printWriter.println(currentPath.toString());
                printWriter.close();
                fileWriter.close();
                pm.openMulticase(caseList.get(0), multiCaseFile);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(OpenCasePanel.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        t.start();
    }

    @Override
    public void processStarted() {

    }

    @Override
    public void processFinished() {

    }

    @Override
    public void caseIsOpening() {
        buttonOpen.setEnabled(false);
    }

    @Override
    public void caseWasClosed() {
        buttonOpen.setEnabled(true);
    }

}
