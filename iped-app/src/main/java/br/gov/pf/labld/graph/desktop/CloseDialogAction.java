package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JDialog;

public class CloseDialogAction extends AbstractAction {

  private static final long serialVersionUID = 2152169482166949002L;

  private JDialog dialog;

  public CloseDialogAction(JDialog dialog) {
    super();
    this.dialog = dialog;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    dialog.setVisible(false);
    dialog.dispose();
  }

}
