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

    protected int anchor = 0, lead = 0;
    private BitSet selectedCells;

    public GalleryTable(TableModel tableModel) {
        super(tableModel);
        InputMap inputMap = getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "Right");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "Left");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK), "ShiftRight");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK), "ShiftLeft");
        getActionMap().put("Right", new ArrowAction(1, false));
        getActionMap().put("Left", new ArrowAction(-1, false));
        getActionMap().put("ShiftRight", new ArrowAction(1, true));
        getActionMap().put("ShiftLeft", new ArrowAction(-1, true));
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
        int minCell = Math.min(currentCell, anchor);
        int maxCell = Math.max(currentCell, anchor);
        if (toggle) {
            if (!extend) {
                if (isCellSelected(rowIndex, columnIndex)) {
                    selectedCells.clear(currentCell);
                    this.getCellEditor(rowIndex, columnIndex).stopCellEditing();
                } else {
                    selectedCells.set(currentCell);
                }
            } else {
                if (selectedCells.get(anchor)) {
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

        lead = currentCell;
        if (!extend) {
            anchor = currentCell;
        }

        super.changeSelection(rowIndex, columnIndex, toggle, extend);
    }

    public int getLeadSelectionIndex() {
        if (selectedCells.get(lead)) {
            return lead;
        }
        return -1;
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
        if (selectedCells == null) {
            selectedCells = new BitSet();
        } else {
            selectedCells.clear();
        }
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
    private final boolean shiftDown;

    public ArrowAction(int dir, boolean shiftDown) {
        this.dir = dir;
        this.shiftDown = shiftDown;
    }

    public void actionPerformed(ActionEvent e) {
        GalleryTable tab = (GalleryTable) e.getSource();
        int row = tab.lead / tab.getColumnCount();
        int col = tab.lead % tab.getColumnCount();
        if (col + dir >= 0 && col + dir < tab.getColumnCount()) {
            if (row * tab.getColumnCount() + col + dir < App.get().ipedResult.getLength()) {
                tab.getSelectionModel().setValueIsAdjusting(true);
                tab.changeSelection(row, col + dir, false, shiftDown);
                tab.getSelectionModel().setValueIsAdjusting(false);
            }
        } else if (row + dir >= 0 && row + dir < tab.getRowCount()) {
            tab.getSelectionModel().setValueIsAdjusting(true);
            tab.changeSelection(row + dir, dir > 0 ? 0 : tab.getColumnCount() - 1, false, shiftDown);
            tab.getSelectionModel().setValueIsAdjusting(false);
        }
    }
}
