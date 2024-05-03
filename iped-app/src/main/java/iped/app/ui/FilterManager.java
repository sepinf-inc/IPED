package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.roaringbitmap.RoaringBitmap;

import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.utils.UTF8Properties;
import iped.viewers.api.ActionListenerControl;
import iped.viewers.api.IFilter;
import iped.viewers.api.IFilterer;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class FilterManager implements ActionListener, ListSelectionListener, ActionListenerControl {

    HashMap<IFilter, RoaringBitmap[]> cachedFilterBitsets = new HashMap<IFilter, RoaringBitmap[]>();
    boolean useCachedBitmaps = false;

    List<IQueryFilterer> queryFilterers = new ArrayList<IQueryFilterer>();
    List<IResultSetFilterer> resultSetFilterers = new ArrayList<IResultSetFilterer>();
    LinkedHashMap<IFilterer, Boolean> filterers = new LinkedHashMap<IFilterer, Boolean>();

    private static File userFilters = getGlobalFilterFile(); // $NON-NLS-1$ //$NON-NLS-2$
    private File defaultFilter;

    private UTF8Properties filters = new UTF8Properties();
    private HashMap<String, String> localizationMap = new HashMap<>();
    private volatile boolean updatingCombo = false;
    private JComboBox<String> comboFilter;

    JDialog dialog;

    JLabel labFilters = new JLabel(Messages.getString("FilterManager.Filters")); //$NON-NLS-1$
    JLabel labExpr = new JLabel(Messages.getString("FilterManager.Expresion")); //$NON-NLS-1$

    JButton butSave = new JButton(Messages.getString("FilterManager.Save")); //$NON-NLS-1$
    JButton butNew = new JButton(Messages.getString("FilterManager.New")); //$NON-NLS-1$
    JButton butDelete = new JButton(Messages.getString("FilterManager.Delete")); //$NON-NLS-1$

    DefaultListModel<String> listModel = new DefaultListModel<String>();
    JList<String> list = new JList<String>(listModel);
    JScrollPane scrollList = new JScrollPane(list);

    JTextArea expression = new JTextArea();
    JScrollPane scrollExpression = new JScrollPane(expression);
    Color defaultColor;
    private ComboFilterer cf;

    private static final File getGlobalFilterFile() {
        String name = "ipedFilters"; //$NON-NLS-1$
        String locale = System.getProperty(iped.localization.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
        if (locale != null && !locale.equals("pt-BR")) //$NON-NLS-1$
            name += "-" + locale; //$NON-NLS-1$
        name += ".txt"; //$NON-NLS-1$
        return new File(System.getProperty("user.home") + "/.iped/" + name); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static class FilterComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return MessagesFilter.get(o1, o1).compareTo(MessagesFilter.get(o2, o2));
        }
    }

    public void loadFilters() {
        try {
            if (userFilters.exists()) {
                filters.load(userFilters);
            }

            if (defaultFilter == null) {
                defaultFilter = new File(App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir(), "conf/DefaultFilters.txt"); //$NON-NLS-1$
            }
            filters.load(defaultFilter);

            // fix filter values saved by old versions, see #1392
            for (Entry<Object, Object> entry : filters.entrySet()) {
                if (entry.getValue().toString().contains("\\\\:")) {
                    entry.setValue(entry.getValue().toString().replace("\\\\:", "\\:"));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        updateFilter();
    }

    private void updateFilter() {
        updatingCombo = true;
        Object prevSelected = comboFilter.getSelectedItem();

        comboFilter.removeAllItems();
        comboFilter.addItem(App.FILTRO_TODOS);
        comboFilter.addItem(App.FILTRO_SELECTED);

        List<String> filternames = filters.keySet().stream().map(i -> (String) i).collect(Collectors.toList());
        Collections.sort(filternames, new FilterComparator());
        for (String filter : filternames) {
            String localizedName = MessagesFilter.get(filter, filter);
            localizationMap.put(localizedName, filter);
            comboFilter.addItem(localizedName);
        }

        if (prevSelected != null) {
            if (prevSelected == App.FILTRO_TODOS || prevSelected == App.FILTRO_SELECTED || filters.containsKey(prevSelected)) {
                comboFilter.setSelectedItem(prevSelected);
            } else {
                comboFilter.setSelectedIndex(1);
                updatingCombo = false;
                comboFilter.setSelectedIndex(0);
            }
        }

        updatingCombo = false;
    }

    private void populateList() {

        String name = list.getSelectedValue();
        List<String> filternames = filters.keySet().stream().map(i -> (String) i).collect(Collectors.toList());
        Collections.sort(filternames, new FilterComparator());
        listModel.clear();
        for (String filter : filternames) {
            String localizedName = MessagesFilter.get(filter, filter);
            localizationMap.put(localizedName, filter);
            listModel.addElement(localizedName);
        }
        list.setSelectedValue(name, true);
    }

    public FilterManager(JComboBox<String> comboFilter) {
        this.comboFilter = comboFilter;
        defaultColor = comboFilter.getBackground();
        cf = new ComboFilterer(this, comboFilter);
        queryFilterers.add(cf);// the objects himself is a query filterer
        resultSetFilterers.add(cf);
        filterers.put(cf, true);
    }

    private void createDialog() {
        dialog = new JDialog(App.get());
        dialog.setLayout(null);
        dialog.setTitle(Messages.getString("FilterManager.Title")); //$NON-NLS-1$
        dialog.setBounds(0, 0, 680, 450);

        expression.setLineWrap(true);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        expression.setToolTipText(Messages.getString("FilterManager.Expression.Tip")); //$NON-NLS-1$
        butNew.setToolTipText(Messages.getString("FilterManager.New.Tip")); //$NON-NLS-1$
        butSave.setToolTipText(Messages.getString("FilterManager.Save.Tip")); //$NON-NLS-1$
        butDelete.setToolTipText(Messages.getString("FilterManager.Del.Tip")); //$NON-NLS-1$

        Dimension butSize = new Dimension(85, 30);
        butNew.setPreferredSize(butSize);
        butSave.setPreferredSize(butSize);
        butDelete.setPreferredSize(butSize);

        JPanel left = new JPanel(new BorderLayout(2, 2));
        left.add(labFilters, BorderLayout.NORTH);
        left.add(scrollList, BorderLayout.CENTER);
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        leftButtons.add(butDelete);
        left.add(leftButtons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(250, 4000));

        JPanel right = new JPanel(new BorderLayout(2, 2));
        right.add(labExpr, BorderLayout.NORTH);
        right.add(scrollExpression, BorderLayout.CENTER);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightButtons.add(butSave);
        rightButtons.add(butNew);
        right.add(rightButtons, BorderLayout.SOUTH);

        JPanel main = new JPanel(new BorderLayout(8, 2));
        main.add(left, BorderLayout.WEST);
        main.add(right, BorderLayout.CENTER);
        dialog.setContentPane(main);

        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        populateList();

        list.addListSelectionListener(this);
        butSave.addActionListener(this);
        butNew.addActionListener(this);
        butDelete.addActionListener(this);

    }

    public void setVisible(boolean visible) {
        if (dialog == null) {
            createDialog();
        }
        dialog.setVisible(true);
        dialog.setLocationRelativeTo(null);
    }

    public String getFilterExpression(String filter) {
        return filters.getProperty(localizationMap.get(filter));
    }

    public boolean isUpdatingFilter() {
        return updatingCombo;
    }

    public void setUpdatingFilter(boolean updating) {
        updatingCombo = updating;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == butNew) {
            String newLabel = JOptionPane.showInputDialog(dialog, Messages.getString("FilterManager.NewName"), //$NON-NLS-1$
                    list.getSelectedValue());
            if (newLabel != null && !(newLabel = newLabel.trim()).isEmpty() && !listModel.contains(newLabel)) {
                String filter = localizationMap.getOrDefault(newLabel, newLabel);
                filters.put(filter, expression.getText());
            }
        }

        String filter = list.getSelectedValue();
        filter = localizationMap.getOrDefault(filter, filter);
        if (e.getSource() == butSave && filter != null) {
            filters.put(filter, expression.getText());
        }
        if (e.getSource() == butDelete && filter != null) {
            filters.remove(filter);
        }

        populateList();
        updateFilter();
        try {
            filters.store(userFilters);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        String filter = list.getSelectedValue();
        if (filter != null) {
            expression.setText(getFilterExpression(filter));
        }

    }

    public List<IQueryFilterer> getQueryFilterers() {
        return queryFilterers;
    }

    public List<IResultSetFilterer> getResultSetFilterers() {
        return resultSetFilterers;
    }

    public void addQueryFilterer(IQueryFilterer qf) {
        queryFilterers.add(qf);
        filterers.put(qf, false);
    }

    public void addResultSetFilterer(IResultSetFilterer rsf) {
        resultSetFilterers.add(rsf);
        filterers.put(rsf, false);
    }

    public Set<IFilterer> getFilterers() {
        return filterers.keySet();
    }

    public void notifyFilterChange() {
        App.get().setTableDefaultColor(!TableHeaderFilterManager.get().hasFiltersApplied());
    }

    public boolean isFiltererEnabled(IFilterer t) {
        Boolean result = filterers.get(t);
        return result != null && result;
    }

    public MultiSearchResult applyFilter(IResultSetFilter rsFilter, MultiSearchResult input) {
        MultiSearchResult result;
        try {
            result = (MultiSearchResult) rsFilter.filterResult(input);
            if (useCachedBitmaps) {
                cachedFilterBitsets.put(rsFilter, result.getCasesBitSets((IPEDMultiSource) input.getIPEDSource()));
            }

            return result;
        } catch (ParseException | QueryNodeException | IOException e) {
            e.printStackTrace();
            return input;
        }
    }

    public void setFilterEnabled(IFilterer t, boolean selected) {
        filterers.put(t, selected);
        if (selected) {
            t.fireActionListener(new ActionEvent(t, IFilterer.ENABLE_FILTER_EVENT, "Enable"));
            this.actionPerformed(new ActionEvent(t, IFilterer.ENABLE_FILTER_EVENT, "Enable"));
        } else {
            t.fireActionListener(new ActionEvent(t, IFilterer.DISABLE_FILTER_EVENT, "Disable"));
            this.actionPerformed(new ActionEvent(t, IFilterer.DISABLE_FILTER_EVENT, "Enable"));
        }
    }

    public MultiSearchResult applyExcludeFilter(RoaringBitmap[] resultBitSet, MultiSearchResult input) {
        LinkedHashSet<IItemId> ids = new LinkedHashSet<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        float[] primitiveScores;

        if (resultBitSet != null) {
            int i = 0;
            while (i < input.getLength()) {
                IItemId itemId = input.getItem(i);
                if (!resultBitSet[itemId.getSourceId()].contains(itemId.getId())) {
                    ids.add(itemId);
                    scores.add(input.getScore(i));
                }
                i++;
            }

            primitiveScores = new float[scores.size()];
            i = 0;
            for (Float f : scores) {
                primitiveScores[i++] = f;
            }
        } else {
            primitiveScores = new float[0];
        }

        MultiSearchResult result = new MultiSearchResult(ids.toArray(new IItemId[0]), primitiveScores);

        return result;
    }

    public MultiSearchResult applyFilter(RoaringBitmap[] resultBitSet, MultiSearchResult input) {
        LinkedHashSet<IItemId> ids = new LinkedHashSet<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        float[] primitiveScores;

        if (resultBitSet != null) {
            int i = 0;
            while (i < input.getLength()) {
                IItemId itemId = input.getItem(i);
                if (resultBitSet[itemId.getSourceId()].contains(itemId.getId())) {
                    ids.add(itemId);
                    scores.add(input.getScore(i));
                }
                i++;
            }

            primitiveScores = new float[scores.size()];
            i = 0;
            for (Float f : scores) {
                primitiveScores[i++] = f;
            }
        } else {
            primitiveScores = new float[0];
        }

        MultiSearchResult result = new MultiSearchResult(ids.toArray(new IItemId[0]), primitiveScores);

        return result;
    }

    public RoaringBitmap[] getCachedBitmaps(IResultSetFilter rsFilter) {
        if (!useCachedBitmaps) {
            return null;
        }
        return cachedFilterBitsets.get(rsFilter);
    }
}

