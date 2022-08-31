package iped.viewers.timelinegraph.swingworkers;

import java.util.BitSet;
import java.util.Date;

import javax.swing.JTable;

import iped.app.ui.BookmarksController;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.timelinegraph.IpedDateAxis;


/*
 * Extends BitSetSelectWorker to check (instead of highlight) docids setted in bitset
 */
public class EventPeriodCheckWorker extends BitSetSelectWorker {

	public EventPeriodCheckWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider, BitSet bs,
			boolean clearPreviousSelection) {
		super(domainAxis, resultsProvider, bs, clearPreviousSelection);
	}

	@Override
	public void processResultsItem(JTable t, int i) {
        Boolean checked = (Boolean) t.getModel().getValueAt(i, 1);
        t.getModel().setValueAt(select, i, 1);
	}

	@Override
	protected void doSelect() {
		BookmarksController.get().setMultiSetting(true);
		super.doSelect();
	}

	@Override
	protected void done() {
		BookmarksController.get().setMultiSetting(false);
		resultsProvider.getIPEDSource().getMultiBookmarks().saveState();
        BookmarksController.get().updateUISelection();
		super.done();
	}


}
