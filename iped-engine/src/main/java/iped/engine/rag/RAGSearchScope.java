package iped.engine.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BytesRef;

import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.properties.ExtraProperties;

/** A selection snapshot that preserves the source of every selected item. */
public class RAGSearchScope {

    private final Map<Integer, IIPEDSource> sources = new HashMap<>();
    private final Map<Integer, Set<Integer>> items = new HashMap<>();

    public RAGSearchScope(List<? extends IIPEDSource> sources, Collection<IItemId> selectedItems) {
        for (IIPEDSource source : sources) {
            this.sources.put(source.getSourceId(), source);
        }
        for (IItemId item : selectedItems) {
            if (item == null || !this.sources.containsKey(item.getSourceId()) || item.getId() < 0) {
                throw new IllegalArgumentException("Invalid item in RAG search scope");
            }
            items.computeIfAbsent(item.getSourceId(), key -> new HashSet<>()).add(item.getId());
        }
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    Query createLuceneFilter() {
        Map<LeafReader, Query> filters = new IdentityHashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : items.entrySet()) {
            List<BytesRef> ids = new ArrayList<>();
            for (int id : entry.getValue()) {
                ids.add(new BytesRef(Integer.toString(id)));
            }
            Query filter = new TermInSetQuery("fragParentNumericId", ids);
            for (LeafReaderContext leaf : sources.get(entry.getKey()).getReader().leaves()) {
                filters.put(leaf.reader(), filter);
            }
        }
        return filters.isEmpty() ? new MatchNoDocsQuery() : new SourceItemsQuery(filters);
    }

    /** OpenSearch fragments are routed by the parent item's global ID. */
    Collection<String> getGlobalIds() throws IOException {
        Set<String> globalIds = new HashSet<>();
        for (Map.Entry<Integer, Set<Integer>> entry : items.entrySet()) {
            IIPEDSource source = sources.get(entry.getKey());
            for (int id : entry.getValue()) {
                int docId = id <= source.getLastId() ? source.getLuceneId(id) : -1;
                if (docId < 0) {
                    throw new IOException("Selected RAG item is not present in its source");
                }
                String globalId = source.getSearcher().doc(docId, Collections.singleton(ExtraProperties.GLOBAL_ID))
                        .get(ExtraProperties.GLOBAL_ID);
                if (globalId == null || globalId.isEmpty()) {
                    throw new IOException("Selected RAG item has no global ID");
                }
                globalIds.add(globalId);
            }
        }
        return globalIds;
    }

    /** Applies each numeric ID filter only to the Lucene readers of its source. */
    private static class SourceItemsQuery extends Query {

        private final Map<LeafReader, Query> filters;

        private SourceItemsQuery(Map<LeafReader, Query> filters) {
            this.filters = filters;
        }

        @Override
        public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
            Map<Query, Weight> weights = new IdentityHashMap<>();
            for (Query filter : filters.values()) {
                if (!weights.containsKey(filter)) {
                    weights.put(filter, searcher.createWeight(searcher.rewrite(filter), ScoreMode.COMPLETE_NO_SCORES, 1f));
                }
            }
            return new ConstantScoreWeight(this, boost) {
                @Override
                public Scorer scorer(LeafReaderContext context) throws IOException {
                    Query filter = filters.get(context.reader());
                    if (filter == null) {
                        return null;
                    }
                    Scorer scorer = weights.get(filter).scorer(context);
                    if (scorer == null) {
                        return null;
                    }
                    return scorer.twoPhaseIterator() == null
                            ? new ConstantScoreScorer(this, score(), scoreMode, scorer.iterator())
                            : new ConstantScoreScorer(this, score(), scoreMode, scorer.twoPhaseIterator());
                }

                @Override
                public boolean isCacheable(LeafReaderContext context) {
                    return false;
                }
            };
        }

        @Override
        public void visit(QueryVisitor visitor) {
            visitor.visitLeaf(this);
        }

        @Override
        public String toString(String field) {
            return "RAG source selection";
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }
}
