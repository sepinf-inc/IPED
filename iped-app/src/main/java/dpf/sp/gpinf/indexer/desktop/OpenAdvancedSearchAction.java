package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class OpenAdvancedSearchAction extends AbstractAction {

  private static final long serialVersionUID = -2869124354296262534L;

  private AppGraphAnalytics app;
  private AdvancedSearchDialog dialog;

  public OpenAdvancedSearchAction(AppGraphAnalytics app) {
    super();
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (dialog == null || !dialog.isOpen() || !dialog.isVisible()) {
      dialog = new AdvancedSearchDialog(app);
      dialog.setVisible(true);
    } else {
      dialog.toFront();
    }
  }

}
