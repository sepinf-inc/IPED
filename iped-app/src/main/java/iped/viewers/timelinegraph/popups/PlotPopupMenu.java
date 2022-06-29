package iped.viewers.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jfree.chart.entity.XYItemEntity;

import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.timelinegraph.IpedChartPanel;

public class PlotPopupMenu extends JPopupMenu implements ActionListener{
	XYItemEntity chartEntity;
	IMultiSearchResultProvider resultsProvider;
	Date date;
	SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/YYYY");
	IpedChartPanel ipedChartPanel = null;

	JMenuItem filterBeforeMenu;
	JMenuItem filterAfterMenu;

	public PlotPopupMenu(IpedChartPanel ipedChartPanel, IMultiSearchResultProvider resultsProvider) {
		this.ipedChartPanel = ipedChartPanel;
		this.resultsProvider = resultsProvider;

		filterBeforeMenu = new JMenuItem("Filtrar anteriores a ");
		filterBeforeMenu.addActionListener(this);
        add(filterBeforeMenu);

        filterAfterMenu = new JMenuItem("Filtrar posteriores a ");
        filterAfterMenu.addActionListener(this);
        add(filterAfterMenu); 
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource()==filterBeforeMenu) {
			Date firstDate = new Date(0);
			ipedChartPanel.addFilter(firstDate, date);
		}
		if(e.getSource()==filterAfterMenu) {
			Date lastDate = new Date(Long.MAX_VALUE);
			ipedChartPanel.addFilter(date, lastDate);
		}
	}

	public void setDate(Date date) {
		this.date = date;
		
		filterBeforeMenu.setText("Filtrar anteriores a "+ sdf.format(date));
		filterAfterMenu.setText("Filtrar posteriores a "+ sdf.format(date));
	}	

}
