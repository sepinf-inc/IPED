package iped.app.home.newcase.tabs.process;

import java.awt.Component;

<<<<<<< HEAD
import javax.swing.JCheckBox;
=======
import javax.swing.JComponent;
>>>>>>> branch '#23_UI_4_config_proc' of https://github.com/thiagofuer/IPED.git
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

public class TableTaskEnabledCellRenderer extends DefaultTableCellRenderer {
    TableCellRenderer wrapped;
    private JLabel emptyLabel;
    boolean showUnremovableCheckBox = true;
    
    public TableTaskEnabledCellRenderer(TableCellRenderer wrapped) {
        this.wrapped = wrapped;
        this.emptyLabel = new JLabel();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        Component comp = null;
        if(column==1) {
            TasksTableModel tm = (TasksTableModel) table.getModel();
            if(tm.isCellEditable(row, column)) {
<<<<<<< HEAD
                JCheckBox checkbox = (JCheckBox) wrapped.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                checkbox.setEnabled(true);
                return checkbox;
=======
                comp =  wrapped.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
>>>>>>> branch '#23_UI_4_config_proc' of https://github.com/thiagofuer/IPED.git
            }else {
<<<<<<< HEAD
                if(showUnremovableCheckBox){
                    JCheckBox checkbox = (JCheckBox) wrapped.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    checkbox.setEnabled(false);
                    checkbox.setSelected(true);
                    return checkbox;
                }else{
                    return emptyLabel;
                }
=======
                comp = emptyLabel;
>>>>>>> branch '#23_UI_4_config_proc' of https://github.com/thiagofuer/IPED.git
            }
        } else {
            comp = wrapped.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);;
        }
        ((JComponent) comp).setOpaque(true);
        comp.setBackground(TableCellRendererUtil.getBackground(table, row, isSelected));
        return comp;
    }

}
