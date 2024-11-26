package iped.app.ui.controls;

import javax.swing.Icon;

import bibliothek.gui.dock.common.action.CButton;

public class CSelButton extends CButton {
    private boolean isSelected;
    private final Icon iconSel;
    private final Icon iconNotSel;

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
        setIcon(isSelected ? iconSel : iconNotSel);
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void toggle() {
        setSelected(!isSelected);
    }

    public CSelButton(String text, Icon iconSel, Icon iconNotSel) {
        super(text, iconSel);
        this.iconSel = iconSel;
        this.iconNotSel = iconNotSel;
        isSelected = true;
    }
}
