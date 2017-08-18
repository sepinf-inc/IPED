package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.NumericUtils;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.regex.RegexTask;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.search.QueryBuilder;

public class MetadataPanel extends JPanel implements ActionListener, ListSelectionListener{
    
    private static final String SORT_COUNT = "Ocorrências";
    private static final String SORT_ALFANUM = "Alfanumérica";
    private static final String MONEY_FIELD = RegexTask.REGEX_PREFIX + "VALOR_MONETARIO";
    
    private volatile static AtomicReader reader;
    
    JList<ValueCount> list = new JList<ValueCount>();
    JScrollPane scrollList = new JScrollPane(list);
    JComboBox<String> sort = new JComboBox<String>();
    JComboBox<String> groups;
    JComboBox<String> props = new JComboBox<String>();
    JButton update = new JButton("Atualizar");
    
    volatile NumericDocValues numValues;
    volatile SortedNumericDocValues numValuesSet;
    volatile SortedDocValues docValues;
    volatile SortedSetDocValues docValuesSet;
    
    volatile MultiSearchResult ipedResult;
    ValueCount[] array;
    
    boolean updatingProps = false, updatingList = false;
    volatile boolean updatingResult = false;

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    public MetadataPanel(){
        super(new BorderLayout());
        
        groups = new JComboBox<String>(ColumnsManager.groupNames);
        groups.setSelectedItem(null);
        groups.setMaximumRowCount(15);
        groups.addActionListener(this);
        
        props.setMaximumRowCount(30);
        props.addActionListener(this);
        
        sort.addItem(SORT_COUNT);
        sort.addItem(SORT_ALFANUM);
        sort.addActionListener(this);
        
        list.setFixedCellHeight(18);
        list.setFixedCellWidth(2000);
        list.addListSelectionListener(this);
        
        JPanel l1 = new JPanel(new BorderLayout());
        JLabel label = new JLabel("Grupo:");
        label.setPreferredSize(new Dimension(90, 20));
        l1.add(label, BorderLayout.WEST);
        l1.add(groups, BorderLayout.CENTER);
        
        JPanel l2 = new JPanel(new BorderLayout());
        label = new JLabel("Propriedade:");
        label.setPreferredSize(new Dimension(90, 20));
        l2.add(label, BorderLayout.WEST);
        l2.add(props, BorderLayout.CENTER);
        
        JPanel l3 = new JPanel(new BorderLayout());
        label = new JLabel("Ordenação:");
        label.setPreferredSize(new Dimension(90, 20));
        l3.add(label, BorderLayout.WEST);
        l3.add(sort, BorderLayout.CENTER);
        l3.add(update, BorderLayout.EAST);
        
        update.addActionListener(this);
        
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(l1);
        top.add(l2);
        top.add(l3);
        
        this.add(top, BorderLayout.NORTH);
        this.add(scrollList, BorderLayout.CENTER);
    }
    
    interface LookupOrd{
        String lookupOrd(int ord);
    }
    
    class LookupOrdSDV implements LookupOrd{

        SortedDocValues sdv;
        
        LookupOrdSDV(SortedDocValues sdv){
            this.sdv = sdv;
        }
        @Override
        public String lookupOrd(int ord) {
            return sdv.lookupOrd(ord).utf8ToString();
        }
    }
    
    class LookupOrdSSDV implements LookupOrd{

        SortedSetDocValues ssdv;
        
        LookupOrdSSDV(SortedSetDocValues ssdv){
            this.ssdv = ssdv;
        }
        @Override
        public String lookupOrd(int ord) {
            return ssdv.lookupOrd(ord).utf8ToString();
        }
    }
    
    private static class ValueCount{
        LookupOrd lo;
        int ord, count;
        
        ValueCount(LookupOrd lo, int ord, int count){
            this.lo = lo;
            this.ord = ord;
            this.count = count;
        }
        public String getVal(){
            try{
                return lo.lookupOrd(ord);
                
            }catch(Exception e){
                //LookupOrd fica inválido (IndexReader fechado) ao atualizar interface durante processamento
                return "[Clique em Atualizar]";
            }
        }
        @Override
        public String toString() {
            return getVal() + " (" + count + ")";
        }
    }
    
    private class RangeCount extends ValueCount{
        long start, end;
        
