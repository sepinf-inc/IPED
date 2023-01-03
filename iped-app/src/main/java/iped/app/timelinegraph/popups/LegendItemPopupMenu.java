package iped.app.timelinegraph.popups;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.List;

import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.LegendItemBlockContainer;

import iped.app.timelinegraph.IpedChartPanel;
import iped.app.timelinegraph.IpedCombinedDomainXYPlot;
import iped.app.ui.App;
import iped.app.ui.Messages;

public class LegendItemPopupMenu extends JPopupMenu implements ActionListener {
    IpedChartPanel ipedChartPanel;

    JMenuItem hide;
    JMenuItem show;
    JMenuItem filter;
    JMenuItem unfilter;

    public LegendItemPopupMenu(IpedChartPanel ipedChartPanel) {
        this.ipedChartPanel = ipedChartPanel;

        hide = new JMenuItem(Messages.getString("TimeLineGraph.hideSeriesOnChart"));
        hide.addActionListener(this);
        add(hide);

        show = new JMenuItem("Show");
        show.addActionListener(this);
        add(show);

        filter = new JMenuItem(Messages.getString("TimeLineGraph.filterEventFromResultSet"));
        filter.addActionListener(this);
        add(filter);

        unfilter = new JMenuItem(Messages.getString("TimeLineGraph.unfilterEventFromResultSet", "Unfilter"));
        unfilter.addActionListener(this);
        add(unfilter);
    }

    public boolean isSelected(List<LegendItemBlockContainer> selLegends, String currSeries) {
        for (Iterator iterator = selLegends.iterator(); iterator.hasNext();) {
            LegendItemBlockContainer legendItemBlockContainer = (LegendItemBlockContainer) iterator.next();
            if (legendItemBlockContainer.getSeriesKey().equals(currSeries)) {
                return true;
            }
        }
        return false;
    }

    public void hideSelection(List<LegendItemBlockContainer> selLegends) {
        IpedCombinedDomainXYPlot rootPlot = ((IpedCombinedDomainXYPlot) ipedChartPanel.getChart().getPlot());
        List<XYPlot> xyPlots = rootPlot.getSubplots();

        for (XYPlot xyPlot : xyPlots) {
            for (int i = 0; i < xyPlot.getDataset(0).getSeriesCount(); i++) {
                String currSeries = (String) xyPlot.getDataset(0).getSeriesKey(i);
                if (isSelected(selLegends, currSeries)) {
                    rootPlot.getRenderer().setPlot(xyPlot);
                    ipedChartPanel.getHiddenEvents().add(currSeries);
                    if (rootPlot.getRenderer().isSeriesVisible(i)) {
                        rootPlot.getRenderer().setSeriesVisible(i, false, true);
                    }
                }
            }
        }
    }

