package iped.engine.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import iped.engine.rag.LLMProvider.PromptMessage;
import iped.engine.rag.RAGService.HistoryTurn;
import iped.localization.LocaleResolver;

public class LLMProviderPromptTest {

    private static final LLMProvider PROVIDER = (question, context, history) -> null;

    private Locale previous;

    @Before
    public void setUp() {
        previous = LocaleResolver.getLocale();
    }

    @After
    public void tearDown() {
        LocaleResolver.setLocale(previous);
    }

    @Test
    public void testPortuguesePromptLabelsAndHistory() {
        LocaleResolver.setLocale(new Locale("pt", "BR"));
        List<PromptMessage> prompt = PROVIDER.buildPrompt("e sobre ele?", "[7] Documento: a.pdf\n---\ntexto\n\n",
                Arrays.asList(new HistoryTurn("quem assinou?", "Fulano [7], [31] e Beltrano [12][31].")));
        assertEquals(4, prompt.size());
        assertEquals("Pergunta: quem assinou?", prompt.get(1).content);
        assertEquals("Fulano e Beltrano.", prompt.get(2).content);
        assertEquals("Contexto (trechos de documentos do caso; podem ser parciais ou irrelevantes):\n\n"
                + "[7] Documento: a.pdf\n---\ntexto\n\n\nPergunta: e sobre ele?", prompt.get(3).content);
    }

    @Test
    public void testEnglishPromptLabels() {
        LocaleResolver.setLocale(Locale.ENGLISH);
        List<PromptMessage> prompt = PROVIDER.buildPrompt("who?", "ctx\n", Arrays.asList(new HistoryTurn("q", "a")));
        assertEquals("Question: q", prompt.get(1).content);
        assertEquals("Context (excerpts from case documents; may be partial or irrelevant):\n\nctx\n\nQuestion: who?",
                prompt.get(3).content);
    }

    @Test
    public void testSystemPromptAuthorizesPertinentPersonalDataWithoutBypassWording() {
        for (Locale locale : new Locale[] { new Locale("pt", "BR"), Locale.ENGLISH }) {
            LocaleResolver.setLocale(locale);
            String system = PROVIDER.getSystemPrompt("q");
            assertFalse(system, system.contains("Ignore"));
            assertTrue(system, system.contains(locale.getLanguage().equals("pt") ? "ambiente forense autorizado"
                    : "authorized forensic environment"));
        }
    }

    @Test
    public void testStripCitationsKeepsOtherBrackets() {
        assertEquals("Nome e cargo [Art. 2º].", LLMProvider.stripCitations("Nome [7] e cargo [Art. 2º] [31, 7]."));
        assertEquals("Fonte:.", LLMProvider.stripCitations("Fonte: [31] e [7]."));
        assertEquals("Lista: Ana, Bia.", LLMProvider.stripCitations("Lista: Ana [7], Bia [7]."));
        assertNull(LLMProvider.stripCitations(null));
    }
}
