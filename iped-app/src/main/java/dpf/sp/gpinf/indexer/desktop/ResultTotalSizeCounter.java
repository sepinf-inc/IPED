package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;

import javax.swing.SwingUtilities;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;

import dpf.sp.gpinf.indexer.process.IndexItem;
import iped3.IItemId;
import iped3.search.IMultiSearchResult;

public class ResultTotalSizeCounter {

    private static volatile Thread lastCounter;

    public void countVolume(final IMultiSearchResult result) {

        if (lastCounter != null)
            lastCounter.interrupt();

        Thread counter = new Thread() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        App.get().resultsModel.updateLengthHeader(-1);
                        App.get().resultsTable.getTableHeader().repaint();
                    }
                });
                long volume = 0;
                try {
                    LeafReader atomicReader = App.get().appCase.getAtomicReader();
                    NumericDocValues ndv = atomicReader.getNumericDocValues(IndexItem.LENGTH);
                    for (IItemId item : result.getIterator()) {
                        int doc = App.get().appCase.getLuceneId(item);
                        long len = ndv.get(doc);
                        volume += len;
                        if (Thread.currentThread().isInterrupted())
                            return;
                    }
                } catch (IOException e) {
                    // e.printStackTrace();
                }

                volume = volume / (1024 * 1024);
                final long fVol = volume;

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        App.get().resultsModel.updateLengthHeader(fVol);
                        App.get().resultsTable.getTableHeader().repaint();
                    }
                });

            }
        };

        counter.start();
        lastCounter = counter;

    }
}
