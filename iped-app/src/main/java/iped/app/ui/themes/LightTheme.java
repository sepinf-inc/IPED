package iped.app.ui.themes;

import java.awt.Color;

import iped.app.ui.Messages;

public class LightTheme extends Theme {
    public LightTheme() {
        put("nimbusOrange", new Color(47, 92, 180));
        put("nimbusRed", Color.blue);
        put("nimbusFocus", new Color(140, 190, 220));
        put("Table[Enabled+Selected].textForeground", Color.white);

        put("Filter.Icon0", new Color(152, 156, 162, 200));
        put("Filter.Icon0Hover", new Color(40, 86, 132, 200));
        put("Filter.Icon1", new Color(255, 255, 255));
        put("Filter.Icon2", new Color(172, 176, 182));
        put("Filter.Icon2Hover", new Color(70, 116, 232));
        put("Filter.Arrow", new Color(70, 116, 162, 220));
        put("Filter.Border0", new Color(146, 151, 161));
        put("Filter.Border1", new Color(171, 175, 181));
        put("Filter.Border2", new Color(214, 216, 220));
        put("Filter.Header1", new Color(215, 218, 224));
        put("Filter.Header1Sorted", new Color(180, 198, 214));
        put("Filter.Header2", new Color(249, 249, 251));
        put("Filter.Header2Sorted", new Color(220, 228, 236));

        put("Resizable.Spot", new Color(180, 180, 180));
    }

    @Override
    public String getName() {
        return Messages.getString("Theme.Light");
    }
}
