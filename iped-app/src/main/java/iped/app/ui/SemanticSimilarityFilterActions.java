package iped.app.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.rag.RAGService;

/**
 * Static action handler for the "Find Similar Evidence (AI)" context menu
 * item. This class:
 * <ol>
 *   <li>Retrieves the average embedding vector of all text fragments belonging
 *       to the currently selected item from OpenSearch.</li>
 *   <li>Performs a KNN vector search to find semantically similar items.</li>
 *   <li>Activates {@link SemanticSimilarityFilterer} with the returned item IDs,
 *       which causes the result table to be refreshed.</li>
 * </ol>
 */
public class SemanticSimilarityFilterActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(SemanticSimilarityFilterActions.class);

    /** Clears the current semantic-similarity filter and refreshes the table. */
    public static void clear() {
        App app = App.get();
        app.semanticSimilarityFilterer.clearFilter();
        app.appletListener.updateFileListing();
    }

    /**
     * Starts an async search for items semantically similar to the currently
     * highlighted item and updates the result table when done.
     */
    public static void searchSimilarItems() {
        App app = App.get();

        int selIdx = app.resultsTable.getSelectedRow();
        if (selIdx == -1) {
            return;
        }

        IItemId itemId = app.ipedResult.getItem(app.resultsTable.convertRowIndexToModel(selIdx));
        if (itemId == null) {
            return;
        }

        IItem item = app.appCase.getItemByItemId(itemId);
        if (item == null) {
            return;
        }

        RAGService ragService = RAGService.getInstance();
        if (ragService == null || !ragService.getConfig().isEnabled()) {
            JOptionPane.showMessageDialog(
                    app,
                    Messages.getString("RAGAssistant.NotEnabled"),
                    Messages.getString("RAGAssistant.ErrorTitle"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Use the string representation of the item's IPED integer ID as the parent ID.
        // The RAGService.getAverageEmbeddingForItem expects the parent ID stored in
        // OpenSearch's document_content.parent field (which is the IPED item integer ID).
        String parentId = String.valueOf(item.getId());

        new SwingWorker<Set<String>, Void>() {
            @Override
            protected Set<String> doInBackground() throws Exception {
                float[] avgVector = ragService.getAverageEmbeddingForItem(parentId);
                if (avgVector == null) {
                    return null;
                }
                List<String> similarIds = ragService.findSimilarDocumentIds(avgVector, 200);
                return new HashSet<>(similarIds);
            }

            @Override
            protected void done() {
                try {
                    Set<String> similarIds = get();
                    if (similarIds == null || similarIds.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                app,
                                "No semantically similar items were found for the selected evidence.",
                                Messages.getString("RAGAssistant.ErrorTitle"),
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    app.semanticSimilarityFilterer.setSimilarItemIds(similarIds);
                    app.appletListener.updateFileListing();
                } catch (Exception e) {
                    LOGGER.error("Error performing semantic similarity search", e);
                    JOptionPane.showMessageDialog(
                            app,
                            "Error: " + e.getMessage(),
                            Messages.getString("RAGAssistant.ErrorTitle"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Returns {@code true} if the RAG module is initialized for this case. */
    public static boolean isFeatureEnabled() {
        RAGService rag = RAGService.getInstance();
        return rag != null && rag.getConfig().isEnabled();
    }
}
