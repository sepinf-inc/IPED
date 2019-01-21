package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class ConnectSelectedAction extends AbstractAction {

  private static final long serialVersionUID = 3030773521363551714L;

  private AppGraphAnalytics app;

  public ConnectSelectedAction(AppGraphAnalytics app) {
    super();
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    app.connectSelected();
  }

}
