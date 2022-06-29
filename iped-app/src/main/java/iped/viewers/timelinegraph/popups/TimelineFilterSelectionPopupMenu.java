package iped.viewers.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.viewers.timelinegraph.IpedChartPanel;

public class TimelineFilterSelectionPopupMenu extends JPopupMenu implements ActionListener{
	JMenuItem filterSelection;
	JMenuItem selectItems;
	JMenuItem continueSelection;
	JMenuItem clearFilter;
	IpedChartPanel ipedChartPanel;
	Date[] dates = null;

	public Date[] getDates() {
		return dates;
	}

	public void setDates(Date[] dates) {
		this.dates = dates;
	}

	public TimelineFilterSelectionPopupMenu(IpedChartPanel ipedChartPanel) {
		this.ipedChartPanel = ipedChartPanel;

		filterSelection = new JMenuItem(Messages.getString("TimeLineGraph.filterAllDefinedIntervals"));
		filterSelection.addActionListener(this);
        add(filterSelection);

        selectItems = new JMenuItem(Messages.getString("TimeLineGraph.selectItensOnThisInterval"));
        selectItems.addActionListener(this);
        add(selectItems);

        clearFilter = new JMenuItem(Messages.getString("TimeLineGraph.deleteInterval"));
        clearFilter.addActionListener(this);
        add(clearFilter);

        continueSelection = new JMenuItem(Messages.getString("TimeLineGraph.continueIntervalSelection"));
        continueSelection.addActionListener(this);
        add(continueSelection);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource()==clearFilter) {
			ipedChartPanel.removeFilter(dates);
			ipedChartPanel.repaint();
		}

		if(e.getSource()==filterSelection) {
			ipedChartPanel.getIpedChartsPanel().setApplyFilters(true);
			App app = (App) ipedChartPanel.getIpedChartsPanel().getResultsProvider();			
			app.getAppListener().updateFileListing();
		}

		if(e.getSource()==selectItems) {
			ipedChartPanel.getIpedChartsPanel().selectItemsOnInterval(dates[0], dates[1], false);
		}
	}

}
