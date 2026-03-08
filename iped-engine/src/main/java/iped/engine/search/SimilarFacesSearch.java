package iped.engine.search;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.BytesRef;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;

public class SimilarFacesSearch {

    public static final String FACE_FEATURES = "face_encodings";
    public static final String FACE_LOCATIONS = "face_locations";

    private static final float DEFAULT_MIN_DISTANCE = 0.4f;

    private static float minDist = DEFAULT_MIN_DISTANCE;
    private static int mode = 0; // Mode 0 = OR, Mode 1 = AND
    private static Set<Integer> selectedIdxs;

    private IPEDMultiSource ipedCase;
    private float[][] refSimilarityFeatures;

    public SimilarFacesSearch(IPEDSource ipedCase, IItem refImage) {
        this.ipedCase = ipedCase instanceof IPEDMultiSource ? (IPEDMultiSource) ipedCase
                : new IPEDMultiSource(Collections.singletonList(ipedCase));
        this.refSimilarityFeatures = getFaceFeatures(refImage, selectedIdxs);
    }

    public MultiSearchResult search() throws IOException {
        IPEDSearcher searcher = new IPEDSearcher(ipedCase, "*:*");
        MultiSearchResult result = searcher.multiSearch();
        return filter(result);
    }

    public MultiSearchResult filter(MultiSearchResult result) throws IOException {
        score(result);
        return ImageSimilarityLowScoreFilter.filter(result, distToScore(minDist));
    }

    public static final int getMinScore() {
        return (int) distToScore(minDist);
    }

    public static final void setMinScore(int minScore) {
        minDist = 1 - ((float) minScore / 100);
    }

    public static final void setMode(int mode) {
        SimilarFacesSearch.mode = mode;
    }

    public static final void setSelectedIdxs(Set<Integer> selectedIdxs) {
        SimilarFacesSearch.selectedIdxs = selectedIdxs;
    }

    private static final float distToScore(float dist) {
        return Math.max(0, (1 - dist) * 100);
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
                    SortedSetDocValues similarityFeaturesValues = null;
                    try {
                        similarityFeaturesValues = leafReader.getSortedSetDocValues(FACE_FEATURES);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    int i0 = Math.min(len, itemsPerThread * threadIdx);
                    int i1 = Math.min(len, i0 + itemsPerThread);
                    int numRefFaces = refSimilarityFeatures.length;
                    float[] distsPerFace = new float[numRefFaces];
                    for (int i = i0; i < i1; i++) {
                        if (i % 1000 == 0 && this.isInterrupted()) {
                            return;
                        }
                        IItemId itemId = result.getItem(i);
                        int luceneId = ipedCase.getLuceneId(itemId);
                        long ordinal;
                        float score = 0;
                        Arrays.fill(distsPerFace, minDist + 1);
                        try {
                            boolean hasVal = similarityFeaturesValues.advanceExact(luceneId);
                            while (hasVal && (ordinal = similarityFeaturesValues
                                    .nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                                BytesRef bytesRef = similarityFeaturesValues.lookupOrd(ordinal);
                                float[] currentFeatures = convToFloatVec(bytesRef.bytes);
                                int faceIdx = -1;
                                float bestDist = minDist + 1;
                                for (int j = 0; j < numRefFaces; j++) {
                                    float dist = distance(refSimilarityFeatures[j], currentFeatures);
                                    if (dist < bestDist) {
                                        bestDist = dist;
                                        faceIdx = j;
                                    }
                                }
                                if (faceIdx != -1 && bestDist < distsPerFace[faceIdx]) {
                                    distsPerFace[faceIdx] = bestDist;
                                }
                            }
                            if (numRefFaces == 1) {
                                if (distsPerFace[0] <= minDist) {
                                    score = distToScore(distsPerFace[0]);
                                }
                            } else {
                                // Multiple reference faces
                                if (mode == 0) {
                                    // OR mode
                                    float closestDist = distsPerFace[0];
                                    for (int j = 1; j < numRefFaces; j++) {
                                        closestDist = Math.min(closestDist, distsPerFace[j]);
                                    }
                                    if (closestDist <= minDist) {
                                        score = distToScore(closestDist);
                                    }
                                } else {
                                    // AND mode
                                    float maxDist = 0;
                                    for (int j = 0; j < numRefFaces; j++) {
                                        maxDist = Math.max(maxDist, distsPerFace[j]);
                                        if (maxDist > minDist) {
                                            break;
                                        }
                                    }
                                    if (maxDist <= minDist) {
                                        score = distToScore(maxDist);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            result.setScore(i, score);
                        }
                    }
                }
            }).start();
        }
        boolean canceled = false;
        for (Thread thread : threads) {
            if (thread != null) {
                if (!canceled) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        canceled = true;
                    }
                }
                if (canceled) {
                    thread.interrupt();
                }
            }
        }

    }

    private static float[] convToFloatVec(byte[] bytes) {
        float[] result = new float[bytes.length / 4];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        for (int i = 0; i < result.length; i++) {
            result[i] = bb.getFloat();
        }
        return result;
    }

    public static float distance(float[] a, float[] b) {
        if (a.length != b.length) {
            return Float.MAX_VALUE;
        }
        float dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return 1.0f - dot;
    }

    private static float[][] getFaceFeatures(IItem item, Set<Integer> idxs) {
        if (item == null) {
            return new float[0][0];
        }
        Object value = item.getExtraAttribute(FACE_FEATURES);
        if (value instanceof Collection) {
            List<float[]> l = new ArrayList<float[]>();
            Iterator<?> it = ((Collection<?>) value).iterator();
            int idx = 0;
            while (it.hasNext()) {
                Object o = it.next();
                if (idxs == null || idxs.isEmpty() || idxs.contains(idx)) {
                    if (o instanceof byte[]) {
                        l.add(convToFloatVec((byte[]) o));
                    }
                }
                idx++;
            }
            return l.toArray(new float[0][]);
        }
        return new float[][] { convToFloatVec((byte[]) value) };
    }

    public static List<String> getMatchLocations(IItem refItem, IItem matchItem) {
        ArrayList<String> matchLocations = new ArrayList<>();
        Object location = matchItem.getExtraAttribute(SimilarFacesSearch.FACE_LOCATIONS);
        if (location instanceof List) {
            float[][] refFeatures = getFaceFeatures(refItem, selectedIdxs);
            float[][] matchFeatures = getFaceFeatures(matchItem, null);
            for (int i = 0; i < matchFeatures.length; i++) {
                float[] mi = matchFeatures[i];
                for (int j = 0; j < refFeatures.length; j++) {
                    float[] rj = refFeatures[j];
                    if (distance(mi, rj) <= minDist) {
                        matchLocations.add((String) ((List<?>) location).get(i));
                        break;
                    }
                }
            }
        } else {
            matchLocations.add((String) location);
        }
        return matchLocations;
    }
}
