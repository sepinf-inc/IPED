package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.FixedBitSet;

import iped3.IItem;

public class SimilarFacesSearch {

    public static final String FACE_FEATURES = "face_encodings";
    public static final String FACE_LOCATIONS = "face_locations";

    private static final float DEFAULT_MIN_SCORE = 50;

    private static float minimumScore = DEFAULT_MIN_SCORE;

    private static int KNN_BATCH = 1000;

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
        return ImageSimilarityLowScoreFilter.filter(result, minimumScore);
    }

    public static final int getMinScore() {
        return (int) minimumScore;
    }

    public static final void setMinScore(int minScore) {
        minimumScore = minScore;
    }
    
    public static final float scoreToSquaredDist(float minScore) {
        float dist = 1 - ((float) minScore / 100);
        return dist * dist;
    }

    private static final float squaredDistToScore(float squaredDist) {
        return Math.max(0, (1 - (float) Math.sqrt(squaredDist)) * 100);
    }

    private static final float convertLuceneScoreToFinalScore(float luceneScore) {
        // inverts the formula taken from
        // https://github.com/apache/lucene/blob/releases/lucene/9.0.0/lucene/core/src/java/org/apache/lucene/index/VectorSimilarityFunction.java
        float squareDistance = (1f / luceneScore) - 1;

        return squaredDistToScore(squareDistance);
    }

    private void score(MultiSearchResult result) throws IOException {

        LeafReader leafReader = ipedCase.getLeafReader();
        HashMap<Integer, Float> topDocsMap = new HashMap<>();
        FixedBitSet bits = new FixedBitSet(leafReader.maxDoc());
        bits.set(0, bits.length());
        int faceNumber = 0;
        loop: while (true) {
            String field = faceNumber == 0 ? FACE_FEATURES : FACE_FEATURES + faceNumber;
            if (leafReader.getFieldInfos().fieldInfo(field) == null) {
                break;
            }
            TopDocs topDocs = leafReader.searchNearestVectors(field, refSimilarityFeatures, KNN_BATCH, bits);
            if (topDocs.scoreDocs.length == 0) {
                faceNumber++;
                bits.set(0, bits.length());
                continue;
            }
            for (ScoreDoc doc : topDocs.scoreDocs) {
                float finalScore = convertLuceneScoreToFinalScore(doc.score);
                if (finalScore >= minimumScore) {
                    Float otherFaceScore = topDocsMap.get(doc.doc);
                    if (otherFaceScore == null || otherFaceScore < finalScore) {
                        topDocsMap.put(doc.doc, finalScore);
                    }
                    bits.clear(doc.doc);
                } else {
                    faceNumber++;
                    bits.set(0, bits.length());
                    continue loop;
                }
            }
        }

        for (int i = 0; i < result.getLength(); i++) {
            int luceneId = ipedCase.getLuceneId(result.getItem(i));
            Float score = topDocsMap.get(luceneId);
            if (score != null) {
                result.setScore(i, score);
            } else {
                result.setScore(i, 0);
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
            float maxDistSquared = scoreToSquaredDist(minimumScore);
            float[] ref = getFirstFace(refImage);
            List<byte[]> features = (List<byte[]>) item.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES);
            for (int i = 0; i < ((List) location).size(); i++) {
                float[] face = convToFloatVec(features.get(i));
                if (distance(ref, face, maxDistSquared) <= maxDistSquared) {
                    matchLocations.add((String) ((List) location).get(i));
                }
            }
        } else {
            matchLocations.add((String) location);
        }
        return matchLocations;
    }

}
