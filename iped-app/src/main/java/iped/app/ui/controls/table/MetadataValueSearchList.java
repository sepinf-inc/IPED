package iped.app.ui.controls.table;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import iped.app.metadata.MetadataSearch;
import iped.app.metadata.ValueCount;
import iped.app.ui.App;
import iped.app.ui.ResultTableModel;
import iped.app.ui.TableHeaderFilterManager;
import iped.app.ui.controls.IPEDSearchList;
import iped.app.ui.popups.FieldValuePopupMenu;

public class MetadataValueSearchList extends IPEDSearchList<ValueCount> {

    private static HashMap<String, Set<ValueCount>> lastFilteredValuesPerField = new HashMap<>();

    private MetadataSearch metadataSearch;
    private JPopupMenu menu = new JPopupMenu();
    private JButton btFiltrar;
    private TableHeaderFilterManager fm;
    private JButton btClear;

    public MetadataValueSearchList(String field) {
        try {
            fm = TableHeaderFilterManager.get();

            Set<ValueCount> values = lastFilteredValuesPerField.get(field);
            if (values != null) {
                this.selected = values;
            }
            lastFilteredValuesPerField.put(field, this.selected);

            metadataSearch = TableHeaderFilterManager.get().getMetadataSearch(field);

            JMenuItem emptyMenu = new JCheckBoxMenuItem(FieldValuePopupMenu.EMPTY_STR);
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
            this.setBackground(menu.getBackground());

            metadataSearch.setIpedResult(App.get().getResults());

            this.availableItems = metadataSearch.countValues(field);

            createGUI(availableItems);

            JPanel panelButons = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 1));
            this.add(panelButons, BorderLayout.SOUTH);

            btFiltrar = new JButton(FieldValuePopupMenu.FILTER);
            panelButons.add(btFiltrar);

            btFiltrar.addActionListener(new ActionListener() {
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
            panelButons.add(btClear);

            btClear.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    selected.clear();
                    btFiltrar.doClick();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void show(Component invoker, int colIndex, int x, int y) {
        JTableHeader header = (JTableHeader) invoker;
        if (colIndex >= 4) {
            int colwidth = header.getColumnModel().getColumn(colIndex).getWidth();
            colwidth = Math.min(Math.max(colwidth, 150), 1000);
            this.setPreferredSize(new Dimension(colwidth, 250));
            menu.show(invoker, x, y);
        }
    }

    public static void install(JTable resultsTable) {
        JTableHeader header = resultsTable.getTableHeader();
        TableCellRenderer dr = header.getDefaultRenderer();
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            Color originalColor = this.getForeground();

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component result = dr.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                column = resultsTable.convertColumnIndexToModel(column);
                String field = ((ResultTableModel) resultsTable.getModel()).getColumnFieldName(column);
                if (TableHeaderFilterManager.get().isFieldFiltered(field)) {
                    result.setForeground(Color.red);
                } else {
                    result.setForeground(originalColor);
                }
                return result;
            }

        });
        resultsTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    TableColumnModel cm = header.getColumnModel();
                    int pos = 0;
                    int ci = cm.getColumnIndexAtX(e.getX());
                    for (int i = 0; i < ci; i++) {
                        pos += cm.getColumn(i).getWidth();
                    }

                    int colModel = resultsTable.convertColumnIndexToModel(ci);
                    String field = ((ResultTableModel) resultsTable.getModel()).getColumnFieldName(colModel);

                    MetadataValueSearchList m = new MetadataValueSearchList(field);
                    m.show(header, ci, pos, header.getY() + header.getHeight());
                }
            }
        });
        App.get().getFilterManager().addResultSetFilterer(TableHeaderFilterManager.get());
        App.get().getFilterManager().addQueryFilterer(TableHeaderFilterManager.get());
    }
}