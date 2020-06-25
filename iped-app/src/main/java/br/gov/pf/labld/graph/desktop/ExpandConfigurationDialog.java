package br.gov.pf.labld.graph.desktop;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

import br.gov.pf.labld.graph.ConnectionQueryListener;
import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import dpf.sp.gpinf.indexer.desktop.Messages;

public class ExpandConfigurationDialog extends JDialog implements MouseListener {

  private static final long serialVersionUID = -4634318483160759620L;
  
  public static final int MAX_NEIGHBOURS = 20;

  private AppGraphAnalytics app;

  private ExpandConfigurationTableModel dataModel;

  private JTable table;
  
  private JRadioButton nodes = new JRadioButton(new RadioAction());
  private JRadioButton edges = new JRadioButton(new RadioAction());
  private ButtonGroup radioGroup = new ButtonGroup();
  
  private JLabel topXLabel = new JLabel();
  private JTextField topXNodes = new JTextField();
  private JButton expandButton;

  public ExpandConfigurationDialog(JFrame frame, AppGraphAnalytics app) {
    super();
    this.app = app;
    init(frame);
  }

  private void init(JFrame frame) {
    setTitle(Messages.getString("GraphAnalysis.ExpandConfiguration"));
    setPreferredSize(new Dimension(350, 400));

    JButton cancelButton = new JButton(new CancelAction());
    cancelButton.setText(Messages.getString("GraphAnalysis.Cancel"));

    this.dataModel = new ExpandConfigurationTableModel();

    this.table = new JTable(this.dataModel);
    this.table.setFillsViewportHeight(true);
    this.table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    this.table.addMouseListener(this);
    this.table.getColumnModel().getColumn(1).setMaxWidth(60);
    
    nodes.setText(Messages.getString("GraphAnalysis.Expand.Entities"));
    edges.setText(Messages.getString("GraphAnalysis.Expand.Links"));
    radioGroup.add(nodes);
    radioGroup.add(edges);
    edges.setSelected(true);
    
    topXLabel.setText(Messages.getString("GraphAnalysis.Expand.MaxNodes"));
    topXNodes.setText(Integer.toString(MAX_NEIGHBOURS));
    
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(1, 5, 1, 5);
    c.fill = GridBagConstraints.BOTH;
    
    c.weightx = 0.25;
    c.weighty = 0.05;
    c.gridwidth = 1;
    c.gridx = 1;
    c.gridy = 0;
    panel.add(edges, c);
    
    c.gridx = 2;
    c.gridy = 0;
    panel.add(nodes, c);
    
    c.weightx = 0.75;
    c.weighty = 0.05;
    c.gridwidth = 3;
    c.gridx = 0;
    c.gridy = 1;
    panel.add(topXLabel, c);
    
    c.weightx = 0.25;
    c.gridwidth = 1;
    c.gridx = 3;
    c.gridy = 1;
    panel.add(topXNodes, c);

    c.weightx = 1;
    c.weighty = 0.85;
    c.gridwidth = 4;
    c.gridx = 0;
    c.gridy = 2;

    panel.add(new JScrollPane(table), c);

    c.fill = GridBagConstraints.HORIZONTAL;

    c.gridwidth = 2;
    c.weightx = 0.5;
    c.weighty = 0.05;
    c.gridx = 0;
    c.gridy = 3;
    expandButton = new JButton(new ExpandAction());
    expandButton.setText(Messages.getString("GraphAnalysis.Expand"));
    panel.add(expandButton, c);

    c.gridx = 2;
    c.gridy = 3;
    panel.add(cancelButton, c);

    add(panel);
    pack();
    table.doLayout();
    setLocationRelativeTo(frame);

    getRootPane().registerKeyboardAction(new CancelAction(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
  }

  public void loadData() {
    ExpandConfigurationDialog.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    new ExpandConfigurationWorker().execute();
  }
  
  private class RadioAction extends AbstractAction{

    @Override
    public void actionPerformed(ActionEvent e) {
        loadData();
    }
    
  }

  private class ExpandAction extends AbstractAction {

    private static final long serialVersionUID = 6908833600236751589L;

    @Override
    public void actionPerformed(ActionEvent e) {
      int[] rows = ExpandConfigurationDialog.this.table.getSelectedRows();
      Set<String> labelsOrTypes = new HashSet<>(rows.length);
      for (int row : rows) {
        String label = (String) ExpandConfigurationDialog.this.table.getValueAt(row, 0);
        labelsOrTypes.add(label);
      }
      int maxNodes = -1;
      try {
          maxNodes = Integer.parseInt(topXNodes.getText().trim());
      }catch(NumberFormatException ex) {}
      
      app.expandSelectedWithLabelsOrTypes(labelsOrTypes, edges.isSelected(), maxNodes);
      close();
    }

  }

  private class CancelAction extends AbstractAction implements ActionListener {

    private static final long serialVersionUID = -1931529363924950702L;

    @Override
    public void actionPerformed(ActionEvent e) {
      ExpandConfigurationDialog.this.close();
    }

  }

  private class ExpandConfigurationWorker extends SwingWorker<Void, Void> implements ConnectionQueryListener {

    @Override
    protected Void doInBackground() throws Exception {
      Long id = Long.parseLong(app.getContextMenuNodeId());
      dataModel.clear();
      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      if(edges.isSelected()) {
          graphService.findRelationships(id, this);
      }else {
          graphService.findConnections(id, this);
      }
      return null;
    }

    @Override
    public void connectionsFound(String label, int quantity) {
      dataModel.addResult(label, quantity);
    }

    @Override
    protected void done() {
      ExpandConfigurationDialog.this.dataModel.fireTableDataChanged();
      ExpandConfigurationDialog.this.table.doLayout();
      ExpandConfigurationDialog.this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

  }

  private static class ExpandConfigurationTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1058750719066442600L;

    private String[] headers = new String[] { Messages.getString("GraphAnalysis.Type"),
        Messages.getString("GraphAnalysis.Quantity") };

    private List<Object[]> data = new ArrayList<>();

    @Override
    public int getRowCount() {
      return data.size();
    }

    @Override
    public int getColumnCount() {
      return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return data.get(rowIndex)[columnIndex];
    }

    @Override
    public String getColumnName(int column) {
      return this.headers[column];
    }

    public void addResult(String label, int quantity) {
      this.data.add(new Object[] { label, quantity });
    }

    public void clear() {
      this.data.clear();
    }

  }

  private void close() {
    this.setVisible(false);
    this.dispose();
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    if(e.getClickCount() == 2) {
      expandButton.doClick();    
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
