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
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.BytesRef;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.localization.Messages;
import iped.properties.ExtraProperties;

public class SimilarFacesSearch {

    public static final String FACE_FEATURES = "face_encodings";
    public static final String FACE_LOCATIONS = "face_locations";

    private static final int LEGACY_MIN_SCORE = 50;
    private static final int MODERN_MIN_SCORE = 60;

    private static int defaultMinScore = LEGACY_MIN_SCORE;
    private static Integer customMinScore;
    private static float minDist = 0.5f;
    private static int mode = 0; // Mode 0 = OR, Mode 1 = AND
    private static Set<Integer> selectedIdxs;

    private IPEDMultiSource ipedCase;
    private float[][] refSimilarityFeatures;
    private String refModel;

    public SimilarFacesSearch(IPEDSource ipedCase, IItem refImage) {
        validateReference(refImage);
        this.ipedCase = ipedCase instanceof IPEDMultiSource ? (IPEDMultiSource) ipedCase
                : new IPEDMultiSource(Collections.singletonList(ipedCase));
        this.refSimilarityFeatures = getFaceFeatures(refImage, selectedIdxs);
        this.refModel = getModel(refImage, refSimilarityFeatures);
        defaultMinScore = FaceRecognitionModels.DLIB.equals(refModel) ? LEGACY_MIN_SCORE : MODERN_MIN_SCORE;
        updateMinDistance();
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
        return customMinScore != null ? customMinScore : defaultMinScore;
    }

    /** Default for this reference's embedding space, unless the user chose a custom threshold. */
    public static int getMinScore(IItem reference) {
        validateReference(reference);
        String model = getModel(reference, getFaceFeatures(reference, null));
        return customMinScore != null ? customMinScore
                : FaceRecognitionModels.DLIB.equals(model) ? LEGACY_MIN_SCORE : MODERN_MIN_SCORE;
    }

    public static final void setMinScore(int minScore) {
        customMinScore = minScore;
        updateMinDistance();
    }

    public static void resetMinScore() {
        customMinScore = null;
        updateMinDistance();
    }

    private static void updateMinDistance() {
        minDist = (100 - getMinScore()) / 100.0f;
    }

    public static final void setMode(int mode) {
        SimilarFacesSearch.mode = mode;
    }

    public static final void setSelectedIdxs(Set<Integer> selectedIdxs) {
        SimilarFacesSearch.selectedIdxs = selectedIdxs;
    }

    private static final float distToScore(float dist) {
        return Math.max(0, Math.min(100, (1 - dist) * 100));
    }

