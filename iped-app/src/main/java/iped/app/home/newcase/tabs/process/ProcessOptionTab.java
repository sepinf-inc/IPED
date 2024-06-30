package iped.app.home.newcase.tabs.process;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.configurables.ConfigurablePanel;
import iped.app.home.configurables.ConfigurablePanelFactory;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.home.configurables.api.IConfigurablePanelFactory;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.newcase.model.ExistentCaseOptions;
import iped.app.home.newcase.model.IPEDProcess;
import iped.app.home.processmanager.ProcessManager;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
import iped.configuration.Configurable;
import iped.configuration.EnabledInterface;
import iped.configuration.IConfigurationDirectory;
import iped.engine.config.ConfigurableChangeListener;
import iped.engine.config.ConfigurationDirectory;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.config.ProfileManager;
import iped.engine.config.SerializedConfigurationDirectory;
import iped.engine.config.TaskInstallerConfig;
import iped.engine.task.AbstractTask;
import iped.engine.task.ScriptTask;
import iped.exception.IPEDException;

/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 *          Patrick Dalla Bernardina
 */
public class ProcessOptionTab extends DefaultPanel implements TableModelListener, ConfigurableChangeListener {

    private JTable jtableTasks;
    private TasksTableModel tasksTableModel;
    private TaskInstallerConfig taskInstallerConfig;// referece to current task installer being edited
    private List<AbstractTask> taskArrayList;// list of tasks being edited

    JFileChooser scriptChooser = new JFileChooser();
    private JComboBox<IConfigurationDirectory> profilesCombo;
    private ConfigurationManager selectedConfigurationManager;// configuration manager corresponding to the current profile selected

    /**
     * The default config manager will be used as the base line to create the task
     * list. Other profiles will only enable or disable these tasks, or add delete
     * Script tasks.
     */
    private ConfigurationManager defaultConfigurationManager;// configuration manager corresponding to iped default distributed configuration

    private JPanel selectProfilePanel;
    private JPanel createProfilePanel;
    private JTextField tfProfileName;
    private JButton buttonStartProcess;

    /**
     * Profile representing the configs in "conf" folder.
     */
    ConfigurationDirectory baselineProfile;

    private JButton deleteProfileBtn;
    private Object[] profilesArray;
    private boolean isInsertingProfile;
    private JScrollPane fsScrollPanel;
    private ConfigurablePanel fsConfigPanel;

    private static final String SELECT_PROFILE_PANEL = "selectProfilePanel";
    private static final String CREATE_PROFILE_PANEL = "createProfilePanel";

    public ProcessOptionTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        this.createObjectInstances();
        this.setLayout(new BorderLayout());
        this.add(createTitlePanel(), BorderLayout.NORTH);

        JComponent panelForm = createFormPanel();

        this.add(panelForm, BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
        // show select profile panel and hide create profile panel
        // use this method after create createNavigationButtonsPanel to avoid
        // NullpointerException
        showProfilePanel(SELECT_PROFILE_PANEL);
    }

