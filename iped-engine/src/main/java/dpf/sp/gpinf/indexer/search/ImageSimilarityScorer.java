package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.util.BytesRef;

import dpf.sp.gpinf.indexer.lucene.DocValuesUtil;
import dpf.sp.gpinf.indexer.process.task.ImageSimilarityTask;
import dpf.sp.gpinf.indexer.search.ImageSimilarityLowScoreFilter;
import gpinf.similarity.ImageSimilarity;
import iped3.IItem;
import iped3.IItemId;
import iped3.util.BasicProps;
import scala.Int;

public class ImageSimilarityScorer {
    /**
     * Constant used in the conversion from the raw squared distance (>=0, in an
     * arbitrary scale) of the reference image to the actual score (used to sort the
     * results and to be shown on the table). Although the score is limited to
     * [0,100] (avoiding negative values that could be produced by the conversion
     * formula), scores < 1 will be later discarded (i.e. not included in the
     * results): score = 100 - distance * distTList<oScoreMult / numFeatures So higher
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

    /**
     * Minimum score to accept an image (below that it won't be included in the
     * results).
     */
    private static float minScore = 50;
    //private final float cut;
    private static final Logger logger = LoggerFactory.getLogger(ImageSimilarityScorer.class);


    private final IPEDSource ipedCase;
    private final MultiSearchResult result;
    //private final byte[] refSimilarityFeatures;
    private final IItem refItem;
    private final int len;
    
    private final List<Integer> topResults = new ArrayList<Integer>();
    private final Map<Integer, byte[]> topFeatures = new HashMap<Integer, byte[]>();
    private final Map<Integer, Integer> refDist = new HashMap<Integer, Integer>();

    public ImageSimilarityScorer(IPEDSource ipedCase, MultiSearchResult result, IItem refItem) {
        this.ipedCase = ipedCase;
        this.result = result;
        this.len = result.getLength();
        this.refItem = refItem;
        //this.refSimilarityFeatures = (Arrays.asList((byte[]) refItem.getExtraAttribute(ImageSimilarityTask.SIMILARITY_FEATURES))).get(0);
        
        
    }

    public static final int getMinScore() {
        return (int) minScore;
    }

    public static final void setMinScore(int score) {        
        minScore = (float) score;
    }

