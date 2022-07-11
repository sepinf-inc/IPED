package iped.viewers.timelinegraph.swingworkers;

import java.awt.Dialog.ModalityType;
import java.util.Date;
import java.util.List;

import javax.swing.JTable;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.data.IItemId;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.viewers.api.CancelableWorker;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.timelinegraph.DateUtil;
import iped.viewers.timelinegraph.IpedChartsPanel;
import iped.viewers.timelinegraph.IpedDateAxis;
import iped.viewers.util.ProgressDialog;

public class SelectWorker extends CancelableWorker<Void, Void> {
	IMultiSearchResultProvider resultsProvider;
	IpedDateAxis domainAxis;
	Date start;
	Date end;
	boolean clearPreviousSelection;
    ProgressDialog progressDialog;
    boolean select = true;
    String eventType=null;

	public SelectWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider,Date start, Date end, boolean clearPreviousSelection) {
		this.resultsProvider = resultsProvider;
		this.start = start;
		this.end = end;
		this.clearPreviousSelection=clearPreviousSelection;
	}

	public SelectWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider,Date start, Date end, boolean select, boolean clearPreviousSelection) {
		this.resultsProvider = resultsProvider;
		this.start = start;
		this.end = end;
		this.clearPreviousSelection=clearPreviousSelection;
		this.select = select;
	}

	public SelectWorker(String eventType, IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider, Date start, Date end,
			boolean b) {
		this.resultsProvider = resultsProvider;
		this.start = start;
		this.end = end;
		this.clearPreviousSelection=clearPreviousSelection;
		this.select = select;
		this.eventType = eventType;
	}

	@Override
	protected Void doInBackground() throws Exception {
        progressDialog = new ProgressDialog(App.get(), this, true, 0, ModalityType.TOOLKIT_MODAL);
        progressDialog.setNote(Messages.get("TimeLineGraph.highlightingItemsProgressLabel"));
        selectItemsOnInterval(start, end, select, clearPreviousSelection);
		return null;
	}

	@Override
	public boolean doCancel(boolean mayInterrupt) {
        if (progressDialog != null)
            progressDialog.close();

        JTable t = resultsProvider.getResultsTable();
		t.getSelectionModel().setValueIsAdjusting(false);

        return cancel(mayInterrupt);
	}

	@Override
	protected void done() {
        if (progressDialog != null)
            progressDialog.close();
	}

	public void selectItemsOnInterval(Date start, Date end, boolean clearPreviousSelection) {
		selectItemsOnInterval(start, end, true, clearPreviousSelection);
	}

	public void selectItemsOnInterval(Date start, Date end, boolean select, boolean clearPreviousSelection) {
		JTable t = resultsProvider.getResultsTable();
		t.getSelectionModel().setValueIsAdjusting(true);

		if(clearPreviousSelection) {
			t.clearSelection();
		}

        LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();

        SortedSetDocValues timeStampValues;
        SortedSetDocValues timeEventGroupValues;

        try {
            timeStampValues = reader.getSortedSetDocValues(BasicProps.TIMESTAMP);
    		timeEventGroupValues = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
    		BinaryDocValues eventsInDocOrdsValues = reader.getBinaryDocValues(ExtraProperties.TIME_EVENT_ORDS);

    		for (int i = 0; i < resultsProvider.getResults().getLength(); i++) {
                IItemId item = resultsProvider.getResults().getItem(i);

                int luceneId = resultsProvider.getIPEDSource().getLuceneId(item);
                
                boolean tsvAdv = timeStampValues.advanceExact(luceneId);
                
                if(tsvAdv) {
                	long ord;
    	            while ((ord = timeStampValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
    	                if (ord > Integer.MAX_VALUE) {
    	                    throw new RuntimeException("Integer overflow when converting timestamp ord to int");
    	                }
    	                
    	                Date d = domainAxis.ISO8601DateParse(timeStampValues.lookupOrd(ord).utf8ToString());
    	                if(d!=null) {
        	                if(start.getTime()<=d.getTime() && end.getTime()>=d.getTime()) {
        		                int row = t.convertRowIndexToView(i);
        		                if(select) {
            		                t.addRowSelectionInterval(row, row);
        		                }else {
        		                	t.removeRowSelectionInterval(row, row);
        		                }
        	                }
    	                }
    	            }
                }
            }

    		System.out.println("selecao finalizada");
        }catch(Exception e) {
        	e.printStackTrace();
        }finally {
    		t.getSelectionModel().setValueIsAdjusting(false);
		}
	}
}
