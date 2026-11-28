package iped.engine.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorPipeline;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.processors.AnalyzerQueryNodeProcessor;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;

import iped.engine.lucene.analysis.AnalyzerVersion;
import iped.engine.lucene.analysis.StandardASCIIAnalyzer;

public class UnicodeQueryParsingTest {

    private static Query parse(String text) throws Exception {
        StandardASCIIAnalyzer analyzer = new StandardASCIIAnalyzer();
        analyzer.setConvertCharsToLower(true);
        analyzer.setConvertCharsToAscii(true);
        analyzer.setAnalyzerVersion(AnalyzerVersion.UNICODE);
        StandardQueryParser parser = new StandardQueryParser(analyzer);
        parser.setAllowLeadingWildcard(true);
        QueryNodeProcessorPipeline pipeline = (QueryNodeProcessorPipeline) parser.getQueryNodeProcessor();
        for (int i = 0; i < pipeline.size(); i++) {
            if (pipeline.get(i) instanceof AnalyzerQueryNodeProcessor) {
                pipeline.add(i, new ClusterScriptPhraseQueryNodeProcessor());
                break;
            }
        }
        return parser.parse(text, "content");
    }

    @Test
    public void testScriptsWithoutSpacesBecomePhrases() throws Exception {
        Query query = parse("杂志");
        assertTrue(query instanceof PhraseQuery);
        assertEquals("content:\"杂 志\"", query.toString());
        assertEquals("content:\"ส วั ส ดี\"", parse("สวัสดี").toString());
        assertEquals("content:\"이 름\"", parse("이름").toString());
        // the wildcard workaround needed by legacy cases still works
        assertEquals("content:\"杂 志\"", parse("*杂志*").toString());
    }

    @Test
    public void testOtherScriptsAreSingleTerms() throws Exception {
        assertTrue(parse("Москве") instanceof TermQuery);
        assertEquals("content:בראשית", parse("בְּרֵאשִׁית").toString());
        assertEquals("content:تقرا", parse("تُقرأ").toString());
        assertEquals("content:моск*", parse("Моск*").toString());
    }

    @Test
    public void testFoldedQuerySyntaxIsReplaced() {
        assertEquals("这是什么 ", QueryBuilder.replaceFoldedQuerySyntax("这是什么？"));
        assertEquals("王明 这是什么", QueryBuilder.replaceFoldedQuerySyntax("王明：这是什么"));
        assertEquals(" 这是书 ", QueryBuilder.replaceFoldedQuerySyntax("（这是书）"));
        assertEquals("Lisboa   Porto", QueryBuilder.replaceFoldedQuerySyntax("Lisboa – Porto"));
        // typographic quotes are kept, they are used as phrase quotes
        assertEquals("“foo bar”", QueryBuilder.replaceFoldedQuerySyntax("“foo bar”"));
        assertEquals("plain ascii: a?b*", QueryBuilder.replaceFoldedQuerySyntax("plain ascii: a?b*"));
    }
}
