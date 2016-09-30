package dpf.sp.gpinf.indexer.desktop;

import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EventObject;

import javax.swing.JTable;
import javax.swing.table.TableModel;

public class GalleryTable extends JTable {

  private static final long serialVersionUID = 1L;

  private int lastCell = 0;
  private BitSet selectedCells = new BitSet();

  public GalleryTable(TableModel tableModel) {
    super(tableModel);
  }

  @Override
  public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
    int currentCell = rowIndex * this.getColumnCount() + columnIndex;
    if (currentCell > App.get().results.getLength() - 1) {
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
    selectedCells.set(0, App.get().results.getLength());
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
