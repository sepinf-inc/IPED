package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;

import javax.swing.SwingUtilities;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SlowCompositeReaderWrapper;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.ItemIdImpl;
import iped3.ItemId;
import iped3.search.MultiSearchResult;

public class ResultTotalSizeCounter {

	private static volatile Thread lastCounter;

	public void countVolume(final MultiSearchResult result) {

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
				    AtomicReader atomicReader = App.get().appCase.getAtomicReader();
	                NumericDocValues ndv = atomicReader.getNumericDocValues(IndexItem.LENGTH);
	                for (ItemId item : result.getIterator()) {
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
