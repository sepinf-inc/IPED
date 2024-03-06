package iped.app.ui.controls;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

public class CheckboxListCellRenderer<E> extends JPanel implements ListCellRenderer<E> {
    JCheckBox enabledCheckBox = new JCheckBox();
    int maxStringWidth = 0;

    public CheckboxListCellRenderer() {
        this.setLayout(new BorderLayout());
        this.setBackground(Color.white);
        add(enabledCheckBox, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus) {
        enabledCheckBox.setSelected(isSelected);
        enabledCheckBox.setText(value.toString());

        this.add(enabledCheckBox, BorderLayout.CENTER);
        if(isSelected) {
            this.setBackground(Color.BLUE);
            enabledCheckBox.setForeground(Color.white);
        }else {
            this.setBackground(Color.white);
            enabledCheckBox.setForeground(Color.black);
        }

        return this;
    }

    public int getMaxStringWidth() {
        if(maxStringWidth<enabledCheckBox.getWidth()) {
            maxStringWidth = enabledCheckBox.getWidth();
        }
        return maxStringWidth;
    }

    public void addCheckBoxActionListener(ActionListener l) {
        enabledCheckBox.addActionListener(l);
    } 
}
