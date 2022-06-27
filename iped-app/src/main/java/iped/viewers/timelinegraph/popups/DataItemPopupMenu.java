package iped.viewers.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;

import org.jfree.chart.entity.XYItemEntity;

import iped.data.IItemId;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.timelinegraph.TimeTableCumulativeXYDataset;


public class DataItemPopupMenu extends JPopupMenu implements ActionListener {
	XYItemEntity chartEntity;
	IMultiSearchResultProvider resultsProvider;
	
	JMenuItem selectEventItens;
	JMenuItem selectPeriodItens;
	JMenuItem checkEventItens;
	JMenuItem checkPeriodItens;
	
	public DataItemPopupMenu(IMultiSearchResultProvider resultsProvider) {
		this.resultsProvider = resultsProvider;

		selectEventItens = new JMenuItem("selecionar itens do evento do periodo");
		selectEventItens.addActionListener(this);
        add(selectEventItens);

		selectPeriodItens = new JMenuItem("selecionar itens do periodo");
        selectPeriodItens.addActionListener(this);
        add(selectPeriodItens); 

        checkEventItens = new JMenuItem("marcar itens do evento do periodo");
        checkEventItens.addActionListener(this);
        add(checkEventItens);

        checkPeriodItens = new JMenuItem("marcar itens do periodo");
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
			List<IItemId> items = ((TimeTableCumulativeXYDataset) chartEntity.getDataset()).getItems(chartEntity.getItem(), chartEntity.getSeriesIndex());
			JTable t = resultsProvider.getResultsTable();
			t.getSelectionModel().setValueIsAdjusting(true);

			try {
		        for (int i = 0; i < resultsProvider.getResults().getLength(); i++) {
		            IItemId item = resultsProvider.getResults().getItem(i);
		            if(items.contains(item)) {
		                int row = t.convertRowIndexToView(i);
		                t.addRowSelectionInterval(row, row);
		            }
		        }
			}finally{
				t.getSelectionModel().setValueIsAdjusting(false);
			}
		}
		if(e.getSource()==checkEventItens) {
			List<IItemId> items = ((TimeTableCumulativeXYDataset) chartEntity.getDataset()).getItems(chartEntity.getItem(), chartEntity.getSeriesIndex());
	        JTable t = resultsProvider.getResultsTable();

			for (int i = 0; i < resultsProvider.getResults().getLength(); i++) {
	            IItemId item = resultsProvider.getResults().getItem(i);
	            if(items.contains(item)) {
	            	Boolean checked = (Boolean) t.getValueAt(t.convertRowIndexToView(i), t.convertColumnIndexToView(1));
	    	        t.setValueAt(!checked.booleanValue(), t.convertRowIndexToView(i), t.convertColumnIndexToView(1));
	            }
	        }
		}
	}

}
