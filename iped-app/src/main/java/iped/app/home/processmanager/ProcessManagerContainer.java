package iped.app.home.processmanager;

/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.newcase.model.IPEDProcess;
import iped.app.home.newcase.tabs.caseinfo.CaseInfoManager;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class ProcessManagerContainer extends DefaultPanel {

    private IPEDProcess ipedProcess;
    private JLabel labelTitle;
    private ImageIcon startingIcon;
    private ImageIcon errorIcon;
    private ImageIcon successIcon;
    private JLabel currentLabelIcon;
    private JPanel errorOptionsButtonPanel;
    private JPanel successOptionsButtonPanel;
    private IpedStartException ipedStartException;

    private final String START_PROCESS = "startprocess";
    private final String FAILED_PROCESS = "failedprocess";

    public ProcessManagerContainer(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        ipedProcess = NewCaseContainerPanel.getInstance().getIpedProcess();
        this.setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );
        this.add(createTitlePanel());
        createLabelIcons();
        currentLabelIcon = new JLabel(startingIcon, JLabel.CENTER);
        this.add(currentLabelIcon);
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
        startingIcon = createNewButtonIcon("plug-in.png", new Dimension(400,400));
        errorIcon = createNewButtonIcon("plug_error.png", new Dimension(400,400));
        successIcon = createNewButtonIcon("success.png", new Dimension(400,400));
    }

    private JPanel createErrorOptionsButtonpanel(){
        errorOptionsButtonPanel = new JPanel();
        errorOptionsButtonPanel.setBackground(super.getCurrentBackGroundColor());
        JButton buttonBack = new JButton("Back to case");
        buttonBack.addActionListener(e->{
            mainFrame.showPanel(MainFrameCardsNames.NEW_CASE);
        });
        errorOptionsButtonPanel.add(buttonBack);
        JButton buttonShowLog = new JButton("Show error log");
        buttonShowLog.addActionListener(e->{
            if(ipedStartException != null) {
                JTextArea textArea = new JTextArea();
                textArea.setText(ExceptionUtils.getStackTrace(ipedStartException));
                JOptionPane.showMessageDialog(this, textArea, ipedStartException.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });
        errorOptionsButtonPanel.add(buttonShowLog);
        errorOptionsButtonPanel.setVisible(false);
        return errorOptionsButtonPanel;
    }

    private JPanel createSuccessOptionsButtonpanel(){
        successOptionsButtonPanel = new JPanel();
        successOptionsButtonPanel.setBackground(super.getCurrentBackGroundColor());
        JButton buttonExit = new JButton("Exit");
        buttonExit.addActionListener(e->{
            mainFrame.showPanel(MainFrameCardsNames.NEW_CASE);
        });
        successOptionsButtonPanel.add(buttonExit);
        JButton buttonOpenCase = new JButton("Open case");
        buttonOpenCase.addActionListener(e->{
            if(ipedStartException != null) {
                JTextArea textArea = new JTextArea();
                textArea.setText(ExceptionUtils.getStackTrace(ipedStartException));
                JOptionPane.showMessageDialog(this, textArea, ipedStartException.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });
        successOptionsButtonPanel.add(buttonOpenCase);
        successOptionsButtonPanel.setVisible(false);
        return successOptionsButtonPanel;
    }

    private void saveCaseInfoJsonOnCaseOutputPath(){
        CaseInfoManager ciManager = new CaseInfoManager();
        //Populate caseinfo materials with evidences info
        ciManager.castEvidenceListToMaterialsList(ipedProcess.getCaseInfo(), ipedProcess.getEvidenceList());
        //Save the CaseInfo.json on case output
        ciManager.saveCaseInfo(ipedProcess.getCaseInfo(), Paths.get(ipedProcess.getCaseOutputPath().toString(), "CaseInfo.json").toFile());
    }

    public void startProcess(){
        labelTitle.setText(Messages.get("Home.ProcessManager.StartingProcess"));
        currentLabelIcon.setIcon(startingIcon);
        errorOptionsButtonPanel.setVisible(false);
        successOptionsButtonPanel.setVisible(false);


        saveCaseInfoJsonOnCaseOutputPath();
        ProcessManager processManager = new ProcessManager();
        ArrayList<String> commandList =  new ArrayList<String>();
        commandList.add(processManager.getJarBinCommand());
        commandList.add(processManager.getIpedJarCommand());
        //commandList.add(Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), "iped.exe").toString() );
        commandList.addAll(processManager.getEvidencesCommandList(ipedProcess.getEvidenceList()) );
        commandList.addAll(processManager.getCaseOutputCommand(ipedProcess.getCaseOutputPath()));
        commandList.addAll(ipedProcess.getOptions());
        try {
            processManager.startIpedProcess(commandList);
            labelTitle.setText("IPED executed successfully!!");
            currentLabelIcon.setIcon(successIcon);
            errorOptionsButtonPanel.setVisible(false);
            successOptionsButtonPanel.setVisible(true);
        } catch (IpedStartException e) {
            ipedStartException = e;
            labelTitle.setText("IPED execution failed..");
            currentLabelIcon.setIcon(errorIcon);
            errorOptionsButtonPanel.setVisible(true);
            successOptionsButtonPanel.setVisible(false);
            e.printStackTrace();
        }
    }

    public void showPanel(String cardName){
        ((CardLayout) this.getLayout()).show(this, cardName);
    }

    private ImageIcon createNewButtonIcon(String imageFilename, Dimension iconDimension){
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(imageFilename)));
        Image resizedImage = icon.getImage().getScaledInstance( iconDimension.width, iconDimension.height, java.awt.Image.SCALE_SMOOTH);
        return new ImageIcon(resizedImage);
    }

}
