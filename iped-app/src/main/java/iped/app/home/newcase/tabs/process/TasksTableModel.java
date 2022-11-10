package iped.app.home.newcase.tabs.process;/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import javax.swing.*;
import javax.swing.table.AbstractTableModel;

import iped.app.home.MainFrame;
import iped.configuration.Configurable;
import iped.engine.config.ConfigurationManager;
import iped.engine.task.AbstractTask;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TasksTableModel extends AbstractTableModel {
    ConfigurationManager configurationManager;

    private static final long serialVersionUID = 3318254202725499526L;
    
    private final String[] COLLUM_NAME = {"#", " ", "TASK", "OPÇÕES"};
    private final List<AbstractTask> taskList;
    private ArrayList<Boolean> enabled = new ArrayList<Boolean>();
    MainFrame mainFrame;

    public TasksTableModel(ConfigurationManager configurationManager, MainFrame mainFrame, List<AbstractTask> taskList) {
        this.configurationManager=configurationManager;
        this.taskList = taskList;
        this.mainFrame = mainFrame;
        for (int i=0; i<taskList.size();i++) {
            enabled.add(false);
        }
    }

    public List<AbstractTask> getTaskList() {
        return taskList;
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
            case 0: return rowIndex+1;
            case 1: {
                if(rowIndex>=enabled.size()) {
                    enabled.add(false);
                }
                return enabled.get(rowIndex);
            }
            case 2: return currentTask.getName();
            case 3: return createColumnOptionPanel(rowIndex);
            default: return "";
        }
    }

    private JPanel createColumnOptionPanel(int rowIndex){
        AbstractTask task = taskList.get(rowIndex);
        JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );

        List<Configurable<?>> configurables = task.getConfigurables();
        
        if(configurables.size()>0) {
            GridBagConstraints gbc = new GridBagConstraints();
            JButton taskOptionButton = new JButton("...");
            
            taskOptionButton.addActionListener( e -> {
                TaskConfigTabPanel tp = new TaskConfigTabPanel(configurationManager, task, mainFrame);
                mainFrame.showPanel(tp);
            });

            taskOptionButton.setVerticalAlignment(SwingConstants.CENTER);
            panel.add(taskOptionButton, gbc);
        }
        
        return panel;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex){
            case 0: return false;
            case 1: return true;
            case 2: return false;
            case 3: return true;
            default: return false;
        }
    }

    @Override
    public String getColumnName(int column) {
        return COLLUM_NAME[column];
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        AbstractTask currentTask = taskList.get(rowIndex);
        switch (columnIndex){
            case 1: enabled.set(rowIndex, ((boolean) aValue)); break;
        }
    }

    public void setEnabled(List<AbstractTask> enabledTaskArrayList) {
        for (int i=0; i<enabledTaskArrayList.size();i++) {
            int j = taskList.indexOf(enabledTaskArrayList.get(i));
            if(j>-1) {
                enabled.set(j, true);
            }
        }
    }


}
