package iped.engine.rag;

import java.io.IOException;

/**
 * Interface for vector embedding generation providers.
 *
 * @author Rui Sant'Ana Junior
 */
public interface EmbeddingProvider {
    /**
     * Generates a numeric vector representation (embedding) for the given text.
     * 
     * @param text input text
     * @return float array representation of the embedding
     * @throws IOException if network or IO errors occur
     * @throws InterruptedException if thread is interrupted during call
     */
    float[] generateEmbedding(String text) throws IOException, InterruptedException;

    /**
     * Generates numeric vector representations (embeddings) for a list of texts in batch.
     * 
     * @param texts list of input texts
     * @return list of float array representations matching input texts order
     * @throws IOException if network or IO errors occur
     * @throws InterruptedException if thread is interrupted during call
     */
    default java.util.List<float[]> generateEmbeddings(java.util.List<String> texts) throws IOException, InterruptedException {
        java.util.List<float[]> list = new java.util.ArrayList<>();
        if (texts != null) {
            for (String t : texts) {
                list.add(generateEmbedding(t));
            }
        }
        return list;
    }
}
