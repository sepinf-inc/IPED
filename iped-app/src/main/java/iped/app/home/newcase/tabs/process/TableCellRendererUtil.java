package iped.app.home.newcase.tabs.process;

import java.awt.Color;

import javax.swing.JTable;
import javax.swing.UIManager;

public class TableCellRendererUtil {

    public static Color getBackground(JTable table, int row, boolean isSelected) {
        if (isSelected) {
            return table.getSelectionBackground();
        } else if (row % 2 != 0) {
            return new Color(UIManager.getColor("Table.alternateRowColor").getRGB());
        } else {
            return new Color(UIManager.getColor("Table.background").getRGB());
        }
    }

}
