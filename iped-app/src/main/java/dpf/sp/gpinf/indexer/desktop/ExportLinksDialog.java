package dpf.sp.gpinf.indexer.desktop;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.LabelQueryListener;
import dpf.sp.gpinf.indexer.desktop.CheckboxListCellRenderer.CheckboxListItem;
import dpf.sp.gpinf.indexer.desktop.CheckboxListCellRenderer.CheckboxSelectionMouseAdapter;

public class ExportLinksDialog extends JDialog {

  private static final long serialVersionUID = -2822033056249324729L;

  private DefaultListModel<CheckboxListItem> originListModel;
  private DefaultListModel<CheckboxListItem> destinyListModel;

  private AppGraphAnalytics app;

  private static final Dimension TYPE_LIST_DIM = new Dimension(120, 150);

  private JSpinner spinner;

  public ExportLinksDialog() {
    super();
    createGUI();
  }

  public ExportLinksDialog(AppGraphAnalytics app) {
    this.app = app;
    createGUI();
  }

  private void createGUI() {
    setTitle(Messages.get("GraphAnalysis.ExportLinks"));
    setPreferredSize(new Dimension(500, 500));
    setMinimumSize(new Dimension(350, 300));

    JPanel containerPanel = new JPanel();
    containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.X_AXIS));
    containerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    containerPanel.add(createOriginPanel());
    containerPanel.add(createDistancePanel());
    containerPanel.add(createDestinyPanel());

    JPanel dialogPanel = new JPanel();
    dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));

    dialogPanel.add(containerPanel);
    dialogPanel.add(createButtonsPanel());

    add(dialogPanel);

    setModalityType(ModalityType.APPLICATION_MODAL);
    pack();

    if (app != null) {
      setLocationRelativeTo(App.get());
    }
    loadTypes();

    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    getRootPane().registerKeyboardAction(new CloseDialogAction(this), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private JPanel createDestinyPanel() {

    destinyListModel = new DefaultListModel<>();

    JList<CheckboxListItem> destinyList = createTypeListGUI();
    destinyList.setModel(destinyListModel);

    JScrollPane destinyListScroller = new JScrollPane(destinyList);
    destinyListScroller.setPreferredSize(TYPE_LIST_DIM);

    JLabel destinyLabel = new JLabel(Messages.get("GraphAnalysis.Destiny"));
    destinyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    JPanel destinyPanel = new JPanel();
    destinyPanel.setLayout(new BoxLayout(destinyPanel, BoxLayout.Y_AXIS));
    destinyPanel.add(destinyLabel);
    destinyPanel.add(destinyListScroller);
    return destinyPanel;
  }

  private JPanel createOriginPanel() {

    originListModel = new DefaultListModel<>();

    JList<CheckboxListItem> originList = createTypeListGUI();
    originList.setModel(originListModel);

    JScrollPane originListScroller = new JScrollPane(originList);
    originListScroller.setPreferredSize(TYPE_LIST_DIM);

    JLabel originLabel = new JLabel(Messages.get("GraphAnalysis.Origin"));
    originLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    JPanel originPanel = new JPanel();
    originPanel.setLayout(new BoxLayout(originPanel, BoxLayout.Y_AXIS));
    originPanel.add(originLabel);
    originPanel.add(originListScroller);
    return originPanel;
  }

  private JPanel createButtonsPanel() {
    JButton cancelButton = new JButton(new CloseDialogAction(this));
    cancelButton.setText(Messages.get("GraphAnalysis.Cancel"));

    JButton exportButton = new JButton(new ExportLinksAction(app.getGraphModel(), this));
    exportButton.setText(Messages.get("GraphAnalysis.Export"));

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    buttonsPanel.add(Box.createHorizontalGlue());
    buttonsPanel.add(cancelButton);
    buttonsPanel.add(Box.createRigidArea(new Dimension(10, 0)));
    buttonsPanel.add(exportButton);
    return buttonsPanel;
  }

  private JPanel createDistancePanel() {
    JSpinner spinner = createDistanceSpinner();

    JPanel distanceSpinnersPanel = new JPanel();
    distanceSpinnersPanel.setLayout(new FlowLayout());
    distanceSpinnersPanel.add(spinner);

    JLabel distanceLabel = new JLabel(Messages.get("GraphAnalysis.Distance"));
    distanceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    JPanel distancePanel = new JPanel();
    distancePanel.setLayout(new BoxLayout(distancePanel, BoxLayout.Y_AXIS));
    distancePanel.add(distanceLabel);
    distancePanel.add(distanceSpinnersPanel);

    return distancePanel;
  }

  private JSpinner createDistanceSpinner() {
    spinner = new JSpinner();
    spinner.setPreferredSize(new Dimension(40, 30));
    spinner.setModel(new SpinnerNumberModel(1, 1, 5, 1));
    JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "##");
    editor.getTextField().setEditable(false);
    spinner.setEditor(editor);

    return spinner;
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

  public void loadTypes() {
    new LoadLabelsWorker().execute();
  }

  public int getNumOfLinks() {
    return 2;
  }

  public List<String> getLinks(int num) {
    if (num == 0) {
      return getLinks(originListModel);
    } else if (num == 1) {
      return getLinks(destinyListModel);
    } else {
      throw new IllegalArgumentException();
    }
  }

  public int getDistance(int num) {
    if (num == 0) {
      int distance = Integer.parseInt(spinner.getValue().toString());
      return distance;
    } else {
      throw new IllegalArgumentException();
    }
  }

  private List<String> getLinks(DefaultListModel<CheckboxListItem> model) {
    List<String> result = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      CheckboxListItem item = model.getElementAt(index);
      if (item.isSelected()) {
        result.add(item.getLabel());
      }
    }
    return result;
  }

  private class LoadLabelsWorker extends SwingWorker<Void, Void> implements LabelQueryListener {

    private List<String> labels = new ArrayList<>();

    @Override
    protected Void doInBackground() throws Exception {
      originListModel.clear();
      destinyListModel.clear();

      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      graphService.findLabels(this);
      return null;
    }

    @Override
    public void labelFound(String label) {
      labels.add(label);
    }

    @Override
    protected void done() {
      originListModel.ensureCapacity(labels.size());
      destinyListModel.ensureCapacity(labels.size());

      for (String label : labels) {
        originListModel.addElement(new CheckboxListItem(label));
        destinyListModel.addElement(new CheckboxListItem(label));
      }

      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

  }

  public void showSucessMessage(File output) {
    JOptionPane.showMessageDialog(this, Messages.getString("GraphAnalysis.FileSaved", output.getAbsolutePath()));
    setVisible(false);
    dispose();
  }

}
