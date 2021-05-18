package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.CategoryLocalization;
import dpf.sp.gpinf.indexer.desktop.TimelineResults.TimeItemId;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.NamedEntityTask;
import dpf.sp.gpinf.indexer.process.task.regex.RegexTask;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.search.QueryBuilder;
import dpf.sp.gpinf.indexer.util.IconUtil;
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
    private static final String EVENT_SEPARATOR = Pattern.quote(IndexItem.EVENT_SEPARATOR);
    private static final int MAX_TERMS_TO_HIGHLIGHT = 1024;

    private volatile static LeafReader reader;

    JList<ValueCount> list = new JList<ValueCount>();
    JScrollPane scrollList = new JScrollPane(list);
    JSlider sort = new JSlider(JSlider.HORIZONTAL, 0, 1, 0);
    JComboBox<String> groups;
    JComboBox<String> props = new JComboBox<String>();
    JSlider scale = new JSlider(JSlider.HORIZONTAL, 0, 1, 0);
    JButton update = new JButton();
    JTextField listFilter = new JTextField();
    JButton copyResultToClipboard = new JButton();
    private final JButton reset = new JButton();
    private final JTextField propsFilter = new JTextField();
    
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
    volatile double min, max, interval;

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String emptyPropsFilter = "[" + Messages.getString("MetadataPanel.FilterProps") + "] " + (char)0x2191;
    private static final String emptyListFilter = "[" + Messages.getString("MetadataPanel.FilterValues") + "] " + (char)0x2193;
    
    public MetadataPanel() {
        super(new BorderLayout());

        groups = new JComboBox<String>(ColumnsManager.groupNames);
        groups.setSelectedItem(null);
        groups.setMaximumRowCount(15);
        groups.addActionListener(this);

        props.setMaximumRowCount(30);
        props.addActionListener(this);

        scale.setToolTipText(LINEAR_SCALE + " / " + LOG_SCALE);
        scale.setPreferredSize(new Dimension(30, 15));
        scale.setEnabled(false);
        scale.addChangeListener(this);
        scale.addMouseListener(new SliderMouseListener(scale));

        sort.setToolTipText(SORT_COUNT + " / " + SORT_ALFANUM);
        sort.setPreferredSize(new Dimension(30, 15));
        sort.addChangeListener(this);
        sort.addMouseListener(new SliderMouseListener(sort));

        update.setIcon(IconUtil.getIcon("refresh", RES_PATH, 16));
        update.setToolTipText(Messages.getString("MetadataPanel.Update"));
        update.setPreferredSize(new Dimension(20, 20));
        update.setContentAreaFilled(false);
        update.addMouseListener(new ButtonMouseListener(update));

        copyResultToClipboard.setIcon(IconUtil.getIcon("copy", RES_PATH, 16));
        copyResultToClipboard.setToolTipText(Messages.getString("MetadataPanel.CopyClipboard"));
        copyResultToClipboard.setPreferredSize(new Dimension(20, 20));
        copyResultToClipboard.setContentAreaFilled(false);
        copyResultToClipboard.addMouseListener(new ButtonMouseListener(copyResultToClipboard));
        
        reset.setIcon(IconUtil.getIcon("clear", RES_PATH, 16));
        reset.setToolTipText(Messages.getString("MetadataPanel.Clear"));
        reset.setPreferredSize(new Dimension(20, 20));
        reset.setContentAreaFilled(false);
        reset.addMouseListener(new ButtonMouseListener(reset));
        reset.addActionListener(this);

        list.setFixedCellHeight(18);
        list.setFixedCellWidth(2000);
        list.addListSelectionListener(this);

        JPanel l1 = new JPanel(new BorderLayout());
        JLabel label = new JLabel(Messages.getString("MetadataPanel.Group")); //$NON-NLS-1$
        label.setPreferredSize(new Dimension(80, 20));
        l1.add(label, BorderLayout.WEST);
        l1.add(groups, BorderLayout.CENTER);

        JPanel l2 = new JPanel(new BorderLayout());
        label = new JLabel(Messages.getString("MetadataPanel.Property")); //$NON-NLS-1$
        label.setPreferredSize(new Dimension(80, 20));
        l2.add(label, BorderLayout.WEST);
        l2.add(props, BorderLayout.CENTER);

        JPanel l3 = new JPanel(new BorderLayout());
        label = new JLabel(Messages.getString("MetadataPanel.Filter"));
        label.setPreferredSize(new Dimension(80, 20));
        l3.add(label, BorderLayout.WEST);
        JPanel l3c = new JPanel(new GridLayout(1,2,0,4));
        l3c.add(propsFilter);
        l3c.add(listFilter);
        l3.add(l3c, BorderLayout.CENTER);
        
        setupFilter(propsFilter, emptyPropsFilter);
        setupFilter(listFilter, emptyListFilter);

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
        l4.add(new JLabel(Messages.getString("MetadataPanel.Scale")));
        l4.add(scale);
        l4.add(Box.createRigidArea(new Dimension(10, 0)));
        l4.add(copyResultToClipboard);
        l4.add(update);
        l4.add(reset);

        listFilter.addActionListener(this);
        copyResultToClipboard.addActionListener(this);
        update.addActionListener(this);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(l1);
        top.add(l2);
        top.add(l3);
        top.add(l4);

        this.add(top, BorderLayout.NORTH);
        this.add(scrollList, BorderLayout.CENTER);
    }

    abstract class LookupOrd {
        boolean isCategory = false;

        abstract String lookupOrd(int ord);
    }

    class LookupOrdSDV extends LookupOrd {

        SortedDocValues sdv;

        LookupOrdSDV(SortedDocValues sdv) {
            this.sdv = sdv;
        }

        @Override
        public String lookupOrd(int ord) {
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
        public String lookupOrd(int ord) {
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
            return getVal() + " (" + count + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private class RangeCount extends ValueCount {
        double start, end;

        RangeCount(double start, double end, int ord, int count) {
            super(null, ord, count);
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            String startStr = NumberFormat.getNumberInstance().format(start);
            String endStr = NumberFormat.getNumberInstance().format(end);
            return startStr + " TO " + endStr + " (" + count + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
            String filterStr = propsFilter.getText();
            filterStr = filterStr.isEmpty() || filterStr.equals(emptyPropsFilter) ? null : filterStr.toLowerCase();
            String[] fields = ColumnsManager.getInstance().fieldGroups[selIdx];
            for (String f : fields){
                f = BasicProps.getLocalizedField(f);
                if (filterStr == null || f.toLowerCase().contains(filterStr))
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
        if (searchValue.isEmpty() || searchValue.equals(emptyListFilter)) {
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
        if (numValues == null)
            numValues = reader.getNumericDocValues(IndexItem.POSSIBLE_NUM_DOCVALUES_PREFIX + field); // $NON-NLS-1$
        numValuesSet = reader.getSortedNumericDocValues(field);
        if (numValuesSet == null)
            numValuesSet = reader.getSortedNumericDocValues(IndexItem.POSSIBLE_NUM_DOCVALUES_PREFIX + field); // $NON-NLS-1$
        docValues = reader.getSortedDocValues(field);
        if (docValues == null)
            docValues = reader.getSortedDocValues(IndexItem.POSSIBLE_STR_DOCVALUES_PREFIX + field); // $NON-NLS-1$
        docValuesSet = reader.getSortedSetDocValues(field);
        if (docValuesSet == null)
            docValuesSet = reader.getSortedSetDocValues(IndexItem.POSSIBLE_STR_DOCVALUES_PREFIX + field); // $NON-NLS-1$
        if (BasicProps.TIME_EVENT.equals(field)) {
            eventDocValuesSet = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
        }
        isCategory = BasicProps.CATEGORY.equals(field);
        eventSetToOrdsCache.clear();
    }

    public static final boolean isFloat(String field) {
        return Float.class.equals(IndexItem.getMetadataTypes().get(field));
    }

    public static final boolean isDouble(String field) {
        return Double.class.equals(IndexItem.getMetadataTypes().get(field));
    }

    public static final boolean mayBeNumeric(String field) {
        return IndexItem.getMetadataTypes().get(field) == null
                || !IndexItem.getMetadataTypes().get(field).equals(String.class);
    }

    private long[] getEventOrdsFromEventSet(SortedSetDocValues eventDocValues, String eventSet) {
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

    private MultiSearchResult getIdsWithOrd(MultiSearchResult result, String field, Set<Integer> ordsToGet) {

        boolean mayBeNumeric = mayBeNumeric(field);
        boolean isFloat = isFloat(field);
        boolean isDouble = isDouble(field);
        boolean isTimeEvent = BasicProps.TIME_EVENT.equals(field);

        ArrayList<IItemId> items = new ArrayList<>();
        ArrayList<Float> scores = new ArrayList<>();
        int k = 0;
        if (mayBeNumeric && numValues != null) {
            Bits docsWithField = null;
            try {
                docsWithField = reader.getDocsWithField(field);
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (IItemId item : result.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                if (docsWithField != null && docsWithField.get(doc)) {
                    double val = numValues.get(doc);
                    if (isFloat)
                        val = NumericUtils.sortableIntToFloat((int) val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long) val);
                    int ord = 20;
                    if (logScale) {
                        if (val < 0) {
                            ord -= 1;
                            val *= -1;
                            if (val > 1)
                                ord = ord - (int) Math.log10(val);
                        } else if (val > 1)
                            ord = (int) Math.log10(val) + ord;
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
        } else if (mayBeNumeric && numValuesSet != null) {
            for (IItemId item : result.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                numValuesSet.setDocument(doc);
                for (int i = 0; i < numValuesSet.count(); i++) {
                    double val = numValuesSet.valueAt(i);
                    if (isFloat)
                        val = NumericUtils.sortableIntToFloat((int) val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long) val);
                    int ord = 20;
                    if (logScale) {
                        if (val < 0) {
                            ord -= 1;
                            val *= -1;
                            if (val > 1)
                                ord = ord - (int) Math.log10(val);
                        } else if (val > 1)
                            ord = (int) Math.log10(val) + ord;
                    } else {
                        ord = (int) ((val - min) / interval);
                        if (val == max && min != max)
                            ord--;
                    }
                    if (ordsToGet.contains(ord)) {
                        items.add(item);
                        scores.add(result.getScore(k));
                        break;
                    }
                }
                k++;
            }
        } else if (docValues != null) {
            for (IItemId item : result.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                if (ordsToGet.contains(docValues.getOrd(doc))) {
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
                    docValuesSet.setDocument(doc);
                    long ord;
                    while ((ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
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
        field = BasicProps.getNonLocalizedField(field.trim());

        loadDocValues(field);

        boolean mayBeNumeric = mayBeNumeric(field);
        final boolean isNumeric = mayBeNumeric && (numValues != null || numValuesSet != null);
        boolean isFloat = isFloat(field);
        boolean isDouble = isDouble(field);
        boolean isTimeEvent = BasicProps.TIME_EVENT.equals(field);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (isNumeric)
                    scale.setEnabled(true);
                else
                    scale.setEnabled(false);
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

        if (isNumeric && numValues != null) {
            Bits docsWithField = reader.getDocsWithField(field);
            if (logScale) {
                // 0 to 19: count negative numbers. 20 to 39: count positive numbers
                valueCount = new int[40];
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    if (docsWithField.get(doc)) {
                        double val = numValues.get(doc);
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        int ord = 20;
                        if (val < 0) {
                            ord -= 1;
                            val *= -1;
                            if (val > 1)
                                ord = ord - (int) Math.log10(val);
                        } else if (val > 1)
                            ord = (int) Math.log10(val) + ord;
                        valueCount[ord]++;
                    }
                }
            } else {
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    if (docsWithField.get(doc)) {
                        double val = numValues.get(doc);
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        if (val < min)
                            min = val;
                        if (val > max)
                            max = val;
                    }
                }
                valueCount = new int[10];
                interval = (max - min) / valueCount.length;
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    if (docsWithField.get(doc)) {
                        double val = numValues.get(doc);
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        int ord = (int) ((val - min) / interval);
                        if (ord == valueCount.length)
                            ord = valueCount.length - 1;
                        valueCount[ord]++;
                    }
                }
            }
        } else if (isNumeric && numValuesSet != null) {
            if (logScale) {
                // 0 to 19: count negative numbers. 20 to 39: count positive numbers
                valueCount = new int[40];
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    numValuesSet.setDocument(doc);
                    int prevOrd = -1;
                    for (int i = 0; i < numValuesSet.count(); i++) {
                        double val = numValuesSet.valueAt(i);
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        int ord = 20;
                        if (val < 0) {
                            ord -= 1;
                            val *= -1;
                            if (val > 1)
                                ord = ord - (int) Math.log10(val);
                        } else if (val > 1)
                            ord = (int) Math.log10(val) + ord;
                        if (ord != prevOrd)
                            valueCount[ord]++;
                        prevOrd = ord;
                    }
                }
            } else {
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    numValuesSet.setDocument(doc);
                    for (int i = 0; i < numValuesSet.count(); i++) {
                        double val = numValuesSet.valueAt(i);
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        if (val < min)
                            min = val;
                        if (val > max)
                            max = val;
                    }
                }
                valueCount = new int[10];
                interval = (max - min) / valueCount.length;
                for (IItemId item : ipedResult.getIterator()) {
                    int doc = App.get().appCase.getLuceneId(item);
                    numValuesSet.setDocument(doc);
                    int prevOrd = -1;
                    for (int i = 0; i < numValuesSet.count(); i++) {
                        double val = numValuesSet.valueAt(i);
                        if (isFloat)
                            val = NumericUtils.sortableIntToFloat((int) val);
                        else if (isDouble)
                            val = NumericUtils.sortableLongToDouble((long) val);
                        int ord = (int) ((val - min) / interval);
                        if (ord == valueCount.length)
                            ord = valueCount.length - 1;
                        if (ord != prevOrd)
                            valueCount[ord]++;
                        prevOrd = ord;
                    }
                }
            }
        } else if (docValues != null) {
            valueCount = new int[docValues.getValueCount()];
            for (IItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                int ord = docValues.getOrd(doc);
                if (ord != -1)
                    valueCount[ord]++;
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
                    docValuesSet.setDocument(doc);
                    long ord, prevOrd = -1;
                    while ((ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                        if (prevOrd != ord)
                            valueCount[(int) ord]++;
                        prevOrd = ord;
                    }
                }
            }
        }
        // System.out.println("new");
        ArrayList<ValueCount> list = new ArrayList<ValueCount>();
        if (isNumeric) {
            for (int ord = 0; ord < valueCount.length; ord++)
                if (valueCount[ord] > 0) {
                    if (logScale) {
                        if (ord < 20) {
                            long end = ord == 19 ? 0 : -(long) Math.pow(10, 19 - ord);
                            long start = -((long) Math.pow(10, 19 - ord + 1) - 1);
                            list.add(new RangeCount(start, end, ord, valueCount[ord]));
                        } else {
                            long start = ord == 20 ? 0 : (long) Math.pow(10, ord - 20);
                            long end = (long) Math.pow(10, ord - 20 + 1) - 1;
                            list.add(new RangeCount(start, end, ord, valueCount[ord]));
                        }
                    } else {
                        double start = min + ord * interval;
                        double end = min + (ord + 1) * interval;
                        list.add(new RangeCount(start, end, ord, valueCount[ord]));
                    }
                }
        } else if (docValues != null) {
            LookupOrd lo = new LookupOrdSDV(docValues);
            for (int ord = 0; ord < valueCount.length; ord++)
                if (valueCount[ord] > 0)
                    list.add(new ValueCount(lo, ord, valueCount[ord]));
        } else if (docValuesSet != null) {
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
            int[] categoryOrd = RowComparator.getLocalizedCategoryOrd(docValuesSet);
            Arrays.sort(array, new Comparator<ValueCount>() {
                @Override
                public int compare(ValueCount o1, ValueCount o2) {
                    return categoryOrd[o1.ord] - categoryOrd[o2.ord];
                }
            });
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

    public MultiSearchResult getFilteredItemIds(MultiSearchResult result) throws ParseException, QueryNodeException {

        if (!isFiltering()) {
            return result;
        }

        String field = (String) props.getSelectedItem();
        field = BasicProps.getNonLocalizedField(field.trim());

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

        StringBuilder str = new StringBuilder();
        str.append(IndexItem.CONTENT + ":("); //$NON-NLS-1$
        for (String term : terms) {
            str.append("\"" + escape(term) + "\" "); //$NON-NLS-1$ //$NON-NLS-2$
        }
        str.append(")"); //$NON-NLS-1$

        return new QueryBuilder(App.get().appCase).getQuery(str.toString());

    }

    private String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c))
                sb.append('\\');
            sb.append(c);
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
            setStateChanged(sort);
            sortAndUpdateList(filteredArray);

        } else if (e.getSource() == scale) {
            setStateChanged(scale);
            populateList();
        }

    }
    
    private void setStateChanged(JSlider slider) {
        for (MouseListener l : slider.getMouseListeners()) {
            if (l instanceof SliderMouseListener) {
                ((SliderMouseListener) l).stateChanged = true;
            }
        }
    }

    private class SliderMouseListener extends MouseAdapter {

        private boolean stateChanged = false;
        private JSlider slider;

        private SliderMouseListener(JSlider slider) {
            this.slider = slider;
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!stateChanged) {
                slider.setValue(1 - slider.getValue());
            }
            stateChanged = false;
        }

    }

    private class ButtonMouseListener extends MouseAdapter {
        
        private JButton button;
        
        private ButtonMouseListener(JButton button) {
            this.button = button;
        }
        
        @Override
        public void mouseEntered(MouseEvent e){
            button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1, true));
        }
        @Override
        public void mouseExited(MouseEvent e){
            button.setBorder(null);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            button.setContentAreaFilled(true);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            button.setContentAreaFilled(false);
        }
    }
    
    private void resetPanel() {
        if (groups.getSelectedItem() != null) {
            boolean empty = list.isSelectionEmpty();
            groups.setSelectedItem(null);
            clearTextFilter(listFilter, emptyListFilter);
            clearTextFilter(propsFilter, emptyPropsFilter);
            sort.setValue(0);
            scale.setValue(0);
            updateProps();
            clearFilter();
            lastPropSel = null;
            if (!empty) {
                App.get().appletListener.updateFileListing();
                updateTabColor();
            }
        }
    }

    private void setupFilter(JTextField field, String emptyFilter) {
        field.addFocusListener(new FocusListener() {
            public void focusLost(FocusEvent e) {
                String text = field.getText().trim();
                if (text.isEmpty() || text.equals(emptyFilter)) {
                    clearTextFilter(field, emptyFilter);
                }
            }

            public void focusGained(FocusEvent e) {
                String text = field.getText().trim();
                if (text.equals(emptyFilter)) {
                    field.setText("");
                }
                field.setForeground(Color.black);
            }
        });
        clearTextFilter(field, emptyFilter);
    }
    
    private void clearTextFilter(JTextField field, String emptyFilter) {
        field.setText(emptyFilter);
        field.setForeground(new Color(140, 140, 140));
    }
}
