package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;

public class DarkTheme extends Theme {
    @Override
    public String getName() {
        return "Escuro";
    }

    @Override
    public void apply() {
        put("control", new Color(128, 128, 128));
        put("info", new Color(128, 128, 128));
        put("nimbusBase", new Color(18, 30, 49));
        put("nimbusAlertYellow", new Color(248, 187, 0));
        put("nimbusDisabledText", new Color(128, 128, 128));
        put("nimbusFocus", new Color(150, 180, 210));
        put("nimbusGreen", new Color(176, 179, 50));
        put("nimbusInfoBlue", new Color(66, 139, 221));
        put("nimbusLightBackground", new Color(29, 38, 47));
        put("nimbusOrange", new Color(47, 92, 180));
        put("nimbusRed", Color.blue);
        put("nimbusSelectedText", new Color(255, 255, 255));
        put("nimbusSelectionBackground", new Color(100, 130, 160));
        put("nimbusSelection", new Color(100, 130, 160));
        put("text", new Color(249, 249, 250));
        put("Gallery.cellSelected", new Color(100, 130, 160));
        put("Gallery.cellBackground", new Color(29, 38, 47));
        put("Gallery.background", new Color(39, 43, 47));
        put("Gallery.cellSelectBorder", new Color(130, 160, 220));
        put("Gallery.cellBorder", new Color(53, 60, 67));
        put("Table[Enabled+Selected].textForeground", Color.white);
        
        putDock("stack.tab.text.selected.focused", Color.white);
    }
}
