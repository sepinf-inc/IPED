package iped.app.timelinegraph.swingworkers;

import javax.swing.JTable;

import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedDateAxis;
import iped.app.ui.BookmarksController;
import iped.app.ui.Messages;
import iped.viewers.api.IMultiSearchResultProvider;

/*
 * Extends BitSetSelectWorker to check (instead of highlight) docids setted in bitset
 */
public class EventPeriodCheckWorker extends BitSetHighlightWorker {

    public EventPeriodCheckWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider, RoaringBitmap bs,
            boolean clearPreviousSelection) {
        super(domainAxis, resultsProvider, bs, clearPreviousSelection);
    }

    public EventPeriodCheckWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider,
            boolean highlight, RoaringBitmap bs) {
        super(domainAxis, resultsProvider, bs, false);
        this.highlight = highlight;
    }

    @Override
    public void processResultsItem(JTable t, int i) {
        t.getModel().setValueAt(highlight, i, 1);
    }

    @Override
    protected void doProcess() {
        BookmarksController.get().setMultiSetting(true);
        super.doProcess();
    }

    @Override
    protected void done() {
        BookmarksController.get().setMultiSetting(false);
        resultsProvider.getIPEDSource().getMultiBookmarks().saveState();
        BookmarksController.get().updateUISelection();
        super.done();
    }

    @Override
    public String getProgressNote() {
        return Messages.get("TimeLineGraph.checkingItemsProgressLabel");
    }

}
