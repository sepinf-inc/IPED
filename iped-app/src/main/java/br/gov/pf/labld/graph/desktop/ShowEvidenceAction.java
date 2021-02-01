package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class ShowEvidenceAction extends AbstractAction {

    private static final long serialVersionUID = 5800382694596829060L;

    private AppGraphAnalytics app;

    public ShowEvidenceAction(AppGraphAnalytics app) {
        super();
        this.app = app;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        app.openHoveredEvidence();
    }

}
