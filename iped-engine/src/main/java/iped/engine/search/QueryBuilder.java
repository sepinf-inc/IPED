package iped.engine.search;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorPipeline;
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.queryparser.flexible.standard.processors.AnalyzerQueryNodeProcessor;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.search.join.ToParentBlockJoinQuery;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IIPEDSource;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.IndexTaskConfig;
import iped.engine.data.IPEDMultiSource;
import iped.engine.localization.CategoryLocalization;
import iped.engine.lucene.PublicPointRangeQuery;
import iped.engine.lucene.analysis.AnalyzerVersion;
import iped.engine.lucene.analysis.AppAnalyzer;
import iped.engine.lucene.analysis.FastASCIIFoldingFilter;
import iped.engine.lucene.analysis.UnicodeTokenizer;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.localization.LocalizedProperties;
import iped.properties.BasicProps;
import iped.utils.LocalizedFormat;

public class QueryBuilder {

    private static Logger logger = LoggerFactory.getLogger(QueryBuilder.class);

    private static Analyzer spaceAnalyzer = new WhitespaceAnalyzer();

    private static HashMap<String, PointsConfig> pointsConfigCache;

    private static IIPEDSource prevIpedCase;

    private static QueryBitSetProducer parentsFilter;

    private static Object lock = new Object();

    /**
     * Non ASCII characters that ASCII folding turns into query syntax
     * characters. Typographic double quotes are not included, they are accepted
     * as phrase quotes.
     */
    private static final boolean[] FOLDS_TO_QUERY_SYNTAX = buildFoldsToQuerySyntax();

    private IIPEDSource ipedCase;

    private boolean allowLeadingWildCard = true;

    private boolean mapChildToParentDocs = false;

    public QueryBuilder(IIPEDSource ipedCase) {
        this.ipedCase = ipedCase;
    }

    public QueryBuilder(IIPEDSource ipedCase, boolean mapChildToParentDocs) {
        this.ipedCase = ipedCase;
        this.mapChildToParentDocs = mapChildToParentDocs;
    }

    public void setAllowLeadingWildcard(boolean allow) {
        this.allowLeadingWildCard = allow;
    }

    private static boolean[] buildFoldsToQuerySyntax() {
        String syntaxChars = "+-&|!(){}[]^~*?:\\/"; //$NON-NLS-1$
        boolean[] result = new boolean[Character.MAX_VALUE + 1];
        char[] in = new char[1];
        char[] out = new char[8];
        for (int c = 0x80; c <= Character.MAX_VALUE; c++) {
            in[0] = (char) c;
            int len = FastASCIIFoldingFilter.foldToASCII(in, 0, out, 0, 1);
            result[c] = len == 1 && syntaxChars.indexOf(out[0]) >= 0;
        }
        return result;
    }

    /**
     * Replaces by spaces the punctuation that ASCII folding would turn into query
     * syntax, like the full width question marks, colons and parenthesis of CJK
     * texts or en/em dashes, which would otherwise become wildcards, field
     * separators, groups or NOT operators when a text is pasted in the search box.
     */
    static String replaceFoldedQuerySyntax(String text) {
        char[] chars = null;
        for (int i = 0; i < text.length(); i++) {
            if (FOLDS_TO_QUERY_SYNTAX[text.charAt(i)]) {
                if (chars == null) {
                    chars = text.toCharArray();
                }
                chars[i] = ' ';
            }
        }
        return chars == null ? text : new String(chars);
    }

    /**
     * Joins the terms of a phrase with spaces, except between characters of
     * scripts written without spaces, which are indexed one character per term.
     */
    private static String joinPhraseTerms(List<Term> terms) {
        StringBuilder sb = new StringBuilder();
        String prev = null;
        for (Term term : terms) {
            String text = term.text().toLowerCase();
            if (prev != null && !(isClusterScriptBoundary(prev, true) && isClusterScriptBoundary(text, false))) {
                sb.append(' ');
            }
            sb.append(text);
            prev = text;
        }
        return sb.toString();
    }

    private static boolean isClusterScriptBoundary(String text, boolean atEnd) {
        if (text.isEmpty()) {
            return false;
        }
        int cp = atEnd ? text.codePointBefore(text.length()) : text.codePointAt(0);
        return UnicodeTokenizer.isClusterScriptChar(cp) || Character.getType(cp) == Character.NON_SPACING_MARK
                || Character.getType(cp) == Character.COMBINING_SPACING_MARK;
    }

