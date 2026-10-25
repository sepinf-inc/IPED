package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.Test;

import iped.engine.rag.LLMProvider.PromptMessage;

public class RAGContextPanelTest {

    private static RAGContextSnapshot searchSnapshot() {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.include("7", "case-A#0", "synthetic", "old\n\tneedle", 1, 0, 0);
        builder.include("7", "case-B#0", "synthetic", "new needle", 1, 1, 100);
        return builder.build();
    }

    @Test
    public void testLookupNavigatesHighlightsAndClearsOnEdit() throws Exception {
        RAGContextPanel[] holder = new RAGContextPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RAGContextPanel(searchSnapshot());
            ((JTextField) field(holder[0], "queryField")).setText("needle");
            holder[0].startSearch();
        });
        RAGContextPanel panel = holder[0];
        try {
            finished(panel);
            SwingUtilities.invokeAndWait(() -> {
                JTable table = (JTable) field(panel, "fragments");
                JTextArea content = (JTextArea) field(panel, "content");
                assertEquals(0, table.getSelectedRow());
                assertEquals(1, content.getHighlighter().getHighlights().length);
                assertEquals(5, content.getHighlighter().getHighlights()[0].getStartOffset());
                ((JButton) field(panel, "nextButton")).doClick();
                assertEquals(1, table.getSelectedRow());
                assertEquals("new needle", content.getText());
                ((JButton) field(panel, "previousButton")).doClick();
                assertEquals(0, table.getSelectedRow());
                ((JTextField) field(panel, "queryField")).setText("changed expression");
                assertNull(field(panel, "searchResult"));
                assertEquals(0, content.getHighlighter().getHighlights().length);
                assertFalse(((JButton) field(panel, "nextButton")).isEnabled());
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    public void testNewQueryOwnsViewDespiteLateOldLookup() throws Exception {
        BlockingPanel[] holder = new BlockingPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new BlockingPanel();
            ((JTextField) field(holder[0], "queryField")).setText("old");
            holder[0].startSearch();
        });
        BlockingPanel panel = holder[0];
        try {
            assertTrue(panel.started.await(5, TimeUnit.SECONDS));
            assertFalse(panel.ranOnEdt);
            SwingUtilities.invokeAndWait(() -> {
                ((JTextField) field(panel, "queryField")).setText("new");
                panel.startSearch();
            });
            finished(panel);
            panel.release.countDown();
            assertTrue(panel.returned.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                RAGPassageSearch.Result result = (RAGPassageSearch.Result) field(panel, "searchResult");
                assertNotNull(result);
                assertEquals(1, result.hits.get(0).fragmentOrdinal);
                assertEquals(1, ((JTable) field(panel, "fragments")).getSelectedRow());
            });
        } finally {
            panel.release.countDown();
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    public void testClosingInspectionDropsLateLookup() throws Exception {
        BlockingPanel[] holder = new BlockingPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new BlockingPanel();
            ((JTextField) field(holder[0], "queryField")).setText("old");
            holder[0].startSearch();
        });
        BlockingPanel panel = holder[0];
        try {
            assertTrue(panel.started.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(panel::close);
            panel.release.countDown();
            assertTrue(panel.returned.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                panel.startSearch();
                assertNull(field(panel, "searchWorker"));
                assertNull(field(panel, "searchResult"));
                assertEquals(0, ((JTextArea) field(panel, "content")).getHighlighter().getHighlights().length);
            });
        } finally {
            panel.release.countDown();
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    public void testNoMatchAndLimitAreDifferentUiOutcomes() throws Exception {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.include("7", "case-A#0", "synthetic", "x".repeat(80), 1, 0, 0);
        RAGContextPanel[] holder = new RAGContextPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RAGContextPanel(builder.build());
            ((JTextField) field(holder[0], "queryField")).setText("absent");
            holder[0].startSearch();
        });
        RAGContextPanel panel = holder[0];
        try {
            finished(panel);
            String[] noMatchStatus = new String[1];
            SwingUtilities.invokeAndWait(() -> {
                noMatchStatus[0] = ((JTextArea) field(panel, "searchStatus")).getText();
                assertEquals(RAGPassageSearch.State.COMPLETE,
                        ((RAGPassageSearch.Result) field(panel, "searchResult")).state);
                ((JTextField) field(panel, "queryField")).setText("x");
                panel.startSearch();
            });
            finished(panel);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(RAGPassageSearch.State.MATCH_LIMIT,
                        ((RAGPassageSearch.Result) field(panel, "searchResult")).state);
                assertFalse(noMatchStatus[0].equals(((JTextArea) field(panel, "searchStatus")).getText()));
                assertTrue(((JButton) field(panel, "nextButton")).isEnabled());
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    private static Object field(Object panel, String name) {
        try {
            Field field = RAGContextPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(panel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void finished(RAGContextPanel panel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean[] done = { false };
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> done[0] = field(panel, "searchWorker") == null);
            if (done[0]) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Lookup did not complete on the EDT");
    }

    private static class BlockingPanel extends RAGContextPanel {
        private static final long serialVersionUID = 1L;
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch returned = new CountDownLatch(1);
        volatile boolean ranOnEdt;

        BlockingPanel() {
            super(searchSnapshot());
        }

        @Override
        RAGPassageSearch.Result findPassages(String query, BooleanSupplier cancelled) {
            ranOnEdt = SwingUtilities.isEventDispatchThread();
            if ("old".equals(query)) {
                started.countDown();
                try {
                    while (true) {
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("Test release timeout");
                            }
                            break;
                        } catch (InterruptedException e) {
                            // Deliberately return late to test the UI ownership guard.
                        }
                    }
                    return super.findPassages(query, () -> false);
                } finally {
                    returned.countDown();
                }
            }
            return super.findPassages(query, cancelled);
        }
    }

    @Test
    public void testInspectionPreservesFullBodiesAndSeparateChunkIdentities() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
            String body = "<html><b>Literal evidence</b>\nUnicode: \u00e7 \ud83d\ude00\n";
            builder.include("7", "case-A#0", "<html>name", body, 1, 0, 0);
            builder.include("7", "case-A#1", "overlap", body, 1, 1, 100);
            builder.include("7", "case-B#0", "another case", "Other text", 1, 2, 200);
            builder.omit(3, "8", "case-C#0", false);
            RAGContextPanel panel = new RAGContextPanel(builder.build());
            JSplitPane split = (JSplitPane) ((JTabbedPane) panel.getComponent(1)).getComponentAt(0);
            JTable table = (JTable) ((JScrollPane) split.getTopComponent()).getViewport().getView();
            JTextArea content = (JTextArea) ((JScrollPane) split.getBottomComponent()).getViewport().getView();
            assertEquals(3, table.getRowCount());
            assertEquals(body, content.getText());
            assertFalse(content.isEditable());
            assertFalse(table.isCellEditable(0, 0));
            assertEquals("case-A#1", table.getValueAt(1, 3));
            assertEquals("case-B#0", table.getValueAt(2, 3));
            javax.swing.JComponent rendered = (javax.swing.JComponent) table.prepareRenderer(
                    table.getCellRenderer(0, 2), 0, 2);
            assertNull(rendered.getClientProperty("html"));
            table.setRowSelectionInterval(2, 2);
            assertEquals("Other text", content.getText());
            table.setRowSelectionInterval(1, 1);
            assertEquals(body, content.getText());
            assertEquals(0, content.getCaretPosition());
        });
    }

    @Test
    public void testEmptyAdmittedContextRemainsInspectable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
            builder.omit(0, "7", "case-A#0", true);
            RAGContextPanel panel = new RAGContextPanel(builder.build());
            JSplitPane split = (JSplitPane) ((JTabbedPane) panel.getComponent(1)).getComponentAt(0);
            JTable table = (JTable) ((JScrollPane) split.getTopComponent()).getViewport().getView();
            JTextArea content = (JTextArea) ((JScrollPane) split.getBottomComponent()).getViewport().getView();
            assertEquals(0, table.getRowCount());
            assertEquals("", content.getText());
        });
    }

    @Test
    public void testRenderPromptKeepsOrderRolesAndLiteralContent() {
        List<PromptMessage> prompt = Arrays.asList(new PromptMessage("system", "Synthetic instruction."),
                new PromptMessage("user", "Context:\n<b>[7]</b>\n\nQuestion: q\n\nAnswer:"));
        assertEquals("===== 1. system =====\nSynthetic instruction.\n\n"
                + "===== 2. user =====\nContext:\n<b>[7]</b>\n\nQuestion: q\n\nAnswer:",
                RAGContextPanel.renderPrompt(prompt));
    }

    @Test
    public void testPromptTabShowsRetainedPrompt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
            builder.include("7", "case-A#0", "synthetic", "body", 1, 0, 0);
            List<PromptMessage> prompt = Arrays.asList(new PromptMessage("system", "Synthetic instruction."),
                    new PromptMessage("user", "Context:\nbody"));
            builder.prompt(prompt);
            RAGContextPanel panel = new RAGContextPanel(builder.build());
            JTabbedPane tabs = (JTabbedPane) panel.getComponent(1);
            assertEquals(2, tabs.getTabCount());
            JTextArea text = promptText(tabs);
            assertEquals(RAGContextPanel.renderPrompt(prompt), text.getText());
            assertFalse(text.isEditable());
            assertEquals(0, text.getCaretPosition());
        });
    }

    @Test
    public void testPromptTabWithoutRetainedPromptIsEmpty() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RAGContextPanel panel = new RAGContextPanel(searchSnapshot());
            JTabbedPane tabs = (JTabbedPane) panel.getComponent(1);
            assertEquals("", promptText(tabs).getText());
        });
    }

    private static JTextArea promptText(JTabbedPane tabs) {
        JPanel promptTab = (JPanel) tabs.getComponentAt(1);
        return (JTextArea) ((JScrollPane) promptTab.getComponent(1)).getViewport().getView();
    }
}
