package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.util.BytesRef;

import gpinf.similarity.ImageSimilarity;
import iped3.IItem;
import iped3.IItemId;
import iped3.search.IMultiSearchResult;
import iped3.util.BasicProps;

public class ImageSimilarityScorer {
    /**
     * Constant used in the conversion from the raw squared distance (>=0, in an
     * arbitrary scale) of the reference image to the actual score (used to sort
     * the results and to be shown on the table). Although the score is limited
     * to [0,100] (avoiding negative values that could be produced by the
     * conversion formula), scores < 1 will be later discarded (i.e. not
     * included in the results): score = 100 - distance * distToScoreMult /
     * numFeatures So higher values will increase the distance weight, therefore
     * reducing the score (i.e. bringing less images).
     */
    private static final float distToScoreMult = 4;

    public static void score(IMultiSearchResult result, IItem refItem) throws IOException {
        byte[] refSimilarityFeatures = refItem.getImageSimilarityFeatures();
        if (refSimilarityFeatures == null) {
            return;
        }
        AtomicReader atomicReader = App.get().appCase.getAtomicReader();
        int len = result.getLength();

        int numThreads = Math.min(len, Runtime.getRuntime().availableProcessors());
        Thread[] threads = new Thread[numThreads];
        for (int k = 0; k < numThreads; k++) {
            int threadIdx = k;
            (threads[k] = new Thread() {
                public void run() {
                    BinaryDocValues similarityFeaturesValues = null;
                    try {
                        similarityFeaturesValues = atomicReader.getBinaryDocValues(BasicProps.SIMILARITY_FEATURES);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    for (int i = threadIdx; i < len; i += numThreads) {
                        IItemId itemId = result.getItem(i);
                        int luceneId = App.get().appCase.getLuceneId(itemId);
                        BytesRef bytesRef = similarityFeaturesValues.get(luceneId);
                        if (bytesRef == null) {
                            result.setScore(i, 0);
                        } else {
                            byte[] currSimilarityFeatures = bytesRef.bytes;
                            int distance = ImageSimilarity.distance(refSimilarityFeatures, currSimilarityFeatures);
                            float score = Math.max(0, 100 - distance * distToScoreMult / refSimilarityFeatures.length);
                            if (distance == 0) {
                                String refHash = refItem.getHash();
                                if (refHash != null) {
                                    try {
                                        Document doc = atomicReader.document(luceneId);
                                        String currHash = doc.get(BasicProps.HASH);
                                        if (refHash.equals(currHash)) {
                                            score = 1000;
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        break;
                                    }
                                }
                            }
                            result.setScore(i, score);
                        }
                    }
                }
            }).start();
        }
        for (Thread thread : threads) {
            try {
                if (thread != null) {
                    thread.join();
                }
            } catch (InterruptedException e) {
            }
        }
    }
}
