package iped.app.ui.utils;

import javax.swing.Icon;

import bibliothek.gui.dock.common.action.CButton;

public class CCustomButton extends CButton {
    public CCustomButton(String text, Icon icon) {
        super(text, icon);
        Icon disabled = new TranslucentIcon(icon, 0.1f);
        setDisabledHoverIcon(disabled);
        setDisabledIcon(disabled);
        setDisabledPressedIcon(disabled);
    }
}
