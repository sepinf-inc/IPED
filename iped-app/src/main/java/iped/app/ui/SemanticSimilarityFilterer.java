package iped.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.rag.RAGService;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilterer;

/**
 * Filterer that restricts the result table to items that are semantically
 * similar to a reference document, as determined by the RAG module.
 *
 * The set of matching IPED item-IDs (source-relative integer IDs stored as
 * strings) is populated externally by {@code SemanticSimilarityFilterActions}
 * and translated into a Lucene id: query here.
 */
public class SemanticSimilarityFilterer implements IQueryFilterer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SemanticSimilarityFilterer.class);

    /** IPED item-IDs (string form) that are semantically similar to the reference. */
    private Set<String> similarItemIds;
    private SemanticSimilarityFilter currentFilter;

    // ----------------------------------------------------- public API

    /**
     * Activates the semantic-similarity filter with the given set of item-IDs.
     * Pass {@code null} to clear.
     */
    public void setSimilarItemIds(Set<String> ids) {
        this.similarItemIds = ids;
        this.currentFilter = null; // force recreation on next getQuery()
    }

    public Set<String> getSimilarItemIds() {
        return similarItemIds;
    }

    public boolean isActive() {
        return similarItemIds != null && !similarItemIds.isEmpty();
    }

    // ----------------------------------------------------- IQueryFilterer

    @Override
    public List<IFilter> getDefinedFilters() {
        if (currentFilter != null) {
            List<IFilter> list = new ArrayList<>();
            list.add(currentFilter);
            return list;
        }
        return null;
    }

    @Override
    public boolean hasFilters() {
        return isActive();
    }

    @Override
    public boolean hasFiltersApplied() {
        return false; // colour-coding handled externally
    }

    @Override
    public void clearFilter() {
        similarItemIds = null;
        currentFilter = null;
    }

    @Override
    public Query getQuery() {
        if (!isActive()) {
            return null;
        }
        if (currentFilter == null) {
            currentFilter = new SemanticSimilarityFilter(similarItemIds);
        }
        return currentFilter.getQuery();
    }
}
