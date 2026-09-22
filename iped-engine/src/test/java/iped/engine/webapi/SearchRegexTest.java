package iped.engine.webapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;

/**
 * Unit tests for the P2-11 regex support in /search ({@link Search#prepareQ}
 * through {@link Search#buildStructuredQuery}). A balanced, space-free
 * {@code /pattern/} span in q is restored after the legacy slash escaping so
 * the flexible query parser builds a RegexpQuery from it (same raw text the
 * GUI feeds QueryBuilder.getQuery). Everything else - unbalanced or
 * space-bearing slash pairs, wildcards, paths, phrases - keeps the legacy
 * escaped behaviour byte-for-byte. Malformed patterns and patterns longer
 * than {@link Search#MAX_REGEX_LENGTH} throw the IllegalArgumentException
 * that Search.doSearch maps to HTTP 400 naming q (never an empty 200). No
 * HTTP server or index is needed.
 */
public class SearchRegexTest {

    private static String q(String query) {
        return Search.buildStructuredQuery(query, null, null, null, null, null, null);
    }

    // ---- valid regex spans reach the parser (restored, GUI syntax) ----

    @Test
    public void bareRegexSpanIsRestored() {
        assertEquals("/comprov.*/", q("/comprov.*/"));
    }

    @Test
    public void regexCombinedWithAndIsPreserved() {
        assertEquals("pix AND /comprovante/", q("pix AND /comprovante/"));
    }

    @Test
    public void fieldQualifiedRegexIsPreserved() {
        assertEquals("content:/re/", q("content:/re/"));
    }

    @Test
    public void adjacentRegexSpansAreBothRestored() {
        assertEquals("/a//b/", q("/a//b/"));
    }

    @Test
    public void regexSpanWithFilterComposition() {
        assertEquals("( /re/ ) AND category:\"whatsapp\"", q2("/re/"));
    }

    private static String q2(String query) {
        return Search.buildStructuredQuery(query, Collections.singletonList("whatsapp"), null, null,
                null, null, null);
    }

    @Test
    public void patternOfMaximalLengthIsAccepted() {
        StringBuilder p = new StringBuilder("/");
        for (int i = 0; i < Search.MAX_REGEX_LENGTH; i++) {
            p.append('a');
        }
        String query = p.append('/').toString();
        assertEquals(query, q(query));
    }

    // ---- legacy behaviour kept byte-for-byte outside valid spans ----

    @Test
    public void unbalancedSlashKeepsLegacyEscape() {
        // regression pin of the pre-P2-11 default: a single '/' stays escaped
        assertEquals("a\\/b", q("a/b"));
    }

    @Test
    public void twoSlashesFormASpanEvenAroundShortTerms() {
        // "a/b/c": the inner pair IS a balanced span (GUI parity: the parser
        // reads a AND /b/ AND c, exactly like the GUI raw text)
        assertEquals("a/b/c", q("a/b/c"));
    }

    @Test
    public void slashSpanAroundSpaceStaysEscaped() {
        assertEquals("\\/a b\\/", q("/a b/"));
    }

    @Test
    public void quotedPhraseWithSlashesIsRestoredToTheRawPhrase() {
        // inside quotes the parser builds a phrase either way - restoring
        // keeps the string byte-identical to what the GUI would submit
        assertEquals("\"a/b/c\"", q("\"a/b/c\""));
    }

    @Test
    public void userEscapedSlashIsUntouched() {
        // a pre-escaped slash (chars a, \, /, b) never forms a restorable span
        // (the span body excludes backslashes): legacy escaping still applies
        assertEquals("a\\\\/b", q("a\\/b"));
    }

    @Test
    public void wildcardsArePassedThroughUnchanged() {
        assertEquals("comprovante*", q("comprovante*"));
        assertEquals("compr?vante", q("compr?vante"));
    }

    // ---- malformed / oversized regex: IllegalArgumentException -> HTTP 400 (names q) ----

    private static void assertRejectedNamingQ(String query) {
        try {
            String out = q(query);
            fail("expected IllegalArgumentException for " + query + " but got: " + out);
        } catch (IllegalArgumentException e) {
            assertTrue("message must name the parameter: " + e.getMessage(),
                    e.getMessage().contains("parameter 'q'"));
        }
    }

    @Test
    public void unclosedGroupIsRejected() {
        assertRejectedNamingQ("/comprov(/");
    }

    @Test
    public void unclosedCharacterClassIsRejected() {
        assertRejectedNamingQ("/comprov[0-9/");
    }

    @Test
    public void unclosedRepeatBraceIsRejected() {
        // Lucene's RegExp accepts degenerate ranges like {2,1}, but an
        // unterminated '{' is a hard syntax error (RegExpSyntaxError).
        assertRejectedNamingQ("/comprov{2/");
    }

    @Test
    public void oversizedPatternIsRejectedWithoutCompiling() {
        StringBuilder query = new StringBuilder("/");
        for (int i = 0; i < Search.MAX_REGEX_LENGTH + 1; i++) {
            query.append('a');
        }
        query.append('/');
        try {
            q(query.toString());
            fail("expected IllegalArgumentException for oversized pattern");
        } catch (IllegalArgumentException e) {
            assertTrue("message must name the parameter: " + e.getMessage(),
                    e.getMessage().contains("parameter 'q'"));
            assertTrue("message must state the limit: " + e.getMessage(),
                    e.getMessage().contains(String.valueOf(Search.MAX_REGEX_LENGTH)));
        }
    }

    @Test
    public void malformedRegexWithFiltersIsAlsoRejected() {
        try {
            q2("/comprov(/");
            fail("expected IllegalArgumentException for malformed regex with filters");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("parameter 'q'"));
        }
    }
}
