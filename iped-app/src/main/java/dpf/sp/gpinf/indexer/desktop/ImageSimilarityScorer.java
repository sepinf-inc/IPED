package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.util.BytesRef;

import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import gpinf.similarity.ImageSimilarity;
import iped3.IItem;
import iped3.IItemId;
import iped3.util.BasicProps;

public class ImageSimilarityScorer {
    /**
     * Constant used in the conversion from the raw squared distance (>=0, in an
     * arbitrary scale) of the reference image to the actual score (used to sort the
     * results and to be shown on the table). Although the score is limited to
     * [0,100] (avoiding negative values that could be produced by the conversion
     * formula), scores < 1 will be later discarded (i.e. not included in the
     * results): score = 100 - distance * distToScoreMult / numFeatures So higher
     * values will increase the distance weight, therefore reducing the score (i.e.
     * bringing less images).
     */
    private static final float distToScoreMult = 4;

    /**
     * Special score use to identify the images identical to the reference image
     * (hash is the same), which can include the reference image itself.
     */
    private static final float identicalScore = 1000;

    /**
     * For the best (maxTop) images found, organize them not only based on the
     * distance to the reference image. For each image, up to (rangeCheck) images
     * after the current one is checked and possibly reordered to present results in
     * a more convenient way (grouping similar images).
     */
    private static final int maxTop = 2000;
    private static final int rangeCheck = 100;
    private float cut = 1;

    private final MultiSearchResult result;
    private final byte[] refSimilarityFeatures;
    private final IItem refItem;
    private final int len;

    private final List<Integer> topResults = new ArrayList<Integer>();
    private final Map<Integer, byte[]> topFeatures = new HashMap<Integer, byte[]>();
    private final Map<Integer, Integer> refDist = new HashMap<Integer, Integer>();

    public ImageSimilarityScorer(MultiSearchResult result, IItem refItem) {
        this.result = result;
        this.len = result.getLength();
        this.refItem = refItem;
        this.refSimilarityFeatures = refItem.getImageSimilarityFeatures();
    }

    public void score() throws IOException {
        if (len == 0 || refSimilarityFeatures == null) {
            return;
        }
        LeafReader leafReader = App.get().appCase.getLeafReader();
        int numThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[numThreads];
        int evalCut = (int) (100 * refSimilarityFeatures.length / distToScoreMult);
        int itemsPerThread = (len + numThreads - 1) / numThreads;
        for (int k = 0; k < numThreads; k++) {
            int threadIdx = k;
            (threads[k] = new Thread() {
                public void run() {
                    BinaryDocValues similarityFeaturesValues = null;
                    try {
                        similarityFeaturesValues = leafReader.getBinaryDocValues(BasicProps.SIMILARITY_FEATURES);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    int i0 = Math.min(len, itemsPerThread * threadIdx);
                    int i1 = Math.min(len, i0 + itemsPerThread);
                    for (int i = i0; i < i1; i++) {
                        IItemId itemId = result.getItem(i);
                        int luceneId = App.get().appCase.getLuceneId(itemId);
                        BytesRef bytesRef = similarityFeaturesValues.get(luceneId);
                        if (bytesRef == null || bytesRef.length == 0) {
                            result.setScore(i, 0);
                        } else {
                            byte[] currSimilarityFeatures = bytesRef.bytes;
                            int distance = ImageSimilarity.distance(refSimilarityFeatures, currSimilarityFeatures,
                                    evalCut);
                            float score = Math.max(0, 100 - distance * distToScoreMult / refSimilarityFeatures.length);
                            if (distance == 0) {
                                String refHash = refItem.getHash();
                                if (refHash != null) {
                                    try {
                                        Document doc = leafReader.document(luceneId);
                                        String currHash = doc.get(BasicProps.HASH);
                                        if (refHash.equals(currHash)) {
                                            score = identicalScore;
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

        organizeTopResults();
    }

    private void organizeTopResults() {
        for (int i = 0; i < len; i++) {
            if (result.getScore(i) > cut) {
                topResults.add(i);
                trim(maxTop << 1);
            }
        }
        trim(0);
        int start = topResults.size();
        for (int i = 1; i < topResults.size(); i++) {
            int idx = topResults.get(i);
            if (result.getScore(idx) < identicalScore) {
                start = i;
                break;
            }
        }
        if (topResults.size() - start <= 2)
            return;
        float maxScore = result.getScore(topResults.get(start));
        float minScore = result.getScore(topResults.get(topResults.size() - 1));

        BinaryDocValues similarityFeaturesValues = null;
        LeafReader leafReader = App.get().appCase.getLeafReader();
        try {
            similarityFeaturesValues = leafReader.getBinaryDocValues(BasicProps.SIMILARITY_FEATURES);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        for (int i = start - 1; i < topResults.size(); i++) {
            int idx = topResults.get(i);
            IItemId itemId = result.getItem(idx);
            int luceneId = App.get().appCase.getLuceneId(itemId);
            BytesRef bytesRef = similarityFeaturesValues.get(luceneId);
            byte[] currFeatures = bytesRef.bytes.clone();
            topFeatures.put(idx, currFeatures);
            refDist.put(idx, ImageSimilarity.distance(refSimilarityFeatures, currFeatures));
        }

        for (int i = start - 1; i < topResults.size() - 2; i++) {
            int pivot = topResults.get(i);
            int limit = Math.min(topResults.size() - 1, i + rangeCheck);
            int minDist = Integer.MAX_VALUE;
            int best = i + 1;
            byte[] featuresPivot = topFeatures.get(pivot);
            for (int j = i + 1; j <= limit; j++) {
                int idx = topResults.get(j);
                int currDist = refDist.get(idx);
                if (currDist < minDist) {
                    currDist += ImageSimilarity.distance(featuresPivot, topFeatures.get(idx), minDist - currDist);
                    if (currDist < minDist) {
                        minDist = currDist;
                        best = j;
                    }
                }
            }
            if (best != i + 1) {
                Collections.rotate(topResults.subList(i + 1, best + 1), 1);
            }
        }

        float score = maxScore;
        float delta = (maxScore - minScore) / (topResults.size() - start - 1);
        for (int i = start; i < topResults.size(); i++) {
            int idx = topResults.get(i);
            result.setScore(idx, score);
            score -= delta;
        }
    }

    private void trim(int size) {
        if (topResults.size() >= size) {
            Collections.sort(topResults, new Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    return Float.compare(result.getScore(b), result.getScore(a));
                }
            });
            if (topResults.size() > maxTop) {
                topResults.subList(maxTop, topResults.size()).clear();
                cut = result.getScore(topResults.get(topResults.size() - 1));
            }
        }
    }
}