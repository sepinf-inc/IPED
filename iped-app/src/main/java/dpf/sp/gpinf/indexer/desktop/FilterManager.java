package dpf.sp.gpinf.indexer.desktop;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public class FilterManager implements ActionListener, ListSelectionListener {

    private static File userFilters = getGlobalFilterFile(); // $NON-NLS-1$ //$NON-NLS-2$
    private File defaultFilter;

    private UTF8Properties filters = new UTF8Properties();
    private HashMap<String, String> localizationMap = new HashMap<>();
    private volatile boolean updatingCombo = false;
    private JComboBox<String> comboFilter;

    JDialog dialog;

    JLabel msg = new JLabel(Messages.getString("FilterManager.Filters")); //$NON-NLS-1$
    JLabel texto = new JLabel(Messages.getString("FilterManager.Expresion")); //$NON-NLS-1$

    JButton save = new JButton(Messages.getString("FilterManager.Save")); //$NON-NLS-1$
    JButton rename = new JButton(Messages.getString("FilterManager.Rename")); //$NON-NLS-1$
    JButton novo = new JButton(Messages.getString("FilterManager.New")); //$NON-NLS-1$
    JButton delete = new JButton(Messages.getString("FilterManager.Delete")); //$NON-NLS-1$

    DefaultListModel<String> listModel = new DefaultListModel<String>();
    JList<String> list = new JList<String>(listModel);
    JScrollPane scrollList = new JScrollPane(list);

    JTextArea expression = new JTextArea();
    JScrollPane scrollExpression = new JScrollPane(expression);
    Color defaultColor;

    private static final File getGlobalFilterFile() {
        String name = "ipedFilters"; //$NON-NLS-1$
        String locale = System.getProperty(iped3.util.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
        if (locale != null && !locale.equals("pt-BR")) //$NON-NLS-1$
            name += "-" + locale; //$NON-NLS-1$
        name += ".txt"; //$NON-NLS-1$
        return new File(System.getProperty("user.home") + "/.indexador/" + name); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void loadFilters() {
        try {
            if (userFilters.exists()) {
                filters.load(userFilters);
            }

            if (defaultFilter == null)
                defaultFilter = new File(App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir(),
                        "conf/DefaultFilters.txt"); //$NON-NLS-1$

            filters.load(defaultFilter);

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

        Object[] filternames = filters.keySet().toArray();
        Arrays.sort(filternames, Collator.getInstance());
        for (Object filter : filternames) {
            String localizedName = MessagesFilter.get((String) filter, (String) filter);
            localizationMap.put(localizedName, (String) filter);
            comboFilter.addItem(localizedName);
        }

        if (prevSelected != null) {
            if (prevSelected == App.FILTRO_TODOS || prevSelected == App.FILTRO_SELECTED
                    || filters.containsKey(prevSelected)) {
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
        Object[] filternames = filters.keySet().toArray();
        Arrays.sort(filternames, Collator.getInstance());
        listModel.clear();
        for (Object filter : filternames) {
            String localizedName = MessagesFilter.get((String) filter, (String) filter);
            localizationMap.put(localizedName, (String) filter);
            listModel.addElement(localizedName);
        }
        list.setSelectedValue(name, true);
    }

    public FilterManager(JComboBox<String> comboFilter) {
        this.comboFilter = comboFilter;
        defaultColor = comboFilter.getBackground();
    }

    private void createDialog() {
        dialog = new JDialog();
        dialog.setLayout(null);
        dialog.setTitle(Messages.getString("FilterManager.Title")); //$NON-NLS-1$
        dialog.setBounds(0, 0, 680, 350);
        dialog.setAlwaysOnTop(true);

        expression.setLineWrap(true);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        expression.setToolTipText(Messages.getString("FilterManager.Expression.Tip")); //$NON-NLS-1$
        novo.setToolTipText(Messages.getString("FilterManager.New.Tip")); //$NON-NLS-1$
        save.setToolTipText(Messages.getString("FilterManager.Save.Tip")); //$NON-NLS-1$
        delete.setToolTipText(Messages.getString("FilterManager.Del.Tip")); //$NON-NLS-1$

        msg.setBounds(20, 20, 200, 20);
        texto.setBounds(300, 20, 200, 20);
        scrollList.setBounds(20, 40, 260, 230);
        scrollExpression.setBounds(300, 40, 340, 230);
        novo.setBounds(550, 270, 90, 30);
        save.setBounds(450, 270, 90, 30);
        delete.setBounds(190, 270, 90, 30);

        populateList();

        dialog.getContentPane().add(msg);
        dialog.getContentPane().add(texto);
        dialog.getContentPane().add(scrollList);
        dialog.getContentPane().add(scrollExpression);
        dialog.getContentPane().add(novo);
        dialog.getContentPane().add(save);
        dialog.getContentPane().add(delete);

        list.addListSelectionListener(this);
        save.addActionListener(this);
        novo.addActionListener(this);
        delete.addActionListener(this);

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
        if (e.getSource() == novo) {
            String newLabel = JOptionPane.showInputDialog(dialog, Messages.getString("FilterManager.NewName"), //$NON-NLS-1$
                    list.getSelectedValue());
            if (newLabel != null && !(newLabel = newLabel.trim()).isEmpty() && !listModel.contains(newLabel)) {
                String filter = localizationMap.getOrDefault(newLabel, newLabel);
                filters.put(filter, expression.getText());
            }
        }

        String filter = list.getSelectedValue();
        filter = localizationMap.getOrDefault(filter, filter);
        if (e.getSource() == save && filter != null) {
            filters.put(filter, expression.getText());
        }
        if (e.getSource() == delete && filter != null) {
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

}
