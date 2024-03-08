package iped.app.ui.controls;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import iped.app.metadata.ValueCount;

public class IPEDSearchList<E> extends JPanel {

    protected JTextField txFilter;
    protected List<E> availableItems;
    protected Set<ValueCount> selected = new HashSet<ValueCount>();

    private JTable table;
    private List<ValueCount> data;
    private TableModel model;

    protected IPEDSearchList() {
        super(new BorderLayout());
    }

    private void adjustFirstColWidth() {
        table.getColumnModel().getColumn(0).setMaxWidth(18);
    }

    private class TableModel extends AbstractTableModel {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        private List<ValueCount> data;

        private TableModel(List<ValueCount> data) {
            this.data = data;
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Class<?> getColumnClass(int c) {
            if (c == 0) {
                return Boolean.class;
            }
            return ValueCount.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return selected.contains(data.get(rowIndex));
            } else {
                return data.get(rowIndex);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0 && aValue instanceof Boolean) {
                if ((Boolean) aValue) {
                    selected.add(data.get(rowIndex));
                } else {
                    selected.remove(data.get(rowIndex));
                }
            }
        }

    }

    private class FilteredList {

        private List<ValueCount> data = new ArrayList<>();

        public FilteredList(String text) {
            Predicate checkContains = new Predicate<E>() {
                @Override
                public boolean test(E t) {
                    if (!text.equals("")) {
                        return t.toString().contains(text);
                    } else {
                        return true;
                    }

                }
            };

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < availableItems.size(); i++) {
                        E o = availableItems.get(i);
                        if (checkContains.test(o)) {
                            synchronized (this) {
                                if (data == null) {
                                    return;// thread was canceled
                                }
                                data.add((ValueCount) o);
                            }
                        }
                    }
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (this) {
                                if (data != null) {
                                    TableModel filteredModel = new TableModel(data);
                                    table.setModel(filteredModel);
                                    adjustFirstColWidth();
                                    filteredModel.fireTableDataChanged();
                                }
                            }
                        }
                    });
                }
            };
            Thread thread = new Thread(r);
            thread.start();
        }

        public void cancel() {
            synchronized (this) {
                data = null;
            }
        }
    }

    public void createGUI(List<ValueCount> m) {
        data = m;
        model = new TableModel(data);
        table = new JTable(model);
        table.setTableHeader(null);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        adjustFirstColWidth();
        table.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    for (int row : table.getSelectedRows()) {
                        boolean checked = (Boolean) table.getValueAt(row, 0);
                        table.setValueAt(!checked, row, 0);
                    }
                    table.repaint();
                }
            }
        });

        txFilter = new JTextField();
        txFilter.addKeyListener(new KeyListener() {
            private IPEDSearchList<E>.FilteredList fl;

            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                String text = txFilter.getText();
                if (text.length() > 0) {
                    if (fl != null) {
                        fl.cancel();
                    }
                    fl = new FilteredList(text);
                } else {
                    table.setModel(model);
                    adjustFirstColWidth();
                    model.fireTableDataChanged();
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }
        });

        this.add(txFilter, BorderLayout.NORTH);

        JScrollPane listScrollPanel = new JScrollPane(table);
        listScrollPanel.setBackground(this.getBackground());
        listScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScrollPanel.setAutoscrolls(true);
        this.add(listScrollPanel, BorderLayout.CENTER);
    }

}
