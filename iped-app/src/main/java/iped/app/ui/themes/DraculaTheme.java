
package iped.app.ui.themes;

import java.awt.Color;

import iped.app.ui.Messages;

public class DraculaTheme extends Theme {

    private static final Color BACKGROUND = new Color(40, 42, 54);
    private static final Color CURRENT_LINE = new Color(68, 71, 90);
    private static final Color FOREGROUND = new Color(248, 248, 242);
    private static final Color COMMENT = new Color(98, 114, 164);

    private static final Color PURPLE = new Color(189, 147, 249);
    private static final Color PINK = new Color(255, 121, 198);
    private static final Color CYAN = new Color(139, 233, 253);
    private static final Color GREEN = new Color(80, 250, 123);
    private static final Color ORANGE = new Color(255, 184, 108);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color YELLOW = new Color(241, 250, 140);

    public DraculaTheme() {

        // Nimbus
        put("control", CURRENT_LINE);
        put("info", CURRENT_LINE);
        put("nimbusBase", BACKGROUND);
        put("nimbusLightBackground", BACKGROUND);
        put("nimbusDisabledText", COMMENT);
        put("nimbusFocus", PURPLE);
        put("nimbusGreen", GREEN);
        put("nimbusInfoBlue", CYAN);
        put("nimbusOrange", ORANGE);
        put("nimbusRed", RED);
        put("nimbusSelectedText", FOREGROUND);
        put("nimbusSelectionBackground", CURRENT_LINE);
        put("nimbusSelection", CURRENT_LINE);
        put("text", FOREGROUND);

        // Gallery
        put("Gallery.cellSelected", CURRENT_LINE);
        put("Gallery.cellBackground", BACKGROUND);
        put("Gallery.background", BACKGROUND);
        put("Gallery.cellSelectBorder", PURPLE);
        put("Gallery.cellBorder", COMMENT);
        put("Gallery.warning", YELLOW);

        // Tables and viewers
        put("Table[Enabled+Selected].textForeground", FOREGROUND);
        put("Viewer.background", BACKGROUND);
        put("Viewer.foreground", FOREGROUND);
        put("Viewer.htmlLink", CYAN);

        // Graphs
        put("Graph.defaultEdge", COMMENT);
        put("Graph.defaultNode", PURPLE);
        put("Graph.selectedNodeBox", PINK);
        put("Graph.selectionBox", CYAN);

        // Trees
        put("Tree.expandIcon", COMMENT);
        put("Tree.expandIconSel", FOREGROUND);

        // Filters
        put("Filter.Icon0", new Color(68, 71, 90, 200));
        put("Filter.Icon0Hover", PURPLE);
        put("Filter.Icon1", FOREGROUND);
        put("Filter.Icon2", COMMENT);
        put("Filter.Icon2Hover", PINK);
        put("Filter.Arrow", PURPLE);
        put("Filter.Border0", BACKGROUND);
        put("Filter.Border1", CURRENT_LINE);
        put("Filter.Border2", COMMENT);
        put("Filter.Header1", CURRENT_LINE);
        put("Filter.Header1Sorted", PURPLE);
        put("Filter.Header2", BACKGROUND);
        put("Filter.Header2Sorted", CURRENT_LINE);

        // Resizable components
        put("Resizable.Spot", COMMENT);

        // Docking panels
        putDock("stack.tab.text.selected.focused", FOREGROUND);
    }

    @Override
    public String getName() {
        return Messages.getString("Theme.Dracula");
    }
}