    public void showSelection(List<LegendItemBlockContainer> selLegends) {
        IpedCombinedDomainXYPlot rootPlot = ((IpedCombinedDomainXYPlot) ipedChartPanel.getChart().getPlot());
        List<XYPlot> xyPlots = rootPlot.getSubplots();

        for (XYPlot xyPlot : xyPlots) {
            for (int i = 0; i < xyPlot.getDataset(0).getSeriesCount(); i++) {
                String currSeries = (String) xyPlot.getDataset(0).getSeriesKey(i);
                if (isSelected(selLegends, currSeries)) {
                    rootPlot.getRenderer().setPlot(xyPlot);
                    ipedChartPanel.getHiddenEvents().remove(currSeries);
                    if (!rootPlot.getRenderer().isSeriesVisible(i)) {
                        rootPlot.getRenderer().setSeriesVisible(i, true, true);
                    }
                }
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        List<LegendItemBlockContainer> selLegends = ipedChartPanel.getIpedChartsPanel().getLegendList().getSelectedValuesList();

        if (e.getSource() == hide) {
            hideSelection(selLegends);
        }
        if (e.getSource() == show) {
            showSelection(selLegends);
        }

        if (e.getSource() == filter) {
            IpedCombinedDomainXYPlot rootPlot = ((IpedCombinedDomainXYPlot) ipedChartPanel.getChart().getPlot());
            List<XYPlot> xyPlots = rootPlot.getSubplots();

            for (XYPlot xyPlot : xyPlots) {
                for (int i = 0; i < xyPlot.getDataset(0).getSeriesCount(); i++) {
                    String currSeries = (String) xyPlot.getDataset(0).getSeriesKey(i);
                    if (isSelected(selLegends, currSeries)) {
                        if (!ipedChartPanel.getExcludedEvents().contains(currSeries)) {
                            ipedChartPanel.getExcludedEvents().add(currSeries);
                        }
                        xyPlot.getRenderer().setPlot(xyPlot);
                    }
                }
            }

            if (ipedChartPanel.hasNoFilter()) {
                ipedChartPanel.getIpedChartsPanel().setApplyFilters(false);
                App app = (App) ipedChartPanel.getIpedChartsPanel().getGUIProvider();
                app.setDockablesColors();
            } else {
                ipedChartPanel.getIpedChartsPanel().setApplyFilters(true);
            }
            ipedChartPanel.filterSelection();
        }

        if (e.getSource() == unfilter) {
            IpedCombinedDomainXYPlot rootPlot = ((IpedCombinedDomainXYPlot) ipedChartPanel.getChart().getPlot());
            List<XYPlot> xyPlots = rootPlot.getSubplots();

            for (XYPlot xyPlot : xyPlots) {
                for (int i = 0; i < xyPlot.getDataset(0).getSeriesCount(); i++) {
                    String currSeries = (String) xyPlot.getDataset(0).getSeriesKey(i);
                    if (isSelected(selLegends, currSeries)) {
                        ipedChartPanel.getExcludedEvents().remove(currSeries);
                        xyPlot.getRenderer().setPlot(xyPlot);
                    }
                }
            }

            if (ipedChartPanel.hasNoFilter()) {
                ipedChartPanel.getIpedChartsPanel().setApplyFilters(false);
                App app = (App) ipedChartPanel.getIpedChartsPanel().getGUIProvider();
                app.setDockablesColors();
            } else {
                ipedChartPanel.getIpedChartsPanel().setApplyFilters(true);
            }
            ipedChartPanel.filterSelection();
        }
    }

    @Override
    public void show(Component invoker, int x, int y) {
        JList list = ipedChartPanel.getIpedChartsPanel().getLegendList();
        List<LegendItemBlockContainer> selLegends = list.getSelectedValuesList();
        boolean selectionContainsExcluded = false;
        boolean selectionContainsIncluded = false;
        boolean selectionContainsHidden = false;
        boolean selectionContainsNotHidden = false;
        for (Iterator iterator = selLegends.iterator(); iterator.hasNext();) {
            LegendItemBlockContainer legendItemBlockContainer = (LegendItemBlockContainer) iterator.next();
            if (ipedChartPanel.getExcludedEvents().contains(legendItemBlockContainer.getSeriesKey())) {
                selectionContainsExcluded = true;
            } else {
                selectionContainsIncluded = true;
            }
            if (ipedChartPanel.getHiddenEvents().contains(legendItemBlockContainer.getSeriesKey())) {
                selectionContainsHidden = true;
            } else {
                selectionContainsNotHidden = true;
            }
            if (selectionContainsIncluded && selectionContainsExcluded && selectionContainsHidden && selectionContainsNotHidden) {
                break;
            }
        }

        if (selectionContainsExcluded) {
            unfilter.setEnabled(true);
        } else {
            unfilter.setEnabled(false);
        }

        if (selectionContainsIncluded) {
            filter.setEnabled(true);
        } else {
            filter.setEnabled(false);
        }

        if (!selectionContainsHidden) {
            show.setEnabled(false);
        } else {
            show.setEnabled(true);
        }
        if (!selectionContainsNotHidden) {
            hide.setEnabled(false);
        } else {
            hide.setEnabled(true);
        }

        super.show(invoker, x, y);
    }
}
