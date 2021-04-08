package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.SortedSetDocValues;

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
        short[] eventsInDocOrds = new short[Short.MAX_VALUE];
        int idx = 0;
        for (IItemId id : items.getIterator()) {
            int luceneId = App.get().appCase.getLuceneId(id);
            timeStampValues.setDocument(luceneId);
            timeEventGroupValues.setDocument(luceneId);
            String eventsInDocStr = eventsInDocOrdsValues.get(luceneId).utf8ToString();
            int i = 0, j = 0, k = 0;
            while ((j = eventsInDocStr.indexOf(',', i)) != -1) {
                eventsInDocOrds[k++] = Short.parseShort(eventsInDocStr.substring(i, j));
                i = j + 1;
            }
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

        public String getTimeStampValue() {
            return timeStampValues.lookupOrd(timeStampOrd).utf8ToString();
        }

        public int getTimeEventOrd() {
            return this.timeEventOrd;
        }

        public String getTimeEventValue() {
            return timeEventGroupValues.lookupOrd(timeEventOrd).utf8ToString();
        }

    }

}
