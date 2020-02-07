package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class ExpandConfigurationAction extends AbstractAction {

  private static final long serialVersionUID = 6277963012519118927L;

  private AppGraphAnalytics app;

  public ExpandConfigurationAction(AppGraphAnalytics app) {
    super();
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    ExpandConfigurationDialog dialog = new ExpandConfigurationDialog(App.get(), app);
    dialog.loadData();
    dialog.setVisible(true);
  }

}
