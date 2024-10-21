package iped.app.home.newcase.tabs.process;/*
                                           * @created 13/09/2022
                                           * @project IPED
                                           * @author Thiago S. Figueiredo
                                           */

import java.awt.Component;

import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JTable;

public class TableTaskOptionsCellEditor extends DefaultCellEditor {

    public TableTaskOptionsCellEditor(JCheckBox checkBox) {
        super(checkBox);
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        return table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, isSelected, isSelected, row, column);
    }

}
