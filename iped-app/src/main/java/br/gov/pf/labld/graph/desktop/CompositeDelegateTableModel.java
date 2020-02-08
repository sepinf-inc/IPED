package br.gov.pf.labld.graph.desktop;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

public class CompositeDelegateTableModel extends AbstractTableModel {

    List<AbstractTableModel> models = new ArrayList<AbstractTableModel>();

    AbstractTableModel currentSource;

    public void add(AbstractTableModel model) {
        models.add(model);
    }

    public void remove(AbstractTableModel model) {
        models.remove(model);
    }

    @Override
    public int getRowCount() {
        if (models.size() > 0) {
            return models.get(0).getRowCount();
        }
        return 0;
    }

    @Override
    public int getColumnCount() {
        if (models.size() > 0) {
            return models.get(0).getRowCount();
        }
        return 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (models.size() > 0) {
            return models.get(0).getValueAt(rowIndex, columnIndex);
        }
        return null;
    }

    @Override
    public void fireTableDataChanged() {
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            AbstractTableModel tableModel = (AbstractTableModel) iterator.next();
            tableModel.fireTableDataChanged();
        }
        super.fireTableDataChanged();
    }

    @Override
    public void fireTableStructureChanged() {
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            AbstractTableModel tableModel = (AbstractTableModel) iterator.next();
            tableModel.fireTableStructureChanged();
        }
        super.fireTableStructureChanged();
    }

    @Override
    public void fireTableRowsInserted(int firstRow, int lastRow) {
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            AbstractTableModel tableModel = (AbstractTableModel) iterator.next();
            tableModel.fireTableRowsInserted(firstRow, lastRow);
        }
        super.fireTableRowsInserted(firstRow, lastRow);
    }

    @Override
    public void fireTableRowsUpdated(int firstRow, int lastRow) {
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            AbstractTableModel tableModel = (AbstractTableModel) iterator.next();
            tableModel.fireTableRowsUpdated(firstRow, lastRow);
        }
        super.fireTableRowsUpdated(firstRow, lastRow);
    }

    @Override
    public void fireTableRowsDeleted(int firstRow, int lastRow) {
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            AbstractTableModel tableModel = (AbstractTableModel) iterator.next();
            tableModel.fireTableRowsDeleted(firstRow, lastRow);
        }
        super.fireTableRowsDeleted(firstRow, lastRow);
    }

    @Override
    public void fireTableCellUpdated(int row, int column) {
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            AbstractTableModel tableModel = (AbstractTableModel) iterator.next();
            tableModel.fireTableCellUpdated(row, column);
        }
        super.fireTableCellUpdated(row, column);
    }

    @Override
    public void fireTableChanged(TableModelEvent e) {
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            AbstractTableModel tableModel = (AbstractTableModel) iterator.next();
            tableModel.fireTableChanged(e);
        }
        super.fireTableChanged(e);
    }

    public AbstractTableModel getCurrentSource() {
        return currentSource;
    }

    public void setCurrentSource(AbstractTableModel currentSource) {
        this.currentSource = currentSource;
    }

}
