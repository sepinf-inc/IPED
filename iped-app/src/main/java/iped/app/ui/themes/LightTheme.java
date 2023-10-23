package iped.app.ui.themes;

import java.awt.Color;

import iped.app.ui.Messages;

public class LightTheme extends Theme {
    public LightTheme() {
        put("nimbusOrange", new Color(47, 92, 180));
        put("nimbusRed", Color.blue);
        put("nimbusFocus", new Color(140, 190, 220));
        put("Table[Enabled+Selected].textForeground", Color.white);
    }

    @Override
    public String getName() {
        return Messages.getString("Theme.Light");
    }
}
