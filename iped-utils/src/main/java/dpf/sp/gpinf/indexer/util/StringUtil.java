package dpf.sp.gpinf.indexer.util;

import java.util.Comparator;

public class StringUtil {

    public static Comparator<String> getIgnoreCaseComparator() {
        return new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                if (o1 == null) {
                    return o2 == null ? 0 : -1;
                } else {
                    return o2 == null ? 1 : o1.trim().compareToIgnoreCase(o2.trim());
                }
            }
        };
    }

}
