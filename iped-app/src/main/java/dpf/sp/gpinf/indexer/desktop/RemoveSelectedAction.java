package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class RemoveSelectedAction extends AbstractAction {

  private static final long serialVersionUID = -150299378093154838L;

  private AppGraphAnalytics app;

  public RemoveSelectedAction(AppGraphAnalytics app) {
    super();
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    app.removeSelected();
  }

}