    /**
     * Executed on tab initialization. Creates some needed object references.
     */
    private void createObjectInstances() {
        // create the baseline profile item on profilemanager
        baselineProfile = ProfileManager.get().getDefaultProfile();
        ProfileManager.get().addObject(baselineProfile);

        defaultConfigurationManager = ConfigurationManager.get();

        // for the first time, the selected configuration is the default
        selectedConfigurationManager = new ConfigurationManager(baselineProfile);
        selectedConfigurationManager.addConfigurableChangeListener(this);

        try {
            defaultConfigurationManager.loadConfigs(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JPanel createTitlePanel() {
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(super.getCurrentBackGroundColor());
        JLabel labelTitle = new JLabel(Messages.get("Home.ProcOptions.Title"));
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private JComponent createFormPanel() {
        JPanel panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout(panelForm, BoxLayout.PAGE_AXIS));
        panelForm.setBackground(super.getCurrentBackGroundColor());
        setupSelectProfilesPanel(panelForm);
        setupCreateProfilePanel(panelForm);
        panelForm.add(Box.createRigidArea(new Dimension(10, 10)));

        JPanel tasksPanel = new JPanel();
        tasksPanel.setLayout(new BoxLayout(tasksPanel, BoxLayout.PAGE_AXIS));
        setupTasksTables(tasksPanel);

        JTabbedPane tabPane = new JTabbedPane();
        tabPane.add("Tasks", tasksPanel);
        JPanel fsPanel = new JPanel();
        setupFSConfigPanel(fsPanel);
        fsPanel.setLayout(new BoxLayout(fsPanel, BoxLayout.PAGE_AXIS));
        tabPane.add(iped.engine.localization.Messages.getString(FileSystemConfig.class.getName()), fsPanel);
        tabPane.setPreferredSize(new Dimension(600, 2000));

        panelForm.add(tabPane);

        ItemListener[] listeners = profilesCombo.getItemListeners();
        for (int i = 0; i < listeners.length; i++) {
            profilesCombo.removeItemListener(listeners[i]);
        }
        for (int i = 0; i < profilesArray.length; i++) {
            if (((IConfigurationDirectory) profilesArray[i]).getName().equals("default")) {
                profilesCombo.setSelectedItem((IConfigurationDirectory) profilesArray[i]);
                break;
            }
        }
        for (int i = 0; i < listeners.length; i++) {
            profilesCombo.addItemListener(listeners[i]);
        }

        loadTasksTables((IConfigurationDirectory) profilesCombo.getSelectedItem());

        return panelForm;
    }

    /**
     * Create a Jpanel to user choose a process profile
     * 
     * @param parentPanel
     *            - a parent panel to add the "select profile panel"
     */
    private void setupSelectProfilesPanel(JPanel parentPanel) {
        selectProfilePanel = new JPanel();
        deleteProfileBtn = new JButton("Delete");
        parentPanel.add(selectProfilePanel);
        selectProfilePanel.setBackground(super.getCurrentBackGroundColor());
        selectProfilePanel.add(new JLabel(Messages.get("Home.ProcOptions.ExecProfile")));
        profilesArray = ProfileManager.get().getObjects().toArray();
        profilesCombo = new JComboBox(profilesArray);
        profilesCombo.setPreferredSize(new Dimension(200, (int) profilesCombo.getPreferredSize().getHeight()));
        selectProfilePanel.add(profilesCombo);
        profilesCombo.setSelectedItem(baselineProfile);
        profilesCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                IConfigurationDirectory configDirectory = (IConfigurationDirectory) e.getItem();
                if ((e.getStateChange() == ItemEvent.SELECTED) && (!isInsertingProfile)) {
                    loadTasksTables(configDirectory);
                    setupFSConfigPanel(null);
                }
                if (configDirectory instanceof SerializedConfigurationDirectory) {
                    selectProfilePanel.add(deleteProfileBtn);
                } else {
                    selectProfilePanel.remove(deleteProfileBtn);
                }
                selectProfilePanel.updateUI();
            }
        });
        deleteProfileBtn.addActionListener(e -> {
            IConfigurationDirectory configDirectory = (IConfigurationDirectory) profilesCombo.getSelectedItem();
            ProfileManager.get().removeObject(configDirectory);
            profilesCombo.removeItemAt(profilesCombo.getSelectedIndex());
            selectProfilePanel.remove(deleteProfileBtn);
        });
    }

    /**
     * A JPanel to show user options to create a new profile
     * 
     * @param parentPanel
     *            - a parent panel to add the new "create profile panel"
     */
    private void setupCreateProfilePanel(JPanel parentPanel) {
        createProfilePanel = new JPanel();
        parentPanel.add(createProfilePanel);
        createProfilePanel.setBackground(super.getCurrentBackGroundColor());
        createProfilePanel.add(new JLabel(Messages.get("Home.ProcOptions.NewProfile")));

        tfProfileName = new JTextField();
        tfProfileName.setBackground(Color.RED);
        tfProfileName.setPreferredSize(new Dimension(200, (int) tfProfileName.getPreferredSize().getHeight()));
        tfProfileName.setText(((IConfigurationDirectory) profilesCombo.getSelectedItem()).getName());
        createProfilePanel.add(tfProfileName);

        JButton btInsertProfile = new JButton(Messages.get("Home.ProcOptions.CreateNewProfile"));
        createProfilePanel.add(btInsertProfile);
        btInsertProfile.addActionListener(e -> {
            try {
                isInsertingProfile = true;

                try {
                    fsConfigPanel.applyChanges();
                    fsConfigPanel.fireChangeListener(new ChangeEvent(this));
                } catch (ConfigurableValidationException e1) {
                    e1.printStackTrace();
                }

                updateTaskInstallerConfig();
                IConfigurationDirectory dir = ProfileManager.get().createProfile(tfProfileName.getText(), selectedConfigurationManager);
                profilesCombo.addItem(dir);
                showProfilePanel(SELECT_PROFILE_PANEL);
                profilesCombo.setSelectedItem(dir);
            } catch (FileAlreadyExistsException e1) {
                JOptionPane.showMessageDialog(this, e1.getMessage(), "", JOptionPane.WARNING_MESSAGE);
            } finally {
                isInsertingProfile = false;
            }
        });

        JButton btCancel = new JButton(Messages.get("Home.Cancel"));
        btCancel.addActionListener(e -> {
            loadTasksTables((IConfigurationDirectory) profilesCombo.getSelectedItem());
            setupFSConfigPanel(null);
            showProfilePanel(SELECT_PROFILE_PANEL);
        });
        createProfilePanel.add(btCancel);
    }

