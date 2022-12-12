package iped.app.home.newcase.tabs.evidence.table;/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class TableEvidenceOptionsCellRenderer extends DefaultTableCellRenderer{
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JPanel panel = (JPanel)value;
        panel.setOpaque(false);
        return panel;
    }


}
