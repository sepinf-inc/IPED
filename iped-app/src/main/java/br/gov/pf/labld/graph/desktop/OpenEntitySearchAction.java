package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class OpenEntitySearchAction extends AbstractAction {

  private static final long serialVersionUID = 3717744757010120211L;

  private AppGraphAnalytics app;

  public OpenEntitySearchAction(AppGraphAnalytics app) {
    super();
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    EntitySearchDialog dialog = new EntitySearchDialog(app);
    dialog.setVisible(true);
  }

}
