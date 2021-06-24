package dpf.sp.gpinf.indexer.search;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.PublicPointRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;

import dpf.sp.gpinf.indexer.config.CategoryLocalization;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IndexTaskConfig;
import dpf.sp.gpinf.indexer.process.IndexItem;
import iped3.IIPEDSource;
import iped3.exception.ParseException;
import iped3.exception.QueryNodeException;
import iped3.search.IQueryBuilder;
import iped3.util.BasicProps;

public class QueryBuilder implements IQueryBuilder {

    private static Analyzer spaceAnalyzer = new WhitespaceAnalyzer();

    private static HashMap<String, PointsConfig> pointsConfigCache;

    private static IIPEDSource prevIpedCase;

    private static Object lock = new Object();

    private IIPEDSource ipedCase;

    public QueryBuilder(IIPEDSource ipedCase) {
        this.ipedCase = ipedCase;
    }

    private Set<String> getQueryStrings(Query query) {
        HashSet<String> result = new HashSet<String>();
        if (query != null)
            if (query instanceof BooleanQuery) {
                for (BooleanClause clause : ((BooleanQuery) query).clauses()) {
                    // System.out.println(clause.getQuery().toString());
                    result.addAll(getQueryStrings(clause.getQuery()));
                }
            } else if (query instanceof BoostQuery) {
                result.addAll(getQueryStrings(((BoostQuery) query).getQuery()));
            } else {
                TreeSet<Term> termSet = new TreeSet<Term>();
                if (query instanceof TermQuery)
                    termSet.add(((TermQuery) query).getTerm());
                if (query instanceof PhraseQuery) {
                    List<Term> terms = Arrays.asList(((PhraseQuery) query).getTerms());
                    if (((PhraseQuery) query).getSlop() == 0) {
                        result.add(terms.stream().map(t -> t.text().toLowerCase()).collect(Collectors.joining(" "))); //$NON-NLS-1$
                    } else
                        termSet.addAll(terms);
                }

                for (Term term : termSet)
                    if (term.field().equalsIgnoreCase(IndexItem.CONTENT)) {
                        result.add(term.text().toLowerCase());
                        // System.out.println(term.text());
                    }
            }

        return result;

    }

    private Term getNonLocalizedTerm(Term term) {
        String field = getNonLocalizedField(term.field());
        String value = term.text();
        if (BasicProps.CATEGORY.equals(field)) {
            value = getNonLocalizedCategory(value).toLowerCase();
        }
        return new Term(field, value);
    }

    private String getNonLocalizedCategory(String category) {
        return CategoryLocalization.getInstance().getNonLocalizedCategory(category);
    }

    private String getNonLocalizedField(String field) {
        return BasicProps.getNonLocalizedField(field);
    }

