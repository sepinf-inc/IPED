package iped.app.home.opencase;

import java.nio.file.Path;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;

/*
 * @created 12/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.ui.Messages;

public class CasesTableModel extends AbstractTableModel {

    private final String[] COLUMN_NAME = { Messages.get("Home.OpenCase.CaseTableHeader") };
    private final ArrayList<Path> caseList;

    public CasesTableModel(ArrayList<Path> caseList) {
        this.caseList = caseList == null ? new ArrayList<Path>() : caseList;
    }

    @Override
    public int getRowCount() {
        return (caseList != null) ? caseList.size() : 0;
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAME.length;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return JPanel.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return caseList.get(rowIndex);
            default:
                return "";
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return COLUMN_NAME[columnIndex];
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {

    }

}
