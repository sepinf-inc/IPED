package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;

public class LightTheme extends Theme {
    @Override
    public String getName() {
        return "Claro (padrão)";
    }

    @Override
    public void apply() {
        put("nimbusOrange", new Color(47, 92, 180));
        put("nimbusRed", Color.blue);
        put("nimbusFocus", new Color(140, 190, 220));
        put("Table[Enabled+Selected].textForeground", Color.white);
        put("Gallery.cellSelected", new Color(180, 200, 230));
        put("Gallery.cellBackground", Color.white);
        put("Gallery.background", new Color(240, 240, 242));
        put("Gallery.cellSelectBorder", new Color(20, 50, 80));
        put("Gallery.cellBorder", new Color(200, 200, 202));
    }
}
