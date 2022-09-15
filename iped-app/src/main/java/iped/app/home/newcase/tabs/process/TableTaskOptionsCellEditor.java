package iped.app.home.newcase.tabs.process;/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import javax.swing.*;
import java.awt.*;

public class TableTaskOptionsCellEditor extends DefaultCellEditor{

    public TableTaskOptionsCellEditor(JCheckBox checkBox)
    {
        super(checkBox);
    }
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
    {
        return (JPanel) value;
    }

}