        RangeCount(long start, long end, int ord, int count) {
            super(null, ord, count);
            this.start = start;
            this.end = end;
        }
        @Override
        public String toString() {
            String startStr = NumberFormat.getNumberInstance().format(start);
            String endStr = NumberFormat.getNumberInstance().format(end);
            return startStr + " TO " + endStr + " (" + count + ")";
        }
    }
    
    private static class MoneyCount extends ValueCount implements Comparable<MoneyCount>{
        
        private static Pattern pattern = Pattern.compile("[\\$\\s\\.\\,]");
        //String val;
        long money;
        
        MoneyCount(LookupOrd lo, int ord, int count){
            super(lo, ord, count);  
            String val = getVal();
            char centChar = val.charAt(val.length() - 3);
            if(centChar == '.' || centChar == ',')
                val = val.substring(0, val.length() - 3);
            Matcher matcher = pattern.matcher(val);
            money = Long.valueOf(matcher.replaceAll(""));
        }
        @Override
        public int compareTo(MoneyCount o) {
            return Long.compare(o.money, this.money);
        }
    }
    
    private class CountComparator implements Comparator<ValueCount>{
        @Override
        public final int compare(ValueCount o1, ValueCount o2) {
            return Long.compare(o2.count, o1.count);
        }
    }
    
    private void updateProps(){
        updatingProps = true;
        props.removeAllItems();
        String[] fields = ColumnsManager.getInstance().fieldGroups[groups.getSelectedIndex()];
        for(String f : fields)
            props.addItem(f);
        updatingProps = false;
    }
    
