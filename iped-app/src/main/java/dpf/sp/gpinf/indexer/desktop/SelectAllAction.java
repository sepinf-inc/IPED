package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

class SelectAllAction extends AbstractAction {

  private static final long serialVersionUID = 8392537332595502692L;

  private AppGraphAnalytics app;

  public SelectAllAction(AppGraphAnalytics app) {
    super();
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    app.selectAll();
  }

}