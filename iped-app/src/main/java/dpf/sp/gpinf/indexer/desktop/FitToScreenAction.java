package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class FitToScreenAction extends AbstractAction {

  private static final long serialVersionUID = 4159693274203606454L;

  private AppGraphAnalytics app;

  public FitToScreenAction(AppGraphAnalytics app) {
    super();
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    app.fitToScreen();
  }

}
