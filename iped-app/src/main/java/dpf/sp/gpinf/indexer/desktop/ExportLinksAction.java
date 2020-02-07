package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import br.gov.pf.labld.graph.ExportLinksQuery;

public class ExportLinksAction extends AbstractAction {

  private static final long serialVersionUID = -7078936612400643960L;

  private GraphModel model;
  private ExportLinksDialog dialog;

  public ExportLinksAction(GraphModel model, ExportLinksDialog dialog) {
    super();
    this.model = model;
    this.dialog = dialog;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    int numOfLinks = dialog.getNumOfLinks();
    ExportLinksQuery query = new ExportLinksQuery(numOfLinks);

    for (int index = 0; index < numOfLinks - 1; index++) {
      List<String> types1 = dialog.getLinks(index);
      int distance = dialog.getDistance(index);
      List<String> types2 = dialog.getLinks(index + 1);
      query.addTypes(types1, distance);
      query.addTypes(types2);
    }

    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    int option = fileChooser.showSaveDialog(dialog);
    if (option == JFileChooser.APPROVE_OPTION) {
      File output = fileChooser.getSelectedFile();
      exportToFile(output, query);
    }

  }

  private void exportToFile(File output, ExportLinksQuery query) {
    if (output.isDirectory()) {
      output = new File(output, "links.csv");
    }

    boolean execute = true;
    if (output.exists()) {
      int result = JOptionPane.showConfirmDialog(dialog,
          Messages.getString("GraphAnalysis.FileExistsConfirmation", output.getAbsolutePath()), null,
          JOptionPane.YES_NO_OPTION);
      execute = result == JOptionPane.YES_OPTION;
    }

    if (execute) {
      ExportLinksWorker worker = new ExportLinksWorker(model, dialog, output, query);
      worker.execute();
    }
  }

}
