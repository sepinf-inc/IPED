package iped.engine.preview;

public class PreviewConstants {

    /**
     * <p>
     * Name of the folder inside the IPED case directory that stores item views.
     * </p>
     * This folder is created when generating a report with
     * <code>enableHTMLReport = true</code>.
     * <br>
     * It is also used to read view data from cases generated with
     * IPED versions <= 4.2.x.
     */
    public static final String VIEW_FOLDER_NAME = "view";

    private PreviewConstants() {
    }
}