    private void populateList(){
        App.get().dialogBar.setVisible(true);
        final boolean updateResult = !list.isSelectionEmpty();
        if(updateResult){
            updatingResult = true;
            App.get().appletListener.updateFileListing();
        }
        ipedResult = App.get().ipedResult;
        
        new Thread(){
            @Override
            public void run(){
                try {
                    countValues(updateResult);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
            
    }
    
    private void loadDocValues(String field) throws IOException{
        System.out.println("getDocValues");
        numValues = reader.getNumericDocValues(field);
        if(numValues == null)
            numValues = reader.getNumericDocValues("_num_" + field);
        numValuesSet = reader.getSortedNumericDocValues(field);
        if(numValuesSet == null)
            numValuesSet = reader.getSortedNumericDocValues("_num_" + field);
        docValues = reader.getSortedDocValues(field);
        if(docValues == null)
            docValues = reader.getSortedDocValues("_" + field);
        docValuesSet = reader.getSortedSetDocValues(field);
        if(docValuesSet == null)
            docValuesSet = reader.getSortedSetDocValues("_" + field);
    }
    
    private List<ItemId> getIdsWithOrd(String field, int ordToGet, int valueCount){
        
        boolean isFloat = Float.class.equals(IndexItem.getMetadataTypes().get(field));
        boolean isDouble = Double.class.equals(IndexItem.getMetadataTypes().get(field));
        
        ArrayList<ItemId> items = new ArrayList<ItemId>(); 
        if(numValues != null){
            Bits docsWithField = null;
            try {
                docsWithField = reader.getDocsWithField(field);
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (ItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                if(docsWithField != null && docsWithField.get(doc)){
                    double val = numValues.get(doc);
                    if(isFloat)
                        val = NumericUtils.sortableIntToFloat((int)val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long)val);
                    int ord = 20;
                    if(val < 0){
                        ord -= 1;
                        val *= -1;
                        if(val > 1) ord = ord - (int)Math.log10(val);
                    }else
                        if(val > 1) ord = (int)Math.log10(val) + ord;
                    if(ord == ordToGet)
                        items.add(item);
                }
            }  
        }else if(numValuesSet != null){
            for (ItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                numValuesSet.setDocument(doc);
                for(int i = 0; i < numValuesSet.count(); i++){
                    double val = numValuesSet.valueAt(i);
                    if(isFloat)
                        val = NumericUtils.sortableIntToFloat((int)val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long)val);
                    int ord = 20;
                    if(val < 0){
                        ord -= 1;
                        val *= -1;
                        if(val > 1) ord = ord - (int)Math.log10(val);
                    }else
                        if(val > 1) ord = (int)Math.log10(val) + ord;
                    if(ord == ordToGet){
                        items.add(item);
                        break;
                    }
                }
            }  
        }else if(docValues != null){
            for (ItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                if(ordToGet == docValues.getOrd(doc))
                    items.add(item);
            }    
        }else if(docValuesSet != null){
            for (ItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                docValuesSet.setDocument(doc);
                long ord;
                while((ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS){
                    if(ord == ordToGet){
                        items.add(item);
                        break;
                    }
                }
            }
        }
        
        return items;
    }
    
    private void countValues(boolean updateResult) throws IOException {
        
        reader = App.get().appCase.getAtomicReader();

        String field = (String)props.getSelectedItem();
        if(field == null) return;
        field = field.trim();
        
        loadDocValues(field);
        
        if(updateResult){
            while(App.get().ipedResult == ipedResult)
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            ipedResult = App.get().ipedResult;
            updatingResult = false;
        }
        
        boolean isFloat = Float.class.equals(IndexItem.getMetadataTypes().get(field));
        boolean isDouble = Double.class.equals(IndexItem.getMetadataTypes().get(field));
        
        System.out.println("counting");
        int[] valueCount = null;
        if(numValues != null){
            //0 to 19: count negative numbers. 20 to 39: count positive numbers
            valueCount = new int[40];
            Bits docsWithField = reader.getDocsWithField(field);
            for (ItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                if(docsWithField.get(doc)){
                    double val = numValues.get(doc);
                    if(isFloat)
                        val = NumericUtils.sortableIntToFloat((int)val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long)val);
                    int ord = 20;
                    if(val < 0){
                        ord -= 1;
                        val *= -1;
                        if(val > 1) ord = ord - (int)Math.log10(val);
                    }else
                        if(val > 1) ord = (int)Math.log10(val) + ord;
                    valueCount[ord]++;
                }
            }  
        }else if(numValuesSet != null){
          //0 to 19: count negative numbers. 20 to 39: count positive numbers
            valueCount = new int[40];
            for (ItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                numValuesSet.setDocument(doc);
                int prevOrd = -1;
                for(int i = 0; i < numValuesSet.count(); i++){
                    double val = numValuesSet.valueAt(i);
                    if(isFloat)
                        val = NumericUtils.sortableIntToFloat((int)val);
                    else if (isDouble)
                        val = NumericUtils.sortableLongToDouble((long)val);
                    int ord = 20;
                    if(val < 0){
                        ord -= 1;
                        val *= -1;
                        if(val > 1) ord = ord - (int)Math.log10(val);
                    }else
                        if(val > 1) ord = (int)Math.log10(val) + ord;
                    if(ord != prevOrd)
                        valueCount[ord]++;
                    prevOrd = ord;
                }
            }  
        }else if(docValues != null){
            valueCount = new int[docValues.getValueCount()];
            for (ItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                int ord = docValues.getOrd(doc);
                if(ord != -1)
                    valueCount[ord]++;
            }    
        }else if(docValuesSet != null){
            valueCount = new int[(int)docValuesSet.getValueCount()];
            for (ItemId item : ipedResult.getIterator()) {
                int doc = App.get().appCase.getLuceneId(item);
                docValuesSet.setDocument(doc);
                long ord, prevOrd = -1;
                while((ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS){
                    if(prevOrd != ord)
                        valueCount[(int)ord]++;
                    prevOrd = ord;
                }
            }
        }
        System.out.println("new");
        ArrayList<ValueCount> list = new ArrayList<ValueCount>();
        if(numValues != null || numValuesSet != null){
            for(int ord = 0; ord < valueCount.length; ord++)
                if(valueCount[ord] > 0){
                    if(ord < 20){
                        long end = ord == 19 ? 0 : -(long)Math.pow(10, 19 - ord);
                        long start = -((long)Math.pow(10, 19 - ord + 1) - 1);
                        list.add(new RangeCount(start, end, ord, valueCount[ord]));
                    }else{
                        long start = ord == 20 ? 0 : (long)Math.pow(10, ord - 20);
                        long end = (long)Math.pow(10, ord - 20 + 1) - 1;
                        list.add(new RangeCount(start, end, ord, valueCount[ord]));
                    }
                }
        }else if(docValues != null){
            LookupOrd lo = new LookupOrdSDV(docValues);
            for(int ord = 0; ord < valueCount.length; ord++)
                if(valueCount[ord] > 0)
                    list.add(new ValueCount(lo, ord, valueCount[ord]));
        }else if(docValuesSet != null){
            LookupOrd lo = new LookupOrdSSDV(docValuesSet);
            boolean isMoney = field.equals(MONEY_FIELD);
            for(int ord = 0; ord < valueCount.length; ord++)
                if(valueCount[ord] > 0)
                    if(!isMoney)
                        list.add(new ValueCount(lo, ord, valueCount[ord]));
                    else
                        list.add(new MoneyCount(lo, ord, valueCount[ord]));
        }
            
        
        array = list.toArray(new ValueCount[0]);
        
        sortList();
    }
    
    private void sortList(){
        
        if(array == null)
            return;
        
        System.out.println("sorting");
        
        ValueCount[] sortedArray = array;
        if(sort.getSelectedItem().equals(SORT_COUNT)){
            sortedArray = array.clone();
            Arrays.sort(sortedArray, new CountComparator());
            
        }else if(array.length > 0 && array[0] instanceof MoneyCount){
            Arrays.sort(array);
        }
        System.out.println("Sorted");
        
        updateList(sortedArray);
           
    }
    
    private void updateList(final ValueCount[] sortedArray){
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                updatingList = true;    
                List<ValueCount> selection = list.getSelectedValuesList();
                HashSet<ValueCount> selSet = new HashSet<ValueCount>();
                selSet.addAll(selection);
                list.setListData(sortedArray);
                int[] selIdx = new int[selSet.size()];
                int i = 0;
                for(int idx = 0; idx < sortedArray.length; idx++)
                    if(selSet.contains(sortedArray[idx]))
                        selIdx[i++] = idx;
                if(i > 0)
                    list.setSelectedIndices(selIdx);
                updatingList = false;
                
                System.out.println("finish");
                updateTabColor();
                App.get().dialogBar.setVisible(false);
            }
        });
        
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        
        if(updatingProps)
            return;
        
        if(e.getSource() == update || e.getSource() == props)
            populateList();
        
        else if(e.getSource() == sort)
            sortList();
        
        else if(e.getSource() == groups)
            updateProps();
            
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        
        if((e != null && e.getValueIsAdjusting()) || updatingList)
            return;
        
        System.out.println("listSelectionEvent");
        
        App.get().appletListener.updateFileListing();
        
        updateTabColor();
        
    }
    
