package iped.app.ui.utils;

import javax.swing.Icon;

import bibliothek.gui.dock.common.action.CCheckBox;

public abstract class CCustomCheckBox extends CCheckBox {
    public CCustomCheckBox(String text, Icon icon) {
        super(text, icon);
        Icon disabled = new TranslucentIcon(icon, 0.1f);
        setDisabledHoverIcon(disabled);
        setDisabledIcon(disabled);
        setDisabledPressedIcon(disabled);
    }
}
