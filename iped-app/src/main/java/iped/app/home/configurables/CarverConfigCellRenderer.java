package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import iped.carvers.api.CarverType;

public class CarverConfigCellRenderer extends JPanel implements ListCellRenderer<CarverType> {
    JCheckBox enabledCheckBox = new JCheckBox();
    int maxStringWidth = 0;

    public CarverConfigCellRenderer() {
        this.setLayout(new BorderLayout());
        add(enabledCheckBox, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends CarverType> list, CarverType ct, int index, boolean isSelected, boolean cellHasFocus) {
        enabledCheckBox.setSelected(ct.isEnabled());
        enabledCheckBox.setText(ct.getName());

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
