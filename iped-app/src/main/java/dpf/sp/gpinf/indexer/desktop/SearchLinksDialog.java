package dpf.sp.gpinf.indexer.desktop;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.neo4j.graphdb.Node;

import br.gov.pf.labld.graph.DynLabel;
import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.NodeQueryListener;
import br.gov.pf.labld.graph.links.SearchLinksQuery;
import br.gov.pf.labld.graph.links.SearchLinksQueryProvider;
import dpf.sp.gpinf.indexer.desktop.CheckboxListCellRenderer.CheckboxListItem;
import dpf.sp.gpinf.indexer.desktop.CheckboxListCellRenderer.CheckboxSelectionMouseAdapter;

public class SearchLinksDialog extends JDialog {

  private static final long serialVersionUID = 2974900704370821332L;

  private static final Dimension DATASOURCES_LIST_DIM = new Dimension(180, 100);
  private static final Dimension SEARCHS_DIM = new Dimension(180, 20);

  private AppGraphAnalytics app;

  private DefaultListModel<CheckboxListItem> originListModel;
  private DefaultListModel<CheckboxListItem> destinyListModel;

  private Map<String, JCheckBox> queriesCheckBoxes;

  public SearchLinksDialog(AppGraphAnalytics app) {
    super();
    this.app = app;
    createGUI();
  }

  public SearchLinksDialog() {
    createGUI();
  }

  public static void main(String[] args) {
    System.setProperty("iped-locale", "pt-BR");
    SearchLinksDialog dialog = new SearchLinksDialog();
    dialog.setVisible(true);
  }

  private void createGUI() {
    setTitle(Messages.get("GraphAnalysis.SearchLinks"));
    setPreferredSize(new Dimension(500, 500));
    setMinimumSize(new Dimension(350, 300));

    JPanel containerPanel = new JPanel();
    containerPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10),
        Messages.get("GraphAnalysis.Datasources")));
    containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.X_AXIS));
    containerPanel.add(createOriginPanel());
    containerPanel.add(createDestinyPanel());

    JPanel dialogPanel = new JPanel();
    dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));
    dialogPanel.add(containerPanel);
    dialogPanel.add(createSearchsPanel());
    dialogPanel.add(createButtonsPanel());

    add(dialogPanel);
    dialogPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    setModalityType(ModalityType.APPLICATION_MODAL);
    pack();

    if (app != null) {
      setLocationRelativeTo(App.get());
    }
    loadDatasources();

    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    getRootPane().registerKeyboardAction(new CloseDialogAction(this), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private JPanel createSearchsPanel() {
    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10),
        Messages.get("GraphAnalysis.Links")));

    queriesCheckBoxes = new HashMap<>();

    for (SearchLinksQuery query : SearchLinksQueryProvider.get().getQueries()) {
      JCheckBox checkBox = new JCheckBox(query.getLabel());
      panel.add(checkBox);
      queriesCheckBoxes.put(query.getQueryName(), checkBox);
    }

    panel.setSize(SEARCHS_DIM);

    return panel;
  }

  private void loadDatasources() {
    new LoadDatasourcesWorker().execute();
  }

  private JPanel createOriginPanel() {
    originListModel = new DefaultListModel<>();

    JList<CheckboxListItem> originList = createTypeListGUI();
    originList.setModel(originListModel);

    JScrollPane originListScroller = new JScrollPane(originList);
    originListScroller.setSize(DATASOURCES_LIST_DIM);

    JLabel originLabel = new JLabel(Messages.get("GraphAnalysis.Origin"));
    originLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    JPanel originPanel = new JPanel();
    originPanel.setLayout(new BoxLayout(originPanel, BoxLayout.Y_AXIS));
    originPanel.add(originLabel);
    originPanel.add(originListScroller);
    return originPanel;
  }

  private JPanel createDestinyPanel() {
    destinyListModel = new DefaultListModel<>();

    JList<CheckboxListItem> destinyList = createTypeListGUI();
    destinyList.setModel(destinyListModel);

    JScrollPane destinyListScroller = new JScrollPane(destinyList);
    destinyListScroller.setSize(DATASOURCES_LIST_DIM);

    JLabel destinyLabel = new JLabel(Messages.get("GraphAnalysis.Destiny"));
    destinyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    JPanel destinyPanel = new JPanel();
    destinyPanel.setLayout(new BoxLayout(destinyPanel, BoxLayout.Y_AXIS));
    destinyPanel.add(destinyLabel);
    destinyPanel.add(destinyListScroller);
    return destinyPanel;
  }

  private JList<CheckboxListItem> createTypeListGUI() {
    JList<CheckboxListItem> list = new JList<CheckboxListItem>();
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setLayoutOrientation(JList.VERTICAL);
    list.setVisibleRowCount(-1);
    list.setCellRenderer(new CheckboxListCellRenderer());
    list.addMouseListener(new CheckboxSelectionMouseAdapter());
    return list;
  }

  private JPanel createButtonsPanel() {
    JButton cancelButton = new JButton(new CloseDialogAction(this));
    cancelButton.setText(Messages.get("GraphAnalysis.Cancel"));

    JButton exportButton = new JButton(new SearchLinksAction(this, app));
    exportButton.setText(Messages.get("GraphAnalysis.Search"));

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    buttonsPanel.add(Box.createHorizontalGlue());
    buttonsPanel.add(cancelButton);
    buttonsPanel.add(Box.createRigidArea(new Dimension(10, 0)));
    buttonsPanel.add(exportButton);
    return buttonsPanel;
  }

  public List<String> getQueries() {
    List<String> queries = new ArrayList<>();
    for (Entry<String, JCheckBox> entry : queriesCheckBoxes.entrySet()) {
      if (entry.getValue().isSelected()) {
        queries.add(entry.getKey());
      }
    }
    return queries;
  }

  public List<String> getOrigin() {
    return getIds(originListModel);
  }

  public List<String> getDestiny() {
    return getIds(destinyListModel);
  }

  private List<String> getIds(DefaultListModel<CheckboxListItem> model) {
    List<String> result = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      CheckboxListItem item = model.getElementAt(index);
      if (item.isSelected()) {
        result.add((String) item.getId());
      }
    }
    return result;
  }

  private class LoadDatasourcesWorker extends SwingWorker<Void, Void> implements NodeQueryListener {

    private List<String> ids = new ArrayList<>();
    private List<String> labels = new ArrayList<>();

    @Override
    protected Void doInBackground() throws Exception {
      originListModel.clear();
      destinyListModel.clear();

      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      graphService.search(DynLabel.label("DATASOURCE"), Collections.emptyMap(), this, "name");
      return null;
    }

    @Override
    public boolean nodeFound(Node node) {
      String id = node.getProperty("evidenceId").toString();
      String name = node.getProperty("name").toString();
      ids.add(id);
      labels.add(name);
      return true;
    }

    @Override
    protected void done() {
      originListModel.ensureCapacity(ids.size());
      destinyListModel.ensureCapacity(ids.size());

      for (int index = 0; index < ids.size(); index++) {
        String id = ids.get(index);
        String label = labels.get(index);
        originListModel.addElement(new CheckboxListItem(id, label));
        destinyListModel.addElement(new CheckboxListItem(id, label));
      }

      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

  }

}
