package iped.engine.rag;

import static org.junit.Assert.*;

import org.junit.Test;

public class RAGTextSanitizerTest {

    @Test
    public void preservesIdentifiersComparisonsAndLiteralMarkup() {
        String text = "From: <alice@example.test>\nMessage-ID: <message-123@example.test>\n"
                + "Range: a < b and c > d\nExample: <script>literal</script>";
        assertEquals(text, RAGTextSanitizer.sanitize(text));
    }

    @Test
    public void normalizesLayoutWithoutJoiningWordsOrFlatteningColumns() {
        String text = "first \t\r\nsecond\rthird\n \t\n\n\n"
                + "name\trole\n  code    value\namount\u00a010\u202f000  ";
        assertEquals("first\nsecond\nthird\n\nname\trole\n  code    value\namount 10 000",
                RAGTextSanitizer.sanitize(text));
    }

    @Test
    public void separatesControlCharactersAndPageBoundaries() {
        assertEquals("first second third\npage2\npage3",
                RAGTextSanitizer.sanitize("first\0second\u0007third\fpage2\u000bpage3"));
    }

    @Test
    public void preservesUnicodeAndExtractionReplacementCharacters() {
        String text = "Não 😀 café العربية हिंदी 👩‍💻 a\u200db\u200cc\u200bd\ufeffe �";
        assertEquals(text, RAGTextSanitizer.sanitize(text));
    }

    @Test
    public void removesOnlyCompleteDecorativeSeparatorLines() {
        assertEquals("before\n\nid ABC_____DEF and -----> next\n\nafter",
                RAGTextSanitizer.sanitize("before\n  _____ \n_----_\n"
                        + "id ABC_____DEF and -----> next\n-----\nafter"));
    }

    @Test
    public void preservesUnicodeDelimitersWhenRemovingDecorativeSegments() {
        assertEquals("before\u0085\u0085middle\u2028\u2028end\u2029\u2029tail",
                RAGTextSanitizer.sanitize("before\u0085_____\u0085middle"
                        + "\u2028-----\u2028end\u2029_----_\u2029tail"));
    }

    @Test
    public void retainsInvestigativeHeadersWhileRemovingTechnicalLines() {
        assertEquals("Subject: keep\nFrom: <alice@example.test>\nDate: 2026-01-02\n"
                        + "x-tika: keep existing case-sensitive behavior",
                RAGTextSanitizer.sanitize("Subject: keep\rX-TIKA:content: noise\r"
                        + "From: <alice@example.test>\nDate: 2026-01-02\n"
                        + "  pdf:encrypted: false\nx-tika: keep existing case-sensitive behavior"));
    }

    @Test
    public void removesRgbPaletteLinesWithoutRemovingProseAboutColors() {
        assertEquals("before\nred=2 blue=3 are example values\nafter",
                RAGTextSanitizer.sanitize("before\nindex=1 red=2 green=3 blue=4\n"
                        + "red=2 blue=3 are example values\nafter"));
    }

    @Test
    public void removesRecognizedScriptsBeforeFilteringJoinedMetadata() {
        String script = "document.write(decodeURIComponent(\"OpenX%20ad\"));";
        assertEquals("before\nafter\nFrom: <alice@example.test>",
                RAGTextSanitizer.sanitize("before\n" + script + script + "after\nX-TIKA:"
                        + script + "content: noise\nFrom: <alice@example.test>"));
    }

    @Test
    public void preservesUnrecognizedAndIncompleteScriptExpressions() {
        String expression = "document.write(decodeURIComponent(\"ordinary\"));";
        assertEquals(expression, RAGTextSanitizer.sanitize(expression));
        String incomplete = "document.write(decodeURIComponent(\"unclosed OpenX";
        assertEquals(incomplete, RAGTextSanitizer.sanitize(incomplete));
    }

    @Test
    public void isStableWhenCleaningMixedContentAgain() {
        String text = "From: <alice@example.test>\r\nX-TIKA:content: noise\r"
                + "id ABC_____DEF\n_____\n\nfirst\0second\fthird\u2028-----\u2028end";
        String cleaned = RAGTextSanitizer.sanitize(text);
        assertEquals(cleaned, RAGTextSanitizer.sanitize(cleaned));
    }

    @Test
    public void handlesLongSeparatorsAndInlineRunsWithoutLosingContent() {
        String inline = "prefix " + "_".repeat(20000) + " suffix";
        assertEquals("before\n\n" + inline + "\nafter",
                RAGTextSanitizer.sanitize("before\n" + "_-".repeat(10000)
                        + "\n" + inline + "\nafter"));
    }

    @Test
    public void handlesEmptyAndNoiseOnlyText() {
        assertEquals("", RAGTextSanitizer.sanitize(null));
        assertEquals("", RAGTextSanitizer.sanitize(" \t\r\n\0"));
        assertEquals("", RAGTextSanitizer.sanitize("X-TIKA:content: noise\n_____"));
    }

    @Test
    public void preservesExistingEligibilityAndExplicitHtmlHelperContracts() {
        assertFalse(RAGTextSanitizer.hasUsefulContent(null));
        assertFalse(RAGTextSanitizer.hasUsefulContent("abcdefghi"));
        assertTrue(RAGTextSanitizer.hasUsefulContent("abcdefghij"));
        assertFalse(RAGTextSanitizer.hasUsefulContent("a........."));
        assertEquals(" text ", RAGTextSanitizer.stripHtmlTags("<b>text</b>"));
    }
}
