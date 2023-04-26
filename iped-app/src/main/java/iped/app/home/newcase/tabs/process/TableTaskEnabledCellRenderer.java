package iped.app.home.newcase.tabs.process;

import java.awt.Component;

import javax.swing.JComponent;
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
        Component comp = null;
        if(column==1) {
            TasksTableModel tm = (TasksTableModel) table.getModel();
            if(tm.isCellEditable(row, column)) {
                comp =  wrapped.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }else {
                comp = emptyLabel;
            }
        } else {
            comp = wrapped.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);;
        }
        ((JComponent) comp).setOpaque(true);
        comp.setBackground(TableCellRendererUtil.getBackground(table, row, isSelected));
        return comp;
    }

}
