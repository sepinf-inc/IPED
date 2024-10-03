package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;

import iped.data.IItemReader;

public abstract class AbstractMessageSubitem implements Comparable<AbstractMessageSubitem> {

    protected final IItemReader item;

    public AbstractMessageSubitem(IItemReader item) {
        this.item = item;
    }

    public IItemReader getItem() {
        return item;
    }

    public String getUfedId() {
        return readUfedMetadata(item, "id");
    }

    @Override
    public int compareTo(AbstractMessageSubitem o) {

        String thisIndex = readUfedMetadata(this.item, "source_index");
        String otherIndex = readUfedMetadata(o.item, "source_index");
        try {
            return Integer.parseInt(thisIndex) - Integer.parseInt(otherIndex);
        } catch (NumberFormatException | NullPointerException e) {
        }

        return 0;
    }

}
