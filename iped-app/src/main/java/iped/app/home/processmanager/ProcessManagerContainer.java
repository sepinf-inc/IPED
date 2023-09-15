package iped.app.home.processmanager;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.nio.file.Paths;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import iped.engine.data.ReportInfo;
import org.apache.commons.lang3.exception.ExceptionUtils;

/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.newcase.model.IPEDProcess;
import iped.app.home.newcase.tabs.caseinfo.CaseInfoManager;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;

public class ProcessManagerContainer extends DefaultPanel implements ProcessListener {

    private IPEDProcess ipedProcess;
    private JLabel labelTitle;
    private ImageIcon startingIcon;
    private ImageIcon errorIcon;
    private ImageIcon successIcon;
    private ImageIcon runningIcon;
    private JLabel currentLabelIcon;
    private JPanel errorOptionsButtonPanel;
    private JPanel successOptionsButtonPanel;
    private JPanel processRunningPanel;
    private IpedStartException ipedStartException;
    private JTextArea logTextArea;
    private JButton buttonOpenCase;

    //Constants to change content view
    private final String STARTING_PROCESS = "startingProcess";
    private final String RUNNING_PROCESS = "runningProcess";
    private final String FAILED_PROCESS = "failedProcess";
    private final String FINISHED_PROCESS = "finishedProcess";

    public ProcessManagerContainer(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        ipedProcess = NewCaseContainerPanel.getInstance().getIpedProcess();
        this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        this.add(createTitlePanel());
        createLabelIcons();
        currentLabelIcon = new JLabel(startingIcon, JLabel.CENTER);
        currentLabelIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        this.add(currentLabelIcon);
        this.add(createProcessRunningPanel());
        this.add(createErrorOptionsButtonpanel());
        this.add(createSuccessOptionsButtonpanel());
    }

    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        labelTitle = new JLabel(Messages.get("Home.ProcessManager.StartingProcess"));
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private void createLabelIcons(){
        startingIcon = createNewButtonIcon("plug-in.png");
        runningIcon = createNewButtonIcon("pluged-in.png");
        errorIcon = createNewButtonIcon("plug_error.png");
        successIcon = createNewButtonIcon("success.png");
    }

    private JPanel createProcessRunningPanel(){
        processRunningPanel = new JPanel();
        processRunningPanel.setLayout(new BorderLayout());
        processRunningPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        processRunningPanel.setBackground(super.getCurrentBackGroundColor());
        logTextArea = new JTextArea();
        logTextArea.setMinimumSize(new Dimension(700, 600));
        logTextArea.setPreferredSize(new Dimension(700, 600));
        logTextArea.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        logTextArea.setEditable(false);
        logTextArea.setBorder(BorderFactory.createLineBorder(Color.black,1));
        processRunningPanel.add(new JScrollPane(logTextArea), BorderLayout.CENTER);
        processRunningPanel.setVisible(false);
        return processRunningPanel;
    }

    private JPanel createErrorOptionsButtonpanel(){
        errorOptionsButtonPanel = new JPanel();
        errorOptionsButtonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        errorOptionsButtonPanel.setBackground(super.getCurrentBackGroundColor());
        JButton buttonBack = new JButton(Messages.get("Home.ProcessManager.BackToCaseInfo"));
        buttonBack.addActionListener(e -> {
            NewCaseContainerPanel.getInstance().goToPreviousTab();
        });
        errorOptionsButtonPanel.add(buttonBack);
        JButton buttonShowLog = new JButton(Messages.get("Home.ProcessManager.ShowTerminalLog"));
        buttonShowLog.addActionListener(e->{
            if(ipedStartException != null) {
                JTextArea textArea = new JTextArea();
                textArea.setText(ExceptionUtils.getStackTrace(ipedStartException));
                JOptionPane.showMessageDialog(this, new JScrollPane(textArea), ipedStartException.getMessage(), JOptionPane.PLAIN_MESSAGE);
            }
        });
        errorOptionsButtonPanel.add(buttonShowLog);
        errorOptionsButtonPanel.setVisible(false);
        return errorOptionsButtonPanel;
    }

