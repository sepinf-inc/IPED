package iped.app.graph;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;

import iped.app.ui.App;

public class ExportImageAction extends AbstractAction {

    private static final long serialVersionUID = 7077031453212253610L;

    private AppGraphAnalytics app;

    public ExportImageAction(AppGraphAnalytics app) {
        super();
        this.app = app;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        int option = fileChooser.showSaveDialog(App.get());
        if (option == JFileChooser.APPROVE_OPTION) {
            app.exportImage(fileChooser.getSelectedFile());
        }
    }

}
