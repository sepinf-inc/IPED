package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;

import javax.swing.plaf.ColorUIResource;

public class DarkTheme extends Theme {
    @Override
    public String getName() {
        return "Escuro";
    }

    @Override
    public void apply() {
        put("control", new ColorUIResource(new Color(128, 128, 128)));
        put("info", new ColorUIResource(new Color(128, 128, 128)));
        put("nimbusBase", new ColorUIResource(new Color(18, 30, 49)));
        put("nimbusAlertYellow", new ColorUIResource(new Color(248, 187, 0)));
        put("nimbusDisabledText", new ColorUIResource(new Color(128, 128, 128)));
        put("nimbusFocus", new ColorUIResource(new Color(115, 164, 209)));
        put("nimbusGreen", new ColorUIResource(new Color(176, 179, 50)));
        put("nimbusInfoBlue", new ColorUIResource(new Color(66, 139, 221)));
        put("nimbusLightBackground", new ColorUIResource(new Color(18, 30, 39)));
        put("nimbusOrange", new ColorUIResource(new Color(47, 92, 180)));
        put("nimbusRed", new ColorUIResource(Color.BLUE));
        put("nimbusSelectedText", new ColorUIResource(new Color(255, 255, 255)));
        put("nimbusSelectionBackground", new ColorUIResource(new Color(124, 113, 146)));
        put("nimbusSelection", new ColorUIResource(new Color(124, 113, 146)));
        put("text", new ColorUIResource(new Color(240, 240, 240)));
    }
}