    private JPanel createSuccessOptionsButtonpanel(){
        successOptionsButtonPanel = new JPanel();
        successOptionsButtonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        successOptionsButtonPanel.setBackground(super.getCurrentBackGroundColor());

        JButton buttonBackCase = new JButton(Messages.get("Home.ProcessManager.BackToCaseInfo"));
        buttonBackCase.addActionListener(e -> {
            NewCaseContainerPanel.getInstance().goToPreviousTab();
        });
        successOptionsButtonPanel.add(buttonBackCase);

        buttonOpenCase = new JButton(Messages.get("Home.ProcessManager.OpenCase"));
        buttonOpenCase.addActionListener(e->{
            Thread t = new Thread(() -> {
                ProcessManager pm = new ProcessManager();
                pm.addProcessListener(ProcessManagerContainer.this);
                pm.openSingleCase(ipedProcess.getCaseOutputPath());
            });
            t.start();
        });
        successOptionsButtonPanel.add(buttonOpenCase);

        JButton buttonShowLog = new JButton(Messages.get("Home.ProcessManager.ShowTerminalLog"));
        buttonShowLog.addActionListener(e-> JOptionPane.showMessageDialog(this, new JScrollPane(logTextArea), Messages.get("Home.ProcessManager.TerminalLog"), JOptionPane.PLAIN_MESSAGE));
        successOptionsButtonPanel.add(buttonShowLog);

        JButton buttonExit = new JButton(Messages.get("Home.ProcessManager.ExitApplication"));
        buttonExit.addActionListener(e-> System.exit(0));
        successOptionsButtonPanel.add(buttonExit);

        successOptionsButtonPanel.setVisible(false);
        return successOptionsButtonPanel;
    }


    private void saveCaseInfoJsonOnCaseOutputPath(){
        CaseInfoManager ciManager = new CaseInfoManager();
        ipedProcess.getEvidenceList();
        //Populate caseinfo materials with evidences info
        //ciManager.castEvidenceListToMaterialsList(ipedProcess.getReportInfo(), ipedProcess.getEvidenceList());
        //Save the CaseInfo.json on case output
        ipedProcess.getReportInfo().saveJsonInfoFile(Paths.get(ipedProcess.getCaseOutputPath().toString(), "CaseInfo.json").toFile());
    }

    public void startProcess(){
        switchPanelTo(STARTING_PROCESS);
        saveCaseInfoJsonOnCaseOutputPath();
        Thread t = new Thread(() -> {
            try {
                //After complete the execution the user can back to case info and start the process again, so wee need to clean the previous log
                logTextArea.setText("");
                ProcessManager processManager = new ProcessManager();
                processManager.addProcessListener(ProcessManagerContainer.this);
                processManager.startIpedProcess(ipedProcess, logTextArea);
            } catch (IpedStartException e) {
                ipedStartException = e;
                switchPanelTo(FAILED_PROCESS);
                e.printStackTrace();
            }
        });
        t.start();
    }

    private void switchPanelTo(String panelName){
        switch (panelName){
            case STARTING_PROCESS: {
                labelTitle.setText(Messages.get("Home.ProcessManager.StartingProcess"));
                currentLabelIcon.setIcon(startingIcon);
                currentLabelIcon.setVisible(true);
                processRunningPanel.setVisible(false);
                errorOptionsButtonPanel.setVisible(false);
                successOptionsButtonPanel.setVisible(false);
                break;
            }
            case RUNNING_PROCESS: {
                labelTitle.setText(Messages.get("Home.ProcessManager.ProcessRunning"));
                currentLabelIcon.setIcon(runningIcon);
                currentLabelIcon.setVisible(false);
                processRunningPanel.setVisible(true);
                errorOptionsButtonPanel.setVisible(false);
                successOptionsButtonPanel.setVisible(false);
                break;
            }
            case FAILED_PROCESS: {
                labelTitle.setText(Messages.get("Home.ProcessManager.ProcessFailed"));
                currentLabelIcon.setIcon(errorIcon);
                currentLabelIcon.setVisible(true);
                processRunningPanel.setVisible(false);
                errorOptionsButtonPanel.setVisible(true);
                successOptionsButtonPanel.setVisible(false);
                break;
            }
            case FINISHED_PROCESS: {
                labelTitle.setText(Messages.get("Home.ProcessManager.ProcessFinished"));
                currentLabelIcon.setIcon(successIcon);
                currentLabelIcon.setVisible(true);
                processRunningPanel.setVisible(false);
                errorOptionsButtonPanel.setVisible(false);
                successOptionsButtonPanel.setVisible(true);
                break;
            }
        }
    }

    private ImageIcon createNewButtonIcon(String imageFilename){
        Dimension iconDimension = new Dimension(400,400);
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(imageFilename)));
        Image resizedImage = icon.getImage().getScaledInstance( iconDimension.width, iconDimension.height, java.awt.Image.SCALE_SMOOTH);
        return new ImageIcon(resizedImage);
    }

    /**
     * This method will be fired by ProcessManager when Iped process start
     */
    @Override
    public void processStarted() {
        try {
            Thread.sleep(3000);
            switchPanelTo(RUNNING_PROCESS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * This method will be fired by ProcessManager when Iped process finish
     */
    @Override
    public void processFinished() {
        switchPanelTo(FINISHED_PROCESS);
    }

    @Override
    public void caseIsOpening() {
        buttonOpenCase.setEnabled(false);
        buttonOpenCase.setText(Messages.get("Home.ProcessManager.OpenCaseMsg"));
    }

    @Override
    public void caseWasClosed() {
        buttonOpenCase.setEnabled(true);
        buttonOpenCase.setText(Messages.get("Home.ProcessManager.OpenCase"));
    }
}
