package iped.app.ui;

import java.util.Arrays;
import iped.engine.task.index.IndexItem;

public class ColumnsSelectReportUI extends ColumnsSelectUI {
    private static ColumnsSelectReportUI instance;

    private static final String[] basicReportProps = { IndexItem.NAME, IndexItem.PATH, IndexItem.TYPE, IndexItem.LENGTH, IndexItem.CREATED,
        IndexItem.MODIFIED, IndexItem.ACCESSED, IndexItem.DELETED, IndexItem.CARVED, IndexItem.HASH, IndexItem.ID_IN_SOURCE };

    public static ColumnsSelectReportUI getInstance() {
        if (instance == null)
            instance = new ColumnsSelectReportUI();
        return instance;
    }

    @Override
    public void dispose() {
        super.dispose();
        dialog.setVisible(false);
        columnsManager = null;
        instance = null;
    }

    protected ColumnsSelectReportUI() {
        super();
        saveFileName = ColumnsManager.SELECTED_REPORT_PROPERTIES_FILENAME;

        loadedSelectedProperties = ColumnsManager.loadSelectedFields(saveFileName);
        if (loadedSelectedProperties != null) {
            columnsManager.enableOnlySelectedProperties(loadedSelectedProperties);
        } else {
            columnsManager.enableOnlySelectedProperties(Arrays.asList(basicReportProps));
        }
        updatePanelList();
    }
}
