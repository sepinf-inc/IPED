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
import java.lang.reflect.InvocationTargetException;
import java.text.Collator;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.util.BytesRef;

import iped.data.IItemId;
import iped.engine.datasource.SleuthkitReader;
import iped.engine.localization.CategoryLocalization;
import iped.engine.search.TimelineResults.TimeItemId;
import iped.engine.task.index.IndexItem;
import iped.engine.util.Util;
import iped.localization.LocalizedProperties;
import iped.properties.BasicProps;
import iped.search.IMultiSearchResult;
import iped.utils.DateUtil;
import iped.utils.LocalizedFormat;
import iped.viewers.ATextViewer;
import iped.viewers.components.HitsTableModel;

public class ResultTableModel extends AbstractTableModel implements SearchResultTableModel {

    private static final long serialVersionUID = 1L;

    private static final List<String> basicDateFields = Arrays.asList(IndexItem.ACCESSED, IndexItem.MODIFIED, IndexItem.CREATED, IndexItem.CHANGED);

    private static final String lengthField = LocalizedProperties.getLocalizedField(IndexItem.LENGTH);
    public static String BOOKMARK_COL = Messages.getString("ResultTableModel.bookmark"); //$NON-NLS-1$
    public static String SCORE_COL = Messages.getString("ResultTableModel.score"); //$NON-NLS-1$

    private SimpleDateFormat df = new SimpleDateFormat(Messages.getString("ResultTableModel.DateFormat")); //$NON-NLS-1$
    private SimpleDateFormat fatAccessedDf = new SimpleDateFormat(Messages.getString("ResultTableModel.FATDateFormat")); //$NON-NLS-1$
    private DecimalFormat numberFormat = LocalizedFormat.getDecimalInstance("#,###.############"); //$NON-NLS-1$
    private Collator collator = Collator.getInstance();

    public static String[] fields;

    private static int fixedColdWidths[] = { 55, 18 };
    public static String[] fixedCols = { "", "" }; //$NON-NLS-1$ //$NON-NLS-2$

    private static String[] columnNames = {};
    private static String[] originalColumnNames = {};

