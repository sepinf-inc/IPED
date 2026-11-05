package iped.app.ui;

import java.time.ZoneId;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import iped.utils.DisplayTimeZone;

/** Refresh existing views after changes to display timezone, without reindexing. */
public final class DisplayTimeZoneUI {
    private static Consumer<ZoneId> listener;

    private DisplayTimeZoneUI() {
    }

    public static synchronized void install() {
        if (listener != null) {
            return;
        }
        listener = zone -> SwingUtilities.invokeLater(() -> {
            App app = App.get();
            if (app != null) {
                if (app.resultsModel != null) {
                    app.resultsModel.fireTableDataChanged();
                }
                if (app.resultsTable != null && app.resultsTable.getTableHeader() != null) {
                    app.resultsTable.getTableHeader().repaint();
                }
                if (app.getViewerController() != null) {
                    app.getViewerController().reload();
                }
            }
        });
        DisplayTimeZone.addListener(listener);
    }
}
