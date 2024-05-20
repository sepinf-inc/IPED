package iped.app.ui.themes;

import java.awt.Color;

import iped.app.ui.Messages;

public class DarkTheme extends Theme {
    public DarkTheme() {
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
        put("Gallery.warning", new Color(200, 100, 100));
        put("Table[Enabled+Selected].textForeground", Color.white);
        put("Viewer.background", new Color(29, 38, 47));
        put("Viewer.foreground", Color.white);
        put("Viewer.htmlLink", new Color(200, 200, 255));
        put("Graph.defaultEdge", new Color(128, 128, 128));
        put("Graph.defaultNode", Color.white);
        put("Graph.selectedNodeBox", Color.white);
        put("Graph.selectionBox", Color.white);
        put("Tree.expandIcon", new Color(210, 210, 211));
        put("Tree.expandIconSel", Color.white);

        put("Filter.Icon0", new Color(72, 76, 82, 200));
        put("Filter.Icon0Hover", new Color(40, 86, 132, 200));
        put("Filter.Icon1", new Color(255, 255, 255));
        put("Filter.Icon2", new Color(172, 176, 182));
        put("Filter.Icon2Hover", new Color(70, 116, 232));
        put("Filter.Arrow", new Color(40, 52, 75, 220));
        put("Filter.Border0", new Color(32, 36, 37));
        put("Filter.Border1", new Color(48, 50, 52));
        put("Filter.Border2", new Color(109, 111, 113));
        put("Filter.Header1", new Color(126, 128, 130));
        put("Filter.Header1Sorted", new Color(131, 133, 140));
        put("Filter.Header2", new Color(145, 149, 154));
        put("Filter.Header2Sorted", new Color(150, 154, 164));
        put("Resizable.Spot", new Color(90, 92, 94));

        putDock("stack.tab.text.selected.focused", Color.white);
    }

    @Override
    public String getName() {
        return Messages.getString("Theme.Dark");
    }
}
