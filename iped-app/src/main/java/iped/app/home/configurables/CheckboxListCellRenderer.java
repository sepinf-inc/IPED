package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import com.google.common.base.Predicate;

public class CheckboxListCellRenderer<E> extends JPanel implements ListCellRenderer<E> {
    JCheckBox enabledCheckBox = new JCheckBox();
    Predicate<E> isEnabled;
    int maxStringWidth = 0;

    public CheckboxListCellRenderer(Predicate<E> isEnabled) {
        this.setLayout(new BorderLayout());
        this.isEnabled = isEnabled;
        add(enabledCheckBox, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected,
            boolean cellHasFocus) {
        enabledCheckBox.setSelected(isEnabled.apply(value));
        enabledCheckBox.setText(value.toString());

        this.add(enabledCheckBox, BorderLayout.CENTER);
        
        return this;
    }

    public int getMaxStringWidth() {
        if(maxStringWidth<enabledCheckBox.getWidth()) {
            maxStringWidth = enabledCheckBox.getWidth();
        }
        return maxStringWidth;
    } 

}
