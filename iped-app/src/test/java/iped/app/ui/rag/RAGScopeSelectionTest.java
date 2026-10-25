package iped.app.ui.rag;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.junit.Test;

import iped.data.IBookmarks;
import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.engine.data.ItemId;
import iped.engine.rag.RAGService.RAGSourceDoc;
import iped.engine.search.MultiSearchResult;

public class RAGScopeSelectionTest {

    @Test
    public void testCheckedItemsTakePriorityOverHighlightedAndFilteredItems() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = table();
            table.setRowSelectionInterval(0, 0);
            Set<IItemId> items = RAGAssistantPanel.collectScopeItems(Arrays.asList(source(0, true), source(1, false)),
                    table, results(new ItemId(1, 7), new ItemId(1, 8)));
            assertEquals(Collections.singleton(new ItemId(0, 7)), items);
        });
    }

    @Test
    public void testHighlightedRowsTakePriorityAndRespectTableSorting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = table();
            table.setAutoCreateRowSorter(true);
            table.getRowSorter().toggleSortOrder(0);
            table.setRowSelectionInterval(0, 0);
            Set<IItemId> items = RAGAssistantPanel.collectScopeItems(Collections.singletonList(source(0, false)),
                    table, results(new ItemId(0, 7), new ItemId(0, 8)));
            assertEquals(Collections.singleton(new ItemId(0, 8)), items);
        });
    }

    @Test
    public void testFilteredResultsKeepSameNumericIdsFromDifferentCases() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<IIPEDSource> sources = Arrays.asList(source(0, false), source(1, false));
            Set<IItemId> items = RAGAssistantPanel.collectScopeItems(sources, table(),
                    results(new ItemId(0, 7), new ItemId(1, 7)));
            assertEquals(2, items.size());
            assertTrue(items.contains(new ItemId(0, 7)));
            assertTrue(items.contains(new ItemId(1, 7)));
        });
    }

    @Test
    public void testNoSelectionProducesAnEmptyScope() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(RAGAssistantPanel.collectScopeItems(Collections.singletonList(source(0, false)),
                    table(), results()).isEmpty());
            assertTrue(RAGAssistantPanel.collectScopeItems(Collections.emptyList(), null, null).isEmpty());
        });
    }

    @Test
    public void testSourceKeysDoNotMergeDifferentCasesWithSameNumericId() {
        RAGSourceDoc first = new RAGSourceDoc();
        first.itemId = "7";
        first.contenttrackID = "case-a#1";
        RAGSourceDoc second = new RAGSourceDoc();
        second.itemId = "7";
        second.contenttrackID = "case-b#1";
        assertNotEquals(RAGAssistantPanel.getSourceItemKey(first), RAGAssistantPanel.getSourceItemKey(second));
        second.contenttrackID = "case-a#2";
        assertEquals(RAGAssistantPanel.getSourceItemKey(first), RAGAssistantPanel.getSourceItemKey(second));
    }

    private JTable table() {
        return new JTable(new DefaultTableModel(new Object[][] { { "B" }, { "A" } }, new Object[] { "Name" }));
    }

    private MultiSearchResult results(IItemId... items) {
        return new MultiSearchResult(items, new float[items.length]);
    }

    private IIPEDSource source(int sourceId, boolean checked) {
        IBookmarks bookmarks = (IBookmarks) Proxy.newProxyInstance(IBookmarks.class.getClassLoader(),
                new Class<?>[] { IBookmarks.class }, (proxy, method, args) -> {
                    if ("isChecked".equals(method.getName())) {
                        return checked && ((Integer) args[0]) == 7;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (IIPEDSource) Proxy.newProxyInstance(IIPEDSource.class.getClassLoader(),
                new Class<?>[] { IIPEDSource.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSourceId": return sourceId;
                        case "getLastId": return 8;
                        case "getBookmarks": return bookmarks;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
