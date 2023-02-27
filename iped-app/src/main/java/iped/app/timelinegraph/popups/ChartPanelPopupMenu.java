package iped.app.timelinegraph.popups;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import iped.app.timelinegraph.IpedChartPanel;
import iped.app.timelinegraph.dialog.IntervalDefinitionDialog;
import iped.app.ui.App;
import iped.app.ui.Messages;

public class ChartPanelPopupMenu extends JPopupMenu implements ActionListener {
    IpedChartPanel ipedChartPanel;

    JCheckBoxMenuItem applyFiltersMenu;
    JCheckBoxMenuItem syncSelectionMenu;
    JMenuItem clearAllFiltersMenu;
    JMenuItem resetGraphZoom;

    private JMenuItem openIntervalsDialogMenu;

    public ChartPanelPopupMenu(IpedChartPanel ipedChartPanel) {
        this.ipedChartPanel = ipedChartPanel;

        applyFiltersMenu = new JCheckBoxMenuItem(Messages.getString("TimeLineGraph.applyDefinedFilters"));
        applyFiltersMenu.addActionListener(this);
        add(applyFiltersMenu);

        clearAllFiltersMenu = new JMenuItem(Messages.getString("TimeLineGraph.clearDefinedFilters"));
        clearAllFiltersMenu.addActionListener(this);
        add(clearAllFiltersMenu);

        openIntervalsDialogMenu = new JMenuItem(Messages.getString("TimeLineGraph.openIntervalsDialogMenu"));
        openIntervalsDialogMenu.addActionListener(this);
        add(openIntervalsDialogMenu);

        syncSelectionMenu = new JCheckBoxMenuItem(Messages.getString("TimeLineGraph.syncTableSelectionInChartView"));
        syncSelectionMenu.addActionListener(this);
        add(syncSelectionMenu);

        resetGraphZoom = new JMenuItem(Messages.getString("TimeLineGraph.resetZoom"));
        resetGraphZoom.addActionListener(this);
        add(resetGraphZoom);

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == openIntervalsDialogMenu) {
            (new IntervalDefinitionDialog(ipedChartPanel)).setVisible();
        }

        if (e.getSource() == syncSelectionMenu) {
            ipedChartPanel.getIpedChartsPanel().setSyncViewWithTableSelection(syncSelectionMenu.isSelected());
            if (syncSelectionMenu.isSelected()) {
                ipedChartPanel.getIpedChartsPanel().showSelection();
            }
        }

        if (e.getSource() == applyFiltersMenu) {
            if (!ipedChartPanel.hasNoFilter()) {
                ipedChartPanel.getIpedChartsPanel().setApplyFilters(applyFiltersMenu.isSelected());
                App app = (App) ipedChartPanel.getIpedChartsPanel().getGUIProvider();
                ipedChartPanel.filterSelection();
                app.setDockablesColors();
            }
        }
        if (e.getSource() == clearAllFiltersMenu) {
            ipedChartPanel.removeAllFilters();
        }

        if (e.getSource() == resetGraphZoom) {
            ipedChartPanel.getIpedChartsPanel().resetZoom();
        }
    }

    @Override
    public void show(Component invoker, int x, int y) {
        applyFiltersMenu.setSelected(ipedChartPanel.getIpedChartsPanel().hasFiltersApplied());
        super.show(invoker, x, y);
    }
}
