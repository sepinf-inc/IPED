package iped.engine.webapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * PHASE R5-4a unit tests for the design 07c snippet helpers (F-3): the pure
 * slicing function {@link Search#snippetFromText(String, String, int)}, the
 * raw-q term extractor {@link Search#snippetTerms(String)} and the snippet
 * parameter validation {@link Search#resolveSnippet(String)}. No server or
 * index is required (same offline pattern as SearchQueryBuildTest and
 * SearchPaginationTest).
 */
public class SearchSnippetTest {

    private static final String ELLIPSIS = "\u2026";

    private static String filler(int words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append("w").append(i);
        }
        return sb.toString();
    }

    // ---- termo no inicio: sem elipse de prefixo (design edge 10) ------

    @Test
    public void termAtStartHasNoLeadingEllipsis() {
        String s = Search.snippetFromText("trafico de drogas pela fronteira", "trafico", 100);
        assertEquals("trafico \u2026", s);
    }

    // ---- termo no meio: janela termina no match, aparando palavras ----

    @Test
    public void termInMiddleWindowEndsAtMatch() {
        String text = "alpha bravo charlie delta echo foxtrot golf hotel";
        String s = Search.snippetFromText(text, "golf", 12);
        assertEquals("\u2026 foxtrot golf \u2026", s);
        assertTrue(s.contains("golf"));
        assertTrue(s.length() <= 12 + 4);
    }

    // ---- termo no fim: sem elipse de sufixo ----------------------------

    @Test
    public void termAtEndHasNoTrailingEllipsis() {
        String s = Search.snippetFromText("alpha bravo charlie delta", "delta", 10);
        assertEquals("\u2026 delta", s);
    }

    // ---- termo ausente: trecho do inicio (fragmento de cabeca) ---------

    @Test
    public void absentTermFallsBackToHeadFragment() {
        String s = Search.snippetFromText("alpha bravo charlie delta echo", "zulu", 11);
        assertEquals("alpha bravo \u2026", s);
        assertFalse(s.startsWith(ELLIPSIS));
    }

    // ---- N maior que o texto: texto integral, sem elipses --------------

    @Test
    public void maxLongerThanTextReturnsWholeText() {
        String s = Search.snippetFromText("alpha bravo", "bravo", 100);
        assertEquals("alpha bravo", s);
    }

    // ---- N = 500 (teto do contrato): limite N+4 e termo presente -------

    @Test
    public void maxOf500HonoursLengthAndContainsTerm() {
        String text = filler(2000);
        int max = Search.MAX_SNIPPET_CHARS;
        String s = Search.snippetFromText(text, "w1500", max);
        assertTrue(s.contains("w1500"));
        assertTrue(s.length() <= max + 4);
        assertTrue(s.startsWith(ELLIPSIS));
        assertTrue(s.endsWith(ELLIPSIS));
    }

    // ---- texto vazio/branco/null: omitido do mapa (null) ---------------

    @Test
    public void emptyOrBlankOrNullTextYieldsNull() {
        assertNull(Search.snippetFromText(null, "trafico", 100));
        assertNull(Search.snippetFromText("", "trafico", 100));
        assertNull(Search.snippetFromText("   \t\r\n ", "trafico", 100));
    }

    // ---- matching case-insensitive (design edge 15) --------------------

    @Test
    public void matchingIsCaseInsensitive() {
        String s = Search.snippetFromText("O TRAFICO veio depois", "trafico", 12);
        assertTrue(s.contains("TRAFICO"));
    }

    // ---- q sem termos uteis (curinga/operadores): cabeca ---------------

    @Test
    public void wildcardOnlyQueryYieldsHeadFragment() {
        String text = "lorem ipsum dolor sit amet";
        assertEquals("lorem \u2026", Search.snippetFromText(text, "title:*", 10));
        assertEquals("lorem \u2026", Search.snippetFromText(text, "*", 10));
    }

    // ---- normalizacao de whitespace (design step 7) --------------------

    @Test
    public void whitespaceRunsAreCollapsed() {
        String s = Search.snippetFromText("alpha\tbravo\r\n   charlie\tdelta", "zz", 200);
        assertEquals("alpha bravo charlie delta", s);
    }

    // ---- multiplos termos: menor indice vence (design step 5) ----------

    @Test
    public void multipleTermsUseTheEarliestOccurrence() {
        String s = Search.snippetFromText("kappa bravo charlie alpha", "alpha kappa", 10);
        assertEquals("kappa \u2026", s);
    }

    // ---- extracao de termos do q bruto (design step 4) -----------------

    @Test
    public void termsDropOperatorsFieldPrefixWildcardsAndShortLiterals() {
        assertEquals(Arrays.asList("trafico", "pix"), Search.snippetTerms("trafico AND pix"));
        assertEquals(Arrays.asList("whatsapp"), Search.snippetTerms("category:whatsapp"));
        assertEquals(Arrays.asList("trafico"), Search.snippetTerms("title:\"trafico\""));
        assertTrue(Search.snippetTerms(null).isEmpty());
        assertTrue(Search.snippetTerms("").isEmpty());
        assertTrue(Search.snippetTerms("*").isEmpty());
        assertTrue(Search.snippetTerms("a AND b NOT").isEmpty());
    }

    // ---- validacao do parametro snippet --------------------------------

    @Test
    public void snippetParameterValidation() {
        assertEquals(0, Search.resolveSnippet(null));
        assertEquals(0, Search.resolveSnippet(""));
        assertEquals(0, Search.resolveSnippet("   "));
        assertEquals(0, Search.resolveSnippet("0"));
        assertEquals(100, Search.resolveSnippet("100"));
        assertEquals(500, Search.resolveSnippet(" 500 "));
        assertSnippetRejected("501");
        assertSnippetRejected("-1");
        assertSnippetRejected("abc");
    }

    private static void assertSnippetRejected(String value) {
        try {
            Search.resolveSnippet(value);
            fail("expected IllegalArgumentException for snippet=" + value);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("snippet"));
        }
    }

    // ---- invariante de contrato: len <= N + 4, nunca vazio -------------

    @Test
    public void lengthNeverExceedsMaxPlusEllipsis() {
        String[] qs = {"trafico", "w999", "*", "alpha w1500"};
        String text = filler(400) + " trafico fim";
        for (String q : qs) {
            for (int max : new int[]{1, 2, 5, 17, 100, 500}) {
                String s = Search.snippetFromText(text, q, max);
                assertTrue("max=" + max + " q=" + q + " len=" + s.length(),
                        s.length() <= max + 4);
                assertTrue("vazio max=" + max + " q=" + q, s.trim().length() > 0);
            }
        }
    }
}
