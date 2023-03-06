package iped.app.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;

import bibliothek.gui.dock.common.action.CCheckBox;
import iped.properties.BasicProps;

public class TimelineListener implements ClearFilterListener {

    private CCheckBox timelineButton;
    private Icon defaultIcon, filteredIcon;
    private boolean timelineViewEnabled = false;
    private List<? extends SortKey> timelinePrevSortKeys;

    public TimelineListener(CCheckBox timelineButton, Icon filteredIcon) {
        this.timelineButton = timelineButton;
        this.defaultIcon = timelineButton.getIcon();
        this.filteredIcon = filteredIcon;
    }

    public boolean isTimelineViewEnabled() {
        return this.timelineViewEnabled;
    }

    @Override
    public void clearFilter() {
        timelineButton.setSelected(false);
    }

    public void toggleTimelineTableView() {
        timelineButton.setSelected(!timelineViewEnabled);
    }
    
    public void setTimelineTableView(boolean isEnabled) {
        if (timelineViewEnabled != isEnabled) {
            timelineViewEnabled = isEnabled;
            updateGUI(true);
        }
    }

    private void updateGUI(boolean updateResults) {
        if (timelineViewEnabled) {
            timelineButton.setIcon(filteredIcon);
            ColumnsManager.getInstance().moveTimelineColumns(5);
        } else {
            timelineButton.setIcon(defaultIcon);
            ColumnsManager.getInstance().moveTimelineColumns(14);
        }
        updateSortingColumn();
        if (updateResults) {
            App.get().appletListener.updateFileListing();
        }
    }

    private void updateSortingColumn() {
        int timeStampColIdx = -1;
        for (int i = 0; i < App.get().resultsTable.getModel().getColumnCount(); i++) {
            String colName = App.get().resultsTable.getModel().getColumnName(i);
            if (BasicProps.TIMESTAMP.equalsIgnoreCase(colName)) {
                timeStampColIdx = i;
            }
        }
        if (timeStampColIdx == -1) {
            return;
        }
        List<? extends SortKey> sortKeys = App.get().resultsTable.getRowSorter().getSortKeys();
        if (timelineViewEnabled) {
            if (sortKeys == null || sortKeys.isEmpty() || sortKeys.get(0).getColumn() != timeStampColIdx) {
                timelinePrevSortKeys = sortKeys;
                ArrayList<RowSorter.SortKey> sortScore = new ArrayList<RowSorter.SortKey>();
                sortScore.add(new RowSorter.SortKey(timeStampColIdx, SortOrder.ASCENDING));
                ((ResultTableRowSorter) App.get().resultsTable.getRowSorter()).setSortKeysSuper(sortScore);
            }
        } else if (timelinePrevSortKeys != null) {
            if (sortKeys != null && !sortKeys.isEmpty() && sortKeys.get(0).getColumn() == timeStampColIdx) {
                ((ResultTableRowSorter) App.get().resultsTable.getRowSorter()).setSortKeysSuper(timelinePrevSortKeys);
            }
            timelinePrevSortKeys = null;
        }
    }

}
