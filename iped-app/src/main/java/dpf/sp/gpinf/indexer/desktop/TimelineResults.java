package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.SortedSetDocValues;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import iped3.IIPEDSource;
import iped3.IItemId;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class TimelineResults {

    private static IIPEDSource lastCase = null;
    private static SortedSetDocValues timeStampValues = null;
    private static SortedSetDocValues timeEventGroupValues = null;
    private static BinaryDocValues eventsInDocOrdsValues = null;

    private static void loadDocValues() throws IOException {
        if (timeStampValues == null) {
            synchronized (TimelineResults.class) {
                if (timeStampValues == null) {
                    timeStampValues = App.get().appCase.getAtomicReader().getSortedSetDocValues(BasicProps.TIMESTAMP);
                    timeEventGroupValues = App.get().appCase.getAtomicReader()
                            .getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
                    eventsInDocOrdsValues = App.get().appCase.getAtomicReader()
                            .getBinaryDocValues(ExtraProperties.TIME_EVENT_ORDS);
                }
            }
        }
    }

    public TimelineResults() throws IOException {
        if (lastCase != App.get().getIPEDSource()) {
            timeStampValues = null;
            lastCase = App.get().getIPEDSource();
        }
        loadDocValues();
    }

    public MultiSearchResult expandTimestamps(MultiSearchResult items) throws IOException {

        ArrayList<IItemId> ids = new ArrayList<>();
        ArrayList<Float> scores = new ArrayList<>();
        int[] eventOrd = new int[Short.MAX_VALUE];
        int[] eventsInDocOrds = new int[Short.MAX_VALUE];
        int idx = 0;
        for (IItemId id : items.getIterator()) {
            int luceneId = App.get().appCase.getLuceneId(id);
            String eventsInDocStr = eventsInDocOrdsValues.get(luceneId).utf8ToString();
            if (loadOrdsFromString(eventsInDocStr, eventsInDocOrds) == 0) {
                continue;
            }
            timeStampValues.setDocument(luceneId);
            timeEventGroupValues.setDocument(luceneId);

            long ord;
            short pos = 0;
            while ((ord = timeEventGroupValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                eventOrd[eventsInDocOrds[pos++]] = (int) ord;
            }
            pos = 0;
            while ((ord = timeStampValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                if (ord > Integer.MAX_VALUE) {
                    throw new RuntimeException("Integer overflow when converting timestamp ord to int");
                }
                ids.add(new TimeItemId(id.getSourceId(), id.getId(), (int) ord, eventOrd[pos++]));
                scores.add(items.getScore(idx));
            }
            idx++;
        }
        return new MultiSearchResult(ids.toArray(new IItemId[ids.size()]), toPrimitive(scores));

    }

    private static final int loadOrdsFromString(String string, int[] ret) {
        int i = 0, j = 0, k = 0;
        int len = string.length();
        do {
            j = string.indexOf(IndexItem.EVENT_IDX_SEPARATOR, i);
            if (j == -1) {
                if (i == 0) {
                    return 0;
                }
                j = len;
            }
            ret[k++] = Integer.parseInt(string.substring(i, j));
            i = j + 1;
        } while (j < len);

        return k;
    }

    private static float[] toPrimitive(List<Float> list) {
        if (list == null) {
            return null;
        }
        final float[] result = new float[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.get(i).floatValue();
        }
        return result;
    }

    public static class TimeItemId extends ItemId {

        private int timeStampOrd, timeEventOrd = -1;

        public TimeItemId(int sourceId, int id, int timeStampOrd, int timeEventOrd) {
            super(sourceId, id);
            this.timeStampOrd = timeStampOrd;
            this.timeEventOrd = timeEventOrd;
        }

        public int getTimeStampOrd() {
            return this.timeStampOrd;
        }

        /**
         * This is not thread-safe.
         * 
         * @return
         */
        public String getTimeStampValue() {
            return timeStampValues.lookupOrd(timeStampOrd).utf8ToString();
        }

        public int getTimeEventOrd() {
            return this.timeEventOrd;
        }

        /**
         * This is not thread-safe.
         * 
         * @return
         */
        public String getTimeEventValue() {
            return timeEventGroupValues.lookupOrd(timeEventOrd).utf8ToString();
        }

        public String getTimeEventValue(SortedSetDocValues ssdv) {
            return ssdv.lookupOrd(timeEventOrd).utf8ToString();
        }

        @Override
        public int compareTo(IItemId o) {
            int ret = super.compareTo(o);
            if (ret != 0 || !(o instanceof TimeItemId)) {
                return ret;
            }
            return Integer.compare(timeStampOrd, ((TimeItemId) o).timeStampOrd);
        }

    }

}
