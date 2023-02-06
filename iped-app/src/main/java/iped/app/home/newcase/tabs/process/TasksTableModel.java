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
import java.util.Iterator;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
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
    private ArrayList<Boolean> enabled = new ArrayList<Boolean>();
    private ArrayList<EnabledInterface> enabledConfigurables = new ArrayList<EnabledInterface>();
    MainFrame mainFrame;

    private final TaskInstallerConfig taskInstallerConfig;

    public TasksTableModel(ConfigurationManager configurationManager, MainFrame mainFrame, List<AbstractTask> taskList) {
        this.configurationManager=configurationManager;
        this.taskInstallerConfig = configurationManager.findObject(TaskInstallerConfig.class);
        this.taskList = taskList;
        this.mainFrame = mainFrame;
    }

    public List<AbstractTask> getTaskList() {
        return taskList;
    }

    public void addData(int row, AbstractTask task, Boolean enabled) {
        this.taskList.add(row, task);
        this.enabled.add(row, enabled);
        List<Configurable<?>> configs = task.getConfigurables();
        Configurable<?> enableConfigurable = null;
        Configurable<?> configurable;
        if(configs!= null && configs.size()>0) {
            for (Iterator iterator = configs.iterator(); iterator.hasNext();) {
                configurable = (Configurable<?>) iterator.next();
                if(configurable instanceof EnabledInterface) {
                    enableConfigurable=configurable;
                }
            }
        }
        this.enabledConfigurables.add(row, (EnabledInterface) enableConfigurable);                    
        
    }

    public void addData(AbstractTask task, Boolean enabled) {
        addData(taskList.size(), task, enabled);
    }
    
    public void updateData(ConfigurationManager configurationManager, List<AbstractTask> taskList, ArrayList<Boolean> enabled, ArrayList<EnabledInterface> enabledConfigurable) {
        this.taskList = taskList;
        this.configurationManager = configurationManager;
        this.enabled = enabled;
        this.enabledConfigurables=enabledConfigurable;
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
            case 2: return AbstractTask.class;
            case 3: return JPanel.class;
            default: return String.class;
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(taskList == null)
            return "";
        List<Configurable<?>> configs;
        switch (columnIndex){
            //Task order column
            case 0: return rowIndex+1;
            //Task checkbox column
            case 1: return enabled.get(rowIndex);
            //task name column
            case 2: return taskList.get(rowIndex);
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

        return panel;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex){
            case 0: return false;//task order column
            case 1: return enabledConfigurables.get(rowIndex)!=null; //Checkbox column
            case 2: return true;//task name column
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
            EnabledInterface ec = enabledConfigurables.get(rowIndex);
            ec.setEnabled((boolean) aValue);
            configurationManager.notifyUpdate((Configurable) ec);
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
        enabledConfigurables.remove(i);
    }

    public void changeOrder(int i, int row) {
        if(i!=row) {
            taskList.add(row, taskList.get(i));
            enabled.add(row, enabled.get(i));
            enabledConfigurables.add(row,enabledConfigurables.get(i));
            if(i>row) {
                i++;
            }
            enabled.remove(i);
            taskList.remove(i);
            enabledConfigurables.remove(i);
            configurationManager.notifyUpdate(taskInstallerConfig);
        }
    }
}
