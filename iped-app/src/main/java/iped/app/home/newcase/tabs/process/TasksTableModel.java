package iped.app.home.newcase.tabs.process;/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */


import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;

public class TasksTableModel extends AbstractTableModel {

    private final String[] COLLUM_NAME = {" ", "TASK", "OPÇÕES"};
    private final ArrayList<Task> taskList;

    public TasksTableModel(ArrayList<Task> taskList) {
        this.taskList = taskList;
    }

    public ArrayList<Task> getTaskList() {
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
            case 0: return Boolean.class;
            case 1: return String.class;
            case 2: return JPanel.class;
            default: return String.class;
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(taskList == null)
            return "";
        Task currentTask = taskList.get(rowIndex);
        switch (columnIndex){
            case 0: return currentTask.isEnabled();
            case 1: return currentTask.getName();
            case 2: return createColumnOptionPanel(rowIndex);
            default: return "";
        }
    }

    private JPanel createColumnOptionPanel(int rowIndex ){
        //Task task = taskList.get(rowIndex);
        JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );
        GridBagConstraints gbc = new GridBagConstraints();
        JButton taskOptionButton = new JButton("...");
        taskOptionButton.addActionListener( e -> JOptionPane.showMessageDialog(JOptionPane.getFrameForComponent(taskOptionButton), "Abrir painel de edição da task..") );
        taskOptionButton.setVerticalAlignment(SwingConstants.CENTER);
        panel.add(taskOptionButton, gbc);
        return panel;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex){
            case 0: return true;
            case 1: return false;
            case 2: return true;
            default: return false;
        }
    }

    @Override
    public String getColumnName(int column) {
        return COLLUM_NAME[column];
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Task currentTask = taskList.get(rowIndex);
        switch (columnIndex){
            case 0: currentTask.setEnabled( ((boolean) aValue)); break;
            case 1: currentTask.setName("nome da task"); break;
        }
    }


}
