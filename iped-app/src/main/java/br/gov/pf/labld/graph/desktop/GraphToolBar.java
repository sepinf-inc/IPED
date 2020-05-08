package br.gov.pf.labld.graph.desktop;

import java.awt.Insets;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToolBar;

import org.kharon.history.GraphAction;
import org.kharon.history.GraphHistory;
import org.kharon.history.GraphHistoryListener;
import org.kharon.layout.graphviz.GraphVizAlgorithm;
import org.kharon.layout.graphviz.GraphVizLayout;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class GraphToolBar extends JToolBar implements GraphHistoryListener {

  private static final long serialVersionUID = -3959510439988621678L;

  private AppGraphAnalytics app;

  private JButton undoBtn;
  private JButton redoBtn;

  private List<JButton> buttons = new ArrayList<>();

  public GraphToolBar(AppGraphAnalytics app) {
    super();
    this.app = app;
    init();
  }

  private void init() {
    this.setFloatable(false);
    this.setMargin(new Insets(3, 3, 3, 3));

    URL undoUrl = this.getClass().getResource("action-undo-2x.png");
    undoBtn = createButton(new UndoAction(app), Messages.getString("GraphAnalysis.UndoAction"), undoUrl);
    undoBtn.setEnabled(false);
    this.add(undoBtn);

    URL redoUrl = this.getClass().getResource("action-redo-2x.png");
    redoBtn = createButton(new RedoAction(app), Messages.getString("GraphAnalysis.RedoAction"), redoUrl);
    redoBtn.setEnabled(false);
    this.add(redoBtn);

    this.addSeparator();

    URL fitToScreenUrl = this.getClass().getResource("fit-2x.png");
    JButton fitToScreenBtn = createButton(new FitToScreenAction(app), Messages.getString("GraphAnalysis.FitToScreen"),
        fitToScreenUrl);
    this.addBtn(fitToScreenBtn);

    this.addSeparator();

    URL exportImageUrl = this.getClass().getResource("image-2x.png");
    JButton exportImageBtn = createButton(new ExportImageAction(app), Messages.getString("GraphAnalysis.SaveImage"),
        exportImageUrl);
    this.addBtn(exportImageBtn);

    URL searchEntityUrl = this.getClass().getResource("magnifying-glass-2x.png");
    JButton searchEntityBtn = createButton(new OpenEntitySearchAction(app),
        Messages.getString("GraphAnalysis.SearchEntities"), searchEntityUrl);
    this.addBtn(searchEntityBtn);

    this.addSeparator();

    URL exportLinksUrl = this.getClass().getResource("fork-2x.png");
    JButton exportLinksBtn = createButton(new OpenExportLinkAction(app),
        Messages.getString("GraphAnalysis.ExportLinks"), exportLinksUrl);
    this.addBtn(exportLinksBtn);

    URL advancedSearchUrl = this.getClass().getResource("beaker-2x.png");
    JButton advancedSearchBtn = createButton(new OpenAdvancedSearchAction(app),
        Messages.getString("GraphAnalysis.AdvancedSearch"), advancedSearchUrl);
    this.addBtn(advancedSearchBtn);

    URL searchLinksUrl = this.getClass().getResource("link-intact-2x.png");
    JButton searchLinksBtn = createButton(new OpenSearchLinksAction(app),
        Messages.getString("GraphAnalysis.SearchLinks"), searchLinksUrl);
    this.addBtn(searchLinksBtn);

    this.addSeparator();

    for (GraphVizAlgorithm algo : GraphVizAlgorithm.values()) {
      URL layoutHLUrl = this.getClass().getResource("diagram-2x.png");
      JButton layoutHLBtn = createButton(
          new ApplyGraphLayoutAction(app, new GraphVizLayout(algo, new GraphVizIpedResolver())),
          "Layout " + algo.name(), layoutHLUrl);
      this.addBtn(layoutHLBtn);
    }
  }

  private JButton createButton(Action action, String tooltip, URL icon) {
    JButton btn = new JButton(action);
    btn.setToolTipText(tooltip);
    btn.setIcon(new ImageIcon(icon));
    btn.setMargin(new Insets(2, 2, 2, 2));
    return btn;
  }

  @Override
  public void historyChanged(GraphHistory history, GraphAction action) {
    undoBtn.setEnabled(history.isUndoPossible());
    redoBtn.setEnabled(history.isRedoPossible());
  }

  private void addBtn(JButton btn) {
    super.add(btn);
    buttons.add(btn);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    for (JButton btn : buttons) {
      btn.setEnabled(enabled);
    }
  }

}
