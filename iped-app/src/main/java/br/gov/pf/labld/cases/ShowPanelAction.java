package br.gov.pf.labld.cases;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class ShowPanelAction extends AbstractAction {

    private static final long serialVersionUID = 6677856850841139551L;

    private CaseManagement caseManagement;
    private String panelName;

    public ShowPanelAction(CaseManagement caseManagement, String panelName) {
        super();
        this.caseManagement = caseManagement;
        this.panelName = panelName;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        caseManagement.showPanel(panelName);
    }

}
