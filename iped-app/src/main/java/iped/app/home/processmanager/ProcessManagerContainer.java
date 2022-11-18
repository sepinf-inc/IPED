package iped.app.home.processmanager;

/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.newcase.model.IPEDProcess;
import iped.app.home.style.StyleManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class ProcessManagerContainer extends DefaultPanel {

    private IPEDProcess ipedProcess;

    private final String START_PROCESS = "startprocess";

    public ProcessManagerContainer(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        ipedProcess = NewCaseContainerPanel.getInstance().getIpedProcess();
        this.setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );
        this.add(createTitlePanel());
        this.add(createFormPanel());
    }

    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Aguarde seu processamento esta sendo iniciado");
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel(new CardLayout());
        panelForm.add(new StartingPanelTab(mainFrame), START_PROCESS);

        return panelForm;
    }

    public void startProcess(){
        ProcessManager processManager = new ProcessManager();
        ArrayList<String> commandList =  new ArrayList<String>();
        commandList.add(processManager.getJarBinCommand());
        commandList.addAll(processManager.getEvidencesCommandList(ipedProcess.getEvidenceList()) );
        commandList.addAll(processManager.getCaseOutputCommand(ipedProcess.getCaseOutputPath()));
        processManager.startIpedProcess(commandList);
    }

    public void showPanel(String cardName){
        ((CardLayout) this.getLayout()).show(this, cardName);
    }

}
