package br.gov.pf.labld.graph.desktop;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Relationship;

import br.gov.pf.labld.graph.FreeQueryListener;
import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.Messages;

public class AdvancedSearchDialog extends JDialog {

    private static final long serialVersionUID = 6125670943354716824L;

    private AppGraphAnalytics app;

    private AdvancedSearchDataModel dataModel;

    private JTextArea queryTextArea;

    private JTable resultsTable;

    private JLabel statusLabel;

    private boolean open;

    private JButton addButton;

    private SwingWorker<?, ?> currentWorker = null;

    public AdvancedSearchDialog(AppGraphAnalytics app) {
        this.app = app;
        createGUI();
    }

    public AdvancedSearchDialog() {
        super();
        createGUI();
    }

    private void createGUI() {
        setTitle(Messages.get("GraphAnalysis.AdvancedSearch"));
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.PAGE_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(container);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createQueryPanel(), createResultsPanel());
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(0.7d);
        container.add(splitPane);

        setModalityType(ModalityType.DOCUMENT_MODAL);

        if (app != null) {
            setLocationRelativeTo(App.get());
        }
        pack();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(new CloseDialogAction(this), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    @Override
    public void setVisible(boolean b) {
        this.open = b;
        super.setVisible(b);
    }

    private JPanel createResultsPanel() {
        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.PAGE_AXIS));

        this.dataModel = new AdvancedSearchDataModel();

