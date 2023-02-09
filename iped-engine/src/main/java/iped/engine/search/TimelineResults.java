package iped.engine.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.SortedSetDocValues;

import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.ItemId;
import iped.engine.lucene.DocValuesUtil;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class TimelineResults {

    /**
     * One greater than the maximum number of allowed timestamp values in the same
     * DateTime property.
     */
    private static final short MAX_TIMESTAMPS_PER_PROPERTY = 1024;

    private SortedSetDocValues timeStampValues = null;
    private SortedSetDocValues timeEventGroupValues = null;
    private BinaryDocValues eventsInDocOrdsValues = null;

    private IPEDMultiSource ipedCase;

    private void loadDocValues() throws IOException {
        timeStampValues = ipedCase.getAtomicReader().getSortedSetDocValues(BasicProps.TIMESTAMP);
        timeEventGroupValues = ipedCase.getAtomicReader().getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
        eventsInDocOrdsValues = ipedCase.getAtomicReader().getBinaryDocValues(ExtraProperties.TIME_EVENT_ORDS);
    }

    public TimelineResults(IPEDMultiSource ipedCase) {
        this.ipedCase = ipedCase;
    }

    public MultiSearchResult expandTimestamps(MultiSearchResult items) throws IOException {

        loadDocValues();

        ArrayList<IItemId> ids = new ArrayList<>();
        ArrayList<Float> scores = new ArrayList<>();
        int[] eventOrd = new int[Short.MAX_VALUE];
        int[] blankEventOrd = new int[eventOrd.length];
        Arrays.fill(blankEventOrd, -1);
        int[][] eventsInDocOrds = new int[Short.MAX_VALUE][MAX_TIMESTAMPS_PER_PROPERTY];
        int idx = 0;
        for (IItemId id : items.getIterator()) {
            int luceneId = ipedCase.getLuceneId(id);
            String eventsInDocStr = DocValuesUtil.getVal(eventsInDocOrdsValues, luceneId);
            if (eventsInDocStr.isEmpty()) {
                continue;
            }
            loadOrdsFromString(eventsInDocStr, eventsInDocOrds);
            boolean tsvAdv = timeStampValues.advanceExact(luceneId);
            boolean tegvAdv = timeEventGroupValues.advanceExact(luceneId);

            long ord;
            short pos = 0;
            System.arraycopy(blankEventOrd, 0, eventOrd, 0, eventOrd.length);
            while (tegvAdv && (ord = timeEventGroupValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                for (int k : eventsInDocOrds[pos++]) {
                    if (k == -1) {
                        break;
                    }
                    eventOrd[k] = (int) ord;
                }
            }
            pos = 0;
            while (tsvAdv && (ord = timeStampValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                if (ord > Integer.MAX_VALUE) {
                    throw new RuntimeException("Integer overflow when converting timestamp ord to int");
                }
                if (eventOrd[pos] != -1) {
                    ids.add(new TimeItemId(this, id.getSourceId(), id.getId(), (int) ord, eventOrd[pos]));
                    scores.add(items.getScore(idx));
                }
                pos++;
            }
            idx++;
        }
        return new MultiSearchResult(ids.toArray(new IItemId[ids.size()]), toPrimitive(scores));

    }

    private static final void loadOrdsFromString(String string, int[][] ret) {
        int len = string.length();
        int i = 0, j = 0, k = 0;
        do {
            j = string.indexOf(IndexItem.EVENT_IDX_SEPARATOR, i);
            if (j == -1) {
                j = len;
            }
            loadOrdsFromStringInner(string.substring(i, j), j - i, ret[k++]);
            i = j + 1;
        } while (j < len);
    }

    private static final void loadOrdsFromStringInner(String string, int len, int[] ret) {
        int i = 0, j = 0, k = 0, max_k = ret.length - 1;
        do {
            j = string.indexOf(IndexItem.EVENT_IDX_SEPARATOR2, i);
            if (j == -1) {
                j = len;
            }
            ret[k++] = Integer.parseInt(string.substring(i, j));
            if (k == max_k) {
                break;
            }
            i = j + 1;
        } while (j < len);
        ret[k] = -1;
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

        private TimelineResults timelineResults;
        private int timeStampOrd, timeEventOrd = -1;

        public TimeItemId(TimelineResults timelineResults, int sourceId, int id, int timeStampOrd, int timeEventOrd) {
            super(sourceId, id);
            this.timelineResults = timelineResults;
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
         * @throws IOException
         */
        public String getTimeStampValue() throws IOException {
            return timelineResults.timeStampValues.lookupOrd(timeStampOrd).utf8ToString();
        }

        public int getTimeEventOrd() {
            return this.timeEventOrd;
        }

        /**
         * This is not thread-safe.
         * 
         * @return
         * @throws IOException
         */
        public String getTimeEventValue() throws IOException {
            return timelineResults.timeEventGroupValues.lookupOrd(timeEventOrd).utf8ToString();
        }

        public String getTimeEventValue(SortedSetDocValues ssdv) throws IOException {
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
