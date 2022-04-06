package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;

import dpf.sp.gpinf.indexer.desktop.Messages;

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
