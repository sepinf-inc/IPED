package iped.viewers.timelinegraph.swingworkers;

import java.awt.Dialog.ModalityType;
import java.util.List;

import javax.swing.JTable;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.data.IItemId;
import iped.viewers.api.CancelableWorker;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.util.ProgressDialog;

public class CheckWorker extends CancelableWorker<Void, Void> {
	IMultiSearchResultProvider resultsProvider;
	List<IItemId> items;
    ProgressDialog progressDialog;

	
	public CheckWorker(IMultiSearchResultProvider resultsProvider,List<IItemId> items) {
		this.resultsProvider = resultsProvider;
		this.items = items;
	}

	@Override
	protected Void doInBackground() throws Exception {
        progressDialog = new ProgressDialog(App.get(), this, true, 0, ModalityType.TOOLKIT_MODAL);
        progressDialog.setNote(Messages.get("TimeLineGraph.checkingItemsProgressLabel"));
        checkItems(items);
		return null;
	}
	
	public void checkItems(List<IItemId> items) {
        JTable t = resultsProvider.getResultsTable();
		t.getSelectionModel().setValueIsAdjusting(true);

		for (int i = 0; i < resultsProvider.getResults().getLength(); i++) {
        	if(isCancelled())return;
            IItemId item = resultsProvider.getResults().getItem(i);
            if(items.contains(item)) {
            	Boolean checked = (Boolean) t.getValueAt(t.convertRowIndexToView(i), t.convertColumnIndexToView(1));
    	        t.setValueAt(!checked.booleanValue(), t.convertRowIndexToView(i), t.convertColumnIndexToView(1));
            }
        }
		t.getSelectionModel().setValueIsAdjusting(false);
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


}
