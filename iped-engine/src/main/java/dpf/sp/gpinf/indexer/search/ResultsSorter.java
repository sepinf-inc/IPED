package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.localization.CategoryLocalization;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.TimelineResults.TimeItemId;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItemId;
import iped3.search.IMultiBookmarks;
import iped3.search.IMultiSearchResult;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Class to sort item result sets. This tries to reuse Lucene pre sorted
 * structures when possible, if not, it does a parallel sort and store results
 * in a SoftReference cache to reuse them later.
 * 
 * TODO: Still needs to keep scores if using pre-sorted data, implement second
 * sorting criteria and optimize for bookmarks and timestamps/events.
 * 
 * @author Luís Filipe Nassif
 *
 */
public class ResultsSorter {

    private static Logger logger = LoggerFactory.getLogger(ResultsSorter.class);

    public static final String CHECKED_SORT_KEY = "CHECK_SORT_KEY";
    public static final String SCORE_SORT_KEY = "SCORE_SORT_KEY";
    public static final String BOOKMARK_SORT_KEY = "BOOKMARK_SORT_KEY";

    private static Map<String, SoftReference<int[]>> cache = new ConcurrentHashMap<>();
    private static volatile LeafReader atomicReader;

    private IPEDMultiSource multiCase;

    private String field;
    private boolean isCategory = false;
    private boolean isTimeStamp = false;
    private boolean isTimeEvent = false;

    private boolean sortByCheckbox = false;
    private boolean sortByScore = false;
    private boolean sortByBookmark = false;

    private int[] ordToDoc;
    private int[] localizedCategoryOrds;

    public ResultsSorter(IPEDMultiSource multiCase, String sortField) {
        this.multiCase = multiCase;
        this.field = sortField;
        if (CHECKED_SORT_KEY.equals(field)) {
            sortByCheckbox = true;
        } else if (SCORE_SORT_KEY.equals(field)) {
            sortByScore = true;
        } else if (BOOKMARK_SORT_KEY.equals(field)) {
            sortByBookmark = true;
        } else {
            loadDocValues();
        }

    }

