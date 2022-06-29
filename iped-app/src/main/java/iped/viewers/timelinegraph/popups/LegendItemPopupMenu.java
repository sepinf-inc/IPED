package iped.viewers.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jfree.chart.entity.LegendItemEntity;
import org.jfree.chart.plot.XYPlot;

import iped.app.ui.Messages;
import iped.viewers.timelinegraph.IpedChartPanel;
import iped.viewers.timelinegraph.IpedCombinedDomainXYPlot;

public class LegendItemPopupMenu extends JPopupMenu implements ActionListener {
	IpedChartPanel ipedChartPanel;
	
	LegendItemEntity legendItemEntity;

	JCheckBoxMenuItem hide;
	JMenuItem filter;

	public LegendItemPopupMenu(IpedChartPanel ipedChartPanel) {
		this.ipedChartPanel = ipedChartPanel;		

		hide = new JCheckBoxMenuItem(Messages.getString("TimeLineGraph.hideSeriesOnChart"));
		hide.setSelected(false);
		hide.addActionListener(this);
        add(hide);

		filter = new JMenuItem(Messages.getString("TimeLineGraph.filterEventFromResultSet"));
		filter.addActionListener(this);
        add(filter); 
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource()==hide) {
			IpedCombinedDomainXYPlot rootPlot = ((IpedCombinedDomainXYPlot) ipedChartPanel.getChart().getPlot());
			List<XYPlot> xyPlots = rootPlot.getSubplots();

			for (XYPlot xyPlot : xyPlots) {
				for(int i=0; i<xyPlot.getDataset(0).getSeriesCount(); i++) {
					String currSeries = (String) xyPlot.getDataset(0).getSeriesKey(i);
					if(currSeries.equals(legendItemEntity.getSeriesKey())) {
						rootPlot.getRenderer().setPlot(xyPlot);
						rootPlot.getRenderer().setSeriesVisible(i, !rootPlot.getRenderer().isSeriesVisible(i), true);
					}
				}
			}

		}
	}

	public void setLegendItemEntity(LegendItemEntity le) {
		this.legendItemEntity=le;

		IpedCombinedDomainXYPlot rootPlot = ((IpedCombinedDomainXYPlot) ipedChartPanel.getChart().getPlot());
		List<XYPlot> xyPlots = rootPlot.getSubplots();

		for (XYPlot xyPlot : xyPlots) {
			for(int i=0; i<xyPlot.getDataset(0).getSeriesCount(); i++) {
				String currSeries = (String) xyPlot.getDataset(0).getSeriesKey(i);
				if(currSeries.equals(legendItemEntity.getSeriesKey())) {					
					hide.setSelected(rootPlot.getRenderer().isSeriesVisible(i));
					
					return;
				}
			}
		}
	}
}
