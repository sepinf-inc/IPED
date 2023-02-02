package iped.app.home.newcase.tabs.process;/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import java.awt.AWTException;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;

import iped.app.home.MainFrame;
import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.configuration.Configurable;
import iped.configuration.EnabledInterface;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.config.TaskInstallerConfig;
import iped.engine.task.AbstractTask;
import iped.engine.task.PythonTask;
import iped.engine.task.ScriptTask;
import iped.utils.IconUtil;

public class TasksTableModel extends AbstractTableModel {


    private static final long serialVersionUID = 3318254202725499526L;

    private final String[] COLLUM_NAME = {"#", " ", "Task", Messages.get("Home.ProcOptions.Table.Options")};
    private ConfigurationManager configurationManager;
    private List<AbstractTask> taskList;
    private ArrayList<Boolean> enabled = new ArrayList<>();
    MainFrame mainFrame;
    private static final String resPath = '/' + App.class.getPackageName().replace('.', '/') + '/';

    private final TaskInstallerConfig taskInstallerConfig;

    private ImageIcon dragIcon;

    public TasksTableModel(ConfigurationManager configurationManager, MainFrame mainFrame, List<AbstractTask> taskList) {
        this.configurationManager=configurationManager;
        this.taskInstallerConfig = configurationManager.findObject(TaskInstallerConfig.class);
        this.taskList = taskList;
        this.mainFrame = mainFrame;
        Image img = (new ImageIcon(IconUtil.class.getResource(resPath + "cursor-hand-icon.png"))).getImage();
        Image newimg = img.getScaledInstance( 16, 16,  java.awt.Image.SCALE_SMOOTH ) ;
        this.dragIcon = new ImageIcon(newimg);
    }

    public List<AbstractTask> getTaskList() {
        return taskList;
    }

    public void addData(int row, AbstractTask task, Boolean enabled) {
        this.taskList.add(row, task);
        this.enabled.add(row, enabled);
    }

    public void addData(AbstractTask task, Boolean enabled) {
        this.taskList.add(task);
        this.enabled.add(enabled);        
    }
    
    public void updateData(ConfigurationManager configurationManager, List<AbstractTask> taskList, ArrayList<Boolean> enabled) {
        this.taskList = taskList;
        this.configurationManager = configurationManager;
        this.enabled = enabled;
        this.fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return (taskList != null)? taskList.size(): 0;
    }

    @Override
    public int getColumnCount() {
        return COLLUM_NAME.length;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch(columnIndex)
        {
            case 0: return Integer.class;
            case 1: return Boolean.class;
            case 2: return String.class;
            case 3: return JPanel.class;
            default: return String.class;
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(taskList == null)
            return "";
        AbstractTask currentTask = taskList.get(rowIndex);
        switch (columnIndex){
            //Task order column
            case 0: return rowIndex+1;
            //Task checkbox column
            case 1: return enabled.get(rowIndex);
            //task name column
            case 2: return currentTask.getName();
            //options button column
            case 3: return createColumnOptionPanel(rowIndex);
            default: return "";
        }
    }

    /**
     * Create a JPanel to change the tasks properties
     */
    private JPanel createColumnOptionPanel(int rowIndex){
        AbstractTask task = taskList.get(rowIndex);
        JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );

        if((task instanceof PythonTask)||(task instanceof ScriptTask)) {
            GridBagConstraints gbc = new GridBagConstraints();
            JButton taskOptionButton = new JButton(dragIcon);
            taskOptionButton.setVerticalAlignment(SwingConstants.CENTER);

            taskOptionButton.addActionListener( e -> {
                Robot robot;
                try {
                    //simulate mouse click with shift pressed to start dragging
                    robot = new Robot();
                    Point point = panel.getLocationOnScreen(); //rect is my custom view
                    robot.mouseMove(point.x,point.y);
                    robot.mousePress(InputEvent.BUTTON1_MASK);
                } catch (AWTException e1) {
                    e1.printStackTrace();
                }            
            });

            panel.add(taskOptionButton, gbc);
        }else {
            List<Configurable<?>> configurables = task.getConfigurables();
            int count = 0;//counts the number of non EnableTaskProperty configurables
            for (Configurable<?> value : configurables) {
                if (!(value instanceof EnableTaskProperty)) {
                    count++;
                }
            }

            if(count>0) {
                GridBagConstraints gbc = new GridBagConstraints();
                JButton taskOptionButton = new JButton("...");

                taskOptionButton.addActionListener( e -> new TaskConfigDialog(configurationManager, task, mainFrame).setVisible(true));

                taskOptionButton.setVerticalAlignment(SwingConstants.CENTER);
                panel.add(taskOptionButton, gbc);
            }
        }
        return panel;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex){
            case 0: return false;//task order column
            case 1: return true; //Checkbox column
            case 2: return false;//task name column
            case 3: return true;//Option button column
            default: return false;
        }
    }

    @Override
    public String getColumnName(int column) {
        return COLLUM_NAME[column];
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        //Enable-disable Column
        if (columnIndex == 1) {
            enabled.set(rowIndex, ((boolean) aValue));
            List<Configurable<?>> configs = taskList.get(rowIndex).getConfigurables();
            for (Configurable<?> config : configs) {
                if (config instanceof EnabledInterface) {
                    ((EnabledInterface) config).setEnabled((boolean) aValue);
                    configurationManager.notifyUpdate(config);
                }
            }
            configurationManager.notifyUpdate(taskInstallerConfig);
        }
    }

    public void setEnabled(List<AbstractTask> enabledTaskArrayList) {
        for (AbstractTask abstractTask : enabledTaskArrayList) {
            int j = taskList.indexOf(abstractTask);
            if (j > -1) {
                enabled.set(j, true);
            }
        }
    }

    public boolean getEnabled(int rowIndex) {
        return enabled.get(rowIndex);
    }

    public void removeRow(int i) {
        enabled.remove(i);
        taskList.remove(i);
    }

    public void changeOrder(int i, int row) {
        if(i!=row) {
            this.taskList.add(row, taskList.get(i));
            this.enabled.add(row, enabled.get(i));
            if(i>row) {
                i++;
            }
            enabled.remove(i);
            taskList.remove(i);
            configurationManager.notifyUpdate(taskInstallerConfig);
        }
    }
}
