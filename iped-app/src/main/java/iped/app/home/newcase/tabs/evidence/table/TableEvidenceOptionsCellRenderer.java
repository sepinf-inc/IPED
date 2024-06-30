package iped.app.home.newcase.tabs.evidence.table;/*
                                                  * @created 13/09/2022
                                                  * @project IPED
                                                  * @author Thiago S. Figueiredo
                                                  */

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * This class is a renderer for evidences table options columns (the most right
 * column) There is no action for button here!!
 */
public class TableEvidenceOptionsCellRenderer extends JPanel implements TableCellRenderer {

    private JPanel renderPanel;

    /**
     * Table Evidences, options columns renderer contructor
     */
    public TableEvidenceOptionsCellRenderer() {
        createRenderPanel();
    }

    /**
     * Create a panel with two buttons one for evidence info dialog and other to
     * remove selected row Since this is a render cell, there is no button action.
     * See {@link TableEvidenceOptionsCellEditor} to debug buttons actions
     */
    private void createRenderPanel() {
        renderPanel = new JPanel();
        renderPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        JButton optionButton = new JButton("...");
        renderPanel.add(optionButton, gbc);
        JButton removeButton = new JButton("X");
        removeButton.setForeground(Color.RED);
        Font newButtonFont = new Font(removeButton.getFont().getName(), Font.BOLD, removeButton.getFont().getSize());
        removeButton.setFont(newButtonFont);
        renderPanel.add(removeButton, gbc);
    }

    /**
     * Return the panel with the options buttons
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        return renderPanel;
    }

}
