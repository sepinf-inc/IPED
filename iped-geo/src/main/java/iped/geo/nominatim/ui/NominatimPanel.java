package iped.geo.nominatim.ui;

import javax.swing.JScrollPane;
import javax.swing.JTree;

import bibliothek.gui.dock.common.DefaultSingleCDockable;
import iped.viewers.api.IMultiSearchResultProvider;

public class NominatimPanel extends JScrollPane {
    IMultiSearchResultProvider app;
    private JTree nominatimTree;

    static NominatimPanel singleton;

    private DefaultSingleCDockable nominatimTabDock;

    public NominatimPanel(IMultiSearchResultProvider app) {
        nominatimTree = new JTree();
        singleton = this;
    }

    public static void install(IMultiSearchResultProvider app) {
        NominatimTreeModel m = new NominatimTreeModel(app);
        m.install();
        singleton.setViewportView(singleton.nominatimTree);
        singleton.nominatimTree.setModel(m);

    }

}