        this.resultsTable = new JTable(dataModel);
        resultsTable.setFillsViewportHeight(true);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        resultsTable.doLayout();
        JScrollPane resultsScroll = new JScrollPane(resultsTable);
        resultsPanel.add(resultsScroll);
        resultsPanel.add(createResultsButtonsPanel());
        return resultsPanel;
    }

    private JPanel createQueryPanel() {
        JPanel queryPanel = new JPanel();
        queryPanel.setLayout(new BoxLayout(queryPanel, BoxLayout.PAGE_AXIS));

        this.queryTextArea = new JTextArea();
        this.queryTextArea.getActionMap().put("Execute", new ExecuteAction());
        KeyStroke controlS = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK);
        this.queryTextArea.getInputMap().put(controlS, "Execute");

        JScrollPane queryScroll = new JScrollPane(queryTextArea);

        // queryScroll.setPreferredSize(new Dimension(500, 250));
        queryPanel.add(queryScroll);
        queryPanel.add(createQueryButtonsPanel());
        return queryPanel;
    }

    private JPanel createQueryButtonsPanel() {
        JButton cancelButton = new JButton(new CancelAction());
        cancelButton.setText(Messages.getString("GraphAnalysis.Cancel"));

        JButton executeButton = new JButton(new ExecuteAction());
        executeButton.setText(Messages.getString("GraphAnalysis.Execute"));

        JButton exportButton = new JButton(new ExportQueryAction());
        exportButton.setText(Messages.getString("GraphAnalysis.Export"));

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buttonsPanel.add(Box.createHorizontalGlue());
        buttonsPanel.add(cancelButton);
        buttonsPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        buttonsPanel.add(exportButton);
        buttonsPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        buttonsPanel.add(executeButton);
        return buttonsPanel;
    }

    private JPanel createResultsButtonsPanel() {
        addButton = new JButton(new AddToGraphAction());
        addButton.setText(Messages.getString("GraphAnalysis.AddToAnalysis"));

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buttonsPanel.add(Box.createHorizontalGlue());

        this.statusLabel = new JLabel("");
        buttonsPanel.add(statusLabel);
        buttonsPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        buttonsPanel.add(addButton);
        return buttonsPanel;
    }

    public boolean isOpen() {
        return open;
    }

    private void cancel() {
        if (currentWorker != null) {
            currentWorker.cancel(true);
        }
    }

    private class CancelAction extends AbstractAction {

        private static final long serialVersionUID = 8868786294434591213L;

        @Override
        public void actionPerformed(ActionEvent e) {
            cancel();
        }

    }

    private class ExecuteAction extends AbstractAction {

        private static final long serialVersionUID = 8093130024947391899L;

        @Override
        public void actionPerformed(ActionEvent e) {
            cancel();
            String query = queryTextArea.getText();
            currentWorker = new AdvancedSearchWorker(query);
            currentWorker.execute();
        }

    }

    private class ExportQueryAction extends AbstractAction {

        private static final long serialVersionUID = -6089668817373131233L;

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            int option = fileChooser.showSaveDialog(App.get());
            if (option == JFileChooser.APPROVE_OPTION) {
                File output = fileChooser.getSelectedFile();
                if (!output.getName().endsWith(".csv")) {
                    output = new File(output.getParentFile(), output.getName() + ".csv");
                }

                cancel();
                String query = queryTextArea.getText();
                currentWorker = new ExportQueryWorker(query, output);
                currentWorker.execute();
            }
        }

    }

    private class ExportQueryWorker extends SwingWorker<Void, Void> implements FreeQueryListener {

        private String query;
        private File output;

        private Writer out;

        private List<String> columns;

        public ExportQueryWorker(String query, File output) {
            super();
            this.query = query;
            this.output = output;
        }

        @Override
        protected Void doInBackground() throws Exception {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            setEnabled(false);

            open();

            GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
            try {
                graphService.advancedSearch(query, this);
            } catch (QueryExecutionException e) {
                JOptionPane.showMessageDialog(AdvancedSearchDialog.this, e.getLocalizedMessage());
            }
            return null;
        }

        private void open() throws FileNotFoundException {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), Charset.forName("utf-8")));
        }

        @Override
        protected void done() {
            close();
            setEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            JOptionPane.showMessageDialog(AdvancedSearchDialog.this, Messages.getString("GraphAnalysis.Done"));
        }

        private void close() {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void columnsFound(List<String> columns) {
            this.columns = columns;
            try {
                Iterator<String> iterator = columns.iterator();
                while (iterator.hasNext()) {
                    out.write("\"");
                    out.write(iterator.next());
                    out.write("\"");
                    if (iterator.hasNext()) {
                        out.write(",");
                    }
                }
                out.write("\r\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void resultFound(Map<String, Object> result) {
            try {
                Iterator<String> iterator = columns.iterator();
                while (iterator.hasNext()) {
                    out.write("\"");
                    Object value = result.get(iterator.next());
                    if (value != null) {
                        out.write(value.toString());
                    } else {
                        out.write("null");
                    }
                    out.write("\"");
                    if (iterator.hasNext()) {
                        out.write(",");
                    }
                }
                out.write("\r\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

    }

    private class AdvancedSearchWorker extends SwingWorker<Void, Void> {

        private String query;
        private long start;
        private long end;

        public AdvancedSearchWorker(String query) {
            super();
            this.query = query;
        }

        @Override
        protected Void doInBackground() throws Exception {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
            try {
                start = System.currentTimeMillis();
                graphService.advancedSearch(query, dataModel);
                end = System.currentTimeMillis();
            } catch (QueryExecutionException e) {
                JOptionPane.showMessageDialog(AdvancedSearchDialog.this, e.getLocalizedMessage());
            }
            return null;
        }

        @Override
        protected void done() {
            dataModel.fireTableStructureChanged();
            dataModel.fireTableDataChanged();
            resultsTable.doLayout();
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            statusLabel.setText(Messages.getString("GraphAnalysis.Results", resultsTable.getRowCount(), (end - start)));
        }

    }

    private class AddToGraphAction extends AbstractAction {

        private static final long serialVersionUID = 3964279252827602007L;

        @Override
        public void actionPerformed(ActionEvent e) {
            int rowCount = dataModel.getRowCount();
            if (rowCount == 0) {
                return;
            }

            HashSet<Long> idEntities = new HashSet<>(rowCount);
            HashSet<Long> idRelationships = new HashSet<>(rowCount);

            for (int index = 0; index < rowCount; index++) {
                for (int col = 0; col < dataModel.getColumnCount(); col++) {
                    Object value = dataModel.getValueAt(index, col);
                    if (value instanceof Node) {
                        Node node = (Node) value;
                        idEntities.add(node.getId());
                    } else if (value instanceof Relationship) {
                        Relationship rel = (Relationship) value;
                        idEntities.add(rel.getStartNodeId());
                        idEntities.add(rel.getEndNodeId());
                        idRelationships.add(rel.getId());
                    } else if (value instanceof Path) {
                        Path path = (Path) value;
                        for (Node node : path.nodes()) {
                            idEntities.add(node.getId());
                        }
                        for (Relationship rel : path.relationships()) {
                            idRelationships.add(rel.getId());
                        }
                    }
                }
            }

            if (!idEntities.isEmpty()) {
                AddNodeWorker nodeWorker = app.addNodesToGraph(idEntities);
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            nodeWorker.get();
                            app.addRelationshipsToGraph(idRelationships);

                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            } else {
                app.addRelationshipsToGraph(idRelationships);
            }

        }

    }

    private class AdvancedSearchDataModel extends AbstractTableModel implements FreeQueryListener {

        private static final long serialVersionUID = -1790129675219773709L;

        private List<String> columns = Collections.emptyList();

        private List<Object[]> results = new ArrayList<>();

        @Override
        public int getRowCount() {
            return results.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return this.results.get(rowIndex)[columnIndex];
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column);
        }

        @Override
        public void columnsFound(List<String> columns) {
            this.columns = new ArrayList<>(columns);
            this.results.clear();
            addButton.setEnabled(false);
        }

        @Override
        public void resultFound(Map<String, Object> resultMap) {
            int size = columns.size();
            Object[] result = new Object[size];
            Object value = null;
            boolean enabled = false;
            for (int index = 0; index < size; index++) {
                value = resultMap.get(columns.get(index));
                result[index] = value;
                if (!enabled) {
                    enabled = value instanceof Node || value instanceof Relationship || value instanceof Path;
                    addButton.setEnabled(enabled);
                }
            }
            results.add(result);
        }

    }

}
