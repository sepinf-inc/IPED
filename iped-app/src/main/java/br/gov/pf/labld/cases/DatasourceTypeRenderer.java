package br.gov.pf.labld.cases;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class DatasourceTypeRenderer extends DefaultListCellRenderer {

    private static final long serialVersionUID = -1435435266846922252L;

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
            boolean cellHasFocus) {
        String type = ((IpedCase.IpedDatasourceType) value).name();
        value = Messages.get("Case." + type);
        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }

}
