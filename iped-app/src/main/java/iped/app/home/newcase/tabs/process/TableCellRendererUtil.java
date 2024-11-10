package iped.app.home.newcase.tabs.process;

import java.awt.Color;

import javax.swing.JTable;
import javax.swing.UIManager;

public class TableCellRendererUtil {

    public static Color getBackground(JTable table, int row, boolean isSelected) {
        if (isSelected) {
            return table.getSelectionBackground();
        } else if (row % 2 != 0) {
            Color c = UIManager.getColor("Table.alternateRowColor");
            return c != null ? new Color(c.getRGB()) : null;
        } else {
            Color c = UIManager.getColor("Table.background");
            return c != null ? new Color(c.getRGB()) : null;
        }
    }

}
