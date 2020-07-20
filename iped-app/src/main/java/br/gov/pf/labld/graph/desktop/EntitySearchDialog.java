package br.gov.pf.labld.graph.desktop;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

import org.neo4j.graphdb.Node;

import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.NodeQueryListener;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.Messages;

public class EntitySearchDialog extends JDialog implements KeyListener, MouseListener {

    private static final long serialVersionUID = -1298461480306818611L;

    private AppGraphAnalytics app;

    private JTextField searchField;
    private JButton searchButton;
    private JTable table;
    private GraphSearchDataModel dataModel;

    public EntitySearchDialog(AppGraphAnalytics app) {
        super(App.get());
        this.app = app;
        this.setLayout(new GridBagLayout());
        createGUI();
    }

    private void createGUI() {
        setTitle(Messages.getString("GraphAnalysis.SearchEntities"));
        setPreferredSize(new Dimension(500, 500));

        searchField = new JTextField();
        searchField.addKeyListener(this);

        searchButton = new JButton(new SearchAction());
        searchButton.setEnabled(false);
        searchButton.setText(Messages.getString("GraphAnalysis.Search"));

        dataModel = new GraphSearchDataModel();

        table = new JTable(dataModel);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.addMouseListener(this);

        table.getColumnModel().getColumn(0).setMaxWidth(40);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.9;
        c.weighty = 0.05;
        c.gridx = 0;
        c.gridy = 0;
        add(searchField, c);

        c.weightx = 0.1;
        c.gridx = 1;
        c.gridy = 0;
        add(searchButton, c);

        c.weightx = 1;
        c.weighty = 0.90;
        c.gridwidth = 2;
        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.BOTH;

        add(new JScrollPane(table), c);

        JButton addButton = new JButton(new AddAction());
        addButton.setText(Messages.getString("GraphAnalysis.Add"));

        JButton cancelButton = new JButton(new CloseDialogAction(this));
        cancelButton.setText(Messages.getString("GraphAnalysis.Cancel"));

        c.weightx = 0.5;
        c.weighty = 0.05;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(addButton, c);

        c.gridx = 1;
        add(cancelButton, c);

        setModalityType(ModalityType.APPLICATION_MODAL);

        pack();
        setLocationRelativeTo(App.get());

        getRootPane().registerKeyboardAction(new CloseDialogAction(this), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    private class GraphSearchWorker extends SwingWorker<Void, Void> implements NodeQueryListener {

        private String text;

        public GraphSearchWorker(String text) {
            super();
            this.text = text;
        }

        @Override
        public boolean nodeFound(Node node) {
            Set<Entry<String, Object>> entrySet = node.getAllProperties().entrySet();
            for (Entry<String, Object> entry : entrySet) {
                Object value = entry.getValue();

                if (value != null) {
                    String upperCase = value.toString().toUpperCase();
                    boolean contains = upperCase.contains(text.toUpperCase());
                    if (contains) {
                        Long id = node.getId();
                        String type = node.getLabels().iterator().next().name();
                        String property = entry.getKey();
                        EntitySearchDialog.this.dataModel.addResult(id, type, property, value.toString());
                    }
                }
            }

            return true;
        }

        @Override
        protected Void doInBackground() throws Exception {
            GraphService service = GraphServiceFactoryImpl.getInstance().getGraphService();
            EntitySearchDialog.this.dataModel.clear();
            service.search(text, this);
            return null;
        }

        @Override
        protected void done() {
            EntitySearchDialog.this.dataModel.fireTableDataChanged();
            EntitySearchDialog.this.table.doLayout();
            EntitySearchDialog.this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }

    }

    private class AddAction extends AbstractAction {

        private static final long serialVersionUID = -7914177162481965540L;

        @Override
        public void actionPerformed(ActionEvent e) {
            int[] rows = EntitySearchDialog.this.table.getSelectedRows();
            Set<Long> ids = new HashSet<>(rows.length);
            for (int row : rows) {
                Long id = (Long) EntitySearchDialog.this.table.getValueAt(row, 0);
                ids.add(id);
            }
            app.addNodesToGraph(ids);
            close();
        }

    }

    private class SearchAction extends AbstractAction {

        private static final long serialVersionUID = 8261799049294542965L;

        @Override
        public void actionPerformed(ActionEvent e) {
            search();
        }

    }

    private static class GraphSearchDataModel extends AbstractTableModel {

        private static final long serialVersionUID = 4510338269383438230L;

        private String[] headers = new String[] { Messages.getString("GraphAnalysis.Id"),
                Messages.getString("GraphAnalysis.Type"), Messages.getString("GraphAnalysis.Property"),
                Messages.getString("GraphAnalysis.Value") };

        private List<Object[]> results = new ArrayList<>();

        @Override
        public int getRowCount() {
            return results.size();
        }

        public void clear() {
            results.clear();
        }

        @Override
        public int getColumnCount() {
            return headers.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return results.get(rowIndex)[columnIndex];
        }

        @Override
        public String getColumnName(int column) {
            return headers[column];
        }

        public void addResult(Long id, String type, String property, String value) {
            this.results.add(new Object[] { id, type, property, value });
        }

    }

    private void close() {
        this.setVisible(false);
        this.dispose();
    }

    @Override
    public void keyTyped(KeyEvent evt) {
    }

    @Override
    public void keyPressed(KeyEvent evt) {

    }

    @Override
    public void keyReleased(KeyEvent evt) {
        String text = this.searchField.getText();
        int length = text.length();
        boolean enabled = length >= 3;
        this.searchButton.setEnabled(enabled);
        if (enabled && evt.getKeyCode() == KeyEvent.VK_ENTER) {
            search();
        }
    }

    private void search() {
        String text = EntitySearchDialog.this.searchField.getText();
        EntitySearchDialog.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new GraphSearchWorker(text).execute();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Point point = e.getPoint();
        int row = table.rowAtPoint(point);
        if (e.getClickCount() == 2 && table.getSelectedRow() != -1 && row != -1) {
            Long id = (Long) table.getValueAt(row, 0);
            app.addNodesToGraph(Arrays.asList(id));
            close();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

}
