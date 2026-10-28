package iped.app.ui;

import java.util.ArrayList;
import java.util.List;

import iped.viewers.api.IFilter;
import iped.viewers.api.IResultSetFilterer;

/**
 * Filterer that restricts the result table to items that are semantically
 * similar to a reference document, as determined by the RAG module.
 *
 * The matching items (with their similarity) are populated externally by
 * {@code SemanticSimilarityFilterActions}.
 */
public class SemanticSimilarityFilterer implements IResultSetFilterer {

    private SemanticSimilarityFilter currentFilter;

    // ----------------------------------------------------- public API

    /** Activates the semantic-similarity filter. Pass {@code null} to clear. */
    public void setFilter(SemanticSimilarityFilter filter) {
        currentFilter = filter;
    }

    public boolean isActive() {
        return currentFilter != null;
    }

    // ----------------------------------------------------- IResultSetFilterer

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
    public IFilter getFilter() {
        return currentFilter;
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
        if (currentFilter != null && App.get() != null) {
            SemanticSimilarityFilterActions.restoreSorting(App.get());
        }
        currentFilter = null;
    }
}
