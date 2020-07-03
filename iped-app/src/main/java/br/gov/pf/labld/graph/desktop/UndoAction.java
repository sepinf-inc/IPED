package br.gov.pf.labld.graph.desktop;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class UndoAction extends AbstractAction {

    private static final long serialVersionUID = -9015593406903569725L;

    private AppGraphAnalytics app;

    public UndoAction(AppGraphAnalytics app) {
        super();
        this.app = app;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        app.undo();
    }

}
