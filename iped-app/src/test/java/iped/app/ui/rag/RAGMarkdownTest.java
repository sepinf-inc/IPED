package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RAGMarkdownTest {

    @Test
    public void testUnderscoresInsideNamesAreKept() {
        assertEquals("nota_fiscal_2024.pdf e __init__x", RAGAssistantPanel.markdownToHtml("nota_fiscal_2024.pdf e __init__x"));
    }

    @Test
    public void testUnderscoreEmphasisAtWordBoundaries() {
        assertEquals("<i>termo</i> e <b>forte</b>", RAGAssistantPanel.markdownToHtml("_termo_ e __forte__"));
    }

    @Test
    public void testHtmlIsEscaped() {
        assertEquals("&lt;script&gt; <b>[5]</b>", RAGAssistantPanel.markdownToHtml("<script> **[5]**"));
    }
}
