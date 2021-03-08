package br.gov.pf.labld.cases;

import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class CaseMenuPanel extends JPanel {

    private static final long serialVersionUID = -5800882667733005981L;

    private CaseManagement caseManagement;

    public CaseMenuPanel(CaseManagement caseManagement) {
        super(new GridLayout(4, 1, 100, 30));
        this.caseManagement = caseManagement;
        createGUI();
    }

    public void createGUI() {
        JButton newCaseButton = new JButton(new ShowPanelAction(caseManagement, NewCasePanel.getPanelName()));
        newCaseButton.setText(Messages.getString("Case.New"));

        JButton openCaseButton = new JButton(new OpenCaseAction(caseManagement));
        openCaseButton.setText(Messages.getString("Case.Open"));

        JButton caseOptionsButton = new JButton(new ShowPanelAction(caseManagement, CaseOptionsPanel.getPanelName()));
        caseOptionsButton.setText(Messages.getString("Case.Options"));

        add(newCaseButton);
        add(openCaseButton);
        add(new JSeparator(JSeparator.HORIZONTAL));
        add(caseOptionsButton);
    }

}
