package iped.engine.search;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    private static final float DEFAULT_MIN_DISTANCE = 0.5f;

    private static float minDistSquared = DEFAULT_MIN_DISTANCE * DEFAULT_MIN_DISTANCE;

    private IPEDMultiSource ipedCase;
    private float[] refSimilarityFeatures;

    public SimilarFacesSearch(IPEDSource ipedCase, IItem refImage) {
        this.ipedCase = ipedCase instanceof IPEDMultiSource ? (IPEDMultiSource) ipedCase
                : new IPEDMultiSource(Collections.singletonList(ipedCase));
        this.refSimilarityFeatures = getFirstFace(refImage);
    }

    public MultiSearchResult search() throws IOException {
        IPEDSearcher searcher = new IPEDSearcher(ipedCase, "*:*");
        MultiSearchResult result = searcher.multiSearch();
        return filter(result);
    }

    public MultiSearchResult filter(MultiSearchResult result) throws IOException {
        score(result);
        return ImageSimilarityLowScoreFilter.filter(result, squaredDistToScore(minDistSquared));
    }

    public static final int getMinScore() {
        return (int) squaredDistToScore(minDistSquared);
    }

    public static final void setMinScore(int minScore) {
        float dist = 1 - ((float) minScore / 100);
        minDistSquared = dist * dist;
    }

    private static final float squaredDistToScore(float squaredDist) {
        return Math.max(0, (1 - (float) Math.sqrt(squaredDist)) * 100);
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
                    for (int i = i0; i < i1; i++) {
                        if (i % 1000 == 0 && this.isInterrupted()) {
                            return;
                        }
                        IItemId itemId = result.getItem(i);
                        int luceneId = ipedCase.getLuceneId(itemId);
                        long ordinal;
                        float score = 0;
                        try {
                            boolean hasVal = similarityFeaturesValues.advanceExact(luceneId);
                            while (hasVal && (ordinal = similarityFeaturesValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                                BytesRef bytesRef = similarityFeaturesValues.lookupOrd(ordinal);
                                float[] currentFeatures = convToFloatVec(bytesRef.bytes);
                                float squaredDist = distance(refSimilarityFeatures, currentFeatures, minDistSquared);
                                if (squaredDist <= minDistSquared) {
                                    score = squaredDistToScore(squaredDist);
                                    break;
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

    public static float distance(float[] a, float[] b, float cut) {
        float distance = 0;
        for (int i = 0; i < a.length && distance <= cut;) {
            float d = a[i] - b[i++];
            distance += d * d + (d = a[i] - b[i++]) * d + (d = a[i] - b[i++]) * d + (d = a[i] - b[i++]) * d;
        }
        return (float) distance;
    }

    private static float[] getFirstFace(IItem refImage) {
        Object value = refImage.getExtraAttribute(FACE_FEATURES);
        if (value instanceof Collection)
            // get first face in image
            value = ((Collection) value).iterator().next();
        return convToFloatVec((byte[]) value);
    }

    public static List<String> getMatchLocations(IItem refImage, IItem item){
        ArrayList<String> matchLocations = new ArrayList<>();
        Object location = item.getExtraAttribute(SimilarFacesSearch.FACE_LOCATIONS);
        if (location instanceof List) {
            float[] ref = getFirstFace(refImage);
            List<byte[]> features = (List<byte[]>) item.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES);
            for (int i = 0; i < ((List) location).size(); i++) {
                float[] face = convToFloatVec(features.get(i));
                if (distance(ref, face, minDistSquared) <= minDistSquared) {
                    matchLocations.add((String) ((List) location).get(i));
                }
            }
        } else {
            matchLocations.add((String) location);
        }
        return matchLocations;
    }

}
