package iped.app.home.newcase.tabs.process;

/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 *          Patrick Dalla Bernardina
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.MainFrameCardsNames;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.newcase.tabs.process.AbstractTaskClassPopupMenu.JAbstractTaskClassMenuItem;
import iped.configuration.Configurable;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.TaskInstallerConfig;
import iped.engine.task.AbstractTask;
import iped.engine.task.PythonTask;
import iped.engine.task.ScriptTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.reflections.Reflections;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ProcessOptionTab extends DefaultPanel implements TableModelListener {

    private JTable jtableTasks;
    private TasksTableModel tasksTableModel;
    private TaskInstallerConfig taskInstallerConfig;
    private List<AbstractTask> enabledTaskArrayList;
    private List<AbstractTask> taskArrayList;

    JFileChooser scriptChooser = new JFileChooser();
    
    public ProcessOptionTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
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
        JPanel panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout( panelForm, BoxLayout.PAGE_AXIS ));
        panelForm.setBackground(Color.white);
        setupProfilesPanel(panelForm);
        panelForm.add( Box.createRigidArea( new Dimension(10, 10) ) );
        setupTasksTables(panelForm);
        //setupScriptsTables(panelForm);
        return panelForm;
    }

    private void setupProfilesPanel(JPanel panel){
        JPanel buttonPanel = new JPanel();
        //buttonPanel.setLayout(new BoxLayout( buttonPanel, BoxLayout.LINE_AXIS ));
        buttonPanel.setBackground(Color.white);
        buttonPanel.add( new JLabel("Perfil de execução:") );
        buttonPanel.add( new JComboBox<>() );
        buttonPanel.add( new JButton("Criar novo perfil") );
        panel.add(buttonPanel);
    }

    private void setupScriptsTables(JPanel panel){
        if(taskInstallerConfig==null) {
            taskInstallerConfig = (TaskInstallerConfig)ConfigurationManager.get().findObject(TaskInstallerConfig.class);
        }
        enabledTaskArrayList = taskInstallerConfig.getNewTaskInstances();
        taskArrayList = new ArrayList<AbstractTask>();
        taskArrayList.addAll(enabledTaskArrayList);

        Reflections reflections = new Reflections("iped.engine.task");
        Set<Class<? extends AbstractTask>> classes = reflections.getSubTypesOf(iped.engine.task.AbstractTask.class);
        for(Class<? extends AbstractTask> aClass : classes) {
            if(!Modifier.isAbstract(aClass.getModifiers())) {
                if(!enabledTaskArrayList.contains(aClass)) {
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

        tasksTableModel = new TasksTableModel(mainFrame, taskArrayList);
        tasksTableModel.setEnabled(enabledTaskArrayList);
        jtableTasks = new JTable();
        setupTableLayout();
        panel.add( new JScrollPane(jtableTasks));
    }

    private void setupTasksTables(JPanel panel){
        if(taskInstallerConfig==null) {
            taskInstallerConfig = (TaskInstallerConfig)ConfigurationManager.get().findObject(TaskInstallerConfig.class);
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

        tasksTableModel = new TasksTableModel(mainFrame, taskArrayList);
        tasksTableModel.setEnabled(enabledTaskArrayList);
        tasksTableModel.addTableModelListener(this);
        jtableTasks = new JTable();
        setupTableLayout();
        panel.add( new JScrollPane(jtableTasks));
    }

    private void setupTableLayout(){
        jtableTasks.setFillsViewportHeight(true);
        jtableTasks.setRowHeight(30);
        jtableTasks.setModel(tasksTableModel);
        jtableTasks.getColumn( jtableTasks.getColumnName(2)).setCellRenderer( new TableTaskOptionsCellRenderer() );
        jtableTasks.getColumn( jtableTasks.getColumnName(2)).setCellEditor( new TableTaskOptionsCellEditor(new JCheckBox()) );

        jtableTasks.getColumn( jtableTasks.getColumnName(0)).setMaxWidth(30);
        jtableTasks.getColumn( jtableTasks.getColumnName(2)).setMaxWidth(60);
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
        
        JButton buttonNext = new JButton("Iniciar processamento");
        buttonNext.addActionListener( e -> mainFrame.startIPEDProcessing() );
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
                mainFrame.showPanel(new TaskConfigTabPanel(task, mainFrame));
            }
        }
    }

}


