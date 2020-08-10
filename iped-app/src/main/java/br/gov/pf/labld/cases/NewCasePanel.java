package br.gov.pf.labld.cases;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.IpedVersion;
import dpf.sp.gpinf.indexer.desktop.Messages;

public class NewCasePanel extends JPanel {

    private static final long serialVersionUID = -1414020777926033114L;

    private static final Logger LOGGER = LoggerFactory.getLogger(NewCasePanel.class);

    protected CaseManagement caseManagement;

    protected FormPanel formPanel;
    protected List<DatasourcePanel> datasources = new ArrayList<>();

    protected JTextField nameField;
    protected JTextField outputField;
    protected JButton chooseOutputButton;

    private TitledBorder titledBorder;

    public NewCasePanel(CaseManagement caseManagement) {
        super(new BorderLayout());
        this.caseManagement = caseManagement;
        createGUI();
    }

    protected void createGUI() {
        titledBorder = BorderFactory.createTitledBorder(Messages.getString("Case.NewCase"));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(20, 10, 10, 10), titledBorder));

        formPanel = new FormPanel();

        addOutputFormItems();
        initDatasoucesGUI();

        JButton addDatasourceButton = new JButton(new AddDatasourceAction());
        addDatasourceButton.setText(Messages.getString("Case.AddDatasource"));
        formPanel.addButton(addDatasourceButton);

        initActionButtons();

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    protected void initActionButtons() {
        JButton saveBtn = new JButton(new SaveCaseAction());
        saveBtn.setText(Messages.getString("Case.SaveCase"));
        formPanel.addButton(saveBtn);
    }

    protected void initDatasoucesGUI() {
        DatasourcePanel datasourcePanel = addDatasource(false);
        datasourcePanel.addDatasourceItemGUI(false, true, "");
    }

    protected void setBorderTitle(String title) {
        titledBorder.setTitle(title);
    }

    protected void clearForm() {
        nameField.setText("");
        outputField.setText("");
        for (DatasourcePanel datasourcePanel : datasources) {
            formPanel.removeSection(datasourcePanel);
        }
        formPanel.revalidate();
        datasources.clear();
        initDatasoucesGUI();
    }

    protected DatasourcePanel addDatasource(boolean removable) {
        DatasourcePanel newPanel = new DatasourcePanel(this, removable);
        datasources.add(newPanel);

        formPanel.addSection(newPanel);
        formPanel.revalidate();
        return newPanel;
    }

    void removeDatasource(DatasourcePanel panel) {
        datasources.remove(panel);
        formPanel.removeSection(panel);
        formPanel.revalidate();
    }

    private Component addOutputFormItems() {
        nameField = new JTextField(30);

        outputField = new JTextField(30);
        outputField.setDisabledTextColor(Color.GRAY);

        chooseOutputButton = new JButton();
        chooseOutputButton.setMargin(new Insets(0, 0, 0, 0));
        ChooseOutputAction chooseOutputAction = new ChooseOutputAction(this, outputField, chooseOutputButton);
        outputField.addFocusListener(chooseOutputAction);
        chooseOutputButton.setAction(chooseOutputAction);
        chooseOutputButton.setText(Messages.getString("Case.CaseChoose"));

        formPanel.addItem("case.name", Messages.getString("Case.CaseName"), nameField);
        formPanel.addComplexItem("case.output", Messages.getString("Case.CaseOutput"), outputField, chooseOutputButton);
        formPanel.layoutForm();

        return formPanel;
    }

    private boolean validateForm() {
        boolean valid = true;

        valid = valid & FormUtils.validateTextFieldNotEmpty(nameField);
        valid = valid & FormUtils.validateTextFieldNotEmpty(outputField);

        for (DatasourcePanel panel : datasources) {
            valid = valid & panel.validateForm();
            panel.repaint();
        }

        return valid;
    }

    private void saveAndShowOpenCasePanel() {
        IpedCase ipedCase = save();
        if (ipedCase != null) {
            caseManagement.showOpenCasePanel();
            caseManagement.getOpenCasePanel().loadCase(ipedCase);
            clearForm();
        }
    }

    protected IpedCase save() {
        IpedCase ipedCase = null;
        if (validateForm()) {
            ipedCase = buildCase();
            File output = ipedCase.getCaseFile();
            output.getParentFile().mkdirs();
            try {
                ipedCase.saveTo(output);
            } catch (IOException e) {
                String msg = Messages.getString("Case.CaseSaveError", e.getLocalizedMessage());
                JOptionPane.showMessageDialog(null, msg);
                LOGGER.error(msg + " file:" + output.getAbsolutePath(), e);
                return null;
            }
        }
        return ipedCase;
    }

    private IpedCase buildCase() {
        IpedCase ipedCase = new IpedCase();
        ipedCase.setName(nameField.getText().trim());
        ipedCase.setOutput(outputField.getText().trim());
        ipedCase.setVersion(IpedVersion.APP_VERSION);

        for (DatasourcePanel panel : datasources) {
            IpedCase.IpedDatasource datasource = new IpedCase.IpedDatasource();

            datasource.setName(panel.getDatasourceName());
            datasource.setType(panel.getDatasourceType());
            datasource.setExtras(panel.getExtras());
            List<String> inputs = panel.getInputs();
            for (String input : inputs) {
                datasource.addInput(input);
            }

            ipedCase.addDatasource(datasource);
        }

        return ipedCase;
    }

    private final class SaveCaseAction extends AbstractAction {
        private static final long serialVersionUID = -8919092384383963881L;

        @Override
        public void actionPerformed(ActionEvent e) {
            saveAndShowOpenCasePanel();
        }
    }

    private final class AddDatasourceAction extends AbstractAction {
        private static final long serialVersionUID = 8792705186990158153L;

        @Override
        public void actionPerformed(ActionEvent e) {
            DatasourcePanel datasourcePanel = addDatasource(true);
            boolean removable = datasourcePanel.getInputs().isEmpty();
            datasourcePanel.addDatasourceItemGUI(!removable, true, "");
        }
    }

    public static String getPanelName() {
        return NewCasePanel.class.getSimpleName();
    }

}
