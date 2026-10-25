package iped.engine.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

/** Browser history is embedded as the reports IPED builds, not as the databases they come from. */
public class RAGConfigBrowserHistoryTest {

    private static boolean embeds(String category, String mimeType) {
        return new RAGConfig().shouldEmbed(Collections.singleton(category), mimeType);
    }

    @Test
    public void testHistoryReportsAreEmbedded() {
        assertTrue(embeds("Internet History", "application/x-chrome-history"));
        assertTrue(embeds("Internet History", "application/x-chrome-downloads"));
        assertTrue(embeds("Internet History", "application/x-chrome-searches"));
        assertTrue(embeds("Internet History", "application/x-edge-history"));
        assertTrue(embeds("Internet History", "application/x-firefox-history"));
        assertTrue(embeds("Internet History", "application/x-msie-cache"));
    }

    @Test
    public void testBrowserDatabasesAreNotEmbedded() {
        assertFalse(embeds("Internet History", "application/x-chrome-sqlite"));
        assertFalse(embeds("Internet History", "application/x-firefox-places"));
        assertFalse(embeds("Internet History", "application/x-safari-sqlite"));
        assertFalse(embeds("Internet History", "application/x-safari-plist"));
        assertFalse(embeds("Internet History", "application/x-edge-web-cache"));
        assertFalse(embeds("Internet History", "application/x-sqlite3"));
    }
}
