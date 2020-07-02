package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.FieldType.NumericType;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.NumericConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.NumericUtils;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.analysis.FastASCIIFoldingFilter;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.process.IndexItem;
import iped3.IIPEDSource;
import iped3.exception.ParseException;
import iped3.exception.QueryNodeException;
import iped3.search.IQueryBuilder;

public class QueryBuilder implements IQueryBuilder {

    private static Analyzer spaceAnalyzer = new WhitespaceAnalyzer(Versao.current);
    
    private static HashMap<String, NumericConfig> numericConfigCache;
    
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
                    if (clause.getQuery() instanceof PhraseQuery && ((PhraseQuery) clause.getQuery()).getSlop() == 0) {
                        String queryStr = clause.getQuery().toString();
                        // System.out.println("phrase: " + queryStr);
                        String field = IndexItem.CONTENT + ":\""; //$NON-NLS-1$
                        if (queryStr.startsWith(field)) {
                            String term = queryStr.substring(queryStr.indexOf(field) + field.length(),
                                    queryStr.lastIndexOf("\"")); //$NON-NLS-1$
                            result.add(term.toLowerCase());
                            // System.out.println(term);
                        }

                    } else {
                        // System.out.println(clause.getQuery().toString());
                        result.addAll(getQueryStrings(clause.getQuery()));
                    }

                }
                // System.out.println("boolean query");
            } else {
                TreeSet<Term> termSet = new TreeSet<Term>();
                query.extractTerms(termSet);
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
            parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
            parser.setNumericConfigMap(getNumericConfigMap());

            // remove acentos, pois StandardQueryParser nÃ£o normaliza wildcardQueries
            AdvancedIPEDConfig advConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                    .findObjects(AdvancedIPEDConfig.class).iterator().next();
            if (analyzer != spaceAnalyzer && advConfig.isConvertCharsToAscii()) {
                char[] input = texto.toCharArray();
                char[] output = new char[input.length * 4];
                FastASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
                texto = (new String(output)).trim();
            }

            try {
                return parser.parse(texto, null);
            } catch (org.apache.lucene.queryparser.flexible.core.QueryNodeException e) {
                throw new QueryNodeException(e);
            }
        }

    }

    private HashMap<String, NumericConfig> getNumericConfigMap() {
        
        synchronized(lock) {
            if(ipedCase == prevIpedCase && numericConfigCache != null) {
                return numericConfigCache;
            } 
        }

        HashMap<String, NumericConfig> numericConfigMap = new HashMap<>();

        DecimalFormat nf = new DecimalFormat();
        NumericConfig configLong = new NumericConfig(NumericUtils.PRECISION_STEP_DEFAULT, nf, NumericType.LONG);
        NumericConfig configInt = new NumericConfig(NumericUtils.PRECISION_STEP_DEFAULT, nf, NumericType.INT);
        NumericConfig configFloat = new NumericConfig(NumericUtils.PRECISION_STEP_DEFAULT, nf, NumericType.FLOAT);
        NumericConfig configDouble = new NumericConfig(NumericUtils.PRECISION_STEP_DEFAULT, nf, NumericType.DOUBLE);

        numericConfigMap.put(IndexItem.LENGTH, configLong);
        numericConfigMap.put(IndexItem.ID, configInt);
        numericConfigMap.put(IndexItem.SLEUTHID, configInt);
        numericConfigMap.put(IndexItem.PARENTID, configInt);
        numericConfigMap.put(IndexItem.FTKID, configInt);

        try {
            for (String field : ipedCase.getAtomicReader().fields()) {
                Class<?> type = IndexItem.getMetadataTypes().get(field);
                if (type == null)
                    continue;
                if (type.equals(Integer.class) || type.equals(Byte.class))
                    numericConfigMap.put(field, configInt);
                else if (type.equals(Long.class))
                    numericConfigMap.put(field, configLong);
                else if (type.equals(Float.class))
                    numericConfigMap.put(field, configFloat);
                else if (type.equals(Double.class))
                    numericConfigMap.put(field, configDouble);
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        synchronized(lock) {
            numericConfigCache = numericConfigMap;
            prevIpedCase = ipedCase;
        }

        return numericConfigMap;
    }
}
