package iped.app.home.newcase.tabs.evidence.table;

/*
 * @created 12/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.newcase.model.Evidence;
import iped.app.home.newcase.tabs.evidence.EvidenceInfoDialog;
import iped.app.ui.Messages;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

public class EvidencesTableModel extends AbstractTableModel {

    private final String[] COLUMN_NAME = {Messages.get("Home.Evidences.Table.FileName"), Messages.get("Home.Evidences.Table.Alias"), Messages.get("Home.Evidences.Table.Path"), Messages.get("Home.Evidences.Table.Options")};
    private final ArrayList<Evidence> evidencesList;
    private EvidenceInfoDialog evidenceInfoDialog;

    public EvidencesTableModel(ArrayList<Evidence> evidencesList, EvidenceInfoDialog evidenceInfoDialog) {
        this.evidencesList = evidencesList;
        this.evidenceInfoDialog = evidenceInfoDialog;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return super.getColumnClass(columnIndex);
    }

    @Override
    public int getRowCount() {
        return (evidencesList != null)? evidencesList.size(): 0;
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAME.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(evidencesList == null)
            return "";
        switch (columnIndex){
            case 0: return evidencesList.get(rowIndex).getFileName();
            case 1: return evidencesList.get(rowIndex).getAlias();
            case 2: return evidencesList.get(rowIndex).getPath();
            case 3: return  evidencesList.get(rowIndex);
            default: return "";
        }
    }


    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex){
            case 1: return true;
            case 3: return true;
            default: return false;
        }
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAME[column];
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if(evidencesList == null || evidencesList.isEmpty())
            return;
        Evidence currentEvidence = evidencesList.get(rowIndex);
        if (columnIndex == 1 ){
            currentEvidence.setAlias(String.valueOf(aValue));
        }
    }



}
