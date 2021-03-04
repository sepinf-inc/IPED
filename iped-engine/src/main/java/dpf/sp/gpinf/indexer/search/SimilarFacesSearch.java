package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.util.BytesRef;

import iped3.IItem;
import iped3.IItemId;

public class SimilarFacesSearch {

    public static final String FACE_FEATURES = "face_encodings";
    private static float minDist = 0.45f;
    private static float minDistSquared = minDist * minDist;

    private IPEDMultiSource ipedCase;
    private double[] refSimilarityFeatures;

    public SimilarFacesSearch(IPEDSource ipedCase, IItem refImage) {
        this.ipedCase = ipedCase instanceof IPEDMultiSource ? (IPEDMultiSource) ipedCase
                : new IPEDMultiSource(Collections.singletonList(ipedCase));
        this.refSimilarityFeatures = convToDoubleVec((byte[]) refImage.getExtraAttribute(FACE_FEATURES));
    }

    public MultiSearchResult search() throws IOException {
        IPEDSearcher searcher = new IPEDSearcher(ipedCase, "*:*");
        MultiSearchResult result = searcher.multiSearch();
        score(result);
        return filter(result);
    }

    public MultiSearchResult filter(MultiSearchResult result) throws IOException {
        score(result);
        return ImageSimilarityLowScoreFilter.filter(result, squaredDistToScore(minDistSquared));
    }

    private float squaredDistToScore(float dist) {
        return Math.max(0, (1 - dist * 2) * 100);
    }

    private void score(MultiSearchResult result) throws IOException {

        LeafReader leafReader = ipedCase.getLeafReader();
        int numThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[numThreads];
        int len = result.getLength();
        int itemsPerThread = (len + numThreads - 1) / numThreads;
        for (int k = 0; k < numThreads; k++) {
            int threadIdx = k;
            (threads[k] = new Thread() {
                public void run() {
                    BinaryDocValues similarityFeaturesValues = null;
                    try {
                        similarityFeaturesValues = leafReader.getBinaryDocValues(FACE_FEATURES);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    int i0 = Math.min(len, itemsPerThread * threadIdx);
                    int i1 = Math.min(len, i0 + itemsPerThread);
                    for (int i = i0; i < i1; i++) {
                        IItemId itemId = result.getItem(i);
                        int luceneId = ipedCase.getLuceneId(itemId);
                        BytesRef bytesRef = similarityFeaturesValues.get(luceneId);
                        if (bytesRef == null || bytesRef.length == 0) {
                            result.setScore(i, 0);
                        } else {
                            double[] currentFeatures = convToDoubleVec(bytesRef.bytes);
                            float distance = distance(refSimilarityFeatures, currentFeatures, minDistSquared);
                            System.out.println(distance);
                            result.setScore(i, squaredDistToScore(distance));
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

    private double[] convToDoubleVec(byte[] bytes) {
        double[] result = new double[bytes.length / 8];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        for (int i = 0; i < result.length; i++) {
            result[i] = bb.getDouble();
        }
        return result;
    }

    public static float distance(double[] a, double[] b, float cut) {
        double distance = 0;
        for (int i = 0; i < a.length && distance < cut;) {
            double d = a[i] - b[i++];
            distance += d * d + (d = a[i] - b[i++]) * d + (d = a[i] - b[i++]) * d + (d = a[i] - b[i++]) * d;
        }
        return (float) distance;
    }

}
