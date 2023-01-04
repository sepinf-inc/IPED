package iped.app.home.newcase;

/*
 * @created 08/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.MainFrame;
import iped.app.home.newcase.model.IPEDProcess;
import iped.app.home.newcase.tabs.caseinfo.CaseInfoTab;
import iped.app.home.newcase.tabs.evidence.EvidencesTab;
import iped.app.home.newcase.tabs.process.ProcessOptionTab;
import iped.app.ui.Messages;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * New Case Page
 * here we got the main container for all necessary pages to start a new processing
 */
public class NewCaseContainerPanel extends DefaultPanel {

    private JTabbedPane tabbedPane;
    private static NewCaseContainerPanel instance;
    private IPEDProcess ipedProcess;

    /**
     * Construc and save this instance reference to a static variable
     * @param mainFrame - A reference of MainFrame instance
     */
    public NewCaseContainerPanel(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI(){
        instance = this;
        this.setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );
        this.add(setupTabbedPanel());
    }

    /**
     * Setup and create a new JTabbedPane instance
     * Here is created a instance of all nested JPanels
     * @return JTabbedPane
     */
    private JTabbedPane setupTabbedPanel(){
        tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
        tabbedPane.setEnabled(false);
        tabbedPane.setUI(new BasicTabbedPaneUI() {
            @Override protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {return 45;}
        });
        tabbedPane.addTab("<html><b>"+Messages.get("Home.CaseInformation")+"</b></html>", null, new CaseInfoTab(mainFrame), Messages.get("Home.CaseInformationTollTip"));
        tabbedPane.addTab("<html><b>"+Messages.get("Home.Evidences")+"</b></html>", null, new EvidencesTab(mainFrame), Messages.get("Home.EvidencesToolTip"));
        tabbedPane.addTab("<html><b>"+Messages.get("Home.ProcessOptions")+"</b></html>", null, new ProcessOptionTab(mainFrame), Messages.get("Home.ProcessOptionsToolTip"));
        return tabbedPane;
    }

    /**
     * Display the next Tab available
     */
    public void goToNextTab(){
        int selectedIndex = tabbedPane.getSelectedIndex();
        int nextIndex = selectedIndex == tabbedPane.getTabCount()-1 ? 0 : selectedIndex+1;
        tabbedPane.setSelectedIndex(nextIndex);
    }

    /**
     * Displau the previous Tab available
     */
    public void goToPreviousTab(){
        int selectedIndex = tabbedPane.getSelectedIndex();
        int previousIndex = selectedIndex == tabbedPane.getTabCount()-1 ? selectedIndex-1 : 0 ;
        tabbedPane.setSelectedIndex(previousIndex);
    }

    /**
     * Go to HomePanel
     */
    public void goHome(){
        mainFrame.showPanel(MainFrameCardsNames.HOME);
    }

    /**
     * Return NewCaseContainerPanel instance reference
     * @return NewCaseContainerPanel - Current NewCaseContainerPanel instance
     */
    public static NewCaseContainerPanel getInstance(){
        return instance;
    }

    public IPEDProcess getIpedProcess() {
        if( ipedProcess == null )
            ipedProcess = new IPEDProcess();
        return ipedProcess;
    }
}