    public void initCols() {

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {

                    updateCols();
                    App.get().resultsModel.fireTableStructureChanged();

                    for (int i = 0; i < fixedColdWidths.length; i++) {
                        App.get().resultsTable.getColumnModel().getColumn(i).setPreferredWidth(fixedColdWidths[i]);
                    }

                    for (int i = 0; i < ColumnsManager.getInstance().colState.initialWidths.size(); i++) {
                        TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i + fixedColdWidths.length);
                        tc.setPreferredWidth(ColumnsManager.getInstance().colState.initialWidths.get(i));

                        ColumnsManager.getInstance().setColumnRenderer(tc);
                    }

                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void updateCols() {

        ArrayList<String> cols = new ArrayList<String>();
        ArrayList<String> cols2 = new ArrayList<String>();
        for (String col : fixedCols) {
            cols.add(col);
            cols2.add(col);
        }

        fields = ColumnsManager.getInstance().getLoadedCols();
        for (String col : fields) {
            String localizedCol = LocalizedProperties.getLocalizedField(col);
            cols.add(localizedCol.substring(0, 1).toUpperCase() + localizedCol.substring(1));
            cols2.add(col);
        }

        columnNames = cols.toArray(new String[0]);
        originalColumnNames = cols2.toArray(new String[0]);
    }

    public ResultTableModel() {
        super();
        df.setTimeZone(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
        fatAccessedDf.setTimeZone(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
        collator.setStrength(Collator.PRIMARY);
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public int getRowCount() {
        return App.get().ipedResult.getLength();
    }

    @Override
    public String getColumnName(int col) {
        if (col == 0) {
            return LocalizedFormat.format(App.get().ipedResult.getLength());
        } else {
            return columnNames[col];
        }
    }

    public String getColumnFieldName(int col) {
        if (col == 0) {
            return LocalizedFormat.format(App.get().ipedResult.getLength());
        } else {
            return originalColumnNames[col];
        }
    }

    public void updateLengthHeader(long mb) {
        for (int i = 0; i < columnNames.length; i++) {
            if (lengthField.equalsIgnoreCase(columnNames[i])) {
                int col = App.get().resultsTable.convertColumnIndexToView(i);
                if (col == -1)
                    return;
                if (mb == -1) {
                    App.get().resultsTable.getColumnModel().getColumn(col).setHeaderValue(columnNames[i] + " (...)"); //$NON-NLS-1$
                } else {
                    App.get().resultsTable.getColumnModel().getColumn(col).setHeaderValue(columnNames[i] + " (" + LocalizedFormat.format(mb) + "MB)"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }

    }

    @Override
    public boolean isCellEditable(int row, int col) {
        if (col == 1) {
            return true;
        } else {
            return false;
        }
    }

    App app = App.get();

    @Override
    public void setValueAt(Object value, int row, int col) {

        app.appCase.getMultiBookmarks().setChecked((Boolean) value, App.get().ipedResult.getItem(row));
        App.get().galleryModel.setValueAt(value, row, col);
        App.get().resultsModel.fireTableCellUpdated(row, col);

        // app.appCase.getMarcadores().setSelected((Boolean)value,
        // app.appCase.getIds()[app.results.getLuceneIds()[row]], app.appCase);
        if (!BookmarksController.get().isMultiSetting()) {
            app.appCase.getMultiBookmarks().saveState();
            BookmarksController.get().updateUISelection();
        }
    }

    @Override
    public Class<?> getColumnClass(int c) {
        if (c == 1) {
            return Boolean.class;
        } else if (columnNames[c].equalsIgnoreCase(lengthField)) {
            return Integer.class;
        } else {
            return String.class;
        }
    }

    @Override
    public IMultiSearchResult getSearchResult() {
        return App.get().ipedResult;
    }

    private Document doc;
    private int lastDocRead = -1;

    @Override
    public Object getValueAt(int row, int col) {

        if (col == 0)
            return LocalizedFormat.format(App.get().resultsTable.convertRowIndexToView(row) + 1);

        if (col == 1)
            return app.appCase.getMultiBookmarks().isChecked(app.ipedResult.getItem(row));

        String value = ""; //$NON-NLS-1$

        IItemId item = App.get().ipedResult.getItem(row);
        int docId = App.get().appCase.getLuceneId(item);

        if (docId != lastDocRead) {
            try {
                doc = app.appCase.getSearcher().doc(docId);
            } catch (IOException e) {
                e.printStackTrace();
                return Messages.getString("ResultTableModel.Error"); //$NON-NLS-1$
            }
        }
        lastDocRead = docId;

        try {
            int fCol = col - fixedCols.length;
            String field = fields[fCol];

            if (field.equals(SCORE_COL)) {
                return app.ipedResult.getScore(row);
            }

            if (field.equals(BOOKMARK_COL)) {
                return Util.concatStrings(app.appCase.getMultiBookmarks().getBookmarkList(app.ipedResult.getItem(row)));
            }

            if (item instanceof TimeItemId) {
                TimeItemId timeItem = (TimeItemId) item;
                if (field.equals(BasicProps.TIMESTAMP)) {
                    return timeItem.getTimeStampValue();
                }
                if (field.equals(BasicProps.TIME_EVENT)) {
                    return timeItem.getTimeEventValue();
                }
            }

            boolean isNumeric = IndexItem.isNumeric(field);

            String[] values = doc.getValues(field);
            if (values.length > 1) {
                boolean sorted = false;
                if (isNumeric) {
                    try {
                        Arrays.sort(values, new Comparator<String>() {
                            @Override
                            public int compare(String o1, String o2) {
                                return Double.valueOf(o1).compareTo(Double.valueOf(o2));
                            }
                        });
                        sorted = true;
                    } catch (NumberFormatException e) {
                    }
                }
                if (!sorted) {
                    Arrays.sort(values, collator);
                }
            }

            if (values.length == 0) {
                BytesRef[] bytes = doc.getBinaryValues(field);
                if (bytes.length > 0) {
                    values = new String[bytes.length];
                    for (int i = 0; i < bytes.length; i++) {
                        values[i] = bytes[i].toString();
                    }
                }
            }

            if (LocalizedProperties.getLocalizedField(BasicProps.CATEGORY).equalsIgnoreCase(getColumnName(col))) {
                for (int i = 0; i < values.length; i++) {
                    values[i] = CategoryLocalization.getInstance().getLocalizedCategory(values[i]);
                }
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (isNumeric) {
                    values[i] = numberFormat.format(Double.valueOf(values[i]));
                }
                sb.append(values[i]);
                if (i != values.length - 1) {
                    if (i == 9) {
                        sb.append(" ..."); //$NON-NLS-1$
                        break;
                    }
                    sb.append(" | "); //$NON-NLS-1$
                }
            }

            value = sb.toString().trim();

            if (value.isEmpty())
                return value;

            if (basicDateFields.contains(field))
                try {
                    Date date = DateUtil.stringToDate(value);
                    if (field.equals(IndexItem.ACCESSED)) {
                        if (doc.get(SleuthkitReader.IN_FAT_FS) != null)
                            return fatAccessedDf.format(date);
                    }
                    return df.format(date);

                } catch (Exception e) {
                    // e.printStackTrace();
                }

            if (field.equals(IndexItem.NAME)) {
                TextFragment[] fragments = TextHighlighter.getHighlightedFrags(false, value, field, 0);
                if (fragments[0].getScore() > 0) {
                    StringBuilder s = new StringBuilder();
                    s.append(HitsTableModel.htmlStartTag);
                    if (App.get().getFontStartTag() != null)
                        s.append(App.get().getFontStartTag());
                    s.append(fragments[0].toString());
                    if (App.get().getFontStartTag() != null)
                        s.append(ATextViewer.HIGHLIGHT_END_TAG);
                    s.append(HitsTableModel.htmlEndTag);
                    value = s.toString();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Messages.getString("ResultTableModel.Error"); //$NON-NLS-1$
        }

        return value;

    }

}
