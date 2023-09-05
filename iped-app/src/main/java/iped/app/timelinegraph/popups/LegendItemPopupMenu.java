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
import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedChartPanel;
import iped.app.timelinegraph.IpedCombinedDomainXYPlot;
import iped.app.timelinegraph.datasets.IpedTimelineDataset;
import iped.app.timelinegraph.swingworkers.EventPeriodCheckWorker;
import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.engine.data.IPEDSource;
import iped.viewers.api.IMultiSearchResultProvider;

public class LegendItemPopupMenu extends JPopupMenu implements ActionListener {
    IpedChartPanel ipedChartPanel;
    JMenuItem hide;
    JMenuItem show;
    JMenuItem filter;
    JMenuItem unfilter;

    JMenuItem selectAll;
    JMenuItem unselectAll;

    JMenuItem checkItems;
    JMenuItem uncheckItems;

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

        selectAll = new JMenuItem("Select all event types");
        selectAll.addActionListener(this);
        add(selectAll);
        
        unselectAll = new JMenuItem("Unselect all event types");
        unselectAll.addActionListener(this);
        add(unselectAll);

        checkItems = new JMenuItem("Check corresponding events items");
        checkItems.addActionListener(this);
        add(checkItems);
        
        uncheckItems = new JMenuItem("Uncheck corresponding events items");
        uncheckItems.addActionListener(this);
        add(uncheckItems);
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

    public void checkEventItems(List<LegendItemBlockContainer> selLegends) {
        List<LegendItemBlockContainer> selLegendsList = ipedChartPanel.getIpedChartsPanel().getLegendList().getSelectedValuesList();
        if (selLegends != null && selLegends.size() > 0) {
            LegendItemBlockContainer libc = selLegendsList.get(0);
            IpedTimelineDataset ds = (IpedTimelineDataset) libc.getDataset();

            RoaringBitmap bs = new RoaringBitmap();// creates empty bitset
            IMultiSearchResultProvider msrp = ipedChartPanel.getIpedChartsPanel().getResultsProvider();
            IPEDSource is = (IPEDSource) msrp.getIPEDSource();

            for (int i = 0; i < selLegends.size(); i++) {
                libc = selLegendsList.get(i);
                ds.bitSetItems(libc.getSeriesKey().toString(), bs);// set bits of items in serie
            }

            EventPeriodCheckWorker bsCheck = new EventPeriodCheckWorker(
                    ipedChartPanel.getIpedChartsPanel().getDomainAxis(), msrp, bs, true);
            bsCheck.execute();
        }
    }
    
    public void unselectEvents(List<LegendItemBlockContainer> selLegends) {
        List<LegendItemBlockContainer> selLegendsList = ipedChartPanel.getIpedChartsPanel().getLegendList().getSelectedValuesList();
    	for (int i = 0; i < selLegendsList.size(); i++) {
    		LegendItemBlockContainer valor = selLegendsList.get(i);
    		
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

        //IMPORTANT
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
        
      //IMPORTANT
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

        //IMPORTANT
        if (e.getSource() == checkItems) {
            checkEventItems(selLegends);
        }
        if (e.getSource() == uncheckItems) {
            unselectEvents(selLegends);
        }

        if (e.getSource() == selectAll) {
            JList list = ipedChartPanel.getIpedChartsPanel().getLegendList();
            list.addSelectionInterval(0, list.getModel().getSize());
        }

        if (e.getSource() == unselectAll) {
            JList list = ipedChartPanel.getIpedChartsPanel().getLegendList();
            list.removeSelectionInterval(0, list.getModel().getSize());
        }

    }

    @Override
    public void show(Component invoker, int x, int y){
        JList list = ipedChartPanel.getIpedChartsPanel().getLegendList();
        List<LegendItemBlockContainer> selLegends = list.getSelectedValuesList();
        boolean selectionContainsExcluded = false;
        boolean selectionContainsIncluded = false;
        boolean selectionContainsHidden = false;
        boolean selectionContainsNotHidden = false;
        boolean selectionContainsSelect = false;
        boolean selectionContainsUnselect = false;
        
        if (selLegends.isEmpty()) {
            unselectAll.setEnabled(false);
        } else {
            unselectAll.setEnabled(true);
        }

        if (selLegends.size() >= list.getModel().getSize()) {
            selectAll.setEnabled(false);
        } else {
            selectAll.setEnabled(true);
        }

        for (Iterator iterator = selLegends.iterator(); iterator.hasNext();) {
            LegendItemBlockContainer legendItemBlockContainer = (LegendItemBlockContainer) iterator.next();
            
            if(ipedChartPanel.getExcludedEvents().contains(legendItemBlockContainer.getSeriesKey())){
                selectionContainsExcluded = true;
            }
            else{
                selectionContainsIncluded = true;
            }
            
            if(ipedChartPanel.getHiddenEvents().contains(legendItemBlockContainer.getSeriesKey())){
                selectionContainsHidden = true;
            }else{
                selectionContainsNotHidden = true;
            }
            
            if(ipedChartPanel.getSelectedEvents().contains(legendItemBlockContainer.getSeriesKey())){
                selectionContainsSelect = true;
            }else{
                selectionContainsUnselect = true;
            }
            
            if(selectionContainsIncluded && selectionContainsExcluded && selectionContainsHidden && selectionContainsNotHidden && selectionContainsSelect && selectionContainsUnselect){
                break;
            }
        }
        
        //Unfilter
        if(selectionContainsExcluded){
            unfilter.setEnabled(true);
        }
        else{
            unfilter.setEnabled(false);
        }

        //Filter
        if(selectionContainsIncluded){
            filter.setEnabled(true);
        }
        else{
            filter.setEnabled(false);
        }

        //Show
        if(!selectionContainsHidden){
            show.setEnabled(false);
        }
        else{
            show.setEnabled(true);
        }
        
        //Hide
        if(!selectionContainsNotHidden){
            hide.setEnabled(false);
        }
        else{
            hide.setEnabled(true);
        }
        
        super.show(invoker, x, y);
    }
}