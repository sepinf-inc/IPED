package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class OpenExportLinkAction extends AbstractAction {

    private static final long serialVersionUID = -2484826492967842455L;

    private AppGraphAnalytics app;

    public OpenExportLinkAction(AppGraphAnalytics app) {
        super();
        this.app = app;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ExportLinksDialog dialog = new ExportLinksDialog(app);
        dialog.setVisible(true);
    }

}
