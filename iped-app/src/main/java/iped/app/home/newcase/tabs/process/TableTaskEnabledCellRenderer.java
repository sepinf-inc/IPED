package iped.app.home.newcase.tabs.process;

import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

public class TableTaskEnabledCellRenderer extends DefaultTableCellRenderer {
    TableCellRenderer wrapped;
    private JLabel emptyLabel;
    
    public TableTaskEnabledCellRenderer(TableCellRenderer wrapped) {
        this.wrapped = wrapped;
        this.emptyLabel = new JLabel();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        if(column==1) {
            TasksTableModel tm = (TasksTableModel) table.getModel();
            if(tm.isCellEditable(row, column)) {
                return wrapped.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }else {
                return emptyLabel;
            }
        }
        return wrapped.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }

}
