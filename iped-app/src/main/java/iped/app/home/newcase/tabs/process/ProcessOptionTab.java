package iped.app.home.newcase.tabs.process;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
import iped.engine.config.*;
import org.reflections.Reflections;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
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
    private TaskInstallerConfig taskInstallerConfig;
    private List<AbstractTask> enabledTaskArrayList;
    private List<AbstractTask> taskArrayList;

    JFileChooser scriptChooser = new JFileChooser();
    private JComboBox<IConfigurationDirectory> profilesCombo;
    private JPanel panelForm;
    private JScrollPane scrollTablePane;
    private ConfigurationManager configurationManager;
    private ConfigurationManager defaultConfigurationManager;
    private JPanel selectProfilePanel;
    private JPanel createProfilePanel;
    private JTextField tfProfileName;
    private JButton buttonStartProcess;

    private static final String SELECT_PROFILE_PANEL = "selectProfilePanel";
    private static final String CREATE_PROFILE_PANEL = "createProfilePanel";

    public ProcessOptionTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        configurationManager=ConfigurationManager.get();
        defaultConfigurationManager=ConfigurationManager.get();
        defaultConfigurationManager.addConfigurableChangeListener(this);
        this.setLayout( new BorderLayout() );
        this.add(createTitlePanel(), BorderLayout.NORTH);
        this.add(createFormPanel(), BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
        //show select profile panel and hide create profile panel
        //use this method after create createNavigationButtonsPanel to avoid NullpointerException
        showProfilePanel(SELECT_PROFILE_PANEL);
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
        panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout( panelForm, BoxLayout.PAGE_AXIS ));
        panelForm.setBackground(super.getCurrentBackGroundColor());
        setupSelectProfilesPanel(panelForm);
        setupCreateProfilePanel(panelForm);
        panelForm.add( Box.createRigidArea( new Dimension(10, 10) ) );
        setupTasksTables(panelForm);
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
        //create a "default" profile item on profilemanager
        ConfigurationDirectory defaultProfile = ProfileManager.get().getDefaultProfile();
        ProfileManager.get().addObject(defaultProfile);
        profilesCombo = new JComboBox(ProfileManager.get().getObjects().toArray());
        profilesCombo.setPreferredSize(new Dimension(200,(int)profilesCombo.getPreferredSize().getHeight()));
        selectProfilePanel.add(profilesCombo);
        profilesCombo.setSelectedItem(defaultProfile);
        profilesCombo.addItemListener(e->{
            if(e.getStateChange()==ItemEvent.SELECTED) {
                reloadTasksTables((IConfigurationDirectory) e.getItem());
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
                IConfigurationDirectory dir = ProfileManager.get().createProfile(tfProfileName.getText(), configurationManager);
                profilesCombo.addItem(dir);
                showProfilePanel(SELECT_PROFILE_PANEL);
                profilesCombo.setSelectedItem(dir);
            } catch (FileAlreadyExistsException e1) {
                JOptionPane.showMessageDialog(this, e1.getMessage(), "", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton btCancel = new JButton(Messages.get("Home.Cancel"));
        btCancel.addActionListener( e -> {
            reloadTasksTables((IConfigurationDirectory) profilesCombo.getSelectedItem());
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



    private void updateTaskInstallerConfig() {
        List<AbstractTask> tasks = new ArrayList<AbstractTask>();
        for(int i=0; i<tasksTableModel.getRowCount();i++) {
            if(tasksTableModel.getEnabled(i)) {
                tasks.add(tasksTableModel.getTaskList().get(i));
            }
            //loop to update enableTaskProperties configurables
            List<Configurable<?>> configs = tasksTableModel.getTaskList().get(i).getConfigurables();
            for (Iterator iterator = configs.iterator(); iterator.hasNext();) {
                Configurable<?> configurable = (Configurable<?>) iterator.next();
                if(configurable instanceof EnableTaskProperty) {
                    ((EnableTaskProperty) configurable).setEnabled(tasksTableModel.getEnabled(i));
                }
            }
        }
        taskInstallerConfig.update(tasks);
    }

    /**
     * Create and populate a table with all tasks list
     * @param parentPanel - Panel to show the tasktable
     */
    private void setupTasksTables(JPanel parentPanel){
        //enabledTaskArrayList: List of Tasks instances from TaskInstallerConfig.xml (default iped tasks and external tasks)
        //installedClasses: List of Classes of enabledTaskArrayList
        //taskArrayList: List of Tasks instances from enabledTaskArrayList (TaskInstallerConfig.xml) and "iped.engine.task" classes

        //Load all tasks class from TaskInstallerConfig.xml using reflection (method taskInstallerConfig.getNewTaskInstances())
        taskInstallerConfig = (TaskInstallerConfig) configurationManager.findObject(TaskInstallerConfig.class);
        if(taskInstallerConfig==null) {
            taskInstallerConfig = (TaskInstallerConfig) defaultConfigurationManager.findObject(TaskInstallerConfig.class);
        }
        enabledTaskArrayList = taskInstallerConfig.getNewTaskInstances();

        //Create a list of classes based on enabledTaskArrayList object instances
        //this list will be used to filter class instance from "iped.engine.task" classes
        List<Class<? extends AbstractTask>> installedClasses = new ArrayList<Class<? extends AbstractTask>>();
        for (Iterator<AbstractTask> iterator = enabledTaskArrayList.iterator(); iterator.hasNext();) {
            AbstractTask abstractTask = iterator.next();
            installedClasses.add(abstractTask.getClass());
        }

        //Copy all tasks instances from enabledTaskArrayList
        taskArrayList = new ArrayList<AbstractTask>();
        taskArrayList.addAll(enabledTaskArrayList);

        //Create instance from "iped.engine.task" classes that isn't loaded and add to taskArrayList
        //Add to taskArrayList tasks who is not in installedClasses list
        Reflections reflections = new Reflections("iped.engine.task");
        Set<Class<? extends AbstractTask>> classes = reflections.getSubTypesOf(iped.engine.task.AbstractTask.class);
        for(Class<? extends AbstractTask> aClass : classes) {
            if(!Modifier.isAbstract(aClass.getModifiers())) {
                if(!installedClasses.contains(aClass)) {
                    try {
                        taskArrayList.add(aClass.getDeclaredConstructor().newInstance());
                    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                            | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }

        //Create the table model
        tasksTableModel = new TasksTableModel(configurationManager, mainFrame, taskArrayList);
        tasksTableModel.setEnabled(enabledTaskArrayList);
        tasksTableModel.addTableModelListener(this);
        jtableTasks = new JTable();
        setupTableLayout();
        if(scrollTablePane==null) {
            scrollTablePane = new JScrollPane();
        }
        scrollTablePane.setViewportView(jtableTasks);
        parentPanel.add(scrollTablePane);
    }

    private void setupTableLayout(){
        jtableTasks.setFillsViewportHeight(true);
        jtableTasks.setRowHeight(30);
        jtableTasks.setModel(tasksTableModel);
        jtableTasks.getColumn( jtableTasks.getColumnName(3)).setCellRenderer( new TableTaskOptionsCellRenderer() );
        jtableTasks.getColumn( jtableTasks.getColumnName(3)).setCellEditor( new TableTaskOptionsCellEditor(new JCheckBox()) );

        jtableTasks.getColumn( jtableTasks.getColumnName(0)).setMaxWidth(30);
        jtableTasks.getColumn( jtableTasks.getColumnName(1)).setMaxWidth(30);
        jtableTasks.getColumn( jtableTasks.getColumnName(3)).setMaxWidth(60);
    }

    private Component createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(super.getCurrentBackGroundColor());
        JButton buttoCancel = new JButton(Messages.get("Home.Back"));
        buttoCancel.addActionListener( e -> NewCaseContainerPanel.getInstance().goToPreviousTab());
        //AbstractTaskClassPopupMenu abstractTaskClassPopupMenu = new AbstractTaskClassPopupMenu(jtableTasks);

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
                taskArrayList.add(task);
                tasksTableModel.fireTableRowsInserted(taskArrayList.size()-1, taskArrayList.size()-1);
            }
        });

        buttonStartProcess = new JButton(Messages.get("Home.ProcOptions.StartProcessing"));
        buttonStartProcess.addActionListener(e -> {
            //mainFrame.showPanel(MainFrameCardsNames.PROCESS_MANAGER);
            mainFrame.startIPEDProcessing();
        } );

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
                new TaskConfigDialog(configurationManager, task, mainFrame).setVisible(true);
            }
        }
    }

    private void reloadTasksTables(IConfigurationDirectory selectedDirectory){
        configurationManager = new ConfigurationManager(selectedDirectory);
        configurationManager.addConfigurableChangeListener(this);
        Set<Configurable<?>> configs = defaultConfigurationManager.getObjects();
        for (Iterator iterator = configs.iterator(); iterator.hasNext();) {
            Configurable<?> configurable = (Configurable<?>) iterator.next();
            //overwrite if already exists
            configurationManager.addObject(configurable);
        }
        try {
            configurationManager.loadConfigs(true);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        setupTasksTables(panelForm);
    }

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public void setConfigurationManager(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    /**
     * A listener for task table item selection
     * this action will show to  user a panel create a new profile!!
     * @param configurable
     */
    @Override
    public void onChange(Configurable<?> configurable) {
        showProfilePanel(CREATE_PROFILE_PANEL);
    }
}
