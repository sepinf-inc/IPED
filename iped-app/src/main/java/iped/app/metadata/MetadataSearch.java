package iped.app.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.data.IItemId;
import iped.engine.data.ItemId;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.TimelineResults.TimeItemId;
import iped.engine.task.index.IndexItem;
import iped.engine.task.regex.RegexTask;
import iped.localization.LocalizedProperties;
import iped.properties.BasicProps;
import iped.search.IMultiSearchResult;

public class MetadataSearch extends MetadataSearchable {
    public static final String EVENT_SEPARATOR = Pattern.quote(IndexItem.EVENT_SEPARATOR);
    public static final String RANGE_SEPARATOR = Messages.getString("MetadataPanel.RangeSeparator"); //$NON-NLS-1$
    public static final String MONEY_FIELD = RegexTask.REGEX_PREFIX + "MONEY"; //$NON-NLS-1$

    volatile boolean logScale = false;
    volatile boolean noRanges = false;

    /*
     * LOG SCALE: Up to 40 bins: 0 -> negative infinite, 1 to 19 -> count negative
     * numbers, 20 to 37 -> count positive numbers, 38 -> positive infinite, 39 ->
     * NaN.
     */
    private static final int logScaleBins = 40;
    private static final int logScaleHalf = 20;

    volatile double min, max, interval;
    volatile long[] actualMin, actualMax;

    // LINEAR SCALE: Up to 10 bins
    private static final int linearScaleBins = 10;

    volatile IMultiSearchResult ipedResult;

    public void setIpedResult(IMultiSearchResult ipedResult2) {
        this.ipedResult = ipedResult2;
    }

