package br.gov.pf.labld.cases;

import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class OpenCaseAction extends AbstractAction {

  private static final long serialVersionUID = -8705501428492639284L;

  private CaseManagement caseManagement;

  public OpenCaseAction(CaseManagement caseManagement) {
    super();
    this.caseManagement = caseManagement;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setFileFilter(new IpedCaseFileFilter());
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

    int returnVal = fileChooser.showOpenDialog(caseManagement);

    if (returnVal == JFileChooser.APPROVE_OPTION) {
      OpenCasePanel openCasePanel = this.caseManagement.getOpenCasePanel();
      if (openCasePanel.loadCase(fileChooser.getSelectedFile())) {
        this.caseManagement.showOpenCasePanel();
      }
    }
  }

  private static final class IpedCaseFileFilter extends FileFilter {

    @Override
    public boolean accept(File file) {
      boolean isIpedCaseFile = file.isFile() && file.getName().equals(IpedCase.IPEDCASE_JSON);
      return file.isDirectory() || isIpedCaseFile;
    }

    @Override
    public String getDescription() {
      return Messages.getString("Case.CaseFile");
    }

  }

}
