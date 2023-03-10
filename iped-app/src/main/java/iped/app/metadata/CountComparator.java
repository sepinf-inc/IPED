package iped.app.metadata;

import java.util.Comparator;

public class CountComparator implements Comparator<ValueCount> {
    @Override
    public final int compare(ValueCount o1, ValueCount o2) {
        return Long.compare(o2.getCount(), o1.getCount());
    }
}
