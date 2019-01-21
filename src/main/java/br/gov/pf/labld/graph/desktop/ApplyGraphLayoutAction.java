package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.kharon.layout.Layout;

public class ApplyGraphLayoutAction extends AbstractAction {

  private static final long serialVersionUID = -14285627889421635L;

  private AppGraphAnalytics app;
  private Layout layout;

  public ApplyGraphLayoutAction(AppGraphAnalytics app, Layout layout) {
    super();
    this.app = app;
    this.layout = layout;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    app.applyLayout(layout);
  }

}
