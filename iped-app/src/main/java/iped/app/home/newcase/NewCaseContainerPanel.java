package iped.app.home.newcase;

import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/*
 * @created 08/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.newcase.model.IPEDProcess;
import iped.app.home.newcase.tabs.caseinfo.CaseInfoTab;
import iped.app.home.newcase.tabs.evidence.EvidencesTab;
import iped.app.home.newcase.tabs.process.ProcessOptionTab;
import iped.app.home.processmanager.ProcessManagerContainer;
import iped.app.ui.Messages;

/**
 * New Case Page
 * here we got the main container for all necessary pages to start a new processing
 */
public class NewCaseContainerPanel extends DefaultPanel {

    private JTabbedPane tabbedPane;
    private static NewCaseContainerPanel instance;
    private IPEDProcess ipedProcess;
    private ProcessManagerContainer pmc;

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
        //tabbedPane.setEnabled(false);


        tabbedPane.setUI(new BasicTabbedPaneUI() {
            @Override protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {return 45;}
        });

        tabbedPane.addTab(Messages.get("Home.CaseInformation"), null, new CaseInfoTab(mainFrame), Messages.get("Home.CaseInformationTollTip"));
        tabbedPane.addTab(Messages.get("Home.Evidences"), null, new EvidencesTab(mainFrame), Messages.get("Home.EvidencesToolTip"));
        tabbedPane.addTab(Messages.get("Home.ProcessOptions"), null, new ProcessOptionTab(mainFrame), Messages.get("Home.ProcessOptionsToolTip"));
        pmc = new ProcessManagerContainer(mainFrame);
        tabbedPane.addTab(Messages.get("Home.ProcessingTab"), null, pmc, Messages.get("Home.ProcessingTabTooltip"));
        disableTabs();
        tabbedPane.setEnabledAt(0, true);
        return tabbedPane;
    }

    /**
     * Display the next Tab available
     */
    public void goToNextTab(){
        int selectedIndex = tabbedPane.getSelectedIndex();
        boolean isLastTabIndex =  (selectedIndex == tabbedPane.getTabCount()-1);
        //if is in the last tab index, prevent to get a invalid index
        int nextIndex = isLastTabIndex ? selectedIndex : selectedIndex+1;
        disableTabs();
        tabbedPane.setSelectedIndex(nextIndex);
        tabbedPane.setEnabledAt(nextIndex, true);
    }

    /**
     * Display the previous Tab available
     */
    public void goToPreviousTab(){
        int selectedIndex = tabbedPane.getSelectedIndex();
        boolean isTheFirstTabIndex = selectedIndex == 0;
        int previousIndex = isTheFirstTabIndex ? selectedIndex : selectedIndex-1 ;
        disableTabs();
        tabbedPane.setSelectedIndex(previousIndex);
        tabbedPane.setEnabledAt(previousIndex, true);
    }

    private void disableTabs(){
        for( int i = 0; i < tabbedPane.getTabCount(); i++ ){
            tabbedPane.setEnabledAt(i, false);
        }
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

    public void startIPEDProcessing() {
        goToNextTab();
        pmc.startProcess();
    }
}
