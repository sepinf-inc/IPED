package br.gov.pf.labld.graph.desktop;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

import org.kharon.GraphPane;
import org.kharon.GraphPreviewPane;
import org.neo4j.graphdb.Node;

import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.NodeQueryListener;
import dpf.sp.gpinf.indexer.desktop.Messages;

public class GraphSidePanel extends JPanel {

    private static final long serialVersionUID = -4029945712353617544L;

    private JPanel containerPanel;

    private NodePropertiesDataModel nodePropertiesDataModel;
    private JTable propertiesTable;

    private boolean uiBuilt = false;

    private JScrollPane propertiesScrollPane;

    private GraphPreviewPane graphPreviewPane;

    public GraphSidePanel(GraphPane graphPane) {
        super();
        this.setLayout(new BorderLayout());
        init(graphPane);
    }

    private void init(GraphPane graphPane) {
        containerPanel = new JPanel();
        containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.PAGE_AXIS));

        nodePropertiesDataModel = new NodePropertiesDataModel();

        propertiesTable = new JTable(nodePropertiesDataModel);
        propertiesTable.setCellSelectionEnabled(true);
        propertiesTable.setFillsViewportHeight(true);

        propertiesScrollPane = new JScrollPane(propertiesTable);

        add(containerPanel, BorderLayout.CENTER);

        graphPreviewPane = new GraphPreviewPane(graphPane);
        graphPreviewPane.setPreferredSize(new Dimension(50, 180));
        add(graphPreviewPane, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, containerPanel, graphPreviewPane);
        splitPane.setOneTouchExpandable(false);
        splitPane.setResizeWeight(0.85d);

        add(splitPane, BorderLayout.CENTER);
    }

    private synchronized void addAll() {
        if (!uiBuilt) {
            containerPanel.add(propertiesScrollPane);
            this.uiBuilt = true;
            revalidate();
        }
    }

    public void loadNode(Long id) {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new LoadNodeWorker(id).execute();
    }

    private class LoadNodeWorker extends SwingWorker<Void, Void> implements NodeQueryListener {

        private Long id;

        public LoadNodeWorker(Long id) {
            super();
            this.id = id;
        }

        @Override
        public boolean nodeFound(Node node) {
            GraphSidePanel.this.nodePropertiesDataModel.read(node);
            return false;
        }

        @Override
        protected Void doInBackground() throws Exception {
            GraphService service = GraphServiceFactoryImpl.getInstance().getGraphService();
            service.getNodes(Arrays.asList(id), this);
            return null;
        }

        @Override
        protected void done() {
            addAll();
            GraphSidePanel.this.nodePropertiesDataModel.fireTableDataChanged();
            GraphSidePanel.this.propertiesTable.doLayout();
            GraphSidePanel.this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }

    }

    private static class NodePropertiesDataModel extends AbstractTableModel {

        private static final long serialVersionUID = 8799136971764084675L;

        private String[] headers = new String[] { Messages.getString("GraphAnalysis.Property"),
                Messages.getString("GraphAnalysis.Value") };

        private List<Object[]> results = new ArrayList<>();

        @Override
        public int getRowCount() {
            return results.size();
        }

        @Override
        public int getColumnCount() {
            return headers.length;
        }

        @Override
        public String getColumnName(int column) {
            return headers[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return results.get(rowIndex)[columnIndex];
        }

        public void read(Node node) {
            results.clear();

            addResult(Messages.getString("GraphAnalysis.Type"), getLabels(node));

            addResult("id", node.getId());

            Map<String, Object> properties = node.getAllProperties();
            TreeSet<String> keys = new TreeSet<>(properties.keySet());
            for (String key : keys) {
                Object value = properties.get(key);
                addResult(key, value);
            }
        }

        private String getLabels(Node node) {
            String labels = StreamSupport.stream(node.getLabels().spliterator(), false).map(l -> l.name())
                    .collect(Collectors.joining(","));
            return labels;
        }

        private void addResult(String key, Object value) {
            if (value != null) {
                value = asString(value);
            }
            results.add(new Object[] { key, value });

        }

        private Object asString(Object value) {

            if (value.getClass().isArray()) {
                // value = Arrays.stream((Object[]) value).map(t ->
                // t.toString()).collect(Collectors.joining(", "));
            }

            return value.toString();
        }

    }

    public GraphPreviewPane getGraphPreviewPane() {
        return graphPreviewPane;
    }

}
