package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;

import javax.swing.plaf.ColorUIResource;

public class LightTheme extends Theme {
    @Override
    public String getName() {
        return "Claro (padrão)";
    }

    @Override
    public void apply() {
        put("nimbusFocus", new ColorUIResource(new Color(140, 190, 220)));
        put("Gallery.cellSelected", new ColorUIResource(new Color(180, 200, 230)));
        put("Gallery.cellBackground", new ColorUIResource(Color.white));
        put("Gallery.background", new ColorUIResource(new Color(240, 240, 242)));
    }
}
