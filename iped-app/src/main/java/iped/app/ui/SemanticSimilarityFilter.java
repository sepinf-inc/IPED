package iped.app.ui;

import java.util.Set;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import iped.viewers.api.IQueryFilter;

/**
 * A Lucene query filter that matches items whose IPED ID is in the provided
 * set of item-IDs (as returned by the semantic-similarity RAG search).
 *
 * Item IDs are stored in the index as integers in the {@code BasicProps.ID}
 * IntPoint field. This filter builds a BooleanQuery of exact IntPoint matches.
 */
public class SemanticSimilarityFilter implements IQueryFilter {

    private final Set<String> itemIds;
    private Query cachedQuery;

    public SemanticSimilarityFilter(Set<String> itemIds) {
        this.itemIds = itemIds;
    }

    @Override
    public Query getQuery() {
        if (cachedQuery != null) {
            return cachedQuery;
        }
        if (itemIds == null || itemIds.isEmpty()) {
            return null;
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String idStr : itemIds) {
            try {
                int id = Integer.parseInt(idStr);
                builder.add(IntPoint.newExactQuery(iped.properties.BasicProps.ID, id), Occur.SHOULD);
            } catch (NumberFormatException ignore) {
                // skip malformed IDs
            }
        }
        builder.setMinimumNumberShouldMatch(1);
        cachedQuery = builder.build();
        return cachedQuery;
    }

    @Override
    public String toString() {
        return Messages.get("FilterValue.SemanticSimilarity");
    }
}
