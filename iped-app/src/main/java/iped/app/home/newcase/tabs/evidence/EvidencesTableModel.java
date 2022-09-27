package iped.app.home.newcase.tabs.evidence;

/*
 * @created 12/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.newcase.model.Evidence;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;

public class EvidencesTableModel extends AbstractTableModel {

    private final String[] COLUMN_NAME = {"FILE NAME", "ALIAS", "PATH", "OPTIONS"};
    private final ArrayList<Evidence> evidencesList;
    private EvidenceInfoDialog evidenceInfoDialog;

    public EvidencesTableModel(ArrayList<Evidence> evidencesList, EvidenceInfoDialog evidenceInfoDialog) {
        this.evidencesList = evidencesList;
        this.evidenceInfoDialog = evidenceInfoDialog;
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
            case 3: return createColumnOptionPanel(rowIndex);
            default: return "";
        }
    }

    private JPanel createColumnOptionPanel(int rowIndex ){
        //Evidence evidence = evidencesList.get(rowIndex);
        JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );
        GridBagConstraints gbc = new GridBagConstraints();
        JButton optionButton = new JButton("...");
        optionButton.addActionListener( e -> evidenceInfoDialog.showDialog(evidencesList.get(rowIndex)) );
        panel.add(optionButton, gbc);
        JButton removeButton = new JButton("X");
        removeButton.setForeground(Color.RED);
        Font newButtonFont=new Font(removeButton.getFont().getName(),Font.BOLD,removeButton.getFont().getSize());
        removeButton.setFont(newButtonFont);
        removeButton.addActionListener( e -> {evidencesList.remove(rowIndex); fireTableDataChanged(); });
        panel.add(removeButton, gbc);
        return panel;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex){
            case 1:
            case 3:
                return true;
            default: return false;
        }
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAME[column];
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Evidence currentEvidence = evidencesList.get(rowIndex);
        if (columnIndex == 1 ){
            currentEvidence.setAlias(String.valueOf(aValue));
        }
    }



}
