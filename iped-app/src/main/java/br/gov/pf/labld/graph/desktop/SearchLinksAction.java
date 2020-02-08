package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;

public class SearchLinksAction extends AbstractAction {

  private static final long serialVersionUID = -3665104461462266295L;

  private SearchLinksDialog dialog;
  private AppGraphAnalytics app;

  public SearchLinksAction(SearchLinksDialog dialog, AppGraphAnalytics app) {
    super();
    this.dialog = dialog;
    this.app = app;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    try {
      List<String> origin = dialog.getOrigin();
      List<String> destiny = dialog.getDestiny();
      List<String> queries = dialog.getQueries();

      app.searchLinks(origin, destiny, queries);
    } finally {
      dialog.setVisible(false);
      dialog.dispose();
    }
  }

}
