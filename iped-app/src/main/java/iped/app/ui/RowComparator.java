/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.app.ui;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemId;
import iped.engine.localization.CategoryLocalization;
import iped.engine.search.TimelineResults.TimeItemId;
import iped.engine.task.index.IndexItem;
import iped.engine.util.Util;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class RowComparator implements Comparator<Integer> {

    private static Logger LOGGER = LoggerFactory.getLogger(RowComparator.class);

    private int col;
    private boolean bookmarkCol = false;
    private boolean scoreCol = false;

    private volatile static LeafReader atomicReader;
    private static boolean loadDocValues = true;

    private App app = App.get();

    private String field;
    private HashSet<String> fieldsToLoad = new HashSet<String>();
    private boolean isIntegerNumber = false;
    private boolean isRealNumber = false;
    private boolean isTimeStamp = false;
    private boolean isTimeEvent = false;
    private boolean isCategory = false;

    private int[] localizedCategoryOrds;

    private int[] sdvOrds;
    private long[] ndvOrds;
    private int[][] ssdvOrds;
    private long[][] sndvOrds;

    public static void setLoadDocValues(boolean load) {
        loadDocValues = load;
    }

    public RowComparator(int col) {
        this.col = col;

        if (col >= ResultTableModel.fixedCols.length) {
            col -= ResultTableModel.fixedCols.length;
            String[] fields = ResultTableModel.fields;

            if (fields[col].equals(ResultTableModel.BOOKMARK_COL))
                bookmarkCol = true;

            else if (fields[col].equals(ResultTableModel.SCORE_COL))
                scoreCol = true;

            else {
                long t = System.currentTimeMillis();
                LOGGER.info("Loading sort data for Column {}...", fields[col]); //$NON-NLS-1$
                loadDocValues(fields[col]);
                t = System.currentTimeMillis() - t;
                LOGGER.info("Loading sort data for Column {} took {}ms", fields[col], t); //$NON-NLS-1$
            }
        }
    }

    public RowComparator(String indexedField) {
        loadDocValues(indexedField);
    }

    private void loadDocValues(String indexedField) {
        field = indexedField;
        fieldsToLoad.add(field);

        isIntegerNumber = IndexItem.isIntegerNumber(field);
        isRealNumber = IndexItem.isRealNumber(field);

        isTimeStamp = BasicProps.TIMESTAMP.equals(field);
        isTimeEvent = BasicProps.TIME_EVENT.equals(field);
        isCategory = BasicProps.CATEGORY.equals(field);

        if (!loadDocValues)
            return;

        try {
            atomicReader = App.get().appCase.getLeafReader();

            SortedDocValues sdv = null;
            SortedSetDocValues ssdv = null;
            NumericDocValues ndv = null;
            SortedNumericDocValues sndv = null;

            if (IndexItem.getMetadataTypes().get(indexedField) == null || !IndexItem.getMetadataTypes().get(indexedField).equals(String.class)) {
                ndv = atomicReader.getNumericDocValues(indexedField);
                if (ndv == null) {
                    sndv = atomicReader.getSortedNumericDocValues(indexedField);
                }
            }
            if (ndv == null && sndv == null) {
                String prefix = ExtraProperties.LOCATIONS.equals(field) ? IndexItem.GEO_SSDV_PREFIX : "";
                ssdv = atomicReader.getSortedSetDocValues(prefix + indexedField);
                if (isCategory) {
                    localizedCategoryOrds = getLocalizedCategoryOrd(ssdv);
                }
            }
            if (ndv == null && sndv == null && ssdv == null) {
                sdv = atomicReader.getSortedDocValues(indexedField);
            }

            loadOrds(sdv, ssdv, ndv, sndv);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadOrds(SortedDocValues sdv, SortedSetDocValues ssdv, NumericDocValues ndv, SortedNumericDocValues sndv) throws IOException {
        int maxDoc = App.get().appCase.getLeafReader().maxDoc();
        if (sdv != null) {
            sdvOrds = new int[maxDoc];
            for (int i = 0; i < maxDoc; i++) {
                if (sdv.advanceExact(i)) {
                    sdvOrds[i] = sdv.ordValue();
                } else {
                    sdvOrds[i] = -1;
                }
            }
        }
        if (ndv != null) {
            ndvOrds = new long[maxDoc];
            for (int i = 0; i < maxDoc; i++) {
                if (ndv.advanceExact(i)) {
                    ndvOrds[i] = ndv.longValue();
                } else {
                    ndvOrds[i] = Long.MIN_VALUE;
                }
            }
        }
        if (ssdv != null) {
            int[] empty = new int[0];
            ssdvOrds = new int[maxDoc][];
            for (int i = 0; i < maxDoc; i++) {
                if (ssdv.advanceExact(i)) {
                    ArrayList<Integer> ords = new ArrayList<>();
                    int ord;
                    while ((ord = (int) ssdv.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                        ords.add(ord);
                    }
                    ssdvOrds[i] = new int[ords.size()];
                    for (int j = 0; j < ords.size(); j++) {
                        ssdvOrds[i][j] = ords.get(j);
                    }
                } else {
                    ssdvOrds[i] = empty;
                }
            }
        }
        if (sndv != null) {
            long[] empty = new long[0];
            sndvOrds = new long[maxDoc][];
            for (int i = 0; i < maxDoc; i++) {
                if (sndv.advanceExact(i)) {
                    sndvOrds[i] = new long[sndv.docValueCount()];
                    for (int j = 0; j < sndv.docValueCount(); j++) {
                        sndvOrds[i][j] = sndv.nextValue();
                    }
                } else {
                    sndvOrds[i] = empty;
                }
            }
        }
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

    public static boolean isNewIndexReader() {
        return atomicReader != App.get().appCase.getLeafReader();
    }

    public boolean isStringComparator() {
        return sdvOrds != null || ssdvOrds != null || bookmarkCol;
    }

    @Override
    public final int compare(Integer a, Integer b) {

        if (Thread.currentThread().isInterrupted())
            throw new RuntimeException(Messages.getString("RowComparator.SortCanceled")); //$NON-NLS-1$

        if (scoreCol)
            return Float.compare(app.ipedResult.getScore(a), app.ipedResult.getScore(b));

        IItemId itemA = app.ipedResult.getItem(a);
        IItemId itemB = app.ipedResult.getItem(b);

        a = app.appCase.getLuceneId(itemA);
        b = app.appCase.getLuceneId(itemB);

        if (col == 1) {
            if (app.appCase.getMultiBookmarks().isChecked(itemA) == app.appCase.getMultiBookmarks().isChecked(itemB))
                return 0;
            else if (app.appCase.getMultiBookmarks().isChecked(itemA) == true)
                return -1;
            else
                return 1;

        } else if (bookmarkCol) {
            return Util.concatStrings(app.appCase.getMultiBookmarks().getBookmarkList(itemA)).compareTo(Util.concatStrings(app.appCase.getMultiBookmarks().getBookmarkList(itemB)));

        } else if (isTimeStamp && itemA instanceof TimeItemId) {
            int ordA = ((TimeItemId) itemA).getTimeStampOrd();
            int ordB = ((TimeItemId) itemB).getTimeStampOrd();
            return Integer.compare(ordA, ordB);

        } else if (isTimeEvent && itemA instanceof TimeItemId) {
            int ordA = ((TimeItemId) itemA).getTimeEventOrd();
            int ordB = ((TimeItemId) itemB).getTimeEventOrd();
            return Integer.compare(ordA, ordB);

        } else if (sdvOrds != null) {
            return sdvOrds[a] - sdvOrds[b];

        } else if (ssdvOrds != null) {
            int result, k = 0, ordA = -1, ordB = -1;
            do {
                ordA = k < ssdvOrds[a].length ? ssdvOrds[a][k] : -1;
                ordB = k < ssdvOrds[b].length ? ssdvOrds[b][k] : -1;

                if (isCategory) {
                    if (ordA > -1) {
                        ordA = localizedCategoryOrds[ordA];
                    }
                    if (ordB > -1) {
                        ordB = localizedCategoryOrds[ordB];
                    }
                }
                result = ordA - ordB;
                k++;

            } while (result == 0 && ordA != -1 && ordB != -1);

            return result;

        } else if (sndvOrds != null) {
            int result, k = 0, countA = sndvOrds[a].length, countB = sndvOrds[b].length;
            do {
                long ordA = k < countA ? sndvOrds[a][k] : Long.MIN_VALUE;
                long ordB = k < countB ? sndvOrds[b][k] : Long.MIN_VALUE;
                result = Long.compare(ordA, ordB);
                k++;

            } while (result == 0 && (k < countA || k < countB));

            return result;

        } else if (ndvOrds != null) {
            return Long.compare(ndvOrds[a], ndvOrds[b]);
        }

        // On demand sorting if DocValues does not exist for this field (much slower)
        try {
            Document doc1 = app.appCase.getReader().document(a, fieldsToLoad);
            Document doc2 = app.appCase.getReader().document(b, fieldsToLoad);

            String v1 = doc1.get(field);
            String v2 = doc2.get(field);

            if (v1 == null || v1.isEmpty()) {
                if (v2 == null || v2.isEmpty())
                    return 0;
                else
                    return -1;
            } else if (v2 == null || v2.isEmpty())
                return 1;

            if (isIntegerNumber) {
                long l1 = Long.parseLong(v1);
                long l2 = Long.parseLong(v2);
                return Long.compare(l1, l2);
            }
            if (isRealNumber) {
                double d1 = Double.parseDouble(v1);
                double d2 = Double.parseDouble(v2);
                return Double.compare(d1, d2);
            }

            return v1.compareTo(v2);

        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }

    }

}
