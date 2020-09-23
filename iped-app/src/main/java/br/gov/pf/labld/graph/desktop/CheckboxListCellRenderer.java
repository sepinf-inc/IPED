package br.gov.pf.labld.graph.desktop;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import br.gov.pf.labld.graph.desktop.CheckboxListCellRenderer.CheckboxListItem;

public class CheckboxListCellRenderer extends JCheckBox implements ListCellRenderer<CheckboxListItem> {

    private static final long serialVersionUID = -3554445949058733191L;

    @Override
    public Component getListCellRendererComponent(JList<? extends CheckboxListItem> list, CheckboxListItem value,
            int index, boolean isSelected, boolean cellHasFocus) {

        setComponentOrientation(list.getComponentOrientation());
        setFont(list.getFont());
        setBackground(list.getBackground());
        setForeground(list.getForeground());
        setSelected(value.isSelected());
        setEnabled(list.isEnabled());

        setText(value == null ? "" : value.getLabel());

        return this;
    }

    public static class CheckboxSelectionMouseAdapter extends MouseAdapter {

        @SuppressWarnings("unchecked")
        @Override
        public void mouseClicked(MouseEvent event) {
            JList<? extends CheckboxListItem> list = (JList<? extends CheckboxListItem>) event.getSource();
            int index = list.locationToIndex(event.getPoint());
            CheckboxListItem item = (CheckboxListItem) list.getModel().getElementAt(index);
            item.setSelected(!item.isSelected());
            list.repaint(list.getCellBounds(index, index));
        }

    }

    public static class CheckboxListItem {

        private Object id;
        private String label;
        private boolean selected;

        public CheckboxListItem(String label) {
            this(label, false);
        }

        public CheckboxListItem(String label, boolean selected) {
            super();
            this.label = label;
            this.selected = selected;
        }

        public CheckboxListItem(Object id, String label) {
            super();
            this.id = id;
            this.label = label;
        }

        public CheckboxListItem(String id, String label, boolean selected) {
            super();
            this.id = id;
            this.label = label;
            this.selected = selected;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public Object getId() {
            return id;
        }

        public void setId(Object id) {
            this.id = id;
        }

    }

}
