package iped.app.ui.controls.table;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import iped.app.metadata.MetadataSearch;
import iped.app.metadata.ValueCount;
import iped.app.ui.App;
import iped.app.ui.ResultTableModel;
import iped.app.ui.TableHeaderFilterManager;
import iped.app.ui.controls.IPEDSearchList;
import iped.app.ui.controls.ResizablePopupMenu;
import iped.app.ui.popups.FieldValuePopupMenu;

public class MetadataValueSearchList extends IPEDSearchList<ValueCount> {
    private static final long serialVersionUID = 7433141453098578420L;

    private static HashMap<String, Set<ValueCount>> lastFilteredValuesPerField = new HashMap<>();

    private MetadataSearch metadataSearch;
    private JPopupMenu menu = new ResizablePopupMenu();
    private JButton btFilter;
    private TableHeaderFilterManager fm;
    private JButton btClear;

    public MetadataValueSearchList(String field) {
        try {
            fm = TableHeaderFilterManager.get();

            Set<ValueCount> values = lastFilteredValuesPerField.get(field);
            if (values != null) {
                selected = values;
            }
            lastFilteredValuesPerField.put(field, selected);

            metadataSearch = TableHeaderFilterManager.get().getMetadataSearch(field);

            Dimension minDim = new Dimension(150, 18);
            JMenuItem emptyMenu = new JCheckBoxMenuItem(FieldValuePopupMenu.EMPTY_STR);
            emptyMenu.setMinimumSize(minDim);
            emptyMenu.setPreferredSize(minDim);
            emptyMenu.setSelected(fm.getContainsEmptyFilter(field));
            emptyMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (emptyMenu.isSelected()) {
                        fm.addEmptyFilter(field);
                    } else {
                        fm.removeEmptyFilter(field);
                    }
                    menu.setVisible(false);
                    App.get().getAppListener().updateFileListing();
                    App.get().setDockablesColors();
                }
            });
            menu.add(emptyMenu);
            JMenuItem nonEmptyMenu = new JCheckBoxMenuItem(FieldValuePopupMenu.NON_EMPTY_STR);
            nonEmptyMenu.setMinimumSize(minDim);
            nonEmptyMenu.setPreferredSize(minDim);
            nonEmptyMenu.setSelected(fm.getContainsNonEmptyFilter(field));
            nonEmptyMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (nonEmptyMenu.isSelected()) {
                        fm.addNonEmptyFilter(field);
                    } else {
                        fm.removeNonEmptyFilter(field);
                    }
                    menu.setVisible(false);
                    App.get().getAppListener().updateFileListing();
                    App.get().setDockablesColors();
                }
            });
            menu.add(nonEmptyMenu);
            menu.add(new JSeparator());

            menu.add(this);
            setOpaque(false);

            metadataSearch.setIpedResult(App.get().getResults());

            availableItems = metadataSearch.countValues(field);

            createGUI(availableItems);

            JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 1));
            panelButtons.setOpaque(false);
            add(panelButtons, BorderLayout.SOUTH);

            Dimension butDim = new Dimension(72, 26);

            btFilter = new JButton(FieldValuePopupMenu.FILTER);
            btFilter.setPreferredSize(butDim);
            panelButtons.add(btFilter);

            btFilter.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (selected.size() == 0) {
                        fm.removeFilter(field);
                    } else {
                        HashSet<ValueCount> selectedClone = new HashSet<ValueCount>();
                        selectedClone.addAll(selected);
                        fm.addFilter(field, selectedClone);
                    }
                    menu.setVisible(false);
                    App.get().getAppListener().updateFileListing();
                    App.get().setDockablesColors();
                }
            });

            btClear = new JButton(FieldValuePopupMenu.CLEAR);
            btClear.setPreferredSize(butDim);
            panelButtons.add(btClear);

            btClear.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    selected.clear();
                    btFilter.doClick();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void show(JTableHeader header, int column) {
        TableColumnModel cm = header.getColumnModel();
        int x = 0;
        for (int i = 0; i < column; i++) {
            x += cm.getColumn(i).getWidth();
        }

        int colModel = header.getTable().convertColumnIndexToModel(column);
        String field = ((ResultTableModel) header.getTable().getModel()).getColumnFieldName(colModel);

        MetadataValueSearchList m = new MetadataValueSearchList(field);

        int colWidth = header.getColumnModel().getColumn(column).getWidth();
        colWidth = Math.min(Math.max(colWidth, 160), 1000);
        m.setMinimumSize(new Dimension(150, 120));
        m.setPreferredSize(new Dimension(colWidth, 250));
        m.menu.show(header, x, header.getY() + header.getHeight());
    }

    public static void install(JTable resultsTable) {
        App.get().getFilterManager().addResultSetFilterer(TableHeaderFilterManager.get());
        App.get().getFilterManager().addQueryFilterer(TableHeaderFilterManager.get());
    }
}
