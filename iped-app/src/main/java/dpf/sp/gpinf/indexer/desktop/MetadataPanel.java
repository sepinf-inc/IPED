package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.localization.CategoryLocalization;
import dpf.sp.gpinf.indexer.localization.LocalizedProperties;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.ImageSimilarityTask;
import dpf.sp.gpinf.indexer.process.task.NamedEntityTask;
import dpf.sp.gpinf.indexer.process.task.regex.RegexTask;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.search.QueryBuilder;
import dpf.sp.gpinf.indexer.search.SimilarFacesSearch;
import dpf.sp.gpinf.indexer.search.TimelineResults.TimeItemId;
import dpf.sp.gpinf.indexer.ui.controls.HintTextField;
import dpf.sp.gpinf.indexer.ui.controls.HoverButton;
import dpf.sp.gpinf.indexer.util.IconUtil;
import dpf.sp.gpinf.indexer.util.LocalizedFormat;
import dpf.sp.gpinf.indexer.util.StringUtil;
import iped3.IItemId;
import iped3.exception.ParseException;
import iped3.exception.QueryNodeException;
import iped3.search.IMultiSearchResult;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class MetadataPanel extends JPanel
        implements ActionListener, ListSelectionListener, ClearFilterListener, ChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataPanel.class);

    private static final String RES_PATH = "/dpf/sp/gpinf/indexer/desktop/";
    private static final String SORT_COUNT = Messages.getString("MetadataPanel.Hits"); //$NON-NLS-1$
    private static final String SORT_ALFANUM = Messages.getString("MetadataPanel.AlphaNumeric"); //$NON-NLS-1$
    private static final String MONEY_FIELD = RegexTask.REGEX_PREFIX + "MONEY"; //$NON-NLS-1$
    private static final String LINEAR_SCALE = Messages.getString("MetadataPanel.Linear"); //$NON-NLS-1$
    private static final String LOG_SCALE = Messages.getString("MetadataPanel.Log"); //$NON-NLS-1$
    private static final String NO_RANGES = Messages.getString("MetadataPanel.NoRanges"); //$NON-NLS-1$
    private static final String RANGE_SEPARATOR = Messages.getString("MetadataPanel.RangeSeparator"); //$NON-NLS-1$    
    private static final String EVENT_SEPARATOR = Pattern.quote(IndexItem.EVENT_SEPARATOR);
    private static final int MAX_TERMS_TO_HIGHLIGHT = 1024;

    private volatile static LeafReader reader;

    JList<ValueCount> list = new JList<ValueCount>();
    JScrollPane scrollList = new JScrollPane(list);
    JSlider sort = new JSlider(JSlider.HORIZONTAL, 0, 1, 0);
    private final JLabel labelScale = new JLabel(Messages.getString("MetadataPanel.Scale")); //$NON-NLS-1$    
    JComboBox<String> groups;
    JComboBox<String> props = new JComboBox<String>();
    JSlider scale = new JSlider(JSlider.HORIZONTAL, -1, 1, 0);
    private final HoverButton update = new HoverButton();
    HintTextField listFilter = new HintTextField(
            "[" + Messages.getString("MetadataPanel.FilterValues") + "] " + (char) 0x2193);
    private final HoverButton copyResultToClipboard = new HoverButton();
    private final HoverButton reset = new HoverButton();
    private final HintTextField propsFilter = new HintTextField(
            "[" + Messages.getString("MetadataPanel.FilterProps") + "] " + (char) 0x2191);
    
    private Object lastPropSel;

    volatile NumericDocValues numValues;
    volatile SortedNumericDocValues numValuesSet;
    volatile SortedDocValues docValues;
    volatile SortedSetDocValues docValuesSet;
    volatile SortedSetDocValues eventDocValuesSet;
    volatile HashMap<String, long[]> eventSetToOrdsCache = new HashMap<>();
    volatile boolean isCategory = false;

    volatile IMultiSearchResult ipedResult;
    ValueCount[] array, filteredArray;

    boolean updatingProps = false, updatingList = false, clearing = false;
    volatile boolean updatingResult = false;

    volatile boolean logScale = false;
    volatile boolean noRanges = false;
    volatile double min, max, interval;

    private static final long serialVersionUID = 1L;
    
    /* LOG SCALE: Up to 40 bins:
     *            0 -> negative infinite,
     *      1 to 19 -> count negative numbers, 
     *     20 to 37 -> count positive numbers,
     *           38 -> positive infinite,
     *           39 -> NaN.
     */
    private static final int logScaleBins = 40;
    private static final int logScaleHalf = 20;

    //LINEAR SCALE: Up to 10 bins 
    private static final int linearScaleBins = 10;

    public MetadataPanel() {
        super(new BorderLayout());

        groups = new JComboBox<String>(ColumnsManager.groupNames);
        groups.setSelectedItem(null);
        groups.setMaximumRowCount(15);
        groups.addActionListener(this);

        props.setMaximumRowCount(30);
        props.addActionListener(this);

        scale.setToolTipText(NO_RANGES + " / " + LINEAR_SCALE + " / " + LOG_SCALE);
        scale.setPreferredSize(new Dimension(42, 15));
        scale.setEnabled(false);
        scale.addChangeListener(this);

        sort.setToolTipText(SORT_COUNT + " / " + SORT_ALFANUM);
        sort.setPreferredSize(new Dimension(30, 15));
        sort.addChangeListener(this);

        update.setIcon(IconUtil.getIcon("refresh", RES_PATH, 16));
        update.setToolTipText(Messages.getString("MetadataPanel.Update"));
        update.setPreferredSize(new Dimension(20, 20));
        update.addActionListener(this);

        copyResultToClipboard.setIcon(IconUtil.getIcon("copy", RES_PATH, 16));
        copyResultToClipboard.setToolTipText(Messages.getString("MetadataPanel.CopyClipboard"));
        copyResultToClipboard.setPreferredSize(new Dimension(20, 20));
        copyResultToClipboard.addActionListener(this);
        
        reset.setIcon(IconUtil.getIcon("clear", RES_PATH, 16));
        reset.setToolTipText(Messages.getString("MetadataPanel.Clear"));
        reset.setPreferredSize(new Dimension(20, 20));
        reset.addActionListener(this);

        list.setFixedCellHeight(18);
        list.setFixedCellWidth(2000);
        list.addListSelectionListener(this);

        JPanel left = new JPanel(new GridLayout(3, 1, 1, 1));
        left.add(new JLabel(Messages.getString("MetadataPanel.Group"))); //$NON-NLS-1$
        left.add(new JLabel(Messages.getString("MetadataPanel.Property"))); //$NON-NLS-1$
        left.add(new JLabel(Messages.getString("MetadataPanel.Filter"))); //$NON-NLS-1$

        JPanel l3c = new JPanel(new GridLayout(1, 2, 0, 4));
        l3c.add(propsFilter);
        l3c.add(listFilter);

        JPanel center = new JPanel(new GridLayout(3, 1, 1, 1));
        center.add(groups);
        center.add(props);
        center.add(l3c);
        
        propsFilter.getDocument().addDocumentListener(new DocumentListener() {
            public void removeUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            public void insertUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            public void changedUpdate(DocumentEvent e) {
                if (propsFilter.isFocusOwner()) {
                    updateProps();
                }
            }
        });
        
        JPanel l4 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 1));
        l4.add(new JLabel(Messages.getString("MetadataPanel.Sort")));
        l4.add(sort);
        l4.add(Box.createRigidArea(new Dimension(10, 0)));
        l4.add(labelScale);
        l4.add(scale);
        l4.add(Box.createRigidArea(new Dimension(10, 0)));
        l4.add(copyResultToClipboard);
        l4.add(update);
        l4.add(reset);

        listFilter.addActionListener(this);

        JPanel top = new JPanel(new BorderLayout(1, 1));
        top.add(center, BorderLayout.CENTER);
        top.add(left, BorderLayout.WEST);
        top.add(l4, BorderLayout.SOUTH);

        this.add(top, BorderLayout.NORTH);
        this.add(scrollList, BorderLayout.CENTER);
    }

    abstract class LookupOrd {
        boolean isCategory = false;

        abstract String lookupOrd(int ord) throws IOException;
    }

    class LookupOrdSDV extends LookupOrd {

        SortedDocValues sdv;

        LookupOrdSDV(SortedDocValues sdv) {
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

        LookupOrdSSDV(SortedSetDocValues ssdv) {
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

    private static class ValueCount {
        LookupOrd lo;
        int ord, count;

        ValueCount(LookupOrd lo, int ord, int count) {
            this.lo = lo;
            this.ord = ord;
            this.count = count;
        }

        public String getVal() {
            try {
                String val = lo.lookupOrd(ord);
                if (lo.isCategory) {
                    val = CategoryLocalization.getInstance().getLocalizedCategory(val);
                }
                return val;

            } catch (Exception e) {
                // LookupOrd get invalid if UI is updated when processing (IndexReader closed)
                // e.printStackTrace();
                return Messages.getString("MetadataPanel.UpdateWarn"); //$NON-NLS-1$
            }
        }

        @Override
        public String toString() {
            NumberFormat nf = LocalizedFormat.getNumberInstance(); 
            return getVal() + " (" + nf.format(count) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static class RangeCount extends ValueCount {
        double start, end;

        RangeCount(double start, double end, int ord, int count) {
            super(null, ord, count);
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            NumberFormat nf = LocalizedFormat.getNumberInstance();
            StringBuilder sb = new StringBuilder();
            sb.append(getVal());
            sb.append(" ("); //$NON-NLS-1$
            sb.append(nf.format(count));
            sb.append(')');            
            return sb.toString();
        }
        
        @Override
        public String getVal() {
            StringBuilder sb = new StringBuilder();
            NumberFormat nf = LocalizedFormat.getNumberInstance();
            sb.append(nf.format(start));
            if (start != end && (!Double.isNaN(start) || !Double.isNaN(end))) {
                sb.append(' ');
                sb.append(RANGE_SEPARATOR);
                sb.append(' ');
                sb.append(nf.format(end));
            }
            return sb.toString();
        }
    }
    
    private static class SingleValueCount extends ValueCount implements Comparable<SingleValueCount> {
        double value;

        SingleValueCount(double value) {
            super(null, 0, 0);
            this.value = value;
        }

        @Override
        public String toString() {
            NumberFormat nf = LocalizedFormat.getNumberInstance(); 
            StringBuilder sb = new StringBuilder();
            sb.append(nf.format(value));
            sb.append(" ("); //$NON-NLS-1$
            sb.append(nf.format(count));
            sb.append(')');
            return sb.toString();
        }

        @Override
        public String getVal() {
            NumberFormat nf = LocalizedFormat.getNumberInstance();
            return nf.format(value);
        }
        
        public int compareTo(SingleValueCount o) {
            return Double.compare(value, o.value);
        }
    }    

    private static class MoneyCount extends ValueCount implements Comparable<MoneyCount> {

        private static Pattern pattern = Pattern.compile("[\\$\\s\\.\\,]"); //$NON-NLS-1$
        // String val;
        long money;

        MoneyCount(LookupOrd lo, int ord, int count) {
            super(lo, ord, count);
            String val = getVal();
            char centChar = val.charAt(val.length() - 3);
            if (centChar == '.' || centChar == ',')
                val = val.substring(0, val.length() - 3);
            Matcher matcher = pattern.matcher(val);
            money = Long.valueOf(matcher.replaceAll("")); //$NON-NLS-1$
        }

        @Override
        public int compareTo(MoneyCount o) {
            return Long.compare(o.money, this.money);
        }
    }

    private class CountComparator implements Comparator<ValueCount> {
        @Override
        public final int compare(ValueCount o1, ValueCount o2) {
            return Long.compare(o2.count, o1.count);
        }
    }

    private void updateProps() {
        updatingProps = true;
        props.removeAllItems();
        int selIdx = groups.getSelectedIndex();
        if (selIdx != -1) {
            String filterStr = propsFilter.getText().toLowerCase();
            List<String> fields = Arrays.asList(ColumnsManager.getInstance().fieldGroups[selIdx]);
            fields = fields.stream().map(f -> LocalizedProperties.getLocalizedField(f)).collect(Collectors.toList());
            Collections.sort(fields, StringUtil.getIgnoreCaseComparator());
            for (String f : fields) {
                if (f.equals(ResultTableModel.BOOKMARK_COL) || f.equals(ResultTableModel.SCORE_COL)
                        || f.startsWith(ImageSimilarityTask.SIMILARITY_FEATURES)
                        || f.startsWith(SimilarFacesSearch.FACE_FEATURES))
                    continue;
                if (filterStr.isEmpty() || f.toLowerCase().contains(filterStr))
                    props.addItem(f);
            }
            props.setSelectedItem(lastPropSel);
        }
        updatingProps = false;
    }

    private void filterResults() {
        if (array == null) {
            return;
        }
        App.get().dialogBar.setVisible(true);
        new Thread() {
            public void run() {
                filteredArray = filter(array);
                sortAndUpdateList(filteredArray);
            }
        }.start();
    }

    private ValueCount[] filter(ValueCount[] values) {
        String searchValue = listFilter.getText();
        if (searchValue.isEmpty()) {
            return values;
        }
        searchValue = searchValue.toLowerCase();
        ArrayList<ValueCount> filtered = new ArrayList<>();
        for (ValueCount valueCount : values) {
            String val = valueCount.getVal();
            if (val != null && val.toLowerCase().contains(searchValue)) {
                filtered.add(valueCount);
            }
        }
        return filtered.toArray(new ValueCount[filtered.size()]);
    }

    private void copyResultsToClipboard() {
        App.get().dialogBar.setVisible(true);
        new Thread() {
            public void run() {
                StringBuffer strBuffer = new StringBuffer();
                for (int i = 0; i < list.getModel().getSize(); i++) {
                    ValueCount item = list.getModel().getElementAt(i);
                    String val = item.getVal();
                    if (val != null && !val.isEmpty()) {
                        strBuffer.append(val);
                        strBuffer.append(System.lineSeparator());
                    }
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(strBuffer.toString()),
                        null);
                App.get().dialogBar.setVisible(false);
            }
        }.start();
    }

    private void populateList() {
        App.get().dialogBar.setVisible(true);
        final boolean updateResult = !list.isSelectionEmpty();
        if (updateResult) {
            updatingResult = true;
            App.get().appletListener.updateFileListing();
        }
        ipedResult = App.get().ipedResult;

        logScale = scale.getValue() == 1;
        noRanges = scale.getValue() == -1;
        if (props.getSelectedItem() != null)
            lastPropSel = props.getSelectedItem();

        new Thread() {
            @Override
            public void run() {
                try {
                    countValues(updateResult);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();

    }

    private void loadDocValues(String field) throws IOException {
        // System.out.println("getDocValues");
        numValues = reader.getNumericDocValues(field);
        numValuesSet = reader.getSortedNumericDocValues(field);
        docValues = reader.getSortedDocValues(field);
        String prefix = ExtraProperties.LOCATIONS.equals(field) ? IndexItem.GEO_SSDV_PREFIX : "";
        docValuesSet = reader.getSortedSetDocValues(prefix + field);
        if (BasicProps.TIME_EVENT.equals(field)) {
            eventDocValuesSet = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
        }
        isCategory = BasicProps.CATEGORY.equals(field);
        eventSetToOrdsCache.clear();
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

    private MultiSearchResult getIdsWithOrd(MultiSearchResult result, String field, Set<Integer> ordsToGet)
            throws IOException {

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

        return new MultiSearchResult(items.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[scores.size()])));
    }

    private void countValues(boolean updateResult) throws IOException {

        long time = System.currentTimeMillis();

        reader = App.get().appCase.getLeafReader();

        String field = (String) props.getSelectedItem();
        if (field == null) {
            updatingResult = false;
            filteredArray = array = new ValueCount[0];
            sortAndUpdateList(filteredArray);
            return;
        }
        field = LocalizedProperties.getNonLocalizedField(field.trim());

        loadDocValues(field);

        boolean isNumeric = IndexItem.isNumeric(field);
        boolean isFloat = IndexItem.isFloat(field);
        boolean isDouble = IndexItem.isDouble(field);
        boolean isTimeEvent = BasicProps.TIME_EVENT.equals(field);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                scale.setEnabled(isNumeric);
                labelScale.setEnabled(isNumeric);
            }
        });

        if (updateResult) {
            while (App.get().ipedResult == ipedResult)
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            ipedResult = App.get().ipedResult;
            updatingResult = false;
        }

        // System.out.println("counting");
        int[] valueCount = null;
        min = Double.MAX_VALUE;
        max = Double.MIN_VALUE;
        interval = 0;

        long[] actualMin = null;
        long[] actualMax = null;
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
                interval = min >= max ? 1 :(max - min) / linearScaleBins;
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
            HashMap<Double,SingleValueCount> map = new HashMap<Double,SingleValueCount>();
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
                    v.count++;
                }
            }
            ArrayList<SingleValueCount> l = new ArrayList<SingleValueCount>(map.values());
            Collections.sort(l);
            for (int i = 0; i < l.size(); i++) {
                l.get(i).ord = i;
            }
            list.addAll(l);
        } else if (isNumeric && numValues != null && noRanges) {
            HashMap<Double,SingleValueCount> map = new HashMap<Double,SingleValueCount>();
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
                    v.count++;
                }
            }
            ArrayList<SingleValueCount> l = new ArrayList<SingleValueCount>(map.values());
            Collections.sort(l);
            for (int i = 0; i < l.size(); i++) {
                l.get(i).ord = i;
            }
            list.addAll(l);            
        } else if (docValues != null) {
            valueCount = new int[docValues.getValueCount()];
            for (IItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                boolean adv = docValues.advanceExact(doc);
                if(adv) {
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
            lo.isCategory = BasicProps.CATEGORY.equals(field);
            boolean isMoney = field.equals(MONEY_FIELD);
            for (int ord = 0; ord < valueCount.length; ord++)
                if (valueCount[ord] > 0)
                    if (!isMoney)
                        list.add(new ValueCount(lo, ord, valueCount[ord]));
                    else
                        list.add(new MoneyCount(lo, ord, valueCount[ord]));
        }

        array = list.toArray(new ValueCount[0]);

        filteredArray = filter(array);

        LOGGER.info("Metadata value counting took {}ms", (System.currentTimeMillis() - time));

        sortAndUpdateList(filteredArray);
    }

    private void sortAndUpdateList(ValueCount[] array) {

        if (array == null)
            return;

        long time = System.currentTimeMillis();

        ValueCount[] sortedArray = array;
        if (sort.getValue() == 0) {
            sortedArray = array.clone();
            Arrays.sort(sortedArray, new CountComparator());

        } else if (array.length > 0 && array[0] instanceof MoneyCount) {
            Arrays.sort(array);

        } else if (isCategory) {
            try {
                int[] categoryOrd = RowComparator.getLocalizedCategoryOrd(docValuesSet);
                Arrays.sort(array, new Comparator<ValueCount>() {
                    @Override
                    public int compare(ValueCount o1, ValueCount o2) {
                        return categoryOrd[o1.ord] - categoryOrd[o2.ord];
                    }
                });
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        LOGGER.info("Metadata value sorting took {}ms", (System.currentTimeMillis() - time));

        updateList(sortedArray);

    }

    private void updateList(final ValueCount[] sortedArray) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                updatingList = true;
                List<ValueCount> selection = list.getSelectedValuesList();
                HashSet<ValueCount> selSet = new HashSet<ValueCount>();
                selSet.addAll(selection);
                list.setListData(sortedArray);
                int[] selIdx = new int[selSet.size()];
                int i = 0;
                for (int idx = 0; idx < sortedArray.length; idx++)
                    if (selSet.contains(sortedArray[idx]))
                        selIdx[i++] = idx;
                if (i > 0)
                    list.setSelectedIndices(selIdx);
                updatingList = false;

                // System.out.println("finish");
                updateTabColor();
                App.get().dialogBar.setVisible(false);
            }
        });

    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (updatingProps)
            return;

        if (e.getSource() == update || e.getSource() == props)
            populateList();

        else if (e.getSource() == groups)
            updateProps();

        else if (e.getSource() == listFilter)
            filterResults();

        else if (e.getSource() == copyResultToClipboard)
            copyResultsToClipboard();

        else if (e.getSource() == reset)
            resetPanel();
        
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {

        if ((e != null && e.getValueIsAdjusting()) || updatingList)
            return;

        if (!clearing)
            App.get().appletListener.updateFileListing();

        updateTabColor();

    }

    private void updateTabColor() {
        if (!list.isSelectionEmpty())
            App.get().setMetadataDefaultColor(false);
        else
            App.get().setMetadataDefaultColor(true);
    }

    public boolean isFiltering() {
        String field = (String) props.getSelectedItem();
        if (field == null || list.isSelectionEmpty() || updatingResult) {
            return false;
        } else {
            return true;
        }
    }

    public MultiSearchResult getFilteredItemIds(MultiSearchResult result) throws ParseException, QueryNodeException, IOException {

        if (!isFiltering()) {
            return result;
        }

        String field = (String) props.getSelectedItem();
        field = LocalizedProperties.getNonLocalizedField(field.trim());

        Set<Integer> ords = new HashSet<>();
        for (ValueCount value : list.getSelectedValuesList()) {
            ords.add(value.ord);
        }
        return getIdsWithOrd(result, field, ords);

    }

    public Set<String> getHighlightTerms() throws ParseException, QueryNodeException {

        String field = (String) props.getSelectedItem();
        if (field == null || !(field.startsWith(RegexTask.REGEX_PREFIX) || field.startsWith(NamedEntityTask.NER_PREFIX))
                || list.isSelectionEmpty())
            return Collections.emptySet();

        Set<String> highlightTerms = new HashSet<String>();
        for (ValueCount item : list.getSelectedValuesList()) {
            highlightTerms.add(item.getVal());
            if (highlightTerms.size() >= MAX_TERMS_TO_HIGHLIGHT) {
                break;
            }
        }

        return highlightTerms;

    }

    public Query getHighlightQuery() throws ParseException, QueryNodeException {

        Set<String> terms = getHighlightTerms();
        if (terms.isEmpty()) {
            return null;
        }

        QueryBuilder stdParser = new QueryBuilder(App.get().appCase);
        ComplexPhraseQueryParser complexPhraseParser = new ComplexPhraseQueryParser(IndexItem.CONTENT,
                App.get().appCase.getAnalyzer());
        stdParser.setAllowLeadingWildcard(true);
        complexPhraseParser.setAllowLeadingWildcard(true);

        Builder builder = new BooleanQuery.Builder();
        for (String term : terms) {
            try {
                String queryStr = "*" + removeIllegalChars(term).trim() + "*";
                Query query;
                if (queryStr.contains(" ")) {
                    query = complexPhraseParser.parse("\"" + queryStr + "\"");
                } else {
                    query = stdParser.getQuery(queryStr);
                }
                builder.add(query, Occur.SHOULD);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return builder.build();
    }

    private String removeIllegalChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int c = s.codePointAt(i);
            if (!Character.isLetterOrDigit(c)) {
                sb.append(' ');
            } else {
                sb.appendCodePoint(c);
            }
        }
        return sb.toString();
    }

    @Override
    public void clearFilter() {
        clearing = true;
        list.setListData(new ValueCount[0]);
        clearing = false;
    }

    @Override
    public void stateChanged(ChangeEvent e) {

        if (e.getSource() == sort) {
            if (!sort.getValueIsAdjusting())
                sortAndUpdateList(filteredArray);

        } else if (e.getSource() == scale) {
            if (!scale.getValueIsAdjusting())
                populateList();
        }

    }
    
    private void resetPanel() {
        if (groups.getSelectedItem() != null) {
            boolean empty = list.isSelectionEmpty();
            clearFilter();
            props.removeAllItems();
            groups.setSelectedItem(null);
            filteredArray = array = new ValueCount[0];
            sort.setValue(0);
            scale.setValue(0);
            lastPropSel = null;
            if (!empty) {
                App.get().appletListener.updateFileListing();
                updateTabColor();
            }
        }
        listFilter.setText("");
        propsFilter.setText("");
    }
}
