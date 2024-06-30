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
    Predicate isEnabled;
    int maxStringWidth = 0;
    boolean indexedPredicate = false;

    public CheckboxListCellRenderer(Predicate isEnabled) {
        this.setLayout(new BorderLayout());
        this.isEnabled = isEnabled;
        add(enabledCheckBox, BorderLayout.CENTER);
    }

    public CheckboxListCellRenderer(Predicate<Integer> isEnabled, boolean indexedPredicate) {
        this(isEnabled);
        this.indexedPredicate = indexedPredicate;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus) {
        if (indexedPredicate) {
            enabledCheckBox.setSelected(isEnabled.apply(index));
        } else {
            enabledCheckBox.setSelected(isEnabled.apply(value));
        }
        enabledCheckBox.setText(value.toString());

        this.add(enabledCheckBox, BorderLayout.CENTER);

        return this;
    }

    public int getMaxStringWidth() {
        if (maxStringWidth < enabledCheckBox.getWidth()) {
            maxStringWidth = enabledCheckBox.getWidth();
        }
        return maxStringWidth;
    }

}