    private void score(MultiSearchResult result) throws IOException {
        if (len == 0) {
            return;
        }
        LeafReader leafReader = ipedCase.getLeafReader();
        int numThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[numThreads];
        
        int itemsPerThread = (len + numThreads - 1) / numThreads;
        for (int k = 0; k < numThreads; k++) {
            int threadIdx = k;
            (threads[k] = new Thread() {
                public void run() {
                    int i0 = Math.min(len, itemsPerThread * threadIdx);
                    int i1 = Math.min(len, i0 + itemsPerThread);
                    for (int i = i0; i < i1; i++) {
                        IItemId itemId = result.getItem(i);
                        int luceneId = ipedCase.getLuceneId(itemId);
                        //IItem item = ipedCase.getItemByLuceneID(luceneId);
                        
                        try{                            
                            float score = getBestScore(leafReader, refItem, luceneId);
                            result.setScore(i, score);                             
                        } catch (IOException e) {
                            e.printStackTrace();
                            break;
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

        //organizeTopResults();
    }
    /* 
    * getBestScoreList returns the best score for an image or videothumb
    *
    */
    private float getBestScore(LeafReader leafReader, IItem refItem, int luceneId) throws IOException {         
        float bestScore = 0;               
        byte[] refSimilarityFeatures = (byte[]) refItem.
                        getExtraAttribute(ImageSimilarityTask.SIMILARITY_FEATURES);//images are stored as byte[]
        int evalCut = (int) (100 * refSimilarityFeatures.length / distToScoreMult);

        List<byte[]> similarityFeaturesList = getSimilarityFeaturesList(leafReader, luceneId);           
        if (similarityFeaturesList != null && similarityFeaturesList.size() > 0){            
            for (byte[] currSimilarityFeatures : similarityFeaturesList){
                if (currSimilarityFeatures != null) { 
                    int distance = ImageSimilarity.distance(refSimilarityFeatures, currSimilarityFeatures,
                            evalCut);                             
                    if (distance == 0) {
                        String refHash = refItem.getHash();
                        if (refHash != null) {                            
                            Document doc = leafReader.document(luceneId);
                            String currHash = doc.get(BasicProps.HASH);
                            if (refHash.equals(currHash)) {                                                             
                                return identicalScore;                                
                            }                            
                        }
                    }
                    float scoreTmp = Math.max(0, 100 - distance * distToScoreMult / refSimilarityFeatures.length);                    
                    if (scoreTmp > bestScore){
                        bestScore = scoreTmp; 
                    }  
                }                                      
            }
        } else {
            return 0;
        }              
        return bestScore;
    }

    private static List<byte[]> getSimilarityFeaturesList(LeafReader leafReader, int luceneId) throws IOException {        
        List<byte[]> similarityFeaturesList = new ArrayList<byte[]>();
        SortedSetDocValues similarityFeaturesValues = null;        
        try {
            long ordinal = 0;
            similarityFeaturesValues = leafReader
                    .getSortedSetDocValues(ImageSimilarityTask.SIMILARITY_FEATURES);
            boolean hasVal = similarityFeaturesValues.advanceExact(luceneId);
            while (hasVal && (ordinal = similarityFeaturesValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {                 
                BytesRef bytesRef = similarityFeaturesValues.lookupOrd(ordinal);
                if (bytesRef != null){
                    similarityFeaturesList.add(bytesRef.bytes);                    
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return similarityFeaturesList;
    }

    public static List<String> getMatchLocations(IItem refItem, IItem item) throws IOException {
        List<String> matchLocations = new ArrayList<String>();
        List<int[]> scoreList = new ArrayList<int[]>(); 
        List<String> frameLocationList = (List<String>) item.
                        getExtraAttribute(ImageSimilarityTask.FRAMES_LOCATIONS);
        
        byte[] refSimilarityFeatures = (byte[]) refItem.
                        getExtraAttribute(ImageSimilarityTask.SIMILARITY_FEATURES);
        int evalCut = (int) (100 * refSimilarityFeatures.length / distToScoreMult);
        Object similarityFeaturesObj = item.getExtraAttribute(ImageSimilarityTask.SIMILARITY_FEATURES);

        List<byte[]> similarityFeaturesList = null;
        if (similarityFeaturesObj instanceof List){
            similarityFeaturesList = (List<byte[]>) similarityFeaturesObj;
        } else {
            similarityFeaturesList = new ArrayList<byte[]>();
            similarityFeaturesList.add((byte[]) similarityFeaturesObj);
        }
        int locationIndex = 0;   
        for (byte[]currSimilarityFeatures : similarityFeaturesList){
            if (currSimilarityFeatures != null) {
                int distance = ImageSimilarity.distance(refSimilarityFeatures, currSimilarityFeatures,
                            evalCut); 
        
                if (distance == 0) {
                    String refHash = refItem.getHash();
                    if (refHash != null) {                            
                        String currHash = item.getHash();
                        if (refHash.equals(currHash)) { // it only occus on images comparison                                                                                     
                            int[] scoreArray = { (int) identicalScore, locationIndex };                                
                            scoreList.add(scoreArray);                                                               
                        }                            
                    }
                }
                float score = Math.max(0, 100 - distance * distToScoreMult / refSimilarityFeatures.length);
                if (score >= getMinScore()){
                    int[] scoreArray = {(int) score, locationIndex};
                    scoreList.add(scoreArray);                    
                }
            }
            locationIndex++;
        }
        if (scoreList != null && frameLocationList != null && frameLocationList.size() > 0 && scoreList.size() > 0) {
            for (int[] scoreArray: scoreList) {
                matchLocations.add(frameLocationList.get(scoreArray[1]));                
            }
        }         
        return matchLocations;
    }

    public MultiSearchResult filter(MultiSearchResult result, int minScore) throws IOException {
        score(result);
        return ImageSimilarityLowScoreFilter.filter(result, minScore);
    }

    /*private final void organizeTopResults() {
        
       
        // cut = SimilarImagesSearch.getMinScore();
        for (int i = 0; i < len; i++) {
            if (result.getScore(i) > cut) {
                topResults.add(i);
                trim(maxTop << 1);
                
            }
        }
    
        trim(0);
        
        SortedSetDocValues similarityFeaturesValues = null;
        try {
            similarityFeaturesValues = ipedCase.getLeafReader()
                    .getSortedSetDocValues(ImageSimilarityTask.SIMILARITY_FEATURES);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        // put features in a map, SortedSetDocValues is not random accessible anymore
        HashMap<Integer, byte[]> idToFeaturesMap = new HashMap<>();
        for (Integer idx : topResults.stream().sorted().collect(Collectors.toList())) {
            IItemId itemId = result.getItem(idx);
            int luceneId = ipedCase.getLuceneId(itemId);
            try{
                BytesRef bytesRef = similarityFeaturesValues.lookupOrd(0);
                byte[] currFeatures = bytesRef.bytes.clone();
                idToFeaturesMap.put(idx, currFeatures);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }           
            
        } 
        
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
        
        
        for (int i = start - 1; i < topResults.size(); i++) {
            int idx = topResults.get(i);
            byte[] currFeatures = idToFeaturesMap.get(idx);
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
                //cut = result.getScore(topResults.get(topResults.size() - 1));
            }
        }
    }*/
    

}