package iped.viewers.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import iped.app.ui.App;
import iped.viewers.timelinegraph.IpedChartPanel;

public class TimelineFilterSelectionPopupMenu extends JPopupMenu implements ActionListener{
	JMenuItem filterSelection;
	JMenuItem selectSelection;
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

		filterSelection = new JMenuItem("Filtrar a partir dos intervalos definidos");
		filterSelection.addActionListener(this);
        add(filterSelection);

        clearFilter = new JMenuItem("Excluir intervalo");
        clearFilter.addActionListener(this);
        add(clearFilter);

        selectSelection = new JMenuItem("Continuar seleção...");
        selectSelection.addActionListener(this);
        add(selectSelection);
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
	}

}
