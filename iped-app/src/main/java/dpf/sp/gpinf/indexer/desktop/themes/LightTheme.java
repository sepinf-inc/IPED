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
    }
}
