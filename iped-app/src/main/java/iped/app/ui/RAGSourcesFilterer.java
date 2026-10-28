package iped.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import iped.data.IItemId;
import iped.viewers.api.IFilter;
import iped.viewers.api.IResultSetFilterer;

/**
 * Filterer that restricts the result table to the sources of an AI Assistant
 * answer, activated from the answer in the AI Assistant tab. While it is
 * active, that tab is highlighted as the other tabs with filters applied.
 */
public class RAGSourcesFilterer implements IResultSetFilterer {

    private RAGSourcesFilter currentFilter;

    /** Activates the filter. Pass {@code null} to clear. */
    public void setFilter(RAGSourcesFilter filter) {
        currentFilter = filter;
        App app = App.get();
        if (app != null) {
            app.setRAGAssistantDefaultColor(filter == null);
        }
    }

    /**
     * Shows only the given sources in the result table, sorted by their relevance.
     *
     * @param sources relevance of each source, as a 0-100 percentage
     */
    public void showInTable(Map<IItemId, Float> sources) {
        App app = App.get();
        setFilter(new RAGSourcesFilter(sources));
        SemanticSimilarityFilterActions.sortByScore(app);
        app.appletListener.updateFileListing();
    }

    public boolean isActive() {
        return currentFilter != null;
    }

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
        return false; // colour-coding handled by the AI Assistant tab
    }

    @Override
    public void clearFilter() {
        if (currentFilter != null && App.get() != null) {
            SemanticSimilarityFilterActions.restoreSorting(App.get());
        }
        setFilter(null);
    }
}
