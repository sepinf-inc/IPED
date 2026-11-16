package iped.app.ui.controls;

import javax.swing.Icon;

import bibliothek.gui.dock.common.action.CButton;
import iped.app.ui.utils.TranslucentIcon;

public class CSelButton extends CButton {
    private boolean isSelected;
    private final Icon iconSel;
    private final Icon iconNotSel;
    private final Icon disabledSel;
    private final Icon disabledNotSel;

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
        setIcon(isSelected ? iconSel : iconNotSel);
        setDisabledHoverIcon(isSelected ? disabledSel : disabledNotSel);
        setDisabledIcon(isSelected ? disabledSel : disabledNotSel);
        setDisabledPressedIcon(isSelected ? disabledSel : disabledNotSel);
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
        disabledSel = new TranslucentIcon(iconSel, 0.1f);
        disabledNotSel = new TranslucentIcon(iconSel, 0.1f);
        setSelected(true);
    }
}
