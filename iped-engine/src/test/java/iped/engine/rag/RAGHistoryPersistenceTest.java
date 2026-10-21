package iped.engine.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.engine.config.RAGConfig;
import iped.engine.rag.RAGService.HistoryEntryRecord;
import iped.engine.rag.RAGService.RAGSourceDoc;

public class RAGHistoryPersistenceTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private RAGService service(File path) {
        return new RAGService(path, new RAGConfig() {
            @Override
            public boolean isEnabled() {
                return false; // Persistence tests must not contact embedding/vector services.
            }
        });
    }

    private void save(RAGService service, String session) {
        RAGSourceDoc first = new RAGSourceDoc();
        first.itemId = "7";
        first.contenttrackID = "case-A#0";
        first.content = "Synthetic source A";
        RAGSourceDoc second = new RAGSourceDoc();
        second.itemId = "7";
        second.contenttrackID = "case-B#1";
        second.content = "Synthetic source B";
        assertTrue(service.saveHistoryEntry(session, "Synthetic question", "Synthetic answer [7]",
                Arrays.asList(first, second)) > 0);
        service.updateSessionTitle(session, "Synthetic title");
    }

    private void assertHistory(RAGService service, String session) {
        List<HistoryEntryRecord> history = service.loadHistoryEntries();
        assertEquals(1, history.size());
        assertEquals(session, history.get(0).sessionId);
        assertEquals("Synthetic answer [7]", history.get(0).answerHtml);
        assertEquals("case-A#0", history.get(0).displaySources.get(0).contenttrackID);
        assertEquals("case-B#1", history.get(0).displaySources.get(1).contenttrackID);
        assertEquals("Synthetic title", service.loadSessionTitles().get(session));
    }

    @Test
    public void testMulticaseListHistorySurvivesReopening() throws Exception {
        File list = temporary.newFile("multi.txt");
        String original = "synthetic-case-A\nsynthetic-case-B\n";
        Files.writeString(list.toPath(), original, StandardCharsets.UTF_8);
        RAGService first = service(list);
        try {
            save(first, "multicase-session");
        } finally {
            first.close();
        }
        RAGService reopened = service(new File(list.getParentFile(), "./multi.txt"));
        try {
            assertHistory(reopened, "multicase-session");
        } finally {
            reopened.close();
        }
        assertEquals(original, Files.readString(list.toPath(), StandardCharsets.UTF_8));
        assertTrue(new File(list.getParentFile(), "iped-multicases/multi.txt/data/embedding_cache.db").isFile());
        assertFalse(new File(list, "data").exists());
    }

    @Test
    public void testDifferentListsInSameFolderHaveSeparateHistory() throws Exception {
        File a = temporary.newFile("a.txt");
        File b = temporary.newFile("b.txt");
        RAGService first = service(a);
        RAGService second = service(b);
        try {
            save(first, "first");
            assertTrue(second.loadHistoryEntries().isEmpty());
            save(second, "second");
            assertHistory(first, "first");
            assertHistory(second, "second");
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    public void testCaseDirectoryStillUsesItsInternalDatabase() throws Exception {
        File caseDir = temporary.newFolder("case");
        assertTrue(new File(caseDir, "iped").mkdir());
        RAGService first = service(caseDir);
        try {
            save(first, "individual");
        } finally {
            first.close();
        }
        RAGService reopened = service(caseDir);
        try {
            assertHistory(reopened, "individual");
        } finally {
            reopened.close();
        }
        assertTrue(new File(caseDir, "iped/data/embedding_cache.db").isFile());
    }

    @Test
    public void testLegacyDatabaseLocationsArePreserved() throws Exception {
        int i = 0;
        for (String relative : Arrays.asList("storage/embedding_cache.db", "iped/storage/embedding_cache.db",
                "iped/data/embedding_cache.db")) {
            File caseDir = temporary.newFolder("legacy-" + i++);
            File db = new File(caseDir, relative);
            assertTrue(db.getParentFile().mkdirs());
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath())) {
                assertFalse(connection.isClosed());
            }
            RAGService first = service(caseDir);
            try {
                save(first, "legacy");
            } finally {
                first.close();
            }
            RAGService reopened = service(caseDir);
            try {
                assertHistory(reopened, "legacy");
            } finally {
                reopened.close();
            }
        }
    }

    @Test
    public void testMulticaseFolderHistorySurvivesReopening() throws Exception {
        File folder = temporary.newFolder("multicase-folder");
        RAGService first = service(folder);
        try {
            save(first, "folder-session");
        } finally {
            first.close();
        }
        RAGService reopened = service(folder);
        try {
            assertHistory(reopened, "folder-session");
        } finally {
            reopened.close();
        }
        assertTrue(new File(folder, "data/embedding_cache.db").isFile());
    }
}
