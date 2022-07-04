package iped.viewers.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jfree.chart.entity.XYItemEntity;

import iped.app.ui.Messages;
import iped.data.IItemId;
import iped.viewers.timelinegraph.IpedChartPanel;
import iped.viewers.timelinegraph.TimeTableCumulativeXYDataset;


public class DataItemPopupMenu extends JPopupMenu implements ActionListener {
	XYItemEntity chartEntity;
	IpedChartPanel ipedChartPanel;

	JMenuItem selectEventItens;
	JMenuItem selectPeriodItens;
	JMenuItem checkEventItens;
	JMenuItem checkPeriodItens;

	List<XYItemEntity> entityList;

	public DataItemPopupMenu(IpedChartPanel ipedChartPanel) {
		this.ipedChartPanel = ipedChartPanel;
		
		selectEventItens = new JMenuItem(Messages.getString("TimeLineGraph.selectEventItensOnPeriod"));
		selectEventItens.addActionListener(this);
        add(selectEventItens);

		selectPeriodItens = new JMenuItem(Messages.getString("TimeLineGraph.selectItensOnPeriod"));
        selectPeriodItens.addActionListener(this);
        add(selectPeriodItens); 

        checkEventItens = new JMenuItem(Messages.getString("TimeLineGraph.checkEventItensOnPeriod"));
        checkEventItens.addActionListener(this);
        add(checkEventItens);

        checkPeriodItens = new JMenuItem(Messages.getString("TimeLineGraph.checkItensOnPeriod"));
        checkPeriodItens.addActionListener(this);
        add(checkPeriodItens); 


	}	

	public XYItemEntity getChartEntity() {
		return chartEntity;
	}

	public void setChartEntity(XYItemEntity chartEntity) {
		this.chartEntity = chartEntity;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource()==selectEventItens) {
			Calendar c = Calendar.getInstance(ipedChartPanel.getIpedChartsPanel().getTimeZone());
			c.setTime(new Date((long)chartEntity.getDataset().getXValue(chartEntity.getSeriesIndex(), chartEntity.getItem())));
			Calendar cEnd = (Calendar) c.clone();
			cEnd.add(Calendar.DAY_OF_MONTH, 1);
			
			ipedChartPanel.getIpedChartsPanel().selectItemsOnInterval((String) chartEntity.getDataset().getSeriesKey(chartEntity.getSeriesIndex()), c.getTime(), cEnd.getTime(), true);
		}
		if(e.getSource()==selectPeriodItens) {
			Calendar c = Calendar.getInstance(ipedChartPanel.getIpedChartsPanel().getTimeZone());
			c.setTime(new Date((long)chartEntity.getDataset().getXValue(chartEntity.getSeriesIndex(), chartEntity.getItem())));
			Calendar cEnd = (Calendar) c.clone();
			cEnd.add(Calendar.DAY_OF_MONTH, 1);
			ipedChartPanel.getIpedChartsPanel().selectItemsOnInterval(c.getTime(), cEnd.getTime(), true);
		}
		if(e.getSource()==checkEventItens) {
			List<IItemId> items = ((TimeTableCumulativeXYDataset) chartEntity.getDataset()).getItems(chartEntity.getItem(), chartEntity.getSeriesIndex());
			ipedChartPanel.getIpedChartsPanel().checkItems(items);
		}
		if(e.getSource()==checkPeriodItens) {
			ArrayList<IItemId> items = new ArrayList<IItemId>();
			for (XYItemEntity xyItemEntity : entityList) {
				items.addAll(((TimeTableCumulativeXYDataset) xyItemEntity.getDataset()).getItems(xyItemEntity.getItem(), xyItemEntity.getSeriesIndex()));
			}
			ipedChartPanel.getIpedChartsPanel().checkItems(items);
		}
	}

	public void setChartEntityList(List<XYItemEntity> entityList) {
		this.entityList=entityList;
	}

}
