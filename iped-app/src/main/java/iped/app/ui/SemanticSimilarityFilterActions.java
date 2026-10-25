package iped.app.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.rag.RAGAssistantPanel;
import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.engine.rag.RAGSearchScope;
import iped.engine.rag.RAGService;
import iped.engine.rag.RAGService.RAGSourceDoc;

/**
 * Static action handler for the "Find Similar Documents (AI)" context menu
 * item. This class:
 * <ol>
 *   <li>Averages the embedding vectors of the text fragments of the currently
 *       selected item, read from the case index.</li>
 *   <li>Performs a KNN vector search to find semantically similar items.</li>
 *   <li>Activates {@link SemanticSimilarityFilterer} with the items found (source
 *       and ID, so multicase items are not mixed), which refreshes the result table.</li>
 * </ol>
 */
public class SemanticSimilarityFilterActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(SemanticSimilarityFilterActions.class);

    private static final int MAX_SIMILAR_ITEMS = 200;

    /** Score column of the result table, also used by the similar images/faces filters. */
    private static final int SCORE_COLUMN = 2;

    private static List<? extends SortKey> prevSortKeys;

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

        RAGService ragService = RAGService.getInstance();
        if (ragService == null || !ragService.getConfig().isEnabled()) {
            JOptionPane.showMessageDialog(
                    app,
                    Messages.getString("RAGAssistant.NotEnabled"),
                    Messages.getString("RAGAssistant.ErrorTitle"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Ask for the threshold as the classic "Find Similar Documents" does; the config value is the default.
        Integer minPercent = askMinPercent(app,
                Math.round(ragService.getConfig().getSemanticSimilarityMinScore() * 100));
        if (minPercent == null) {
            return;
        }

        List<? extends IIPEDSource> sources = app.appCase.getAtomicSources();

        new SwingWorker<Map<IItemId, Float>, Void>() {
            @Override
            protected Map<IItemId, Float> doInBackground() throws Exception {
                float[] avgVector = ragService.getAverageEmbedding(
                        new RAGSearchScope(sources, Collections.singleton(itemId)));
                if (avgVector == null) {
                    return null;
                }
                // Candidates are kept down to cosine 0, so the slider can lower the threshold
                // without a new search; the filter applies the threshold.
                Map<IItemId, Float> similar = new LinkedHashMap<>();
                for (RAGSourceDoc doc : ragService.findSimilarDocuments(avgVector, MAX_SIMILAR_ITEMS, 0f)) {
                    IItemId similarId = RAGAssistantPanel.resolveSourceItem(sources, doc);
                    if (similarId != null && !similar.containsKey(similarId)) {
                        // The score column is a 0-100 bar: show the cosine as a percentage.
                        similar.put(similarId, doc.score * 100);
                    }
                }
                return similar;
            }

            @Override
            protected void done() {
                try {
                    Map<IItemId, Float> similar = get();
                    if (similar == null || similar.isEmpty()) {
                        String key = similar == null ? "SemanticSimilarity.NoEmbedding" : "SemanticSimilarity.NoResults";
                        JOptionPane.showMessageDialog(
                                app,
                                Messages.getString(key),
                                Messages.getString("RAGAssistant.ErrorTitle"),
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    app.semanticSimilarityFilterer.setFilter(new SemanticSimilarityFilter(itemId,
                            app.appCase.getItemByItemId(itemId), similar, minPercent));
                    sortByScore(app);
                    app.appletListener.updateFileListing();
                } catch (Exception e) {
                    LOGGER.error("Error performing semantic similarity search", e);
                    JOptionPane.showMessageDialog(
                            app,
                            Messages.getString("SemanticSimilarity.Error") + " " + e.getMessage(),
                            Messages.getString("RAGAssistant.ErrorTitle"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Asks the minimum similarity (0-100); null when canceled or invalid. */
    private static Integer askMinPercent(App app, int defaultPercent) {
        String input = JOptionPane.showInputDialog(app, Messages.getString("MenuListener.SimilarityLabel"),
                defaultPercent);
        if (input == null) {
            return null;
        }
        Integer percent = parsePercent(input);
        if (percent == null) {
            JOptionPane.showMessageDialog(app, Messages.getString("MenuListener.SimilarityLabel"),
                    Messages.getString("RAGAssistant.ErrorTitle"), JOptionPane.WARNING_MESSAGE);
        }
        return percent;
    }

    /** Integer percentage in [0, 100], or null. */
    static Integer parsePercent(String input) {
        try {
            int percent = Integer.parseInt(input.trim());
            return percent >= 0 && percent <= 100 ? percent : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Sorts by score (the similarity), as the similar images/faces filters do. */
    static void sortByScore(App app) {
        List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
        if (sortKeys == null || sortKeys.isEmpty() || sortKeys.get(0).getColumn() != SCORE_COLUMN) {
            prevSortKeys = sortKeys;
            List<RowSorter.SortKey> sortScore = new ArrayList<>();
            sortScore.add(new RowSorter.SortKey(SCORE_COLUMN, SortOrder.DESCENDING));
            ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(sortScore);
        }
    }

    /** Restores the sorting used before the similarity search, if still sorted by score. */
    static void restoreSorting(App app) {
        List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
        if (prevSortKeys != null && sortKeys != null && !sortKeys.isEmpty()
                && sortKeys.get(0).getColumn() == SCORE_COLUMN) {
            ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(prevSortKeys);
        }
        prevSortKeys = null;
    }

    /** Returns {@code true} if the RAG module is initialized for this case. */
    public static boolean isFeatureEnabled() {
        RAGService rag = RAGService.getInstance();
        return rag != null && rag.getConfig().isEnabled();
    }
}
