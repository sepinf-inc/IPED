package iped.engine.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.search.join.ToChildBlockJoinQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;

public class SimilarDocumentSearch {

    private static Logger logger = LoggerFactory.getLogger(SimilarDocumentSearch.class);

    private static final CharArraySet stopSet = getStopWords();

    public Query getQueryForSimilarDocs(IItemId item, int matchPercent, IPEDSource appCase) {

        MoreLikeThis mlt = new MoreLikeThis(appCase.getReader());
        String[] fields = { IndexItem.CONTENT };

        mlt.setMaxQueryTerms(50);
        mlt.setFieldNames(fields);
        mlt.setAnalyzer(appCase.getAnalyzer());
        mlt.setBoost(true);
        mlt.setMinDocFreq(2);
        mlt.setMaxDocFreqPct(10);
        mlt.setMinTermFreq(1);
        mlt.setMaxNumTokensParsed(10000);
        mlt.setMinWordLen(4);
        mlt.setMaxWordLen(25);
        mlt.setStopWords(stopSet);
        try {
            /*
             * StandardParser autoParser = new StandardParser();
             * autoParser.setFallback(Configuration.fallBackParser);
             * autoParser.setErrorParser(Configuration.errorParser);
             * autoParser.setPrintMetadata(false);
             * 
             * EvidenceFile ev = App.get().appCase.getItemByItemId(item); Metadata m = new
             * Metadata(); m.set(StandardParser.INDEXER_CONTENT_TYPE,
             * ev.getMediaType().toString());
             * 
             * ParsingReader pr = new ParsingReader(autoParser, ev.getStream(), m, new
             * ParseContext()); pr.startBackgroundParsing();
             * 
             * String[] keyTerms = mlt.retrieveInterestingTerms(pr, IndexItem.CONTENT);
             */

            // Approach below Works just with term vectors indexed
            // TODO: test approach above again, so we could disable term vectors, decreasing
            // index size a lot, and we could also accept external documents not in the case

            Query parentQuery = IntPoint.newExactQuery(BasicProps.ID, item.getId());
            QueryBitSetProducer parentFilter = new QueryBitSetProducer(QueryBuilder.getMatchAllItemsQuery());
            ToChildBlockJoinQuery toChildQuery = new ToChildBlockJoinQuery(parentQuery, parentFilter);

            if (appCase instanceof IPEDMultiSource) {
                appCase = ((IPEDMultiSource) appCase).getAtomicSourceBySourceId(item.getSourceId());
            }
            IPEDSearcher searcher = new IPEDSearcher(appCase, toChildQuery);
            searcher.setRewritequery(false);

            int[] docs = searcher.luceneSearch().docs;
            Arrays.sort(docs);
            int docId = docs[0];

            List<String> keyTerms = Arrays.asList(mlt.retrieveInterestingTerms(docId));

            BooleanQuery.Builder query = new BooleanQuery.Builder();
            logger.info("{} representative terms: {}", keyTerms.size(), keyTerms.toString());

            for (String s : keyTerms) {
                query.add(new TermQuery(new Term(IndexItem.CONTENT, s)), Occur.SHOULD);
            }

            query.setMinimumNumberShouldMatch(keyTerms.size() * matchPercent / 100);

            return query.build();

        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    private static CharArraySet getStopWords() {
        CharArraySet stopSet = new CharArraySet(16, false);
        stopSet.addAll(BrazilianAnalyzer.getDefaultStopSet());
        stopSet.addAll(PortugueseAnalyzer.getDefaultStopSet());
        stopSet.addAll(EnglishAnalyzer.getDefaultStopSet());
        return stopSet;
    }

}