    public MultiSearchResult getIdsWithOrd(MultiSearchResult result, String field, Set<Integer> ordsToGet) throws IOException {

        boolean isNumeric = IndexItem.isNumeric(field);
        boolean isFloat = IndexItem.isFloat(field);
        boolean isDouble = IndexItem.isDouble(field);
        boolean isTimeEvent = BasicProps.TIME_EVENT.equals(field);

        // must reset docValues to call advance again
        loadDocValues(field);

        ArrayList<IItemId> items = new ArrayList<>();
        ArrayList<Float> scores = new ArrayList<>();
        int k = 0;
        if (isNumeric && numValues != null && !noRanges) {
            for (IItemId item : result.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = numValues.advanceExact(doc);
                if (adv) {
                    double val = numValues.longValue();
                    if (isFloat)
                        val = NumericUtils.sortableIntToFloat((int) val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long) val);
                    int ord = logScaleHalf;
                    if (logScale) {
                        if (val == Double.NEGATIVE_INFINITY) {
                            ord = 0;
                        } else if (val == Double.POSITIVE_INFINITY) {
                            ord = logScaleBins - 2;
                        } else if (Double.isNaN(val)) {
                            ord = logScaleBins - 1;
                        } else if (val < 0) {
                            ord -= 1;
                            val *= -1;
                            if (val > 1)
                                ord = ord - (int) Math.log10(val);
                            if (ord < 1)
                                ord = 1;
                        } else if (val > 1) {
                            ord = (int) Math.log10(val) + ord;
                            if (ord > logScaleBins - 3)
                                ord = logScaleBins - 3;
                        }
                    } else {
                        ord = (int) ((val - min) / interval);
                        if (val == max && min != max)
                            ord--;
                        if (!isFloat && !isDouble && actualMin != null) {
                            long lval = (long) val;
                            for (int i = Math.max(0, ord - 1); i <= ord + 1 && i < actualMin.length; i++) {
                                if (lval >= actualMin[i] && lval <= actualMax[i]) {
                                    ord = i;
                                    break;
                                }
                            }
                        }
                    }
                    if (ordsToGet.contains(ord)) {
                        items.add(item);
                        scores.add(result.getScore(k));
                    }
                }
                k++;
            }
        } else if (isNumeric && numValuesSet != null && !noRanges) {
            for (IItemId item : result.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = numValuesSet.advanceExact(doc);
                for (int i = 0; adv && i < numValuesSet.docValueCount(); i++) {
                    double val = numValuesSet.nextValue();
                    if (isFloat)
                        val = NumericUtils.sortableIntToFloat((int) val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long) val);
                    int ord = logScaleHalf;
                    if (logScale) {
                        if (val == Double.NEGATIVE_INFINITY) {
                            ord = 0;
                        } else if (val == Double.POSITIVE_INFINITY) {
                            ord = logScaleBins - 2;
                        } else if (Double.isNaN(val)) {
                            ord = logScaleBins - 1;
                        } else if (val < 0) {
                            ord -= 1;
                            val *= -1;
                            if (val > 1)
                                ord = ord - (int) Math.log10(val);
                            if (ord < 1)
                                ord = 1;
                        } else if (val > 1) {
                            ord = (int) Math.log10(val) + ord;
                            if (ord > logScaleBins - 3)
                                ord = logScaleBins - 3;
                        }
                    } else {
                        if (val == Double.NEGATIVE_INFINITY) {
                            ord = 0;
                        } else if (val == Double.POSITIVE_INFINITY || Double.isNaN(val)) {
                            ord = linearScaleBins - 1;
                        } else {
                            ord = (int) ((val - min) / interval);
                            if (val == max && min != max)
                                ord--;
                            if (!isFloat && !isDouble && actualMin != null) {
                                long lval = (long) val;
                                for (int j = Math.max(0, ord - 1); j <= ord + 1 && j < actualMin.length; j++) {
                                    if (lval >= actualMin[j] && lval <= actualMax[j]) {
                                        ord = j;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (ordsToGet.contains(ord)) {
                        items.add(item);
                        scores.add(result.getScore(k));
                        break;
                    }
                }
                k++;
            }
        } else if (isNumeric && numValuesSet != null && noRanges) {
            Set<Double> set = new HashSet<Double>();
            for (IItemId item : result.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = numValuesSet.advanceExact(doc);
                for (int i = 0; adv && i < numValuesSet.docValueCount(); i++) {
                    double val = numValuesSet.nextValue();
                    if (isFloat)
                        val = NumericUtils.sortableIntToFloat((int) val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long) val);
                    set.add(val);
                }
            }
            ArrayList<Double> l = new ArrayList<Double>(set);
            Collections.sort(l);
            set.clear();
            for (int ord : ordsToGet) {
                if (ord >= 0 && ord < l.size())
                    set.add(l.get(ord));
            }
            if (!set.isEmpty()) {
                // must reset docValues to call advance again
                loadDocValues(field);
                for (IItemId item : result.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = numValuesSet.advanceExact(doc);
                    for (int i = 0; adv && i < numValuesSet.docValueCount(); i++) {
                        double val = numValuesSet.nextValue();
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        if (set.contains(val)) {
                            items.add(item);
                            scores.add(result.getScore(k));
                            break;
                        }
                    }
                    k++;
                }
            }
        } else if (isNumeric && numValues != null && noRanges) {
            Set<Double> set = new HashSet<Double>();
            for (IItemId item : result.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = numValues.advanceExact(doc);
                if (adv) {
                    double val = numValues.longValue();
                    if (isFloat)
                        val = NumericUtils.sortableIntToFloat((int) val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long) val);
                    set.add(val);
                }
            }
            ArrayList<Double> l = new ArrayList<Double>(set);
            Collections.sort(l);
            set.clear();
            for (int ord : ordsToGet) {
                if (ord >= 0 && ord < l.size())
                    set.add(l.get(ord));
            }
            if (!set.isEmpty()) {
                // must reset docValues to call advance again
                loadDocValues(field);
                for (IItemId item : result.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = numValues.advanceExact(doc);
                    if (adv) {
                        double val = numValues.longValue();
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        if (set.contains(val)) {
                            items.add(item);
                            scores.add(result.getScore(k));
                        }
                        k++;
                    }
                }
            }
        } else if (docValues != null) {
            for (IItemId item : result.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = docValues.advanceExact(doc);
                if (adv && ordsToGet.contains(docValues.ordValue())) {
                    items.add(item);
                    scores.add(result.getScore(k));
                }
                k++;
            }
        } else if (docValuesSet != null) {
            for (IItemId item : result.getIterator()) {
                if (isTimeEvent && item instanceof TimeItemId) {
                    TimeItemId timeId = (TimeItemId) item;
                    String eventSet = timeId.getTimeEventValue(eventDocValuesSet);
                    long[] ords = getEventOrdsFromEventSet(docValuesSet, eventSet);
                    for (long ord : ords) {
                        if (ordsToGet.contains((int) ord)) {
                            items.add(item);
                            scores.add(result.getScore(k));
                            break;
                        }
                    }
                } else {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = docValuesSet.advanceExact(doc);
                    long ord;
                    while (adv && (ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                        if (ordsToGet.contains((int) ord)) {
                            items.add(item);
                            scores.add(result.getScore(k));
                            break;
                        }
                    }
                }
                k++;
            }
        }

        return new MultiSearchResult(items.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
    }

    private long[] getEventOrdsFromEventSet(SortedSetDocValues eventDocValues, String eventSet) throws IOException {
        long[] ords = eventSetToOrdsCache.get(eventSet);
        if (ords != null) {
            return ords;
        }
        String[] events = eventSet.split(EVENT_SEPARATOR);
        ords = new long[events.length];
        for (int i = 0; i < ords.length; i++) {
            long ord = eventDocValues.lookupTerm(new BytesRef(events[i]));
            ords[i] = ord;
        }
        eventSetToOrdsCache.put(eventSet, ords);
        return ords;
    }

    public ArrayList<ValueCount> countValues(String field) throws IOException {
        reader = App.get().appCase.getLeafReader();

        field = LocalizedProperties.getNonLocalizedField(field.trim());

        loadDocValues(field);

        boolean isNumeric = IndexItem.isNumeric(field);
        boolean isFloat = IndexItem.isFloat(field);
        boolean isDouble = IndexItem.isDouble(field);
        boolean isTimeEvent = BasicProps.TIME_EVENT.equals(field);

        // System.out.println("counting");
        int[] valueCount = null;
        min = Double.MAX_VALUE;
        max = Double.MIN_VALUE;
        interval = 0;

        actualMin = null;
        actualMax = null;
        ArrayList<ValueCount> list = new ArrayList<ValueCount>();

        // Used for linearScale
        boolean hasNegativeInfinite = false;
        boolean hasPositiveInfinite = false;
        boolean hasNaN = false;

        if (isNumeric && numValues != null && !noRanges) {
            if (logScale) {
                valueCount = new int[logScaleBins];
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = numValues.advanceExact(doc);
                    if (adv) {
                        double val = numValues.longValue();
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        int ord = logScaleHalf;
                        if (val == Double.NEGATIVE_INFINITY) {
                            ord = 0;
                        } else if (val == Double.POSITIVE_INFINITY) {
                            ord = logScaleBins - 2;
                        } else if (Double.isNaN(val)) {
                            ord = logScaleBins - 1;
                        } else if (val < 0) {
                            ord -= 1;
                            val *= -1;
                            if (val > 1)
                                ord = ord - (int) Math.log10(val);
                            if (ord < 1)
                                ord = 1;
                        } else if (val > 1) {
                            ord = (int) Math.log10(val) + ord;
                            if (ord > logScaleBins - 3)
                                ord = logScaleBins - 3;
                        }
                        valueCount[ord]++;
                    }
                }
            } else {
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = numValues.advanceExact(doc);
                    if (adv) {
                        double val = numValues.longValue();
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        if (Double.isFinite(val)) {
                            if (val < min)
                                min = val;
                            if (val > max)
                                max = val;
                        }
                    }
                }
                valueCount = new int[linearScaleBins];
                interval = min >= max ? 1 : (max - min) / linearScaleBins;
                long[] rangeMin = null;
                long[] rangeMax = null;
                if (!isFloat && !isDouble) {
                    rangeMin = new long[valueCount.length];
                    rangeMax = new long[valueCount.length];
                    for (int i = 0; i < valueCount.length; i++) {
                        rangeMin[i] = i == 0 ? (long) Math.floor(i * interval + min) : rangeMax[i - 1] + 1;
                        rangeMax[i] = (long) Math.ceil((i + 1) * interval + min);
                    }
                    actualMin = new long[valueCount.length];
                    actualMax = new long[valueCount.length];
                    Arrays.fill(actualMin, Long.MAX_VALUE);
                    Arrays.fill(actualMax, Long.MIN_VALUE);
                }
                // must reset docValues to call advance again
                loadDocValues(field);
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = numValues.advanceExact(doc);
                    if (adv) {
                        double val = numValues.longValue();
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        int ord = (int) ((val - min) / interval);
                        if (val == Double.NEGATIVE_INFINITY) {
                            hasNegativeInfinite = true;
                            ord = 0;
                        } else if (val == Double.POSITIVE_INFINITY) {
                            hasPositiveInfinite = true;
                            ord = linearScaleBins - 1;
                        } else if (Double.isNaN(val)) {
                            hasNaN = true;
                            ord = linearScaleBins - 1;
                        } else if (ord >= linearScaleBins)
                            ord = linearScaleBins - 1;
                        if (!isFloat && !isDouble) {
                            long lval = (long) val;
                            for (int i = Math.max(0, ord - 1); i <= ord + 1 && i < valueCount.length; i++) {
                                if (lval >= rangeMin[i] && lval <= rangeMax[i]) {
                                    ord = i;
                                    break;
                                }
                            }
                            if (lval < actualMin[ord])
                                actualMin[ord] = lval;
                            if (lval > actualMax[ord])
                                actualMax[ord] = lval;
                        }
                        if (ord < 0)
                            ord = 0;
                        else if (ord >= linearScaleBins)
                            ord = linearScaleBins - 1;
                        valueCount[ord]++;
                    }
                }
            }
        } else if (isNumeric && numValuesSet != null && !noRanges) {
            if (logScale) {
                valueCount = new int[logScaleBins];
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = numValuesSet.advanceExact(doc);
                    int prevOrd = -1;
                    for (int i = 0; adv && i < numValuesSet.docValueCount(); i++) {
                        double val = numValuesSet.nextValue();
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        int ord = logScaleHalf;
                        if (val == Double.NEGATIVE_INFINITY) {
                            ord = 0;
                        } else if (val == Double.POSITIVE_INFINITY) {
                            ord = logScaleBins - 2;
                        } else if (Double.isNaN(val)) {
                            ord = logScaleBins - 1;
                        } else if (val < 0) {
                            ord -= 1;
                            val *= -1;
                            if (val > 1)
                                ord = ord - (int) Math.log10(val);
                            if (ord < 1)
                                ord = 1;
                        } else if (val > 1) {
                            ord = (int) Math.log10(val) + ord;
                            if (ord > logScaleBins - 3)
                                ord = logScaleBins - 3;
                        }
                        if (ord != prevOrd)
                            valueCount[ord]++;
                        prevOrd = ord;
                    }
                }
            } else {
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = numValuesSet.advanceExact(doc);
                    for (int i = 0; adv && i < numValuesSet.docValueCount(); i++) {
                        double val = numValuesSet.nextValue();
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        if (Double.isFinite(val)) {
                            if (val < min)
                                min = val;
                            if (val > max)
                                max = val;
                        }
                    }
                }
                valueCount = new int[linearScaleBins];
                interval = min >= max ? 1 : (max - min) / linearScaleBins;
                long[] rangeMin = null;
                long[] rangeMax = null;
                if (!isFloat && !isDouble) {
                    rangeMin = new long[valueCount.length];
                    rangeMax = new long[valueCount.length];
                    for (int i = 0; i < valueCount.length; i++) {
                        rangeMin[i] = i == 0 ? (long) Math.floor(i * interval + min) : rangeMax[i - 1] + 1;
                        rangeMax[i] = (long) Math.ceil((i + 1) * interval + min);
                    }
                    actualMin = new long[valueCount.length];
                    actualMax = new long[valueCount.length];
                    Arrays.fill(actualMin, Long.MAX_VALUE);
                    Arrays.fill(actualMax, Long.MIN_VALUE);
                }
                // must reset docValues to call advance again
                loadDocValues(field);
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = numValuesSet.advanceExact(doc);
                    int prevOrd = -1;
                    for (int i = 0; adv && i < numValuesSet.docValueCount(); i++) {
                        double val = numValuesSet.nextValue();
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        int ord = (int) ((val - min) / interval);
                        if (val == Double.NEGATIVE_INFINITY) {
                            hasNegativeInfinite = true;
                            ord = 0;
                        } else if (val == Double.POSITIVE_INFINITY) {
                            hasPositiveInfinite = true;
                            ord = linearScaleBins - 1;
                        } else if (Double.isNaN(val)) {
                            hasNaN = true;
                            ord = linearScaleBins - 1;
                        } else if (ord >= linearScaleBins)
                            ord = linearScaleBins - 1;
                        if (!isFloat && !isDouble) {
                            long lval = (long) val;
                            for (int j = Math.max(0, ord - 1); j <= ord + 1 && j < valueCount.length; j++) {
                                if (lval >= rangeMin[j] && lval <= rangeMax[j]) {
                                    ord = j;
                                    break;
                                }
                            }
                            if (lval < actualMin[ord])
                                actualMin[ord] = lval;
                            if (lval > actualMax[ord])
                                actualMax[ord] = lval;
                        }
                        if (ord < 0)
                            ord = 0;
                        else if (ord >= linearScaleBins)
                            ord = linearScaleBins - 1;
                        if (ord != prevOrd)
                            valueCount[ord]++;
                        prevOrd = ord;
                    }
                }
            }
        } else if (isNumeric && numValuesSet != null && noRanges) {
            HashMap<Double, SingleValueCount> map = new HashMap<Double, SingleValueCount>();
            for (IItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = numValuesSet.advanceExact(doc);
                for (int i = 0; adv && i < numValuesSet.docValueCount(); i++) {
                    double val = numValuesSet.nextValue();
                    if (isFloat)
                        val = NumericUtils.sortableIntToFloat((int) val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long) val);
                    SingleValueCount v = map.get(val);
                    if (v == null)
                        map.put(val, v = new SingleValueCount(val));
                    v.incrementCount();
                }
            }
            ArrayList<SingleValueCount> l = new ArrayList<SingleValueCount>(map.values());
            Collections.sort(l);
            for (int i = 0; i < l.size(); i++) {
                l.get(i).setOrd(i);
            }
            list.addAll(l);
        } else if (isNumeric && numValues != null && noRanges) {
            HashMap<Double, SingleValueCount> map = new HashMap<Double, SingleValueCount>();
            for (IItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = numValues.advanceExact(doc);
                if (adv) {
                    double val = numValues.longValue();
                    if (isFloat)
                        val = NumericUtils.sortableIntToFloat((int) val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long) val);
                    SingleValueCount v = map.get(val);
                    if (v == null)
                        map.put(val, v = new SingleValueCount(val));
                    v.incrementCount();
                }
            }
            ArrayList<SingleValueCount> l = new ArrayList<SingleValueCount>(map.values());
            Collections.sort(l);
            for (int i = 0; i < l.size(); i++) {
                l.get(i).setOrd(i);
            }
            list.addAll(l);
        } else if (docValues != null) {
            valueCount = new int[docValues.getValueCount()];
            for (IItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = docValues.advanceExact(doc);
                if (adv) {
                    int ord = docValues.ordValue();
                    valueCount[ord]++;
                }
            }
        } else if (docValuesSet != null) {
            valueCount = new int[(int) docValuesSet.getValueCount()];
            for (IItemId item : ipedResult.getIterator()) {
                if (isTimeEvent && item instanceof TimeItemId) {
                    TimeItemId timeId = (TimeItemId) item;
                    String eventSet = timeId.getTimeEventValue(eventDocValuesSet);
                    long[] ords = getEventOrdsFromEventSet(docValuesSet, eventSet);
                    for (long ord : ords) {
                        valueCount[(int) ord]++;
                    }
                } else {
                    int doc = App.get().appCase.getLuceneId(item);
                    boolean adv = docValuesSet.advanceExact(doc);
                    long ord, prevOrd = -1;
                    while (adv && (ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                        if (prevOrd != ord)
                            valueCount[(int) ord]++;
                        prevOrd = ord;
                    }
                }
            }
        }
        if (isNumeric && !noRanges) {
            for (int ord = 0; ord < valueCount.length; ord++) {
                if (valueCount[ord] > 0) {
                    double start = 0;
                    double end = 0;
                    if (logScale) {
                        if (ord == 0)
                            start = end = Double.NEGATIVE_INFINITY;
                        else if (ord == logScaleBins - 2)
                            start = end = Double.POSITIVE_INFINITY;
                        else if (ord == logScaleBins - 1)
                            start = end = Double.NaN;
                        else if (ord < logScaleHalf) {
                            end = ord == logScaleHalf - 1 ? 0 : -(long) Math.pow(10, logScaleHalf - 1 - ord);
                            start = -((long) Math.pow(10, logScaleHalf - ord) - 1);
                        } else {
                            start = ord == logScaleHalf ? 0 : (long) Math.pow(10, ord - logScaleHalf);
                            end = (long) Math.pow(10, ord - logScaleHalf + 1) - 1;
                        }
                    } else {
                        if (actualMin != null) {
                            start = actualMin[ord];
                            end = actualMax[ord];
                        } else {
                            start = min + ord * interval;
                            end = min + (ord + 1) * interval;
                            if (ord == 0 && hasNegativeInfinite) {
                                start = Double.NEGATIVE_INFINITY;
                                if (min >= max)
                                    end = start;
                            } else if (ord == linearScaleBins - 1 && hasNaN) {
                                end = Double.NaN;
                                if (min >= max)
                                    start = end;
                            } else if (ord == linearScaleBins - 1 && hasPositiveInfinite) {
                                end = Double.POSITIVE_INFINITY;
                                if (min >= max)
                                    start = end;
                            }
                        }
                    }
                    list.add(new RangeCount(start, end, ord, valueCount[ord]));
                }
            }
        } else if (docValues != null && (!isNumeric || !noRanges)) {
            LookupOrd lo = new LookupOrdSDV(docValues);
            for (int ord = 0; ord < valueCount.length; ord++)
                if (valueCount[ord] > 0)
                    list.add(new ValueCount(lo, ord, valueCount[ord]));
        } else if (docValuesSet != null && (!isNumeric || !noRanges)) {
            LookupOrd lo = new LookupOrdSSDV(docValuesSet);
            lo.setCategory(BasicProps.CATEGORY.equals(field));
            boolean isMoney = field.equals(MONEY_FIELD);
            for (int ord = 0; ord < valueCount.length; ord++)
                if (valueCount[ord] > 0)
                    if (!isMoney)
                        list.add(new ValueCount(lo, ord, valueCount[ord]));
                    else
                        list.add(new MoneyCount(lo, ord, valueCount[ord]));
        }

        return list;
    }

    public boolean isCategory() {
        return isCategory;
    }

    public SortedSetDocValues getDocValuesSet() {
        return docValuesSet;
    }

    public void setLogScale(boolean b) {
        logScale = b;
    }

    public void setNoRanges(boolean b) {
        noRanges = b;
    }

}

class LookupOrdSDV extends LookupOrd {

    SortedDocValues sdv;

    public LookupOrdSDV(SortedDocValues sdv) {
        this.sdv = sdv;
    }

    @Override
    public String lookupOrd(int ord) throws IOException {
        BytesRef ref;
        synchronized (sdv) {
            ref = sdv.lookupOrd(ord);
        }
        return ref.utf8ToString();
    }
}

class LookupOrdSSDV extends LookupOrd {

    SortedSetDocValues ssdv;

    public LookupOrdSSDV(SortedSetDocValues ssdv) {
        this.ssdv = ssdv;
    }

    @Override
    public String lookupOrd(int ord) throws IOException {
        BytesRef ref;
        synchronized (ssdv) {
            ref = ssdv.lookupOrd(ord);
        }
        return ref.utf8ToString();
    }
}
