package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import iped.localization.LocaleResolver;
import iped.engine.config.RAGConfig;
import iped.engine.rag.RAGService.HistoryTurn;

public class RAGPromptBudgetTest {

    private Locale previous;

    @Before
    public void setUp() {
        previous = LocaleResolver.getLocale();
        LocaleResolver.setLocale(new Locale("pt", "BR"));
    }

    @After
    public void tearDown() {
        LocaleResolver.setLocale(previous);
    }

    @Test
    public void testHistoryBudgetFollowsLocalContextWindow() {
        int small = RAGAssistantPanel.historyCharBudget(RAGAssistantPanel.promptCharBudget(config("local", 4096)));
        int large = RAGAssistantPanel.historyCharBudget(RAGAssistantPanel.promptCharBudget(config("local", 16384)));
        assertEquals((int) ((4096 - 2048) * RAGAssistantPanel.CHARS_PER_TOKEN * 0.20), small);
        assertTrue(large > 4 * small);
    }

    @Test
    public void testRemoteProviderKeepsFixedHistoryBudget() {
        int prompt = RAGAssistantPanel.promptCharBudget(config("remote", 4096));
        assertEquals(Integer.MAX_VALUE, prompt);
        assertEquals(RAGAssistantPanel.REMOTE_HISTORY_CHARS, RAGAssistantPanel.historyCharBudget(prompt));
    }

    @Test
    public void testOlderTurnsThatDoNotFitAreDroppedWhole() {
        List<HistoryTurn> turns = Arrays.asList(turn("q1", 300), turn("q2", 300), turn("q3", 300));
        List<HistoryTurn> kept = RAGAssistantPanel.boundHistory(turns, 5, 2 * cost(turns.get(2)));
        assertEquals(2, kept.size());
        assertEquals("q2", kept.get(0).question);
        assertEquals("q3", kept.get(1).question);
        assertEquals(turns.get(1).answer, kept.get(0).answer);
    }

    @Test
    public void testTurnCountLimitKeepsMostRecentTurns() {
        List<HistoryTurn> turns = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            turns.add(turn("q" + i, 10));
        }
        List<HistoryTurn> kept = RAGAssistantPanel.boundHistory(turns, 5, Integer.MAX_VALUE);
        assertEquals(5, kept.size());
        assertEquals("q3", kept.get(0).question);
        assertEquals("q7", kept.get(4).question);
    }

    @Test
    public void testOversizedMostRecentTurnIsTruncatedToBudget() {
        List<HistoryTurn> turns = Arrays.asList(turn("q1", 10), turn("q2", 5000));
        List<HistoryTurn> kept = RAGAssistantPanel.boundHistory(turns, 5, 1000);
        assertEquals(1, kept.size());
        assertEquals("q2", kept.get(0).question);
        assertTrue(kept.get(0).answer.endsWith(RAGAssistantPanel.TRUNCATION_MARKER));
        assertEquals(1000, cost(kept.get(0)));
    }

    @Test
    public void testBudgetTooSmallForAnyTurnSendsNoHistory() {
        List<HistoryTurn> kept = RAGAssistantPanel.boundHistory(Arrays.asList(turn("q1", 100)), 5,
                RAGAssistantPanel.HISTORY_TURN_TEMPLATE_CHARS);
        assertTrue(kept.isEmpty());
    }

    @Test
    public void testTruncationDoesNotSplitSurrogatePairs() {
        String answer = "a".repeat(10) + "😀" + "b".repeat(100);
        HistoryTurn turn = new HistoryTurn("", answer);
        int maxChars = RAGAssistantPanel.HISTORY_TURN_TEMPLATE_CHARS + 11 + RAGAssistantPanel.TRUNCATION_MARKER.length();
        String kept = RAGAssistantPanel.boundHistory(Arrays.asList(turn), 5, maxChars).get(0).answer;
        assertEquals("a".repeat(10) + RAGAssistantPanel.TRUNCATION_MARKER, kept);
    }

    @Test
    public void testBaseCharsUseRealPromptAndProportionalMargin() {
        String system = "s".repeat(400);
        List<HistoryTurn> history = Arrays.asList(turn("q1", 100));
        int local = RAGAssistantPanel.promptBaseChars(system, "question", history, 10000);
        int remote = RAGAssistantPanel.promptBaseChars(system, "question", history, Integer.MAX_VALUE);
        int expectedRemote = RAGAssistantPanel.PROMPT_TEMPLATE_CHARS + 400 + "question".length() + cost(history.get(0));
        assertEquals(expectedRemote, remote);
        assertEquals(expectedRemote + 1000, local);
        assertEquals(RAGAssistantPanel.PROMPT_TEMPLATE_CHARS,
                RAGAssistantPanel.promptBaseChars(null, null, Collections.emptyList(), Integer.MAX_VALUE));
    }

    @Test
    public void testFollowUpQueryKeepsAccentedTermsAndSkipsAccentedStopWords() {
        List<HistoryTurn> history = Arrays.asList(new HistoryTurn("Não há transferências para João?", "x"));
        assertEquals("e depois há transferências João",
                RAGAssistantPanel.extractSearchQueryWithHistory("e depois há", history));
    }

    @Test
    public void testFragmentHeaderHasIdAndNameButNoScore() {
        assertEquals("[7] Documento: Nota <b>6</b>.pdf\n---\n", RAGAssistantPanel.fragmentHeader("7", "Nota <b>6</b>.pdf"));
        assertEquals("[7]\n---\n", RAGAssistantPanel.fragmentHeader("7", null));
    }

    private static HistoryTurn turn(String question, int answerChars) {
        return new HistoryTurn(question, "x".repeat(answerChars));
    }

    private static int cost(HistoryTurn turn) {
        return RAGAssistantPanel.HISTORY_TURN_TEMPLATE_CHARS + turn.question.length() + turn.answer.length();
    }

    private static RAGConfig config(String provider, int window) {
        return new RAGConfig() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public String getLlmProvider() {
                return provider;
            }

            @Override
            public int getLlmContextWindow() {
                return window;
            }
        };
    }
}
