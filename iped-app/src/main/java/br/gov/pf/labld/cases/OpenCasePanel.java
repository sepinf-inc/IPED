package br.gov.pf.labld.cases;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gov.pf.labld.cases.IpedCase.IpedDatasource;
import br.gov.pf.labld.cases.IpedCase.IpedInput;
import dpf.sp.gpinf.indexer.desktop.Messages;

public class OpenCasePanel extends NewCasePanel {

  private static final long serialVersionUID = 4842178746874938330L;

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenCasePanel.class);

  private IpedCase currentCase;

  public OpenCasePanel(CaseManagement caseManagement) {
    super(caseManagement);
  }

  @Override
  protected void createGUI() {
    super.createGUI();
  }

  @Override
  protected void initActionButtons() {
    JButton processBtn = new JButton(new ProcessCaseAction());
    processBtn.setText(Messages.getString("Case.ProcessCase"));
    formPanel.addButton(processBtn);
  }

  private void prepareGUI() {
    boolean processed = currentCase != null && currentCase.isProcessed();

    setOutputEnabled(processed);
    for (DatasourcePanel panel : this.datasources) {
      panel.setInputEnabled(!processed);
    }
  }

  private void setOutputEnabled(boolean enabled) {
    outputField.setEnabled(!enabled);
    chooseOutputButton.setEnabled(!enabled);
  }

  @Override
  protected void initDatasoucesGUI() {
    // Nothing to do.
  }

  public boolean loadCase(File file) {
    try {
      IpedCase ipedCase = IpedCase.loadFrom(file);
      loadCase(ipedCase);
      return true;
    } catch (IOException e) {
      String msg = Messages.getString("Case.CaseLoadError", e.getLocalizedMessage());
      JOptionPane.showMessageDialog(null, msg);
      LOGGER.error(msg + " file:" + file.getAbsolutePath(), e);
      return false;
    }
  }

  public void loadCase(IpedCase ipedCase) {
    this.currentCase = ipedCase;

    this.clearForm();
    this.setBorderTitle(ipedCase.getCaseFile().getAbsolutePath());

    this.nameField.setText(ipedCase.getName());
    this.outputField.setText(ipedCase.getOutput());
    
    boolean processed = currentCase != null && currentCase.isProcessed();

    List<IpedDatasource> datasources = ipedCase.getDatasources();
    for (int index = 0; index < datasources.size(); index++) {
      IpedDatasource ds = datasources.get(index);
      boolean removable = index > 0 && !ipedCase.isProcessed();
      DatasourcePanel datasourcePanel = this.addDatasource(removable);
      datasourcePanel.setDatasourceName(ds.getName());
      datasourcePanel.setDatasourceType(ds.getType());
      datasourcePanel.setExtras(ds.getExtras());
      List<IpedInput> inputs = ds.getInputs();
      for (int indexInput = 0; indexInput < inputs.size(); indexInput++) {
        String input = inputs.get(indexInput).getPath();
        datasourcePanel.addDatasourceItemGUI(!processed && indexInput > 0, indexInput == 0, input);
      }
    }

    prepareGUI();
    revalidate();
    repaint();
  }

  private class ProcessCaseAction extends AbstractAction {

    private static final long serialVersionUID = 6777351975773340587L;

    @Override
    public void actionPerformed(ActionEvent e) {
      final IpedCase ipedCase = OpenCasePanel.this.save();
      if (ipedCase != null) {
        OpenCasePanel.this.caseManagement.setVisible(false);
        final IpedWorker ipedWorker = new IpedWorker(ipedCase, currentCase, OpenCasePanel.this.caseManagement);
        ipedWorker.execute();
      }
    }

  }

  public static String getPanelName() {
    return OpenCasePanel.class.getSimpleName();
  }

}