    /**
     * A little function do control the display of create profile panel and select
     * profile panel
     * 
     * @param panelName
     *            - Name of the panel to be displayed
     */
    private void showProfilePanel(final String panelName) {
        switch (panelName) {
            case CREATE_PROFILE_PANEL: {
                buttonStartProcess.setEnabled(false);
                buttonStartProcess.setToolTipText(Messages.get("Home.ProcOptions.ButtonStarExecTooltip"));
                createProfilePanel.setVisible(true);
                selectProfilePanel.setVisible(false);
                break;
            }
            case SELECT_PROFILE_PANEL: {
                buttonStartProcess.setEnabled(true);
                buttonStartProcess.setToolTipText("");
                createProfilePanel.setVisible(false);
                selectProfilePanel.setVisible(true);
                break;
            }
        }
    }

    /**
     * Update selected profile task installer configurable with the tasks states
     * defined in UI.
     */
    private void updateTaskInstallerConfig() {
        List<AbstractTask> tasks = new ArrayList<>();
        for (int i = 0; i < tasksTableModel.getRowCount(); i++) {
            AbstractTask currentTask = tasksTableModel.getTaskList().get(i);
            tasks.add(currentTask);
        }
        taskInstallerConfig.update(tasks);
    }

    private Object clone(Object src) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(src);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            // supposedly impossible to occur
            e.printStackTrace();
            return null;
        }
    }

    private void loadTasksTables(IConfigurationDirectory selectedDirectory) {
        selectedConfigurationManager = new ConfigurationManager(selectedDirectory);

        ConfigurationManager.setCurrentConfigurationManager(selectedConfigurationManager);
        selectedConfigurationManager.addConfigurableChangeListener(this);

        Set<Configurable<?>> configs = defaultConfigurationManager.getObjects();
        for (Configurable<?> config : configs) {
            // overwrite if already exists with the clone of default config object
            selectedConfigurationManager.addObject((Configurable<?>) clone(config));
        }

        try {
            selectedConfigurationManager.loadConfigs(true);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        // Get a TaskInstallerConfig instance with the TaskInstaller.xml content frin
        // the selected profile directory
        taskInstallerConfig = selectedConfigurationManager.findObject(TaskInstallerConfig.class);
        // if the selected profile dos not has a TaskInstaller.xml, load it from de
        // CONFDIR location
        if (taskInstallerConfig == null)
            taskInstallerConfig = defaultConfigurationManager.findObject(TaskInstallerConfig.class);
        // Creates a list with all AbstractTask class instance from the
        // TaskInstaller.xml file
        taskArrayList = taskInstallerConfig.getNewTaskInstances();
        ArrayList<Boolean> enabled = new ArrayList<Boolean>();
        ArrayList<EnabledInterface> enabledConfigurables = new ArrayList<EnabledInterface>();

        for (AbstractTask currentTask : taskArrayList) {
            List<Configurable<?>> configurableList = null;
            try {
                configurableList = currentTask.getConfigurables();
            } catch (Exception e) {
            }
            if (configurableList == null || configurableList.isEmpty()) {
                enabledConfigurables.add(null);
                enabled.add(currentTask.isEnabled());
                continue;
            }

            EnabledInterface enabledConfigurable = null;
            for (Iterator iterator = configurableList.iterator(); iterator.hasNext();) {
                Configurable<?> configurable = (Configurable<?>) iterator.next();
                if (configurable instanceof EnabledInterface) {
                    enabledConfigurable = (EnabledInterface) configurable;
                    break;// uses the first EnabledInterface found as the main EnabledInterface for task
                          // enabling
                }
            }
            enabledConfigurables.add(enabledConfigurable);
            if (enabledConfigurable != null) {
                enabled.add(enabledConfigurable.isEnabled());
            } else {
                enabled.add(false);
            }
        }

        tasksTableModel.updateData(selectedConfigurationManager, taskArrayList, enabled, enabledConfigurables);
        jtableTasks.getColumn(jtableTasks.getColumnName(3)).setCellRenderer(new TaskTableConfigurablesCellRenderer(selectedConfigurationManager, mainFrame));
    }

    /**
     * Create the tasks table
     * 
     * @param parentPanel
     *            - Panel to show the tasktable
     */
    private void setupTasksTables(JPanel parentPanel) {
        // Create the table model
        tasksTableModel = new TasksTableModel(selectedConfigurationManager, mainFrame, taskArrayList);
        tasksTableModel.addTableModelListener(this);
        jtableTasks = new JTable() {
            @Override
            public String getToolTipText(MouseEvent event) {
                String tip = null;
                java.awt.Point p = event.getPoint();
                int rowIndex = rowAtPoint(p);
                try {
                    if (rowIndex != 0) {
                        AbstractTask task = (AbstractTask) getValueAt(rowIndex, 2);
                        String title = iped.engine.localization.Messages.getString(task.getClass().getName(), task.getName());
                        tip = iped.engine.localization.Messages.getString(task.getClass().getName() + iped.engine.localization.Messages.TOOLTIP_SUFFIX, title);
                    }
                } catch (RuntimeException e1) {
                }

                return tip;
            }

        };
        setupTableLayout();
        JScrollPane scrollTablePane = new JScrollPane();
        scrollTablePane.setViewportView(jtableTasks);
        parentPanel.add(scrollTablePane);
    }

    /**
     * Create and setup a JTable do manage all Evidences to be processed
     * 
     * @param panel
     *            - A JPanel to add JTable
     */
    private void setupFSConfigPanel(JPanel panel) {
        Configurable<?> fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);

        IConfigurablePanelFactory configPanelFactory = ConfigurablePanelFactory.getInstance();
        fsConfigPanel = (ConfigurablePanel) configPanelFactory.createConfigurablePanel(null, fsConfig, mainFrame);

        fsConfigPanel.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                selectedConfigurationManager.notifyUpdate(fsConfig);
            }
        });

        fsConfigPanel.createConfigurableGUI();

        if (fsScrollPanel == null) {
            fsScrollPanel = new JScrollPane(fsConfigPanel);
            panel.add(fsScrollPanel);
        } else {
            fsScrollPanel.setViewportView(fsConfigPanel);
        }
    }

    private void setupTableLayout() {
        jtableTasks.setFillsViewportHeight(true);
        jtableTasks.setRowHeight(30);
        jtableTasks.setModel(tasksTableModel);
        jtableTasks.getColumn(jtableTasks.getColumnName(3)).setCellRenderer(new TaskTableConfigurablesCellRenderer(selectedConfigurationManager, mainFrame));
        jtableTasks.getColumn(jtableTasks.getColumnName(3)).setCellEditor(new TableTaskOptionsCellEditor(new JCheckBox()));

        jtableTasks.getColumn(jtableTasks.getColumnName(1)).setCellRenderer(new TableTaskEnabledCellRenderer(jtableTasks.getDefaultRenderer(jtableTasks.getColumnClass(1))));

        jtableTasks.getColumn(jtableTasks.getColumnName(2)).setCellRenderer(new TableTaskLabelCellRenderer());
        jtableTasks.getColumn(jtableTasks.getColumnName(2)).setCellEditor(new TableTaskOptionsCellEditor(new JCheckBox()));

        jtableTasks.getColumn(jtableTasks.getColumnName(0)).setMaxWidth(30);
        jtableTasks.getColumn(jtableTasks.getColumnName(1)).setMaxWidth(30);
        jtableTasks.getColumn(jtableTasks.getColumnName(3)).setMaxWidth(60);

        jtableTasks.setTransferHandler(new LineOrderTransferHandler());
        jtableTasks.setDropMode(DropMode.INSERT_ROWS);
        // jtableTasks.setDragEnabled(true);
    }

    private Component createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(super.getCurrentBackGroundColor());
        JButton buttonGoBack = new JButton(Messages.get("Home.Back"));
        buttonGoBack.addActionListener(e -> NewCaseContainerPanel.getInstance().goToPreviousTab());

        JButton buttoAddScriptTask = new JButton(Messages.get("Home.ProcOptions.AddScriptTask"));
        buttoAddScriptTask.addActionListener(e -> {
            File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.SCRIPT_BASE).toFile();
            File exampleScriptFile = new File(scriptDir, ScriptEditPanel.TEMPLATE_SCRIPT_NAME);
            TaskConfigDialog tcd = new TaskConfigDialog(selectedConfigurationManager, new ScriptTask(exampleScriptFile), mainFrame);
            tcd.setModalityType(ModalityType.APPLICATION_MODAL);
            tcd.setVisible(true);

            AbstractTask addedTask = (AbstractTask) tcd.getScriptTask();
            if (addedTask != null) {
                tasksTableModel.addData(addedTask, true);
                tasksTableModel.fireTableRowsInserted(taskArrayList.size() - 1, taskArrayList.size() - 1);
                selectedConfigurationManager.notifyUpdate(taskInstallerConfig);
            }
        });

        buttonStartProcess = new JButton(Messages.get("Home.ProcOptions.StartProcessing"));
        buttonStartProcess.addActionListener(e -> {
            IPEDProcess ipp = NewCaseContainerPanel.getInstance().getIpedProcess();
            boolean existsCaseOnOutput = false;
            try {
                ProcessManager.validateCaseOutput(ipp.getCaseOutputPath());
            } catch (IPEDException ex) {
                existsCaseOnOutput = true;
                // if already has a case in the output directory and the user do not select an
                // option in the caseinfotab, show the options again!!
                if (ipp.getExistentCaseOption() == null) {
                    Object[] options = { Messages.get("Home.AppendExistentCase"), Messages.get("Home.ContinueExistentCase"), Messages.get("Home.RestartExistentCase"), Messages.get("Case.Cancel") };
                    int result = JOptionPane.showOptionDialog(this, Messages.getString("Home.ProcessManager.ExistentCaseAlertMessage"), Messages.getString("Home.ProcessManager.ExistentCaseAlertTitle"), JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE, null, options, options[3]);
                    switch (result) {
                        case 0:
                            ipp.setExistentCaseOption(ExistentCaseOptions.APPEND);
                            break;
                        case 1:
                            ipp.setExistentCaseOption(ExistentCaseOptions.CONTINUE);
                            break;
                        case 2:
                            ipp.setExistentCaseOption(ExistentCaseOptions.RESTART);
                            break;
                        default:
                            ipp.setExistentCaseOption(null);
                            return;
                    }
                }
            }
            if (!existsCaseOnOutput)
                ipp.setExistentCaseOption(null);
            String selectedProfile = ((IConfigurationDirectory) profilesCombo.getSelectedItem()).getName();
            if (baselineProfile.getName().equalsIgnoreCase(selectedProfile))
                ipp.setProfile(null);
            else
                ipp.setProfile(selectedProfile);

            NewCaseContainerPanel.getInstance().startIPEDProcessing();
        });

        panelButtons.add(buttonGoBack);
        // panelButtons.add(buttoAddScriptTask);
        panelButtons.add(buttonStartProcess);
        return panelButtons;
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.INSERT) {
            jtableTasks.getSelectionModel().setLeadSelectionIndex(e.getLastRow());
            AbstractTask task = ((TasksTableModel) jtableTasks.getModel()).getTaskList().get(e.getLastRow());
            List<Configurable<?>> configurables = task.getConfigurables();
            if (configurables != null && configurables.size() > 0) {
                new TaskConfigDialog(selectedConfigurationManager, task, mainFrame).setVisible(true);
            }
        }
    }

    /**
     * A listener for task table item selection this action will show to user a
     * panel create a new profile!!
     *
     */
    @Override
    public void onChange(Configurable<?> configurable) {
        showProfilePanel(CREATE_PROFILE_PANEL);
        jtableTasks.updateUI();
    }
}
