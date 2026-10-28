package iped.engine.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import iped.utils.UTF8Properties;

public class RAGConfigHtmlPathTest {

    @Test
    public void testHtmlOfBrowserCachesAndSystemFoldersIsExcluded() {
        RAGConfig cfg = new RAGConfig();
        assertTrue(cfg.isHtmlInExcludedPath("text/html",
                "img.E01/vol_vol2/Users/joe/AppData/Local/Google/Chrome/User Data/Default/Cache/f_0001"));
        assertTrue(cfg.isHtmlInExcludedPath("text/html",
                "img.E01/vol_vol2/Program Files/App/resources.pak/page.html"));
        assertTrue(cfg.isHtmlInExcludedPath("application/xhtml+xml", "img.E01/vol_vol2/Windows/Help/index.htm"));
    }

    @Test
    public void testUserHtmlAndOtherTypesAreKept() {
        RAGConfig cfg = new RAGConfig();
        assertFalse(cfg.isHtmlInExcludedPath("text/html", "img.E01/vol_vol2/Users/joe/Documents/report.html"));
        // the HTML rule does not apply to the other files of those folders (their databases have own rules)
        assertFalse(cfg.isHtmlInExcludedPath("application/x-sqlite3",
                "img.E01/vol_vol2/Users/joe/AppData/Local/Google/Chrome/User Data/Default/History"));
        assertFalse(cfg.isHtmlInExcludedPath("text/html", null));
    }

    @Test
    public void testPatternFromConfigFile() {
        RAGConfig cfg = new RAGConfig();
        UTF8Properties props = new UTF8Properties();
        props.setProperty("embeddingHtmlPathBlacklist", "/cache/");
        cfg.processProperties(props);
        assertTrue(cfg.isHtmlInExcludedPath("text/html", "dump/CACHE/page.html"));
        assertFalse(cfg.isHtmlInExcludedPath("text/html", "img.E01/vol_vol2/Program Files/App/page.html"));

        props.setProperty("embeddingHtmlPathBlacklist", "");
        cfg.processProperties(props);
        assertFalse(cfg.isHtmlInExcludedPath("text/html", "dump/cache/page.html"));
    }
}
