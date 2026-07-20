package iped.engine.task.aleapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AleappUtilsTest {

    @Test
    public void testPatternWithoutPathSeparators() {
        assertEquals("name:*com && name:whatsapp*", AleappUtils.globToLuceneQuery("*com.whatsapp*"));
    }

    @Test
    public void testExactFilenameMatch() {
        assertEquals("path:\"com.whatsapp/shared_prefs\" && name:\"com.whatsapp_preferences_light.xml\"",
                AleappUtils.globToLuceneQuery("*/com.whatsapp/shared_prefs/com.whatsapp_preferences_light.xml"));
    }

    @Test
    public void testFilenameWithPrefixAndTrailingWildcard() {
        assertEquals("path:\"com.whatsapp/databases\" && name:db*", AleappUtils.globToLuceneQuery("*/com.whatsapp/databases/*.db*"));
    }

    @Test
    public void testFilenameWithPrefixAndDotWildcard() {
        assertEquals("path:\"WhatsApp Videos\" && name:video", AleappUtils.globToLuceneQuery("*/WhatsApp Videos/video.*"));
    }

    @Test
    public void testFilenameWithExtensionMatch() {
        assertEquals("path:\"WhatsApp Audio\" && name:ogg", AleappUtils.globToLuceneQuery("*/WhatsApp Audio/*.ogg"));
    }

    @Test
    public void testMatchAllFilesWithAnyExtension() {
        assertEquals("path:\"WhatsApp Images\"", AleappUtils.globToLuceneQuery("*/WhatsApp Images/*.*"));
    }

    @Test
    public void testMatchAllFiles() {
        assertEquals("path:\"WhatsApp Images\"", AleappUtils.globToLuceneQuery("*/WhatsApp Images/*"));
    }

    @Test
    public void testFilenameWithInfixMatch() {
        assertEquals("path:\"WhatsApp Images\" && name:*file*", AleappUtils.globToLuceneQuery("*/WhatsApp Images/*file*"));
    }

    @Test
    public void testFilenameWithInternalWildcard() {
        assertEquals("path:\"com.runtastic.android/databases\" && name:user && name:db*",
                AleappUtils.globToLuceneQuery("*com.runtastic.android/databases/user.db*"));
    }

    @Test
    public void testRootPathWithExtensionMatch() {
        assertEquals("name:log", AleappUtils.globToLuceneQuery("/*.log"));
    }

    @Test
    public void testPathWithIntermediateWildcardDirectories() {
        assertEquals("path:\"system/usagestats\" && name:\"version\"", AleappUtils.globToLuceneQuery("*/system/usagestats/*/version"));
    }

    @Test
    public void testPathWithPrefixFilenameMatch() {
        assertEquals("path:\"com.android.providers.contacts/databases\" && name:contact*",
                AleappUtils.globToLuceneQuery("*/com.android.providers.contacts/databases/contact*"));
    }

    @Test
    public void testPatternWithTrailingDotWildcard() {
        assertEquals("name:contact", AleappUtils.globToLuceneQuery("contact.*"));
    }

    @Test
    public void testComplexPunctuationInFilename() {
        // Hyphens, underscores, and dots all split the string to avoid Lucene operator conflicts (like '-' acting as NOT).
        // This maximizes recall, allowing the downstream Regex to do the exact validation.
        assertEquals("path:\"logs\" && name:test && name:profile && name:info* && name:db*",
                AleappUtils.globToLuceneQuery("*/logs/test-profile_info*.db*"));
    }

    @Test
    public void testConsecutiveWildcards() {
        // Should compress '**' in paths natively through StringUtils.split behavior,
        // and compress '**' in names to '*'
        assertEquals("path:\"WhatsApp\" && name:db", AleappUtils.globToLuceneQuery("**/WhatsApp/**/*.db"));
    }

    @Test
    public void testEmptyFilenameJustDirectory() {
        // Trailing slash means there's no filename, just a path
        assertEquals("path:\"WhatsApp\"", AleappUtils.globToLuceneQuery("*/WhatsApp/"));
    }

    @Test
    public void testNullInput() {
        assertEquals("", AleappUtils.globToLuceneQuery(null));
    }

    @Test
    public void testEmptyInput() {
        assertEquals("", AleappUtils.globToLuceneQuery(""));
        assertEquals("", AleappUtils.globToLuceneQuery("   "));
    }
}