    private void loadDocValues() {
        isCategory = BasicProps.CATEGORY.equals(field);
        isTimeStamp = BasicProps.TIMESTAMP.equals(field);
        isTimeEvent = BasicProps.TIME_EVENT.equals(field);

        if (atomicReader != multiCase.getAtomicReader()) {
            cache.clear();
        }
        atomicReader = multiCase.getAtomicReader();

        SoftReference<int[]> ref = cache.get(field);
        if (ref != null && (ordToDoc = ref.get()) != null) {
            return;
        }

        try {
            long t = System.currentTimeMillis();

            SortedDocValues sdv = null;
            SortedSetDocValues ssdv = null;
            NumericDocValues ndv = null;
            SortedNumericDocValues sndv = null;

            if (IndexItem.getMetadataTypes().get(field) == null
                    || !IndexItem.getMetadataTypes().get(field).equals(String.class)) {
                ndv = atomicReader.getNumericDocValues(field);
                if (ndv == null) {
                    sndv = atomicReader.getSortedNumericDocValues(field);
                }
            }
            if (ndv == null && sndv == null) {
                String prefix = ExtraProperties.LOCATIONS.equals(field) ? IndexItem.GEO_SSDV_PREFIX : "";
                ssdv = atomicReader.getSortedSetDocValues(prefix + field);
                if (isCategory) {
                    localizedCategoryOrds = getLocalizedCategoryOrd(ssdv);
                }
            }
            if (ndv == null && sndv == null && ssdv == null) {
                sdv = atomicReader.getSortedDocValues(field);
            }

            preSort(sdv, ssdv, ndv, sndv);

            cache.put(field, new SoftReference<int[]>(ordToDoc));

            logger.info("Loading sort data structures for '{}' took {}ms", field,
                    System.currentTimeMillis() - t);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void preSort(SortedDocValues sdv, SortedSetDocValues ssdv, NumericDocValues ndv,
            SortedNumericDocValues sndv) throws IOException {
        int maxDoc = multiCase.getLeafReader().maxDoc();
        if (sdv != null) {
            ArrayList<ArrayList<Integer>> ordToDoc = new ArrayList<>(sdv.getValueCount() + 1);
            while (ordToDoc.size() < sdv.getValueCount() + 1) {
                ordToDoc.add(new ArrayList<>());
            }
            for (int i = 0; i < maxDoc; i++) {
                int ord;
                if (sdv.advanceExact(i)) {
                    ord = sdv.ordValue() + 1;
                } else {
                    ord = 0;
                }
                ordToDoc.get(ord).add(i);
            }
            this.ordToDoc = new int[maxDoc];
            int i = 0;
            for (ArrayList<Integer> l : ordToDoc) {
                for (int doc : l) {
                    this.ordToDoc[i++] = doc;
                }
                l.clear();
            }
        }
        if (ndv != null) {
            long[] ndvOrds = new long[maxDoc];
            Integer[] docs = new Integer[maxDoc];
            for (int i = 0; i < maxDoc; i++) {
                if (ndv.advanceExact(i)) {
                    ndvOrds[i] = ndv.longValue();
                } else {
                    ndvOrds[i] = Long.MIN_VALUE;
                }
                docs[i] = i;
            }
            //this.ndvOrds = ndvOrds;
            long t = System.currentTimeMillis();
            Arrays.parallelSort(docs, new Comparator<Integer>() {
                @Override
                public final int compare(Integer arg0, Integer arg1) {
                    return Long.compare(ndvOrds[arg0], ndvOrds[arg1]);
                }
            });
            logger.info("Sorting ORDs for '{}' took {}ms", field, System.currentTimeMillis() - t);
            // ndvOrds = null;
            this.ordToDoc = new int[maxDoc];
            int i = 0;
            for (int doc : docs) {
                this.ordToDoc[i++] = doc;
            }
        }
        if (ssdv != null) {
            OrdToDocs ordToDocsRoot = new OrdToDocs();
            for (int i = 0; i < maxDoc; i++) {
                if (ssdv.advanceExact(i)) {
                    int ord;
                    OrdToDocs ordToDocs = ordToDocsRoot;
                    while ((ord = (int) ssdv.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                        if (isCategory) {
                            ord = localizedCategoryOrds[ord];
                        }
                        OrdToDocs v = ordToDocs.ordToDocs.get(ord);
                        if (v == null) {
                            v = new OrdToDocs();
                            ordToDocs.ordToDocs.put(ord, v);
                        }
                        ordToDocs = v;
                    }
                    ordToDocs.docs.add(i);
                } else {
                    ordToDocsRoot.docs.add(i);
                }
            }
            this.ordToDoc = new int[maxDoc];
            invertSSDVS(ordToDocsRoot, 0);
        }
        if (sndv != null) {
            long[] empty = new long[0];
            long[][] sndvOrds = new long[maxDoc][];
            Integer[] docs = new Integer[maxDoc];
            for (int i = 0; i < maxDoc; i++) {
                if (sndv.advanceExact(i)) {
                    sndvOrds[i] = new long[sndv.docValueCount()];
                    for (int j = 0; j < sndv.docValueCount(); j++) {
                        sndvOrds[i][j] = sndv.nextValue();
                    }
                } else {
                    sndvOrds[i] = empty;
                }
                docs[i] = i;
            }
            long t = System.currentTimeMillis();
            Arrays.parallelSort(docs, new Comparator<Integer>() {
                @Override
                public final int compare(Integer a, Integer b) {
                    int result, k = 0, countA = sndvOrds[a].length, countB = sndvOrds[b].length;
                    do {
                        long ordA = k < countA ? sndvOrds[a][k] : Long.MIN_VALUE;
                        long ordB = k < countB ? sndvOrds[b][k] : Long.MIN_VALUE;
                        result = Long.compare(ordA, ordB);
                        k++;

                    } while (result == 0 && (k < countA || k < countB));
                    return result;
                }
            });
            logger.info("Sorting ORDs for '{}' took {}ms", field, System.currentTimeMillis() - t);
            // sndvOrds = null;
            this.ordToDoc = new int[maxDoc];
            int i = 0;
            for (int doc : docs) {
                this.ordToDoc[i++] = doc;
            }
        }
    }

    private int invertSSDVS(OrdToDocs ordToDocs, int start) {
        for (Integer doc : ordToDocs.docs) {
            this.ordToDoc[start++] = doc;
        }
        for (OrdToDocs s : ordToDocs.ordToDocs.values()) {
            start = invertSSDVS(s, start);
        }
        return start;
    }

    private static class OrdToDocs {
        ArrayList<Integer> docs = new ArrayList<>();
        TreeMap<Integer, OrdToDocs> ordToDocs = new TreeMap<>();
    }

    public static int[] getLocalizedCategoryOrd(SortedSetDocValues ssdv) throws IOException {
        int[] localizedOrds = new int[(int) ssdv.getValueCount()];
        ArrayList<String> localizedVals = new ArrayList<>();
        for (int i = 0; i < localizedOrds.length; i++) {
            String val = ssdv.lookupOrd(i).utf8ToString();
            String localizedVal = CategoryLocalization.getInstance().getLocalizedCategory(val);
            localizedVals.add(localizedVal);
        }
        ArrayList<String> sortedLocalizedVals = new ArrayList<>(localizedVals);
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(sortedLocalizedVals, collator);
        for (int i = 0; i < localizedOrds.length; i++) {
            localizedOrds[i] = sortedLocalizedVals.indexOf(localizedVals.get(i));
        }
        return localizedOrds;
    }

    public void sort(IMultiSearchResult itemList) {
        sort(itemList, false);
    }

    public void sort(IMultiSearchResult itemList, boolean reverse) {
        if (itemList.getLength() == 0) {
            return;
        } else if (sortByCheckbox) {
            sortByCheckBoxes(itemList, reverse);
        } else if (sortByScore) {
            sortByComparator(itemList, getScoreComparator(itemList), reverse);
        } else if (sortByBookmark) {
            // TODO can be optimized
            sortByComparator(itemList, getBookmarksComparator(itemList), reverse);
        } else if (isTimeStamp && itemList.getItem(0) instanceof TimeItemId) {
            // TODO can be optimized
            sortByComparator(itemList, getTimeStampComparator(itemList), reverse);
        } else if (isTimeEvent && itemList.getItem(0) instanceof TimeItemId) {
            // TODO can be optimized
            sortByComparator(itemList, getTimeEventComparator(itemList), reverse);
        } else {
            sortByPreSortedArray(itemList, reverse);
        }
    }

    private void sortByPreSortedArray(IMultiSearchResult itemList, boolean reverse) {

        long t0 = System.currentTimeMillis();

        LuceneSearchResult docList = MultiSearchResult.get(itemList, multiCase);
        itemList.clear();

        long t1 = System.currentTimeMillis();

        logger.info("item list to lucene list conv took {}ms", t1 - t0);

        BitSet bits = new BitSet();
        for (int doc : docList.docs) {
            bits.set(doc);
        }
        int k = 0;
        for (int i = reverse ? ordToDoc.length - 1 : 0; i >= 0 && i < ordToDoc.length; i += reverse ? -1 : 1) {
            int doc = ordToDoc[i];
            if (bits.get(doc)) {
                // TODO how to keep scores?
                // docList.scores[k] =
                docList.docs[k++] = doc;
                if (k == docList.length) {
                    break;
                }
            }
        }

        long t2 = System.currentTimeMillis();

        logger.info("Sorting took {}ms", t2 - t1);

        IMultiSearchResult result = MultiSearchResult.get(multiCase, docList);
        docList = null;

        long t3 = System.currentTimeMillis();

        logger.info("lucene list to item list conv took {}ms", t3 - t2);

        itemList.setIds(result.getIds());
        itemList.setScores(result.getScores());
    }

    private void sortByCheckBoxes(IMultiSearchResult itemList, boolean reverse) {
        IMultiBookmarks bookmarks = multiCase.getMultiBookmarks();
        IItemId[] origItems = itemList.getIds();
        float[] origScores = itemList.getScores();
        itemList.clear();
        ArrayList<IItemId> checked = new ArrayList<>(), unchecked = new ArrayList<>();
        ArrayList<Float> checkedScore = new ArrayList<>(), uncheckedScore = new ArrayList<>();
        for (int i = 0; i < origItems.length; i++) {
            IItemId item = origItems[i];
            if (bookmarks.isChecked(item)) {
                checked.add(item);
                checkedScore.add(origScores[i]);
            } else {
                unchecked.add(item);
                uncheckedScore.add(origScores[i]);
            }
        }
        origItems = null;
        origScores = null;
        int idx = reverse ? 0 : checked.size();
        checked.addAll(idx, unchecked);
        unchecked = null;
        checkedScore.addAll(idx, uncheckedScore);
        uncheckedScore = null;
        IItemId[] items = checked.toArray(new IItemId[0]);
        checked = null;
        Float[] scores = checkedScore.toArray(new Float[0]);
        checkedScore = null;
        itemList.setIds(items);
        itemList.setScores(ArrayUtils.toPrimitive(scores));
    }

    private void sortByComparator(IMultiSearchResult itemList, final Comparator<Integer> comp, boolean reverse) {
        final Integer[] ord = new Integer[itemList.getLength()];
        for (int i = 0; i < ord.length; i++) {
            ord[i] = i;
        }

        Arrays.parallelSort(ord, comp);

        if (reverse) {
            ArrayUtils.reverse(ord);
        }

        IItemId[] origItems = itemList.getIds();
        float[] origScores = itemList.getScores();
        itemList.clear();
        IItemId[] items = new IItemId[ord.length];
        float[] scores = new float[ord.length];
        for (int i = 0; i < ord.length; i++) {
            items[i] = origItems[ord[i]];
            scores[i] = origScores[ord[i]];
        }
        origItems = null;
        origScores = null;

        itemList.setIds(items);
        itemList.setScores(scores);
    }

    // TODO: optimize to get rid of this
    private Comparator<Integer> getBookmarksComparator(IMultiSearchResult itemList) {
        final IItemId[] origItems = itemList.getIds();
        final IMultiBookmarks bookmarks = multiCase.getMultiBookmarks();

        return new Comparator<Integer>() {
            @Override
            public final int compare(Integer a, Integer b) {
                return Util.concatStrings(bookmarks.getBookmarkList(origItems[a]))
                        .compareTo(Util.concatStrings(bookmarks.getBookmarkList(origItems[b])));
            }
        };
    }

    private Comparator<Integer> getScoreComparator(IMultiSearchResult itemList) {
        final float[] origScores = itemList.getScores();

        return new Comparator<Integer>() {
            @Override
            public final int compare(Integer a, Integer b) {
                return Float.compare(origScores[a], origScores[b]);
            }
        };
    }

    // TODO: optimize to get rid of this
    private Comparator<Integer> getTimeStampComparator(IMultiSearchResult itemList) {
        final IItemId[] origItems = itemList.getIds();

        return new Comparator<Integer>() {
            @Override
            public final int compare(Integer a, Integer b) {
                int ordA = ((TimeItemId) origItems[a]).getTimeStampOrd();
                int ordB = ((TimeItemId) origItems[b]).getTimeStampOrd();
                return Integer.compare(ordA, ordB);
            }
        };
    }

    // TODO: optimize to get rid of this
    private Comparator<Integer> getTimeEventComparator(IMultiSearchResult itemList) {
        final IItemId[] origItems = itemList.getIds();

        return new Comparator<Integer>() {
            @Override
            public final int compare(Integer a, Integer b) {
                int ordA = ((TimeItemId) origItems[a]).getTimeEventOrd();
                int ordB = ((TimeItemId) origItems[b]).getTimeEventOrd();
                return Integer.compare(ordA, ordB);
            }
        };
    }

}
