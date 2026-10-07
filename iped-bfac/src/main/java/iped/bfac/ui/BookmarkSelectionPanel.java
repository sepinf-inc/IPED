package iped.bfac.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import iped.data.IMultiBookmarks;

/**
 * Panel for selecting bookmarks via checkboxes, with colored bookmark icons
 * matching the Create Report dialog UX.
 */
public class BookmarkSelectionPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Object[] TABLE_HEADER = { Boolean.FALSE, "" };

    private final JTable table;
    private final JCheckBox selectAllCheckBox;
    private IMultiBookmarks multiBookmarks;
    private boolean selectAllListenerAdded;
    private boolean headerListenerAdded;

    public BookmarkSelectionPanel(String[] bookmarks) {
        super(new BorderLayout());
        selectAllCheckBox = new JCheckBox();
        table = createTable(buildTableData(bookmarks));
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(400, 120));
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setMultiBookmarks(IMultiBookmarks multiBookmarks) {
        this.multiBookmarks = multiBookmarks;
        table.repaint();
    }

    public void setBookmarks(Set<String> bookmarks) {
        String[] labels = bookmarks.toArray(new String[0]);
        Arrays.sort(labels, Collator.getInstance());
        table.setModel(new BookmarkTableModel(buildTableData(labels), TABLE_HEADER));
        configureTable(table);
        selectAllCheckBox.setSelected(false);
    }

    public List<String> getCheckedBookmarks() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < table.getRowCount(); i++) {
            Boolean checked = (Boolean) table.getValueAt(i, 0);
            if (Boolean.TRUE.equals(checked)) {
                result.add((String) table.getValueAt(i, 1));
            }
        }
        return result;
    }

    public void clearSelection() {
        for (int i = 0; i < table.getRowCount(); i++) {
            table.setValueAt(Boolean.FALSE, i, 0);
        }
        selectAllCheckBox.setSelected(false);
    }

    private Object[][] buildTableData(String[] labels) {
        Object[][] data = new Object[labels.length][];
        for (int i = 0; i < labels.length; i++) {
            data[i] = new Object[] { Boolean.FALSE, labels[i] };
        }
        return data;
    }

    private JTable createTable(Object[][] data) {
        JTable bookmarkTable = new JTable(new BookmarkTableModel(data, TABLE_HEADER));
        configureTable(bookmarkTable);
        return bookmarkTable;
    }

    private void configureTable(JTable bookmarkTable) {
        bookmarkTable.getColumnModel().getColumn(0).setMaxWidth(24);
        bookmarkTable.setRowHeight(BookmarkColorIcon.SIZE + 2);

        ((JComponent) bookmarkTable.getDefaultRenderer(Boolean.class)).setOpaque(true);

        bookmarkTable.getColumnModel().getColumn(0).setHeaderRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                    boolean hasFocus, int row, int col) {
                JTableHeader header = tbl.getTableHeader();
                if (!headerListenerAdded) {
                    header.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (header.columnAtPoint(e.getPoint()) == 0) {
                                selectAllCheckBox.doClick();
                            }
                        }
                    });
                    headerListenerAdded = true;
                }
                return selectAllCheckBox;
            }
        });

        bookmarkTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                String name = value == null ? null : value.toString();
                setIcon(BookmarkColorIcon.getIcon(getBookmarkColorForName(name)));
                return this;
            }
        });

        if (!selectAllListenerAdded) {
            selectAllCheckBox.addActionListener(e -> {
                boolean selected = selectAllCheckBox.isSelected();
                for (int i = 0; i < table.getRowCount(); i++) {
                    table.setValueAt(selected, i, 0);
                }
            });
            selectAllListenerAdded = true;
        }
    }

    private Color getBookmarkColorForName(String name) {
        if (multiBookmarks != null && name != null) {
            return multiBookmarks.getBookmarkColor(name);
        }
        return null;
    }

    private static class BookmarkTableModel extends DefaultTableModel {
        private static final long serialVersionUID = 1L;

        BookmarkTableModel(Object[][] data, Object[] columnNames) {
            super(data, columnNames);
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col != 1) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int col) {
            return col != 1;
        }
    }
}
