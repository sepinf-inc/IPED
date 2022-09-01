package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EventObject;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.table.TableModel;

public class GalleryTable extends JTable {

    private static final long serialVersionUID = 1L;

    private int lastCell = 0;
    private BitSet selectedCells = new BitSet();

    public GalleryTable(TableModel tableModel) {
        super(tableModel);
        InputMap inputMap = getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "Right");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "Left");
        getActionMap().put("Right", new ArrowAction(1));
        getActionMap().put("Left", new ArrowAction(-1));
    }

    @Override
    public void updateUI() {
        setBackground(UIManager.getColor("Gallery.background"));
        super.updateUI();
    }
    
    @Override
    public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        int currentCell = rowIndex * this.getColumnCount() + columnIndex;
        if (currentCell > App.get().ipedResult.getLength() - 1) {
            return;
        }
        int minCell = Math.min(currentCell, lastCell);
        int maxCell = Math.max(currentCell, lastCell);
        if (toggle) {
            if (!extend) {
                if (isCellSelected(rowIndex, columnIndex)) {
                    selectedCells.clear(currentCell);
                    this.getCellEditor(rowIndex, columnIndex).stopCellEditing();
                } else {
                    selectedCells.set(currentCell);
                }
            } else {
                if (selectedCells.get(lastCell)) {
                    selectedCells.set(minCell, maxCell + 1);
                } else {
                    selectedCells.clear(minCell, maxCell + 1);
                }
            }
        } else {
            selectedCells.clear();
            if (!extend) {
                selectedCells.set(currentCell);
            } else {
                selectedCells.set(minCell, maxCell + 1);
            }
        }
        if (!extend) {
            lastCell = rowIndex * this.getColumnCount() + columnIndex;
        }

        super.changeSelection(rowIndex, columnIndex, toggle, extend);
    }

    public void setCellSelectionInterval(int minCell, int maxCell) {
        selectedCells.set(minCell, maxCell + 1);
        this.repaint();
    }

    public int[] getSelectedCells() {
        ArrayList<Integer> selection = new ArrayList<Integer>();
        for (int i = 0; i < selectedCells.length(); i++) {
            if (selectedCells.get(i)) {
                selection.add(i);
            }
        }
        int[] result = new int[selection.size()];
        int idx = 0;
        for (int i : selection) {
            result[idx++] = i;
        }
        return result;
    }

    @Override
    public void selectAll() {
        selectedCells.set(0, App.get().ipedResult.getLength());
        super.selectAll();
    }

    @Override
    public void clearSelection() {
        selectedCells = new BitSet();
        super.clearSelection();
    }

    @Override
    public boolean isCellSelected(int row, int column) {
        return selectedCells.get(row * this.getColumnCount() + column);
    }

    @Override
    public boolean editCellAt(int row, int column, EventObject e) {
        if (e instanceof KeyEvent) {
            return false;
        }

        return super.editCellAt(row, column, e);
    }

}

/**
 * Auxiliary class to allow moving cell selection to the next/previous row when
 * using right/left keys.
 */
class ArrowAction extends AbstractAction {
    private static final long serialVersionUID = 1876354716105372284L;
    private final int dir;

    public ArrowAction(int dir) {
        this.dir = dir;
    }

    public void actionPerformed(ActionEvent e) {
        JTable tab = (JTable) e.getSource();
        int row = tab.getSelectedRow();
        int col = tab.getSelectedColumn();
        if (col + dir >= 0 && col + dir < tab.getColumnCount()) {
            if (row * tab.getColumnCount() + col + dir < App.get().ipedResult.getLength()) {
                tab.getSelectionModel().setValueIsAdjusting(true);
                tab.clearSelection();
                tab.changeSelection(row, col + dir, false, false);
                tab.getSelectionModel().setValueIsAdjusting(false);
            }
        } else if (row + dir >= 0 && row + dir < tab.getRowCount()) {
            tab.getSelectionModel().setValueIsAdjusting(true);
            tab.clearSelection();
            tab.changeSelection(row + dir, dir > 0 ? 0 : tab.getColumnCount() - 1, false, false);
            tab.getSelectionModel().setValueIsAdjusting(false);
        }
    }
}
