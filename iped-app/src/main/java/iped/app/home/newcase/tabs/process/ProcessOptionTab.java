package iped.app.home.newcase.tabs.process;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.reflections.Reflections;

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.engine.config.ConfigurableChangeListener;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ProfileManager;
import iped.engine.config.TaskInstallerConfig;
import iped.engine.task.AbstractTask;
import iped.engine.task.PythonTask;
import iped.engine.task.ScriptTask;

/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 *          Patrick Dalla Bernardina
 */
public class ProcessOptionTab extends DefaultPanel implements TableModelListener, ItemListener, ConfigurableChangeListener{

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
    private ConfigurationManager defaultConfigurationManager=ConfigurationManager.get();
    private JPanel profilePanel;
    private JTextField tfProfile;
    private JButton buttonNext;

    public ProcessOptionTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        configurationManager=ConfigurationManager.get();
        defaultConfigurationManager=ConfigurationManager.get();
        defaultConfigurationManager.addConfigurableChangeListener(this);
        this.setLayout( new BorderLayout() );
        setBorder(new EmptyBorder(10,10,10,10));
        this.add(createTitlePanel(), BorderLayout.NORTH);
        this.add(createFormPanel(), BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
    }

    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Opções de processamento");
        labelTitle.setFont(new Font("Arial Bold", Font.PLAIN, 28));
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private JPanel createFormPanel(){
        panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout( panelForm, BoxLayout.PAGE_AXIS ));
        panelForm.setBackground(Color.white);
        setupProfilesPanel(panelForm);
        panelForm.add( Box.createRigidArea( new Dimension(10, 10) ) );
        setupTasksTables(panelForm);
        return panelForm;
    }

    private void setupProfilesPanel(JPanel panel){
        if(panel.getComponentCount()<=0) {
            profilePanel=new JPanel();
            panel.add(profilePanel);
        }else {
            profilePanel = (JPanel) panel.getComponent(0);
        }
        profilePanel.removeAll();

        profilePanel.setBackground(Color.white);
        profilePanel.add( new JLabel("Perfil de execução:") );
        
        ProfileManager profileManager = ProfileManager.get();
        profilesCombo = new JComboBox(profileManager.getObjects().toArray());
        profilesCombo.setPreferredSize(new Dimension(200,(int)profilesCombo.getPreferredSize().getHeight()));        
        profilePanel.add(profilesCombo);
        profilesCombo.addItemListener(this);
    }

    private void setupNewProfilePanel(JPanel panel){
        if(panel.getComponentCount()<=0) {
            profilePanel=new JPanel();
            panel.add(profilePanel);
        }else {
            profilePanel = (JPanel) panel.getComponent(0);
        }
        profilePanel.removeAll();
        
        profilePanel.setBackground(Color.white);
        profilePanel.add(new JLabel("Novo perfil de execução:") );
        
        buttonNext.setEnabled(false);
        buttonNext.setToolTipText("Save or cancel profile edition before continue.");
        
        tfProfile = new JTextField();
        tfProfile.setBackground(Color.RED);
        tfProfile.setPreferredSize(new Dimension(200,(int)tfProfile.getPreferredSize().getHeight()));        
        tfProfile.setText(((IConfigurationDirectory) profilesCombo.getSelectedItem()).getName());
        profilePanel.add(tfProfile);

        JButton btInsertProfile = new JButton("Criar novo perfil");

        profilePanel.add(btInsertProfile);
        btInsertProfile.addActionListener(e -> {
            try {
                updateTaskInstallerConfig();
                IConfigurationDirectory dir = ProfileManager.get().createProfile(tfProfile.getText(), configurationManager);
                profilesCombo.addItem(dir);

                buttonNext.setEnabled(true);
                buttonNext.setToolTipText("");
                setupProfilesPanel(panel);
                profilesCombo.setSelectedItem(dir);
                panel.repaint();
            } catch (FileAlreadyExistsException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        });

        JButton btCancel = new JButton("Cancelar");
        btCancel.addActionListener( e -> {
            setupTasksTables((IConfigurationDirectory) profilesCombo.getSelectedItem(), panelForm);
            buttonNext.setEnabled(true);
            setupProfilesPanel(panel);
            panel.repaint();
        });

        profilePanel.add(btCancel);
        profilePanel.updateUI();
    }

    private void updateTaskInstallerConfig() {
        List<AbstractTask> tasks = new ArrayList<AbstractTask>();
        for(int i=0; i<tasksTableModel.getRowCount();i++) {
            if(tasksTableModel.getEnabled(i)) {
                tasks.add(tasksTableModel.getTaskList().get(i));
            }
        }
        taskInstallerConfig.update(tasks);
    }

    private void setupTasksTables(JPanel panel){
        taskInstallerConfig = (TaskInstallerConfig) configurationManager.findObject(TaskInstallerConfig.class);
        if(taskInstallerConfig==null) {
            taskInstallerConfig = (TaskInstallerConfig) defaultConfigurationManager.findObject(TaskInstallerConfig.class);
        }
        enabledTaskArrayList = taskInstallerConfig.getNewTaskInstances();
        taskArrayList = new ArrayList<AbstractTask>();
        taskArrayList.addAll(enabledTaskArrayList);

        List<Class<? extends AbstractTask>> installedClasses = new ArrayList<Class<? extends AbstractTask>>();
        for (Iterator<AbstractTask> iterator = taskArrayList.iterator(); iterator.hasNext();) {
            AbstractTask abstractTask = iterator.next();
            installedClasses.add(abstractTask.getClass());            
        }

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

        tasksTableModel = new TasksTableModel(configurationManager, mainFrame, taskArrayList);
        tasksTableModel.setEnabled(enabledTaskArrayList);
        tasksTableModel.addTableModelListener(this);
        jtableTasks = new JTable();
        setupTableLayout();
        if(scrollTablePane==null) {
            scrollTablePane = new JScrollPane();
        }
        scrollTablePane.setViewportView(jtableTasks);
        panel.add(scrollTablePane);
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
        panelButtons.setBackground(Color.white);
        JButton buttoCancel = new JButton("Voltar");
        buttoCancel.addActionListener( e -> NewCaseContainerPanel.getInstance().goToPreviousTab());
        AbstractTaskClassPopupMenu abstractTaskClassPopupMenu = new AbstractTaskClassPopupMenu(jtableTasks);

        JButton buttoAddScriptTask = new JButton("Add script task");
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

        buttonNext = new JButton("Iniciar processamento");
        buttonNext.addActionListener( e -> {
            if(configurationManager.hasChanged()) {
                mainFrame.showPanel(MainFrameCardsNames.PROCESS_MANAGER);
            }
        } );

        panelButtons.add(buttoCancel);
        panelButtons.add(buttoAddScriptTask);
        panelButtons.add(buttonNext);
        return panelButtons;
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        if(e.getType()==TableModelEvent.INSERT) {
            jtableTasks.getSelectionModel().setLeadSelectionIndex(e.getLastRow());
            AbstractTask task = ((TasksTableModel)jtableTasks.getModel()).getTaskList().get(e.getLastRow());
            List<Configurable<?>> configurables = task.getConfigurables();
            if(configurables != null && configurables.size()>0) {
                mainFrame.showPanel(new TaskConfigTabPanel(configurationManager, task, mainFrame));
            }
        }
    }
    
    private void setupTasksTables(IConfigurationDirectory directory, JPanel panel){
        configurationManager = new ConfigurationManager(directory);
        configurationManager.addConfigurableChangeListener(this);
        Set<Configurable<?>> configs = defaultConfigurationManager.getObjects();
        for (Iterator iterator = configs.iterator(); iterator.hasNext();) {
            Configurable<?> configurable = (Configurable<?>) iterator.next();
            configurationManager.addObject(configurable);
        }
        try {
            configurationManager.loadConfigs(true);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        setupTasksTables(panelForm);
    }
    

    @Override
    public void itemStateChanged(ItemEvent e) {
        if(e.getStateChange()==ItemEvent.SELECTED) {
            setupTasksTables((IConfigurationDirectory) e.getItem(), panelForm);
        }
    }

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public void setConfigurationManager(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Override
    public void onChange(Configurable<?> configurable) {
        setupNewProfilePanel(panelForm);
        panelForm.invalidate();
        panelForm.repaint();
    }
}
