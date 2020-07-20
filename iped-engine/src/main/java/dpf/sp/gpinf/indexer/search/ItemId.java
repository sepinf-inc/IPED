package dpf.sp.gpinf.indexer.search;

import iped3.IItemId;

public class ItemId implements IItemId {

    private int sourceId, id;

    public ItemId(int sourceId, int id) {
        this.sourceId = sourceId;
        this.id = id;
    }

    @Override
    public int compareTo(IItemId o) {
        if (sourceId == o.getSourceId())
            return id - o.getId();
        else
            return sourceId - o.getSourceId();
    }

    @Override
    public boolean equals(Object o) {
        return compareTo((ItemId) o) == 0;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public int getSourceId() {
        return this.sourceId;
    }

    public int getId() {
        return this.id;
    }

}