    private void updateTabColor(){
        if(!list.isSelectionEmpty())
            App.get().treeTab.setBackgroundAt(App.get().treeTab.getSelectedIndex(), App.get().alertColor);
        else
            App.get().treeTab.setBackgroundAt(App.get().treeTab.getSelectedIndex(), App.get().defaultTabColor);
    }
    
    public Set<ItemId> getFilteredItemIds() throws ParseException, QueryNodeException{
        
        String field = (String)props.getSelectedItem();
        if(field == null || list.isSelectionEmpty() || updatingResult)
            return null;
        field = field.trim();
        
        Set<ItemId> items = new HashSet<ItemId>();
        for(ValueCount value : list.getSelectedValuesList()){
            List<ItemId> ids = getIdsWithOrd(field, value.ord, value.count);
            items.addAll(ids);
        }
        
        return items;
        
    }
    
    public Set<String> getHighlightTerms() throws ParseException, QueryNodeException{
        
        String field = (String)props.getSelectedItem();
        if(field == null || !field.startsWith(RegexTask.REGEX_PREFIX) || list.isSelectionEmpty())
            return Collections.emptySet();

        Set<String> highlightTerms = new HashSet<String>();
        for(ValueCount item : list.getSelectedValuesList())
            highlightTerms.add(item.getVal());
        
        return highlightTerms;
        
    }
    
    public Query getHighlightQuery() throws ParseException, QueryNodeException{
        
        String field = (String)props.getSelectedItem();
        if(field == null || !field.startsWith(RegexTask.REGEX_PREFIX) || list.isSelectionEmpty())
            return null;
        
        StringBuilder str = new StringBuilder();
        str.append(IndexItem.CONTENT + ":(");
        for(ValueCount item : list.getSelectedValuesList())
            str.append("\"" + escape(item.getVal()) + "\" ");
        str.append(")");
        
        return new QueryBuilder(App.get().appCase).getQuery(str.toString());
        
    }
    
    private String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

}