    public Query getNonLocalizedQuery(Query query) {
        if (query == null) {
            return null;
        }
        if (query instanceof MatchAllDocsQuery) {
            return query;

        } else if (query instanceof BooleanQuery) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            for (BooleanClause clause : ((BooleanQuery) query).clauses()) {
                builder.add(getNonLocalizedQuery(clause.getQuery()), clause.getOccur());
            }
            return builder.build();

        } else if (query instanceof BoostQuery) {
            BoostQuery bq = (BoostQuery) query;
            return new BoostQuery(getNonLocalizedQuery(bq.getQuery()), bq.getBoost());

        } else if (query instanceof TermQuery) {
            Term term = ((TermQuery) query).getTerm();
            return new TermQuery(getNonLocalizedTerm(term));

        } else if (query instanceof PhraseQuery) {
            PhraseQuery pq = (PhraseQuery) query;
            PhraseQuery.Builder builder = new PhraseQuery.Builder();
            builder.setSlop(pq.getSlop());
            int i = 0;

            if (BasicProps.CATEGORY.equals(getNonLocalizedField(pq.getTerms()[0].field()))) {
                List<TermAndPos> terms = new ArrayList<>();
                for (Term term : pq.getTerms()) {
                    terms.add(new TermAndPos(term, pq.getPositions()[i++]));
                }
                Collections.sort(terms);
                String category = terms.stream().map(t -> t.term.text()).collect(Collectors.joining(" "));
                category = getNonLocalizedCategory(category).toLowerCase();
                i = 0;
                for (String term : category.split(" ")) {
                    builder.add(new Term(BasicProps.CATEGORY, term), i++);
                }
            }else {
                for (Term term : pq.getTerms()) {
                    builder.add(getNonLocalizedTerm(term), pq.getPositions()[i++]);
                }
            }
            return builder.build();

        } else if (query instanceof PrefixQuery) {
            PrefixQuery pq = (PrefixQuery) query;
            return new PrefixQuery(getNonLocalizedTerm(pq.getPrefix()));

        } else if (query instanceof WildcardQuery) {
            WildcardQuery q = (WildcardQuery) query;
            return new WildcardQuery(getNonLocalizedTerm(q.getTerm()));

        } else if (query instanceof FuzzyQuery) {
            FuzzyQuery q = (FuzzyQuery) query;
            return new FuzzyQuery(getNonLocalizedTerm(q.getTerm()), q.getMaxEdits(), q.getPrefixLength(),
                    FuzzyQuery.defaultMaxExpansions, q.getTranspositions());

        } else if (query instanceof RegexpQuery) {
            RegexpQuery q = (RegexpQuery) query;
            return new RegexpQuery(getNonLocalizedTerm(q.getRegexp()));

        } else if (query instanceof TermRangeQuery) {
            TermRangeQuery q = (TermRangeQuery) query;
            return new TermRangeQuery(getNonLocalizedField(q.getField()), q.getLowerTerm(), q.getUpperTerm(),
                    q.includesLower(), q.includesUpper());

        } else if (query instanceof PointRangeQuery) {
            PointRangeQuery q = (PointRangeQuery) query;
            return new PublicPointRangeQuery(getNonLocalizedField(q.getField()), q);

        } else if (query instanceof ConstantScoreQuery) {
            ConstantScoreQuery q = (ConstantScoreQuery) query;
            return new ConstantScoreQuery(getNonLocalizedQuery(q.getQuery()));

        } else {
            // TODO: handle MultiPhraseQuery and DisjunctionMaxQuery
            throw new RuntimeException(query.getClass().getSimpleName() + " not handled currently");
        }

    }

    private class TermAndPos implements Comparable<TermAndPos> {
        Term term;
        int position;

        TermAndPos(Term term, int position) {
            this.term = term;
            this.position = position;
        }

        @Override
        public int compareTo(TermAndPos o) {
            return Integer.compare(this.position, o.position);
        }
    }

    public Set<String> getQueryStrings(String queryText) {
        Query query = null;
        if (queryText != null)
            try {
                query = getQuery(queryText, spaceAnalyzer).rewrite(ipedCase.getReader());

            } catch (Exception e) {
                e.printStackTrace();
            }

        Set<String> result = getQueryStrings(query);

        if (queryText != null)
            try {
                query = getQuery(queryText, ipedCase.getAnalyzer()).rewrite(ipedCase.getReader());

                result.addAll(getQueryStrings(query));

            } catch (Exception e) {
                e.printStackTrace();
            }

        return result;
    }

    public Query getQuery(String texto) throws ParseException, QueryNodeException {
        return getQuery(texto, ipedCase.getAnalyzer());
    }

    public Query getQuery(String texto, Analyzer analyzer) throws ParseException, QueryNodeException {

        if(texto.trim().startsWith("* "))
            texto = texto.trim().replaceFirst("\\* ", "*:* ");

        if (texto.trim().isEmpty())
            return new MatchAllDocsQuery();

        else {
            String[] fields = { IndexItem.NAME, IndexItem.CONTENT };

            StandardQueryParser parser = new StandardQueryParser(analyzer);
            parser.setMultiFields(fields);
            parser.setAllowLeadingWildcard(true);
            parser.setFuzzyPrefixLength(2);
            parser.setFuzzyMinSim(0.7f);
            parser.setDateResolution(DateTools.Resolution.SECOND);
            parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
            parser.setPointsConfigMap(getPointsConfigMap());

            // removes diacritics, StandardQueryParser doesn't remove them from WildcardQueries
            IndexTaskConfig indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);
            if (analyzer != spaceAnalyzer && indexConfig.isConvertCharsToAscii()) {
                texto = IndexItem.normalize(texto, false);
            }

            try {
                Query q;
                synchronized (lock) {
                    q = parser.parse(texto, null);
                }
                q = handleNegativeQueries(q, analyzer);
                q = getNonLocalizedQuery(q);
                return q;

            } catch (org.apache.lucene.queryparser.flexible.core.QueryNodeException e) {
                throw new QueryNodeException(e);
            }
        }

    }

    private Query handleNegativeQueries(Query q, Analyzer analyzer) {
        if(q instanceof BoostQuery) {
            float boost = ((BoostQuery) q).getBoost();
            Query query = ((BoostQuery) q).getQuery();
            return new BoostQuery(handleNegativeQueries(query, analyzer), boost);
        }
        if(q instanceof BooleanQuery) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            boolean allNegative = true;
            for (BooleanClause clause : ((BooleanQuery) q).clauses()) {
                Query subQ = handleNegativeQueries(clause.getQuery(), analyzer);
                builder.add(subQ, clause.getOccur());
                if(clause.getOccur() != Occur.MUST_NOT) {
                    allNegative = false;
                }
            }
            if(allNegative) {
                builder.add(new MatchAllDocsQuery(), Occur.SHOULD);
            }
            return builder.build();
        }
        return q;
    }

    private HashMap<String, PointsConfig> getPointsConfigMap() {

        synchronized (lock) {
            if (ipedCase == prevIpedCase && pointsConfigCache != null) {
                return pointsConfigCache;
            }
        }

        HashMap<String, PointsConfig> pointsConfigMap = new HashMap<>();

        DecimalFormat nf = new DecimalFormat();
        PointsConfig configLong = new PointsConfig(nf, Long.class);
        PointsConfig configInt = new PointsConfig(nf, Integer.class);
        PointsConfig configFloat = new PointsConfig(nf, Float.class);
        PointsConfig configDouble = new PointsConfig(nf, Double.class);

        pointsConfigMap.put(IndexItem.LENGTH, configLong);
        pointsConfigMap.put(IndexItem.getLocalizedField(IndexItem.LENGTH), configLong);
        pointsConfigMap.put(IndexItem.ID, configInt);
        pointsConfigMap.put(IndexItem.SLEUTHID, configInt);
        pointsConfigMap.put(IndexItem.PARENTID, configInt);
        pointsConfigMap.put(IndexItem.FTKID, configInt);

        for (String field : LoadIndexFields.getFields(Arrays.asList(ipedCase))) {
            Class<?> type = IndexItem.getMetadataTypes().get(field);
            if (type == null)
                continue;
            if (type.equals(Integer.class) || type.equals(Byte.class))
                pointsConfigMap.put(field, configInt);
            else if (type.equals(Long.class))
                pointsConfigMap.put(field, configLong);
            else if (type.equals(Float.class))
                pointsConfigMap.put(field, configFloat);
            else if (type.equals(Double.class))
                pointsConfigMap.put(field, configDouble);
        }

        synchronized (lock) {
            pointsConfigCache = pointsConfigMap;
            prevIpedCase = ipedCase;
        }

        return pointsConfigMap;
    }
}
