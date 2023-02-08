package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import iped.carvers.api.CarverType;

public class CarverConfigCellRenderer extends JPanel implements ListCellRenderer<CarverType> {
    JCheckBox enabledCheckBox = new JCheckBox();
    JLabel label = new JLabel();
    private Object actionList;
    
    public CarverConfigCellRenderer() {
        this.setLayout(new BorderLayout());
        add(enabledCheckBox, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends CarverType> list, CarverType ct, int index,
            boolean isSelected, boolean cellHasFocus) {
        enabledCheckBox.setSelected(ct.isEnabled());
        enabledCheckBox.setText(ct.getName());
        this.remove(label);
        this.add(enabledCheckBox, BorderLayout.CENTER);
        
        return this;
    } 
    
}
