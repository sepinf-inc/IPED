package iped.app.timelinegraph.datasets;

import java.util.List;

import iped.data.IItemId;

public interface TimelineDataset {
    public List<IItemId> getItems(int item, int seriesId);

}
