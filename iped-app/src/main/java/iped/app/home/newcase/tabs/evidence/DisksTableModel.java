package iped.app.home.newcase.tabs.evidence;/*
                                            * @created 11/12/2022
                                            * @project IPED
                                            * @author Thiago S. Figueiredo
                                            */

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import iped.app.home.newcase.model.Evidence;
import iped.app.ui.Messages;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;

public class DisksTableModel extends AbstractTableModel {

    private final String[] COLUMN_NAME = { Messages.get("Home.Evidences.DisksDialog.Name"), Messages.get("Home.Evidences.DisksDialog.Model"), Messages.get("Home.Evidences.DisksDialog.Size"),
            Messages.get("Home.Evidences.DisksDialog.Serial") };
    private final ArrayList<Evidence> evidencesList;
    private final List<HWDiskStore> disksList;

    public DisksTableModel(ArrayList<Evidence> evidencesList) {
        this.evidencesList = evidencesList;
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        disksList = hal.getDiskStores();
    }

    @Override
    public int getRowCount() {
        return (disksList != null) ? disksList.size() : 0;
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAME.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (disksList == null)
            return "";
        switch (columnIndex) {
            case 0:
                return disksList.get(rowIndex).getName();
            case 1:
                return disksList.get(rowIndex).getModel();
            case 2:
                return getDiskSize(disksList.get(rowIndex).getSize());
            case 3:
                return disksList.get(rowIndex).getSerial();
            default:
                return "";
        }
    }

    public HWDiskStore getDiskAt(int rowIndex) {
        return disksList.get(rowIndex);
    }

    private String getDiskSize(long v) {
        if (v < 1024)
            return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAME[column];
    }

}
