package iped.app.home.config;/*
                             * @created 26/09/2022
                             * @project IPED
                             * @author Thiago S. Figueiredo
                             */

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

public class LanguageComboBoxCellRender extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Languages) {
            Languages person = (Languages) value;
            setText(person.getDescription() + " (" + person.getLanguageTag() + ")");
        }
        return this;
    }

}
