package iped.app.home.newcase.tabs.evidence.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.newcase.model.Evidence;
import iped.app.home.newcase.tabs.evidence.EvidenceInfoDialog;

/**
 * The Evidence table cell editor this class is used to create a custom cell to
 * the Evidence table here we get the evidence info edit button (... button) and
 * the remove row button (x button)
 */
public class TableEvidenceOptionsCellEditor extends AbstractCellEditor implements TableCellEditor {

    private final ArrayList<Evidence> evidencesList;
    private EvidenceInfoDialog infoDialog;

    private JPanel evidenceOptionsPanel;

    /**
     * TableEvidenceOptionsCellEditor constructor this class gonna be instantiated
     * only if user edit the options column cell
     * 
     * @param evidencesList
     *            - the list of evidences used in the evidences table
     * @param infoDialog
     *            - The evidence editor dialog reference
     */
    public TableEvidenceOptionsCellEditor(ArrayList<Evidence> evidencesList, EvidenceInfoDialog infoDialog) {
        this.evidencesList = evidencesList;
        this.infoDialog = infoDialog;
    }

    /**
     * @param table
     *            - Evidence table instance
     * @param value
     *            - Evidence to be edited object reference
     * @param isSelected
     *            - is column selected?
     * @param row
     *            - current row
     * @param column
     *            - current column
     * @return - A JPanel containing two buttons to edit or remove
     */
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        System.out.println("TableEvidenceOptionsCellEditor constructor");
        createOptionPanel(row, table);
        return evidenceOptionsPanel;
    }

    /**
     * Create a panel with two buttons, one for evidence info dialog and other to
     * remove selected row
     * 
     * @param rowIndex
     *            - The evidence table current row
     * @param table
     *            - the evidence table
     * @return - A JPanel containing two buttons to edit or remove
     */
    private JPanel createOptionPanel(int rowIndex, JTable table) {
        evidenceOptionsPanel = new JPanel();
        evidenceOptionsPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        JButton optionButton = new JButton("...");
        optionButton.addActionListener(e -> infoDialog.showDialog(evidencesList.get(rowIndex)));
        evidenceOptionsPanel.add(optionButton, gbc);
        JButton removeButton = new JButton("X");
        removeButton.setForeground(Color.RED);
        Font newButtonFont = new Font(removeButton.getFont().getName(), Font.BOLD, removeButton.getFont().getSize());
        removeButton.setFont(newButtonFont);
        removeButton.addActionListener(e -> {
            evidencesList.remove(rowIndex);
            ((EvidencesTableModel) table.getModel()).fireTableDataChanged();
            fireEditingStopped();
        });
        evidenceOptionsPanel.add(removeButton, gbc);
        return evidenceOptionsPanel;
    }

    /**
     * @return - A JPanel containing two buttons to edit or remove
     */
    @Override
    public Object getCellEditorValue() {
        return evidenceOptionsPanel;
    }

}
