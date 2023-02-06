package iped.app.home.newcase.tabs.process;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
import iped.configuration.EnabledInterface;
import iped.engine.config.*;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.engine.task.AbstractTask;
import iped.engine.task.PythonTask;
import iped.engine.task.ScriptTask;

/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 *          Patrick Dalla Bernardina
 */
public class ProcessOptionTab extends DefaultPanel implements TableModelListener, ConfigurableChangeListener{

    private JTable jtableTasks;
    private TasksTableModel tasksTableModel;
    private TaskInstallerConfig taskInstallerConfig;//referece to current task installer being edited
    private List<AbstractTask> taskArrayList;//list of tasks being edited

    JFileChooser scriptChooser = new JFileChooser();
    private JComboBox<IConfigurationDirectory> profilesCombo;
    private ConfigurationManager selectedConfigurationManager;//configuration manager corresponding to the current profile selected
    private ConfigurationManager defaultConfigurationManager;//configuration manager corresponding to iped default distributed configuration 
    private JPanel selectProfilePanel;
    private JPanel createProfilePanel;
    private JTextField tfProfileName;
    private JButton buttonStartProcess;
    ConfigurationDirectory defaultProfile;

    private static final String SELECT_PROFILE_PANEL = "selectProfilePanel";
    private static final String CREATE_PROFILE_PANEL = "createProfilePanel";

    public ProcessOptionTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        this.createObjectInstances();
        this.setLayout( new BorderLayout() );
        this.add(createTitlePanel(), BorderLayout.NORTH);
        this.add(createFormPanel(), BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
        //show select profile panel and hide create profile panel
        //use this method after create createNavigationButtonsPanel to avoid NullpointerException
        showProfilePanel(SELECT_PROFILE_PANEL);
    }

