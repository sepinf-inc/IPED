package iped.app.home.newcase.tabs.process;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.newcase.tabs.CaseInfoTab;
import iped.app.home.newcase.tabs.evidence.EvidencesTab;
import iped.configuration.Configurable;
import iped.engine.task.AbstractTask;

public class TaskConfigTabPanel extends DefaultPanel {
    AbstractTask task;
    private List<Configurable<?>> configurables;
    private JTabbedPane tabbedPane;
    
    public TaskConfigTabPanel(AbstractTask task, MainFrame mainFrame) {
        super(mainFrame);
        configurables = task.getConfigurables();
        JPanel panelTitle = new JPanel();
        JLabel labelTitle = new JLabel(task.getName());
        labelTitle.setFont(new Font("Arial Bold", Font.PLAIN, 28));
        panelTitle.setBackground(Color.white);
        panelTitle.add(labelTitle);
        this.add(panelTitle, BorderLayout.NORTH);
        setupTabbedPanel();
    }

    @Override
    protected void createAndShowGUI() {
        this.setLayout(new BorderLayout());
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * Setup and create a new JTabbedPane instance
     * Here is created a instance of all nested JPanels
     * @return JTabbedPane
     */
    private JTabbedPane setupTabbedPanel(){
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setUI(new BasicTabbedPaneUI() {
            @Override protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {return 45;}
        });
        for (Iterator iterator = configurables.iterator(); iterator.hasNext();) {
            Configurable<?> configurable = (Configurable<?>) iterator.next();
            tabbedPane.addTab(configurable.getClass().getSimpleName(), UIManager.getIcon("FileView.fileIcon"), new ConfigurablePanel(configurable, mainFrame), "Página para preenchimento das informações do caso");
        }
        this.add(tabbedPane);
        
        
        return tabbedPane;
    }

    private Component createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttoCancel = new JButton("Voltar");
        buttoCancel.addActionListener( e -> mainFrame.showPanel(MainFrameCardsNames.NEW_CASE));
        panelButtons.add(buttoCancel);
        return panelButtons;
    }


}
