package iped.app.ui.columns;

import java.io.File;
import java.util.Set;

import iped.app.ui.columns.ColumnsManager.CheckBoxState;
import iped.engine.task.HTMLReportTask;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;

public class ColumnsSelectReportUI extends ColumnsSelectUI {

    private static ColumnsSelectReportUI instance;

    public static ColumnsSelectReportUI getInstance() {
        if (instance == null)
            instance = new ColumnsSelectReportUI();
        return instance;
    }

    @Override
    public void dispose() {
        super.dispose();
        instance = null;
    }

    protected ColumnsSelectReportUI() {
        super();
    }

    @Override
    protected boolean showCheckVisibleColsButton() {
        return false;
    }

    @Override
    protected File getPropertiesFile() {
        return HTMLReportTask.SELECTED_PROPERTIES_FILE;
    }

    @Override
    protected Set<String> getDefaultProperties() {
        return Set.copyOf(HTMLReportTask.basicReportProps);
    }

    @Override
    protected void updatePanelList() {
        disableRequiredPropertiesCheckBoxes();
        super.updatePanelList();        
    }

    public void disableRequiredPropertiesCheckBoxes() {
        allCheckBoxesState.put(IndexItem.ID_IN_SOURCE, new CheckBoxState(true, false));
        allCheckBoxesState.put(BasicProps.PATH, new CheckBoxState(true, false));
    }
}