    public static String escape(String query) {
        query = query.replace('“', '"').replace('”', '"').replace('„', '"').replace('＂', '"');
        query = query.replace('«', ' ').replace('»', ' ');
        return QueryParserUtil.escape(query);
    }

    private Set<String> getQueryStrings(Query query) {
        Set<String> result = new TreeSet<String>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o2.compareTo(o1);
            }
        });
        if (query != null) {
            if (query instanceof BooleanQuery) {
                for (BooleanClause clause : ((BooleanQuery) query).clauses()) {
                    if (!clause.isProhibited()) {
                        result.addAll(getQueryStrings(clause.getQuery()));
                    }
                }
            } else if (query instanceof BoostQuery) {
                result.addAll(getQueryStrings(((BoostQuery) query).getQuery()));

            } else if (query instanceof ConstantScoreQuery) {
                result.addAll(getQueryStrings(((ConstantScoreQuery) query).getQuery()));

            } else if (query instanceof ToParentBlockJoinQuery) {
                result.addAll(getQueryStrings(((ToParentBlockJoinQuery) query).getChildQuery()));

            } else {
                TreeSet<Term> termSet = new TreeSet<Term>();
                if (query instanceof TermQuery) {
                    termSet.add(((TermQuery) query).getTerm());
                }
                if (query instanceof PhraseQuery) {
                    List<Term> terms = Arrays.asList(((PhraseQuery) query).getTerms());
                    if (((PhraseQuery) query).getSlop() == 0) {
                        result.add(joinPhraseTerms(terms));
                    } else {
                        termSet.addAll(terms);
                    }
                }
                if (query instanceof MultiTermQuery) {
                    MultiTermQuery mtq = (MultiTermQuery) query;
                    try {
                        TermsEnum terms = mtq.getTermsEnum(ipedCase.getLeafReader().terms(mtq.getField()));
                        int maxTerms = IndexSearcher.getMaxClauseCount();
                        BytesRef br;
                        while (termSet.size() < maxTerms && (br = terms.next()) != null) {
                            termSet.add(new Term(mtq.getField(), br.utf8ToString()));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                for (Term term : termSet) {
                    if (term.field().equalsIgnoreCase(IndexItem.CONTENT)) {
                        result.add(term.text().toLowerCase());
                    }
                }
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
        return LocalizedProperties.getNonLocalizedField(field);
    }

    private Query getContentQuery(Query query, String field) {
        if (!mapChildToParentDocs || !BasicProps.CONTENT.equals(field)) {
            return query;
        }
        ToParentBlockJoinQuery blockJoinQuery = new ToParentBlockJoinQuery(query, getParentsFilter(), ScoreMode.Total);
        return blockJoinQuery;
    }

    private QueryBitSetProducer getParentsFilter() {
        synchronized (lock) {
            if (ipedCase == prevIpedCase && parentsFilter != null) {
                return parentsFilter;
            }
            parentsFilter = new QueryBitSetProducer(new FieldExistsQuery(BasicProps.ID));
            ipedCase.getReader().leaves().forEach(context -> {
                try {
                    parentsFilter.getBitSet(context);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            prevIpedCase = ipedCase;
            return parentsFilter;
        }
    }

    public static Query getMatchAllItemsQuery() {
        return IntPoint.newRangeQuery(BasicProps.ID, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public Query rewriteQuery(Query query) {
        return rewriteQuery(query, true);
    }

    public Query rewriteQuery(Query query, boolean expandCategories) {
        if (query == null) {
            return null;
        }
        if (query instanceof MatchAllDocsQuery) {
            return getMatchAllItemsQuery();

        } else if (query instanceof MatchNoDocsQuery) {
            return query;

        } else if (query instanceof FieldExistsQuery) {
            return query;

        } else if (query instanceof BooleanQuery) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            for (BooleanClause clause : ((BooleanQuery) query).clauses()) {
                builder.add(rewriteQuery(clause.getQuery(), expandCategories), clause.getOccur());
            }
            builder.setMinimumNumberShouldMatch(((BooleanQuery) query).getMinimumNumberShouldMatch());
            return builder.build();

        } else if (query instanceof BoostQuery) {
            BoostQuery bq = (BoostQuery) query;
            return new BoostQuery(rewriteQuery(bq.getQuery(), expandCategories), bq.getBoost());

        } else if (query instanceof TermQuery) {
            Term term = ((TermQuery) query).getTerm();
            term = getNonLocalizedTerm(term);
            if (expandCategories && BasicProps.CATEGORY.equals(term.field())) {
                String value = term.text();
                Set<String> descendants = ipedCase.getDescendantsCategories(value);
                if (descendants != null) {
                    BooleanQuery.Builder builder = new BooleanQuery.Builder();
                    builder.add(query, Occur.SHOULD);
                    for (String descendant : descendants) {
                        builder.add(new TermQuery(new Term(term.field(), descendant)), Occur.SHOULD);
                    }
                    BooleanQuery expandedQuery = builder.build();
                    return rewriteQuery(expandedQuery, false);
                }
            }
            return getContentQuery(new TermQuery(term), term.field());

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
            } else {
                for (Term term : pq.getTerms()) {
                    builder.add(getNonLocalizedTerm(term), pq.getPositions()[i++]);
                }
            }
            return getContentQuery(builder.build(), pq.getField());

        } else if (query instanceof PrefixQuery) {
            PrefixQuery pq = (PrefixQuery) query;
            return getContentQuery(new PrefixQuery(getNonLocalizedTerm(pq.getPrefix())), pq.getField());

        } else if (query instanceof WildcardQuery) {
            WildcardQuery q = (WildcardQuery) query;
            return getContentQuery(new WildcardQuery(getNonLocalizedTerm(q.getTerm())), q.getField());

        } else if (query instanceof FuzzyQuery) {
            FuzzyQuery q = (FuzzyQuery) query;
            return getContentQuery(new FuzzyQuery(getNonLocalizedTerm(q.getTerm()), q.getMaxEdits(),
                    q.getPrefixLength(), FuzzyQuery.defaultMaxExpansions, q.getTranspositions()), q.getField());

        } else if (query instanceof RegexpQuery) {
            RegexpQuery q = (RegexpQuery) query;
            return getContentQuery(new RegexpQuery(getNonLocalizedTerm(q.getRegexp())), q.getField());

        } else if (query instanceof TermRangeQuery) {
            TermRangeQuery q = (TermRangeQuery) query;
            return getContentQuery(new TermRangeQuery(getNonLocalizedField(q.getField()), q.getLowerTerm(),
                    q.getUpperTerm(), q.includesLower(), q.includesUpper()), q.getField());

        } else if (query instanceof PointRangeQuery) {
            PointRangeQuery q = (PointRangeQuery) query;
            return new PublicPointRangeQuery(getNonLocalizedField(q.getField()), q);

        } else if (query instanceof ConstantScoreQuery) {
            ConstantScoreQuery q = (ConstantScoreQuery) query;
            return new ConstantScoreQuery(rewriteQuery(q.getQuery(), expandCategories));

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

        Set<String> result = new HashSet<>();
        Query query = null;
        if (queryText != null) {
            try {
                query = getQuery(queryText, spaceAnalyzer);
                result.addAll(getQueryStrings(query));
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                query = getQuery(queryText, ipedCase.getAnalyzer());
                result.addAll(getQueryStrings(query));
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                query = getQuery(queryText, new KeywordAnalyzer());
                result.addAll(getQueryStrings(query));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // a blank term, e.g. from "" or from quoted punctuation that the analysis drops, would match everywhere
        result.removeIf(String::isBlank);

        logger.info("Expanded query terms: {}", result.toString());

        return result;
    }

    public Query getQuery(String texto) throws ParseException, QueryNodeException {
        if (ipedCase instanceof IPEDMultiSource && !texto.trim().isEmpty()) {
            // cases indexed with different text analysis versions: each one matches the query parsed with its own
            IPEDMultiSource multiSource = (IPEDMultiSource) ipedCase;
            if (multiSource.getAnalyzerVersions().size() > 1) {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (int version : multiSource.getAnalyzerVersions()) {
                    builder.add(getQuery(texto, multiSource.getAnalyzer(version)), Occur.SHOULD);
                }
                return builder.build();
            }
        }
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
            parser.setAllowLeadingWildcard(allowLeadingWildCard);
            parser.setFuzzyPrefixLength(2);
            parser.setFuzzyMinSim(0.7f);
            parser.setDateResolution(DateTools.Resolution.SECOND);
            parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
            parser.setPointsConfigMap(getPointsConfigMap());

            HashMap<String, Float> fieldBoost = new HashMap<>();
            fieldBoost.put(IndexItem.NAME, 1000.0f);
            parser.setFieldsBoost(fieldBoost);

            if (AppAnalyzer.getVersion(analyzer) >= AnalyzerVersion.UNICODE) {
                addClusterScriptPhraseProcessor(parser, getFieldsWithPositionsPredicate());
            }

            IndexTaskConfig indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);
            // removes diacritics, StandardQueryParser doesn't remove them from WildcardQueries
            if (analyzer != spaceAnalyzer && indexConfig.isConvertCharsToAscii()) {
                texto = IndexItem.normalize(replaceFoldedQuerySyntax(texto), false);
            }

            try {
                Query q;
                synchronized (lock) {
                    q = parser.parse(texto, null);
                }
                q = handleNegativeQueries(q, analyzer);
                q = rewriteQuery(q);
                return q;

            } catch (org.apache.lucene.queryparser.flexible.core.QueryNodeException e) {
                throw new QueryNodeException(e);
            }
        }

    }

    /**
     * Phrase queries fail on fields indexed without positions (ids, paths,
     * dates...). Unknown fields (no documents) are considered to have them.
     */
    private Predicate<CharSequence> getFieldsWithPositionsPredicate() {
        FieldInfos fieldInfos = ipedCase.getLeafReader().getFieldInfos();
        return field -> {
            FieldInfo info = fieldInfos.fieldInfo(getNonLocalizedField(field.toString()));
            return info == null || info.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
        };
    }

    private static void addClusterScriptPhraseProcessor(StandardQueryParser parser,
            Predicate<CharSequence> fieldHasPositions) {
        QueryNodeProcessor processor = parser.getQueryNodeProcessor();
        if (processor instanceof QueryNodeProcessorPipeline) {
            QueryNodeProcessorPipeline pipeline = (QueryNodeProcessorPipeline) processor;
            for (int i = 0; i < pipeline.size(); i++) {
                if (pipeline.get(i) instanceof AnalyzerQueryNodeProcessor) {
                    pipeline.add(i, new ClusterScriptPhraseQueryNodeProcessor(fieldHasPositions));
                    return;
                }
            }
        }
        logger.warn("Could not configure phrase queries for scripts written without spaces"); //$NON-NLS-1$
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
            } else {
                builder.setMinimumNumberShouldMatch(((BooleanQuery) q).getMinimumNumberShouldMatch());
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

        NumberFormat nf = LocalizedFormat.getNumberInstance();
        PointsConfig configLong = new PointsConfig(nf, Long.class);
        PointsConfig configInt = new PointsConfig(nf, Integer.class);
        PointsConfig configFloat = new PointsConfig(nf, Float.class);
        PointsConfig configDouble = new PointsConfig(nf, Double.class);

        for (String field : LoadIndexFields.getFields(Arrays.asList(ipedCase))) {
            Class<?> type = IndexItem.getMetadataTypes().get(field);
            if (type == null)
                continue;
            if (type.equals(Integer.class) || type.equals(Short.class) || type.equals(Byte.class))
                pointsConfigMap.put(field, configInt);
            else if (type.equals(Long.class))
                pointsConfigMap.put(field, configLong);
            else if (type.equals(Float.class))
                pointsConfigMap.put(field, configFloat);
            else if (type.equals(Double.class))
                pointsConfigMap.put(field, configDouble);
        }

        for (String field : pointsConfigMap.keySet().toArray(new String[0])) {
            String localizedField = LocalizedProperties.getLocalizedField(field);
            pointsConfigMap.put(localizedField, pointsConfigMap.get(field));
        }

        synchronized (lock) {
            pointsConfigCache = pointsConfigMap;
            prevIpedCase = ipedCase;
        }

        return pointsConfigMap;
    }
}
