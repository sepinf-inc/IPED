package iped.app.timelinegraph.swingworkers;

import java.awt.Dialog.ModalityType;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import javax.swing.JTable;

import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedDateAxis;
import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.data.IItemId;
import iped.viewers.api.CancelableWorker;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.util.ProgressDialog;

/*
 * Worker that highlights list of docids setted in a bitset
 * Opens a modal progress dialog while executing.
 */
public class BitSetHighlightWorker extends CancelableWorker<Void, Void> {
    IMultiSearchResultProvider resultsProvider;
    IpedDateAxis domainAxis;
    RoaringBitmap bs;
    boolean clearPreviousItemsHighlighted;
    ProgressDialog progressDialog;
    boolean highlight = true;
    JTable t = null;

    public String getProgressNote() {
        return Messages.get("TimeLineGraph.highlightingItemsProgressLabel");
    }

    public BitSetHighlightWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider, RoaringBitmap bs,
            boolean highlight, boolean clearPreviousSelection) {
        this.resultsProvider = resultsProvider;
        this.t = resultsProvider.getResultsTable();
        this.clearPreviousItemsHighlighted = clearPreviousSelection;
        this.bs = bs;
        this.highlight = highlight;
        this.domainAxis = domainAxis;
    }

    public BitSetHighlightWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider, RoaringBitmap bs,
            boolean clearPreviousSelection) {
        this(domainAxis, resultsProvider, bs, true, clearPreviousSelection);
    }

    protected void doProcess() {
        highlightDocIdsParallel(bs, highlight, clearPreviousItemsHighlighted);
    }

    @Override
    protected Void doInBackground() throws Exception {
        progressDialog = new ProgressDialog(App.get(), this, true, 0, ModalityType.TOOLKIT_MODAL);
        progressDialog.setNote(getProgressNote());
        doProcess();
        return null;
    }

    @Override
    public boolean doCancel(boolean mayInterrupt) {
        if (progressDialog != null)
            progressDialog.close();

        t.getSelectionModel().setValueIsAdjusting(false);

        return cancel(mayInterrupt);
    }

    @Override
    protected void done() {
        if (progressDialog != null)
            progressDialog.close();
    }

    static int sliceSize = 1000;

    public class Counter {
        public int value;
    };

    Counter count = new Counter();

    class SelectionSlice implements Runnable {
        int start = 0;
        RoaringBitmap bs;
        private Semaphore sem;

        public SelectionSlice(RoaringBitmap bs, int start, Semaphore sem) {
            this.start = start;
            this.bs = bs;
            this.sem = sem;
        }

        @Override
        public void run() {
            try {
                IItemId item = null;
                for (int i = start; (i < start + sliceSize) && (i < resultsProvider.getResults().getLength()); i++) {
                    try {
                        if (progressDialog.isCanceled()) {
                            sem.release();
                            return;
                        }
                        synchronized (count) {
                            count.value++;
                        }
                        progressDialog.setProgress(count.value++);
                        int docid = resultsProvider.getIPEDSource().getLuceneId(resultsProvider.getResults().getItem(i));
                        if (bs.contains(docid)) {
                            processResultsItem(t, i);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println(i);
                        System.out.println(item);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                sem.release();
            }
        }
    }

    public void clear() {
        t.clearSelection();
    }

    // process docids on BitSet parameter in parallel
    public void highlightDocIdsParallel(RoaringBitmap bs, boolean highlight, boolean clearPreviousSelection) {
        Date d1 = new Date();

        ExecutorService executor = Executors.newFixedThreadPool(1);
        try {
            t.getSelectionModel().setValueIsAdjusting(true);

            if (clearPreviousSelection) {
                clear();
            }

            int nThreads = (int) Math.ceil((double) resultsProvider.getResults().getLength() / (double) sliceSize);
            Semaphore sem = new Semaphore(nThreads);
            sem.acquire(nThreads);
            count = new Counter();
            for (int i = 0; i < nThreads; i++) {
                executor.execute(new SelectionSlice(bs, i * sliceSize, sem));
            }
            sem.acquire(nThreads);
            sem.release(nThreads);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdownNow();
            Date d2 = new Date();
            System.out.println("Selecao 2 finalizada em:" + (d2.getTime() - d1.getTime()));
            t.getSelectionModel().setValueIsAdjusting(false);
        }
    }

    // process docids on BitSet parameter sequentially
    public void highlightDocIds(RoaringBitmap bs, boolean highlight, boolean clearPreviousSelection) {
        Date d1 = new Date();

        t.getSelectionModel().setValueIsAdjusting(true);

        if (clearPreviousSelection) {
            t.clearSelection();
        }

        try {
            IItemId item = null;
            for (int i = 0; i < resultsProvider.getResults().getLength(); i++) {
                if (progressDialog.isCanceled()) {
                    break;
                }
                progressDialog.setProgress(i);
                if (bs.contains(i)) {
                    processResultsItem(t, i);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Date d2 = new Date();
            System.out.println("Selecao 2 finalizada em:" + (d2.getTime() - d1.getTime()));
            t.getSelectionModel().setValueIsAdjusting(false);
        }
    }

    public void processResultsItem(JTable t, int i) {
        int row = t.convertRowIndexToView(i);
        if (highlight) {
            t.addRowSelectionInterval(row, row);
        } else {
            t.removeRowSelectionInterval(row, row);
        }
    }

}
