package iped.app.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;

import bibliothek.gui.dock.common.action.CCheckBox;
import iped.app.ui.columns.ColumnsManagerUI;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.TimelineResults;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.properties.BasicProps;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IFilter;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class TimelineListener implements IResultSetFilterer {

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
            ColumnsManagerUI.getInstance().moveTimelineColumns(5);
        } else {
            timelineButton.setIcon(defaultIcon);
            ColumnsManagerUI.getInstance().moveTimelineColumns(14);
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

    @Override
    public List getDefinedFilters() {
        ArrayList<IFilter> result = new ArrayList<IFilter>();
        if (isTimelineViewEnabled()) {
            result.add(getFilter());
        }
        return result;
    }

    public String toString() {
        return "Timeline view";
    }

    @Override
    public IFilter getFilter() {
        if (isTimelineViewEnabled()) {
            return new IResultSetFilter() {
                public String toString() {
                    return Messages.get("FilterValue.TimelineView");
                }

                @Override
                public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
                    if (isTimelineViewEnabled()) {
                        return new TimelineResults(App.get().appCase).expandTimestamps((MultiSearchResult) src);
                    } else {
                        return src;
                    }
                }
            };
        }
        return null;
    }

    @Override
    public boolean hasFilters() {
        return isTimelineViewEnabled();
    }

    @Override
    public boolean hasFiltersApplied() {
        return false;
    }

}