    private void score(MultiSearchResult result) throws IOException {

        LeafReader leafReader = ipedCase.getLeafReader();
        boolean legacy = FaceRecognitionModels.DLIB.equals(refModel);
        float distanceLimit = legacy ? minDist * minDist : minDist;
        int numThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[numThreads];
        int len = result.getLength();
        int itemsPerThread = (len + numThreads - 1) / numThreads;
        for (int k = 0; k < numThreads; k++) {
            int threadIdx = k;
            (threads[k] = new Thread() {
                public void run() {
                    SortedSetDocValues similarityFeaturesValues = null;
                    SortedDocValues modelValues = null;
                    try {
                        similarityFeaturesValues = leafReader.getSortedSetDocValues(FACE_FEATURES);
                        modelValues = leafReader.getSortedDocValues(ExtraProperties.FACE_MODEL);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    int i0 = Math.min(len, itemsPerThread * threadIdx);
                    int i1 = Math.min(len, i0 + itemsPerThread);
                    int numRefFaces = refSimilarityFeatures.length;
                    if (similarityFeaturesValues == null || numRefFaces == 0) {
                        for (int i = i0; i < i1; i++) {
                            result.setScore(i, 0);
                        }
                        return;
                    }
                    float[] distsPerFace = new float[numRefFaces];
                    for (int i = i0; i < i1; i++) {
                        if (i % 1000 == 0 && this.isInterrupted()) {
                            return;
                        }
                        IItemId itemId = result.getItem(i);
                        int luceneId = ipedCase.getLuceneId(itemId);
                        long ordinal;
                        float score = 0;
                        Arrays.fill(distsPerFace, distanceLimit + 1);
                        try {
                            String candidateModel = modelValues != null && modelValues.advanceExact(luceneId)
                                    ? modelValues.lookupOrd(modelValues.ordValue()).utf8ToString() : null;
                            boolean hasVal = similarityFeaturesValues.advanceExact(luceneId);
                            while (hasVal && (ordinal = similarityFeaturesValues
                                    .nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                                BytesRef bytesRef = similarityFeaturesValues.lookupOrd(ordinal);
                                float[] currentFeatures = convToFloatVec(bytesRef);
                                if (!refModel.equals(FaceRecognitionModels.resolve(candidateModel, currentFeatures.length))) {
                                    continue;
                                }
                                int faceIdx = -1;
                                float bestDist = distanceLimit + 1;
                                for (int j = 0; j < numRefFaces; j++) {
                                    float dist = legacy ? distance(refSimilarityFeatures[j], currentFeatures, bestDist)
                                            : distance(refSimilarityFeatures[j], currentFeatures);
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
                                if (distsPerFace[0] <= distanceLimit) {
                                    score = distToScore(legacy ? (float) Math.sqrt(distsPerFace[0]) : distsPerFace[0]);
                                }
                            } else {
                                // Multiple reference faces
                                if (mode == 0) {
                                    // OR mode
                                    float closestDist = distsPerFace[0];
                                    for (int j = 1; j < numRefFaces; j++) {
                                        closestDist = Math.min(closestDist, distsPerFace[j]);
                                    }
                                    if (closestDist <= distanceLimit) {
                                        score = distToScore(legacy ? (float) Math.sqrt(closestDist) : closestDist);
                                    }
                                } else {
                                    // AND mode
                                    float maxDist = 0;
                                    for (int j = 0; j < numRefFaces; j++) {
                                        maxDist = Math.max(maxDist, distsPerFace[j]);
                                        if (maxDist > distanceLimit) {
                                            break;
                                        }
                                    }
                                    if (maxDist <= distanceLimit) {
                                        score = distToScore(legacy ? (float) Math.sqrt(maxDist) : maxDist);
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
        return convToFloatVec(new BytesRef(bytes));
    }

    static float[] convToFloatVec(BytesRef bytes) {
        float[] result = new float[bytes.length / 4];
        ByteBuffer bb = ByteBuffer.wrap(bytes.bytes, bytes.offset, bytes.length);
        for (int i = 0; i < result.length; i++) {
            result[i] = bb.getFloat();
        }
        return result;
    }

    public static float distance(float[] a, float[] b) {
        if (a.length != b.length) {
            return Float.MAX_VALUE;
        }
        if (a.length == FaceRecognitionModels.LEGACY_DIMENSIONS) {
            return (float) Math.sqrt(distance(a, b, Float.MAX_VALUE));
        }
        float dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return Float.isFinite(dot) ? Math.max(0, Math.min(2, 1.0f - dot)) : Float.MAX_VALUE;
    }

    /** Legacy squared Euclidean API retained for existing callers. */
    public static float distance(float[] a, float[] b, float cut) {
        if (a.length != b.length) {
            return Float.MAX_VALUE;
        }
        float squared = 0;
        int i = 0;
        // Retain the original four-float loop and early cutoff used for legacy case searches.
        for (; i + 3 < a.length && squared <= cut;) {
            float d = a[i] - b[i++];
            squared += d * d + (d = a[i] - b[i++]) * d + (d = a[i] - b[i++]) * d + (d = a[i] - b[i++]) * d;
        }
        for (; i < a.length && squared <= cut; i++) {
            float delta = a[i] - b[i];
            squared += delta * delta;
        }
        return Float.isFinite(squared) ? squared : Float.MAX_VALUE;
    }

    public static void validateReference(IItem item) {
        float[][] features = getFaceFeatures(item, null);
        if (features.length == 0 || getModel(item, features) == null) {
            throw new IllegalArgumentException(Messages.getString("FaceRecognition.IncompatibleModel"));
        }
    }

    /** Check model terms before an external-image search, without reading every stored vector. */
    public static void validateCaseCompatibility(LeafReader reader, IItem reference) throws IOException {
        validateReference(reference);
        String model = getModel(reference, getFaceFeatures(reference, null));
        if (FaceRecognitionModels.DLIB.equals(model)
                && reader.getSortedSetDocValues(FACE_FEATURES) != null) {
            return;
        }
        SortedDocValues models = reader.getSortedDocValues(ExtraProperties.FACE_MODEL);
        if (models == null || models.lookupTerm(new BytesRef(model)) < 0) {
            throw new IllegalArgumentException(Messages.getString("FaceRecognition.IncompatibleCaseModel"));
        }
    }

    private static String getModel(IItem item, float[][] features) {
        if (item == null || features.length == 0) {
            return null;
        }
        Object tag = item.getExtraAttribute(ExtraProperties.FACE_MODEL);
        String model = FaceRecognitionModels.resolve(tag instanceof String ? (String) tag : null, features[0].length);
        for (float[] feature : features) {
            if (model == null || !model.equals(FaceRecognitionModels.resolve(tag instanceof String ? (String) tag : null, feature.length))) {
                return null;
            }
        }
        return model;
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
        return value instanceof byte[] ? new float[][] { convToFloatVec((byte[]) value) } : new float[0][];
    }

    public static List<String> getMatchLocations(IItem refItem, IItem matchItem) {
        ArrayList<String> matchLocations = new ArrayList<>();
        float[][] refFeatures = getFaceFeatures(refItem, selectedIdxs);
        float[][] matchFeatures = getFaceFeatures(matchItem, null);
        String model = getModel(refItem, refFeatures);
        if (model == null || !model.equals(getModel(matchItem, matchFeatures))) {
            return matchLocations;
        }
        Object location = matchItem.getExtraAttribute(SimilarFacesSearch.FACE_LOCATIONS);
        List<?> locations = location instanceof List ? (List<?>) location : Collections.singletonList(location);
        for (int i = 0; i < matchFeatures.length; i++) {
            float[] mi = matchFeatures[i];
            for (int j = 0; j < refFeatures.length; j++) {
                float[] rj = refFeatures[j];
                if (distance(mi, rj) <= minDist) {
                    if (i < locations.size() && locations.get(i) instanceof String) {
                        matchLocations.add((String) locations.get(i));
                    }
                    break;
                }
            }
        }
        return matchLocations;
    }
}
