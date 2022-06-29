package iped.viewers.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jfree.chart.entity.XYItemEntity;

import iped.app.ui.Messages;
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
	JMenuItem selectBeforeMenu;
	JMenuItem selectAfterMenu;

	public PlotPopupMenu(IpedChartPanel ipedChartPanel, IMultiSearchResultProvider resultsProvider) {
		this.ipedChartPanel = ipedChartPanel;
		this.resultsProvider = resultsProvider;

		
		filterBeforeMenu = new JMenuItem(" ");
		filterBeforeMenu.addActionListener(this);
        add(filterBeforeMenu);

        selectBeforeMenu = new JMenuItem(" ");
        selectBeforeMenu.addActionListener(this);
        add(selectBeforeMenu);

        filterAfterMenu = new JMenuItem(" ");
        filterAfterMenu.addActionListener(this);
        add(filterAfterMenu); 

        selectAfterMenu = new JMenuItem(" ");
        selectAfterMenu.addActionListener(this);
        add(selectAfterMenu);
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
		
		filterBeforeMenu.setText(Messages.getString("TimeLineGraph.filterBefore")+" "+ sdf.format(date));
		filterAfterMenu.setText(Messages.getString("TimeLineGraph.filterAfter")+" "+ sdf.format(date));
		selectBeforeMenu.setText(Messages.getString("TimeLineGraph.selectBefore")+" "+ sdf.format(date));
		selectAfterMenu.setText(Messages.getString("TimeLineGraph.selectAfter")+" "+ sdf.format(date));
	}	

}
