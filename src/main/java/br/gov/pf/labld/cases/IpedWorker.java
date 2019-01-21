package br.gov.pf.labld.cases;

import javax.swing.SwingWorker;

public class IpedWorker extends SwingWorker<Void, Void> {

  private IpedCase ipedCase;
  private IpedCase oldCase;
  private CaseManagement caseManagement;

  public IpedWorker(IpedCase ipedCase, IpedCase oldCase, CaseManagement caseManagement) {
    super();
    this.ipedCase = ipedCase;
    this.oldCase = oldCase;
    this.caseManagement = caseManagement;
  }

  @Override
  protected Void doInBackground() throws Exception {
    IpedProcessHelper processHelper = new IpedProcessHelper(ipedCase, oldCase);
    processHelper.process();
    return null;
  }

  @Override
  protected void done() {
    caseManagement.dispose();
  }

}