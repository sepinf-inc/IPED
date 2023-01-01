package iped.app.home.opencase;

/*
 * @created 12/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.nio.file.Path;
import java.util.ArrayList;

public class CasesTableModel extends AbstractTableModel {

    private final String[] COLUMN_NAME = {"Lista de casos"};
    private final ArrayList<Path> caseList;

    public CasesTableModel(ArrayList<Path> caseList) {
        this.caseList = caseList == null ? new ArrayList<Path>() : caseList;
    }

    @Override
    public int getRowCount() {
        return (caseList != null)? caseList.size(): 0;
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
        switch (columnIndex){
            case 0: return caseList.get(rowIndex);
            default: return "";
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
