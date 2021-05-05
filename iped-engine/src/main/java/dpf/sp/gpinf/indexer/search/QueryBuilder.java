package dpf.sp.gpinf.indexer.search;

import java.text.DecimalFormat;
import java.util.Arrays;
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
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import dpf.sp.gpinf.indexer.analysis.FastASCIIFoldingFilter;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.process.IndexItem;
import iped3.IIPEDSource;
import iped3.exception.ParseException;
import iped3.exception.QueryNodeException;
import iped3.search.IQueryBuilder;

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

            // remove acentos, pois StandardQueryParser nÃ£o normaliza wildcardQueries
            AdvancedIPEDConfig advConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                    .findObjects(AdvancedIPEDConfig.class).iterator().next();
            if (analyzer != spaceAnalyzer && advConfig.isConvertCharsToAscii()) {
                char[] input = texto.toCharArray();
                char[] output = new char[input.length * 4];
                int len = FastASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
                texto = (new String(output, 0, len)).trim();
            }

            try {
                Query q = parser.parse(texto, null);
                q = handleNegativeQueries(q, analyzer);
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
