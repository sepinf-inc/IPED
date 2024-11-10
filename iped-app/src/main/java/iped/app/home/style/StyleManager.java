package iped.app.home.style;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/*
 * @created 15/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;

public class StyleManager {

    public static Font getPageTitleFont() {
        return new Font("Arial Bold", Font.PLAIN, 28);
    }

    public static Font getHomeButtonFont() {
        return new Font("Arial Bold", Font.PLAIN, 20);
    }

    public static Font getTableHeaderFont() {
        return new Font("Arial Bold", Font.PLAIN, 12);
    }

    public static Insets getDefaultPanelInsets() {
        return new Insets(20, 20, 20, 20);
    }

    public static void setTableHeaderStyle(JTable table) {
        if (table == null)
            return;

        table.getTableHeader().setBackground(new Color(219, 221, 226));
        table.getTableHeader().setPreferredSize(new Dimension(table.getTableHeader().getWidth(), 30));
        table.getTableHeader().setFont(StyleManager.getTableHeaderFont());

    }

    public static Color getColumnRowSelectedBackground() {
        return new Color(57, 105, 138);
    }

    public static Color getColumnRowUnSelectedBackground(int row) {
        return (row % 2 == 0) ? Color.WHITE : new Color(242, 242, 242);
    }

    public static Border getColumnRowFocusBorder() {
        return UIManager.getBorder("Table.focusCellHighlightBorder");
    }

}
