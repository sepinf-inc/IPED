package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class OpenSearchAction extends AbstractAction {

  private static final long serialVersionUID = 3717744757010120211L;

  private AppGraphAnalytics app;

  public OpenSearchAction(AppGraphAnalytics app) {
    super();
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    GraphSearchDialog dialog = new GraphSearchDialog(app);
    dialog.setVisible(true);
  }

}
