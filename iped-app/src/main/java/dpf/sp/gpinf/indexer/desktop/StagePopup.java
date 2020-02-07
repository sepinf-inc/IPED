package dpf.sp.gpinf.indexer.desktop;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class StagePopup extends JPopupMenu {

  private static final long serialVersionUID = 1421878293257791515L;

  private JMenuItem selectAll;

  public StagePopup(AppGraphAnalytics appGraphAnalytics) {
    super();
    createItems(appGraphAnalytics);
  }

  public StagePopup(String label, AppGraphAnalytics appGraphAnalytics) {
    super(label);
    createItems(appGraphAnalytics);
  }

  private void createItems(AppGraphAnalytics app) {
    selectAll = new JMenuItem(new SelectAllAction(app));
    selectAll.setText(Messages.getString("GraphAnalysis.SelectAll"));
    add(selectAll);
  }

}
