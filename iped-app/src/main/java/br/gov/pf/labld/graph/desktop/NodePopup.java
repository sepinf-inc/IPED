package br.gov.pf.labld.graph.desktop;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.kharon.Node;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class NodePopup extends JPopupMenu implements PopupMenuListener {

    private static final long serialVersionUID = 1421878293257791515L;

    private JMenuItem expandItem;
    private JMenuItem expandConfigurationItem;
    private JMenuItem connectItem;
    private JMenuItem findPathsItem;
    private JMenuItem removeItem;
    private JMenuItem showItem;

    private AppGraphAnalytics app;

    public NodePopup(AppGraphAnalytics appGraphAnalytics) {
        super();
        this.app = appGraphAnalytics;
        createItems(appGraphAnalytics);
    }

    public NodePopup(String label, AppGraphAnalytics appGraphAnalytics) {
        super(label);
        createItems(appGraphAnalytics);
    }

    private void createItems(AppGraphAnalytics app) {
        this.addPopupMenuListener(this);

        expandItem = new JMenuItem(new ExpandSelectedAction(app));
        expandItem.setText(Messages.getString("GraphAnalysis.Expand"));

        expandConfigurationItem = new JMenuItem(new ExpandConfigurationAction(app));
        expandConfigurationItem.setText(Messages.getString("GraphAnalysis.ExpandConfiguration"));

        connectItem = new JMenuItem(new ConnectSelectedAction(app));
        connectItem.setText(Messages.getString("GraphAnalysis.FindConnectios"));

        findPathsItem = new JMenuItem(new FindPathsAction(app));
        findPathsItem.setText(Messages.getString("GraphAnalysis.FindPaths"));

        removeItem = new JMenuItem(new RemoveSelectedAction(app));
        removeItem.setText(Messages.getString("GraphAnalysis.Remove"));

        showItem = new JMenuItem(new ShowEvidenceAction(app));
        showItem.setText(Messages.getString("GraphAnalysis.ShowEvidence"));

        add(expandItem);
        add(expandConfigurationItem);
        add(connectItem);
        add(findPathsItem);

        addSeparator();

        add(removeItem);
        add(showItem);
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        int numSelected = this.app.getNumSelected();
        boolean multipleSelected = numSelected > 1;

        this.connectItem.setEnabled(multipleSelected);
        this.findPathsItem.setEnabled(multipleSelected);

        Node hoveredNode = this.app.getHoveredNode();
        boolean isEvidenceSelected = hoveredNode != null && !multipleSelected;
        if (hoveredNode != null) {
            isEvidenceSelected = isEvidenceSelected && hoveredNode.getType().equals("EVIDENCIA");
        }
        this.showItem.setEnabled(isEvidenceSelected);
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {

    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {

    }

}
