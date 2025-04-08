package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.columns.ColumnsManager;
import iped.app.metadata.CountComparator;
import iped.app.metadata.MetadataSearch;
import iped.app.metadata.MoneyCount;
import iped.app.metadata.ValueCount;
import iped.app.metadata.ValueCountQueryFilter;
import iped.app.ui.controls.HintTextField;
import iped.app.ui.controls.HoverButton;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.QueryBuilder;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.task.NamedEntityTask;
import iped.engine.task.index.IndexItem;
import iped.engine.task.regex.RegexTask;
import iped.engine.task.similarity.ImageSimilarityTask;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.localization.LocalizedProperties;
import iped.search.IMultiSearchResult;
import iped.utils.IconUtil;
import iped.utils.StringUtil;
import iped.viewers.api.IFilter;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class MetadataPanel extends JPanel implements ActionListener, ListSelectionListener, ChangeListener, IResultSetFilterer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataPanel.class);

    private static final String RES_PATH = "/" + MetadataPanel.class.getPackageName().replace('.', '/') + '/';
    private static final String SORT_COUNT = Messages.getString("MetadataPanel.Hits"); //$NON-NLS-1$
    private static final String SORT_ALFANUM = Messages.getString("MetadataPanel.AlphaNumeric"); //$NON-NLS-1$
    private static final String LINEAR_SCALE = Messages.getString("MetadataPanel.Linear"); //$NON-NLS-1$
    private static final String LOG_SCALE = Messages.getString("MetadataPanel.Log"); //$NON-NLS-1$
    private static final String NO_RANGES = Messages.getString("MetadataPanel.NoRanges"); //$NON-NLS-1$
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
    HintTextField listFilter = new HintTextField("[" + Messages.getString("MetadataPanel.FilterValues") + "] " + (char) 0x2193);
    private final HoverButton copyResultToClipboard = new HoverButton();
    private final HoverButton reset = new HoverButton();
    private final HintTextField propsFilter = new HintTextField("[" + Messages.getString("MetadataPanel.FilterProps") + "] " + (char) 0x2191);

    private Object lastPropSel;

    ValueCount[] array, filteredArray;

    boolean updatingProps = false, updatingList = false, clearing = false;
    volatile boolean updatingResult = false;

    private MetadataSearch ms = new MetadataSearch();

    private static final long serialVersionUID = 1L;

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
                if (f.equals(ResultTableModel.BOOKMARK_COL) || f.equals(ResultTableModel.SCORE_COL) || f.startsWith(ImageSimilarityTask.IMAGE_FEATURES) || f.startsWith(SimilarFacesSearch.FACE_FEATURES))
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
        setWaitVisible(true);
        new Thread() {
            public void run() {
                try {
                    filteredArray = filter(array);
                    sortAndUpdateList(filteredArray);
                } finally {
                    setWaitVisible(false);
                }

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
        setWaitVisible(true);
        new Thread() {
            public void run() {
                try {
                    StringBuffer strBuffer = new StringBuffer();
                    for (int i = 0; i < list.getModel().getSize(); i++) {
                        ValueCount item = list.getModel().getElementAt(i);
                        String val = item.getVal();
                        if (val != null && !val.isEmpty()) {
                            strBuffer.append(val);
                            strBuffer.append(System.lineSeparator());
                        }
                    }
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(strBuffer.toString()), null);
                } finally {
                    setWaitVisible(false);
                }
            }
        }.start();
    }

    private void populateList() {
        setWaitVisible(true);
        final boolean updateResult = !list.isSelectionEmpty();
        Future<MultiSearchResult> future = null;
        if (updateResult) {
            updatingResult = true;
            future = App.get().appletListener.futureUpdateFileListing();
        }
        Future<MultiSearchResult> finalfuture = future;

        ms.setLogScale(scale.getValue() == 1);
        ms.setNoRanges(scale.getValue() == -1);

        if (props.getSelectedItem() != null)
            lastPropSel = props.getSelectedItem();

        new Thread() {
            @Override
            public void run() {
                try {
                    IMultiSearchResult result = null;
                    if (finalfuture != null) {
                        try {
                            result = finalfuture.get();
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                    }
                    if (result == null) {
                        result = App.get().ipedResult;
                    }
                    updatingResult = false;
                    ms.setIpedResult(result);

                    countValues();

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    setWaitVisible(false);
                }
            }
        }.start();

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
                if (!updateAction && i > 0) {
                    list.setSelectedIndices(selIdx);
                }
                updatingList = false;

                // System.out.println("finish");
                updateTabColor();
            }
        });

    }

    private boolean updateAction = false;;

    @Override
    public void actionPerformed(ActionEvent e) {

        if (updatingProps)
            return;

        if ((updateAction = e.getSource() == update) || e.getSource() == props)
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
        String field = (String) props.getSelectedItem();
        if (field == null) {
            return result;
        }
        field = LocalizedProperties.getNonLocalizedField(field.trim());

        Set<Integer> ords = new HashSet<>();
        for (ValueCount value : list.getSelectedValuesList()) {
            ords.add(value.getOrd());
        }
        return ms.getIdsWithOrd(result, field, ords);

    }

    public Set<String> getHighlightTerms() throws ParseException, QueryNodeException {

        String field = (String) props.getSelectedItem();
        if (field == null || !(field.startsWith(RegexTask.REGEX_PREFIX) || field.startsWith(NamedEntityTask.NER_PREFIX)) || list.isSelectionEmpty())
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
        ComplexPhraseQueryParser complexPhraseParser = new ComplexPhraseQueryParser(IndexItem.CONTENT, App.get().appCase.getAnalyzer());
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

    private void setWaitVisible(final boolean visible) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                App.get().dialogBar.setVisible(visible);
            }
        });
    }

    public void setSelectedProperty(String string) {
        // TODO Auto-generated method stub

    }

    private void countValues() throws IOException {
        long time = System.currentTimeMillis();

        String field = (String) props.getSelectedItem();
        if (field == null) {
            updatingResult = false;
            filteredArray = array = new ValueCount[0];
            sortAndUpdateList(filteredArray);
            return;
        }

        field = LocalizedProperties.getNonLocalizedField(field.trim());
        boolean isNumeric = IndexItem.isNumeric(field);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                scale.setEnabled(isNumeric);
                labelScale.setEnabled(isNumeric);
            }
        });

        ArrayList<ValueCount> resultList = ms.countValues(field);

        array = resultList.toArray(new ValueCount[0]);

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

        } else if (ms.isCategory()) {
            try {
                int[] categoryOrd = RowComparator.getLocalizedCategoryOrd(ms.getDocValuesSet());
                Arrays.sort(array, new Comparator<ValueCount>() {
                    @Override
                    public int compare(ValueCount o1, ValueCount o2) {
                        return categoryOrd[o1.getOrd()] - categoryOrd[o2.getOrd()];
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

    @Override
    public List getDefinedFilters() {
        ArrayList<IFilter> result = new ArrayList<IFilter>();
        String field = (String) props.getSelectedItem();
        if (field == null) {
            return result;
        }
        field = LocalizedProperties.getNonLocalizedField(field.trim());
        HashSet<ValueCount> selectedValues = new HashSet<ValueCount>();
        selectedValues.addAll(list.getSelectedValuesList());
        if (isFiltering()) {
            ValueCount sample = selectedValues.iterator().next();
            result.add(new ValueCountQueryFilter(field, selectedValues));
            /*
             * if(sample instanceof RangeCount) { result.add(new ValueCountQueryFilter(field
             * , selectedValues)); }else { result.add(new ValueCountFilter(field ,
             * selectedValues)); }
             */
        }
        return result;
    }

    public String toString() {
        return "Metadata panel filterer";
    }

    @Override
    public IFilter getFilter() {
        if (isFiltering()) {
            return new IResultSetFilter() {
                public String toString() {
                    String field = (String) props.getSelectedItem();
                    field = LocalizedProperties.getNonLocalizedField(field.trim());
                    return field + ":" + list.getSelectedValue();
                }

                @Override
                public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
                    return getFilteredItemIds((MultiSearchResult) src);
                }
            };
        }
        return null;
    }

    @Override
    public boolean hasFilters() {
        return isFiltering();
    }

    @Override
    public boolean hasFiltersApplied() {
        // TODO Auto-generated method stub
        return false;
    }

}
