package iped.app.home.newcase.tabs.process;/*
 * @created 16/12/2022
 * @project IPED
 * @author Patrick Dalla Bernardina
 * @author Thiago S. Figueiredo
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import iped.app.home.MainFrame;
import iped.app.home.configurables.ConfigurablePanel;
import iped.app.home.configurables.ConfigurableValidationException;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
import iped.configuration.Configurable;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.task.AbstractTask;

public class TaskConfigDialog extends JDialog {
    private List<Configurable<?>> configurables;
    private HashMap<Configurable<?>, ConfigurablePanel> configurablePanels = new HashMap<Configurable<?>, ConfigurablePanel>();
    ConfigurationManager configurationManager;
    MainFrame mainFrame;

    public TaskConfigDialog(ConfigurationManager configurationManager, AbstractTask task, MainFrame mainFrame) {
        super(mainFrame);
        this.mainFrame = mainFrame;
        this.configurationManager=configurationManager;
        configurables = task.getConfigurables();
        setModal(true);
        JPanel formPanel = new JPanel(new BorderLayout());
        String localizedName = iped.engine.localization.Messages.getString(task.getClass().getName(), task.getName());
        formPanel.add(createTitlePanel(localizedName), BorderLayout.NORTH);
        formPanel.add(createTabbedPanel(), BorderLayout.CENTER);
        formPanel.add(createButtonsPanel(), BorderLayout.SOUTH);
        this.getContentPane().add(formPanel);
        this.setBounds(0, 0, (mainFrame.getWidth()), (mainFrame.getHeight() - 50));
        this.setLocationRelativeTo(null);
    }

    private JPanel createTitlePanel(String titleText){
        JPanel panelTitle = new JPanel();
        JLabel labelTitle = new JLabel(titleText);
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.setBackground(Color.white);
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    /**
     * Setup and create a new JTabbedPane instance
     * Here is created a instance of all nested JPanels
     * @return JTabbedPane
     */
    private JTabbedPane createTabbedPanel(){
        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setUI(new BasicTabbedPaneUI() {
            @Override protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {return 25;}

        });
        for (Iterator iterator = configurables.iterator(); iterator.hasNext();) {
            Configurable<?> configurable = (Configurable<?>) iterator.next();
            if(!(configurable instanceof EnableTaskProperty)) {
                ConfigurablePanel configPanel = ConfigurablePanel.createConfigurablePanel(configurable, mainFrame);

                configPanel.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        configurationManager.notifyUpdate(configurable);
                    }
                });

                configPanel.createConfigurableGUI();
                configurablePanels.put(configurable,configPanel);
                String localizedName = iped.engine.localization.Messages.getString(configurable.getClass().getName(), configurable.getClass().getSimpleName());
                String localizedTooltip = iped.engine.localization.Messages.getString(configurable.getClass().getName()+iped.engine.localization.Messages.TOOLTIP_SUFFIX, "");
                tabbedPane.addTab(localizedName, UIManager.getIcon("FileView.fileIcon"), configPanel, localizedTooltip);
            }
        }
        return  tabbedPane;
    }

    private Component createButtonsPanel() {
        JButton btCancel = new JButton(Messages.get("Home.Cancel"));
        btCancel.addActionListener( e -> {
            this.setVisible(false);
        });
        JButton btSave = new JButton(Messages.get("Home.Save"));
        btSave.addActionListener( e -> {
            try {
                for (Iterator iterator = configurables.iterator(); iterator.hasNext();) {
                    Configurable<?> configurable = (Configurable<?>)iterator.next();
                    if(!(configurable instanceof EnableTaskProperty)) {
                        ConfigurablePanel configPanel = configurablePanels.get(configurable);
                        if(configPanel.hasChanged()) {
                            configPanel.applyChanges();
                            configPanel.fireChangeListener(new ChangeEvent(this));
                        }
                    }
                }
                this.setVisible(false);
            }catch(ConfigurableValidationException cve) {
                JOptionPane.showMessageDialog(this, cve.getMessage() + "\n" + cve.getCause(), "", JOptionPane.WARNING_MESSAGE);
                cve.printStackTrace();
            }
        });

        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        panelButtons.add(btSave);
        panelButtons.add(btCancel);
        return panelButtons;
    }

}