    /**
     * Executed on tab initialization. Creates some needed object references. 
     */
    private void createObjectInstances(){
        //create a "default" profile item on profilemanager
        defaultProfile = ProfileManager.get().getDefaultProfile();
        ProfileManager.get().addObject(defaultProfile);

        defaultConfigurationManager = ConfigurationManager.get();
        //for the first time, the selected configuration is the default
        selectedConfigurationManager = new ConfigurationManager(defaultProfile);
        defaultConfigurationManager.addConfigurableChangeListener(this);
        try {
            defaultConfigurationManager.loadConfigs(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(super.getCurrentBackGroundColor());
        JLabel labelTitle = new JLabel(Messages.get("Home.ProcOptions.Title"));
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout( panelForm, BoxLayout.PAGE_AXIS ));
        panelForm.setBackground(super.getCurrentBackGroundColor());
        setupSelectProfilesPanel(panelForm);
        setupCreateProfilePanel(panelForm);
        panelForm.add( Box.createRigidArea( new Dimension(10, 10) ) );
        setupTasksTables(panelForm);
        loadTasksTables(defaultProfile);
        return panelForm;
    }

    /**
     * Create a Jpanel to user choose a process profile
     * @param parentPanel - a parent panel to add the "select profile panel"
     */
    private void setupSelectProfilesPanel(JPanel parentPanel){
        selectProfilePanel =new JPanel();
        parentPanel.add(selectProfilePanel);
        selectProfilePanel.setBackground(super.getCurrentBackGroundColor());
        selectProfilePanel.add( new JLabel(Messages.get("Home.ProcOptions.ExecProfile")) );
        profilesCombo = new JComboBox(ProfileManager.get().getObjects().toArray());
        profilesCombo.setPreferredSize(new Dimension(200,(int)profilesCombo.getPreferredSize().getHeight()));
        selectProfilePanel.add(profilesCombo);
        profilesCombo.setSelectedItem(defaultProfile);
        profilesCombo.addItemListener(e->{
            if(e.getStateChange()==ItemEvent.SELECTED) {
                loadTasksTables((IConfigurationDirectory) e.getItem());
            }
        });
    }

    /**
     * A JPanel to show user options to create a new profile
     * @param parentPanel - a parent panel to add the new "create profile panel"
     */
    private void setupCreateProfilePanel(JPanel parentPanel){
        createProfilePanel = new JPanel();
        parentPanel.add(createProfilePanel);
        createProfilePanel.setBackground(super.getCurrentBackGroundColor());
        createProfilePanel.add(new JLabel(Messages.get("Home.ProcOptions.NewProfile") ) );

        tfProfileName = new JTextField();
        tfProfileName.setBackground(Color.RED);
        tfProfileName.setPreferredSize(new Dimension(200,(int) tfProfileName.getPreferredSize().getHeight()));
        tfProfileName.setText(((IConfigurationDirectory) profilesCombo.getSelectedItem()).getName());
        createProfilePanel.add(tfProfileName);

        JButton btInsertProfile = new JButton(Messages.get("Home.ProcOptions.CreateNewProfile"));
        createProfilePanel.add(btInsertProfile);
        btInsertProfile.addActionListener(e -> {
            try {
                updateTaskInstallerConfig();
                IConfigurationDirectory dir = ProfileManager.get().createProfile(tfProfileName.getText(), selectedConfigurationManager);
                profilesCombo.addItem(dir);
                showProfilePanel(SELECT_PROFILE_PANEL);
                profilesCombo.setSelectedItem(dir);
            } catch (FileAlreadyExistsException e1) {
                JOptionPane.showMessageDialog(this, e1.getMessage(), "", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton btCancel = new JButton(Messages.get("Home.Cancel"));
        btCancel.addActionListener( e -> {
            loadTasksTables((IConfigurationDirectory) profilesCombo.getSelectedItem());
            showProfilePanel(SELECT_PROFILE_PANEL);
        });
        createProfilePanel.add(btCancel);
    }


    /**
     * A little function do control the display of create profile panel and select profile panel
     * @param panelName - Name of the panel to be displayed
     */
    private void showProfilePanel(final String panelName){
        switch (panelName){
            case CREATE_PROFILE_PANEL:{
                buttonStartProcess.setEnabled(false);
                buttonStartProcess.setToolTipText(Messages.get("Home.ProcOptions.ButtonStarExecTooltip"));
                createProfilePanel.setVisible(true);
                selectProfilePanel.setVisible(false);
                break;
            }
            case SELECT_PROFILE_PANEL:{
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
        for(int i=0; i<tasksTableModel.getRowCount();i++) {
            tasks.add(tasksTableModel.getTaskList().get(i));
        }
        taskInstallerConfig.update(tasks);
    }

    private void loadTasksTables(IConfigurationDirectory selectedDirectory){
        selectedConfigurationManager = new ConfigurationManager(selectedDirectory);
        ConfigurationManager.setCurrentConfigurationManager(selectedConfigurationManager);
        selectedConfigurationManager.addConfigurableChangeListener(this);
        if(selectedConfigurationManager.getConfigurationDirectory() instanceof ConfigurationDirectory) {
            Set<Configurable<?>> configs = defaultConfigurationManager.getObjects();
            for (Configurable<?> config : configs) {
                //overwrite if already exists
                selectedConfigurationManager.addObject(config);
            }
        }
        try {
            selectedConfigurationManager.loadConfigs(true);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        //Get a TaskInstallerConfig instance with the TaskInstaller.xml content frin the selected profile directory
        taskInstallerConfig = selectedConfigurationManager.findObject(TaskInstallerConfig.class);
        //if the selected profile dos not has a TaskInstaller.xml, load it from de CONFDIR location
        if(taskInstallerConfig==null)
            taskInstallerConfig = defaultConfigurationManager.findObject(TaskInstallerConfig.class);
        //Creates a list with all AbstractTask class instance from the TaskInstaller.xml file
        taskArrayList = taskInstallerConfig.getNewTaskInstances();
        ArrayList<Boolean> enabled = new ArrayList<Boolean>();
        ArrayList<EnabledInterface> enabledConfigurables = new ArrayList<EnabledInterface>();
        for(AbstractTask currentTask : taskArrayList  ){
            List<Configurable<?>> configurableList = currentTask.getConfigurables();
            if (configurableList == null || configurableList.isEmpty()){
                enabledConfigurables.add(null);
                enabled.add(currentTask.isEnabled());
                continue;
            }
            
            EnabledInterface enabledConfigurable = null;
            for (Iterator iterator = configurableList.iterator(); iterator.hasNext();) {
                Configurable<?> configurable = (Configurable<?>) iterator.next();
                if(configurable instanceof EnabledInterface) {
                    enabledConfigurable=(EnabledInterface) configurable;
                }
            }
            enabledConfigurables.add(enabledConfigurable);
            if(enabledConfigurable!=null) {
                enabled.add(enabledConfigurable.isEnabled());
            }
        }

        tasksTableModel.updateData(selectedConfigurationManager, taskArrayList, enabled, enabledConfigurables);
    }

    /**
     * Create the tasks table
     * @param parentPanel - Panel to show the tasktable
     */
    private void setupTasksTables(JPanel parentPanel){
        //Create the table model
        tasksTableModel = new TasksTableModel(selectedConfigurationManager, mainFrame, taskArrayList);
        tasksTableModel.addTableModelListener(this);
        jtableTasks = new JTable();
        setupTableLayout();
        JScrollPane scrollTablePane = new JScrollPane();
        scrollTablePane.setViewportView(jtableTasks);
        parentPanel.add(scrollTablePane);
    }

    private void setupTableLayout(){
        jtableTasks.setFillsViewportHeight(true);
        jtableTasks.setRowHeight(30);
        jtableTasks.setModel(tasksTableModel);
        jtableTasks.getColumn( jtableTasks.getColumnName(3)).setCellRenderer( new TableTaskOptionsCellRenderer() );
        jtableTasks.getColumn( jtableTasks.getColumnName(3)).setCellEditor( new TableTaskOptionsCellEditor(new JCheckBox()) );
        
        jtableTasks.getColumn( jtableTasks.getColumnName(1)).setCellRenderer(new TableTaskEnabledCellRenderer(jtableTasks.getDefaultRenderer(jtableTasks.getColumnClass(1))));

        jtableTasks.getColumn( jtableTasks.getColumnName(2)).setCellRenderer( new TableTaskLabelCellRenderer() );
        jtableTasks.getColumn( jtableTasks.getColumnName(2)).setCellEditor( new TableTaskOptionsCellEditor(new JCheckBox()) );

        jtableTasks.getColumn( jtableTasks.getColumnName(0)).setMaxWidth(30);
        jtableTasks.getColumn( jtableTasks.getColumnName(1)).setMaxWidth(30);
        jtableTasks.getColumn( jtableTasks.getColumnName(3)).setMaxWidth(60);
        
        jtableTasks.setTransferHandler(new LineOrderTransferHandler());
        jtableTasks.setDropMode(DropMode.INSERT_ROWS);
        jtableTasks.setDragEnabled(true);
    }

    private Component createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(super.getCurrentBackGroundColor());
        JButton buttoCancel = new JButton(Messages.get("Home.Back"));
        buttoCancel.addActionListener( e -> NewCaseContainerPanel.getInstance().goToPreviousTab());

        JButton buttoAddScriptTask = new JButton(Messages.get("Home.ProcOptions.AddScriptTask"));
        buttoAddScriptTask.addActionListener( e -> {
            int result = scriptChooser.showOpenDialog(mainFrame);
            if(result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = scriptChooser.getSelectedFile();

                AbstractTask task;
                if (selectedFile.getName().endsWith(".py")) {
                    task = new PythonTask(selectedFile);
                } else {
                    task = new ScriptTask(selectedFile);
                }
                tasksTableModel.addData(task, true);
                tasksTableModel.fireTableRowsInserted(taskArrayList.size()-1, taskArrayList.size()-1);
            }
        });

        buttonStartProcess = new JButton(Messages.get("Home.ProcOptions.StartProcessing"));
        buttonStartProcess.addActionListener(e -> {
            String selectedProfile = ((IConfigurationDirectory) profilesCombo.getSelectedItem()).getName();
            if(defaultProfile.getName().equalsIgnoreCase(selectedProfile) )
                NewCaseContainerPanel.getInstance().getIpedProcess().setProfile(null);
            else
                NewCaseContainerPanel.getInstance().getIpedProcess().setProfile(selectedProfile);
            mainFrame.startIPEDProcessing();
        });

        panelButtons.add(buttoCancel);
        panelButtons.add(buttoAddScriptTask);
        panelButtons.add(buttonStartProcess);
        return panelButtons;
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        if(e.getType()==TableModelEvent.INSERT) {
            jtableTasks.getSelectionModel().setLeadSelectionIndex(e.getLastRow());            
            AbstractTask task = ((TasksTableModel)jtableTasks.getModel()).getTaskList().get(e.getLastRow());
            List<Configurable<?>> configurables = task.getConfigurables();
            if(configurables != null && configurables.size()>0) {
                new TaskConfigDialog(selectedConfigurationManager, task, mainFrame).setVisible(true);
            }
        }
    }



    /**
     * A listener for task table item selection
     * this action will show to  user a panel create a new profile!!
     *
     */
    @Override
    public void onChange(Configurable<?> configurable) {
        showProfilePanel(CREATE_PROFILE_PANEL);
    }
}
