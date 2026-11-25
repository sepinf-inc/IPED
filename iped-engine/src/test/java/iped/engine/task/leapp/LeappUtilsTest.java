package iped.engine.task.leapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LeappUtilsTest {

    @Test
    public void testPatternWithoutPathSeparators() {
        // '*' also matches '/', so the text may be in a folder name: it must be searched in the path
        assertEquals("path:*com && path:whatsapp*", LeappUtils.globToLuceneQuery("*com.whatsapp*"));
    }

    @Test
    public void testExactFilenameMatch() {
        assertEquals("path:\"com.whatsapp/shared_prefs\" && name:\"com.whatsapp_preferences_light.xml\"",
                LeappUtils.globToLuceneQuery("*/com.whatsapp/shared_prefs/com.whatsapp_preferences_light.xml"));
    }

    @Test
    public void testFilenameWithPrefixAndTrailingWildcard() {
        // the trailing '*' may cross '/', so "db" is not bound to the item name
        assertEquals("path:\"com.whatsapp/databases\" && path:db*", LeappUtils.globToLuceneQuery("*/com.whatsapp/databases/*.db*"));
    }

    @Test
    public void testFilenameWithPrefixAndDotWildcard() {
        assertEquals("path:\"WhatsApp Videos/video\"", LeappUtils.globToLuceneQuery("*/WhatsApp Videos/video.*"));
    }

    @Test
    public void testFilenameWithExtensionMatch() {
        assertEquals("path:\"WhatsApp Audio\" && name:\"ogg\"", LeappUtils.globToLuceneQuery("*/WhatsApp Audio/*.ogg"));
    }

    @Test
    public void testMatchAllFilesWithAnyExtension() {
        assertEquals("path:\"WhatsApp Images\"", LeappUtils.globToLuceneQuery("*/WhatsApp Images/*.*"));
    }

    @Test
    public void testMatchAllFiles() {
        assertEquals("path:\"WhatsApp Images\"", LeappUtils.globToLuceneQuery("*/WhatsApp Images/*"));
    }

    @Test
    public void testFilenameWithInfixMatch() {
        assertEquals("path:\"WhatsApp Images\" && path:*file*", LeappUtils.globToLuceneQuery("*/WhatsApp Images/*file*"));
    }

    @Test
    public void testFolderNameWithLeadingWildcard() {
        // "com" may be the end of a longer token (e.g. "xcom.runtastic.android")
        assertEquals("path:*com && path:\"runtastic.android/databases/user\" && path:db*",
                LeappUtils.globToLuceneQuery("*com.runtastic.android/databases/user.db*"));
    }

    @Test
    public void testFolderNameWithTrailingWildcard() {
        // a partial folder name cannot be searched as a whole token
        assertEquals("path:\"data/com\" && path:whats* && path:\"databases\" && name:\"msgstore.db\"",
                LeappUtils.globToLuceneQuery("*/data/com.whats*/databases/msgstore.db"));
    }

    @Test
    public void testRootPathWithExtensionMatch() {
        assertEquals("name:\"log\"", LeappUtils.globToLuceneQuery("/*.log"));
    }

    @Test
    public void testPathWithIntermediateWildcardDirectories() {
        assertEquals("path:\"system/usagestats\" && name:\"version\"", LeappUtils.globToLuceneQuery("*/system/usagestats/*/version"));
    }

    @Test
    public void testPathWithPrefixFilenameMatch() {
        assertEquals("path:\"com.android.providers.contacts/databases\" && path:contact*",
                LeappUtils.globToLuceneQuery("*/com.android.providers.contacts/databases/contact*"));
    }

    @Test
    public void testWildcardInsideFilename() {
        // "info*" may be followed by punctuation and other tokens before ".db"
        assertEquals("path:\"logs/test-profile\" && path:info* && path:db*",
                LeappUtils.globToLuceneQuery("*/logs/test-profile_info*.db*"));
    }

    @Test
    public void testConsecutiveWildcards() {
        assertEquals("path:\"WhatsApp\" && name:\"db\"", LeappUtils.globToLuceneQuery("**/WhatsApp/**/*.db"));
    }

    @Test
    public void testEmptyFilenameJustDirectory() {
        // Trailing slash means there's no filename, just a path
        assertEquals("path:\"WhatsApp\"", LeappUtils.globToLuceneQuery("*/WhatsApp/"));
    }

    @Test
    public void testCaseFoldingCharacterClasses() {
        // the index is case insensitive: "[Cc]" is just "c"
        assertEquals("path:\"com.android.chrome/cache/Cache\"", LeappUtils.globToLuceneQuery("*/com.android.chrome/cache/[Cc]ache/*"));
        assertEquals("name:\"jpeg\"", LeappUtils.globToLuceneQuery("*.[jJ][pP][eE][gG]"));
    }

    @Test
    public void testOtherCharacterClassesAreWildcards() {
        assertEquals("path:\"com.discord/cache/http-cache\"",
                LeappUtils.globToLuceneQuery("*/com.discord/cache/http-cache/[0-9a-f][0-9a-f].[01]"));
        assertEquals("path:\"intrusion_logs\" && path:*2* && name:\"txt\"",
                LeappUtils.globToLuceneQuery("*/intrusion_logs/*2[0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*.txt"));
    }

    @Test
    public void testRootAnchoredPattern() {
        // "root/" is the prefix seekers prepend to the relative paths: it is not part of any item path
        assertEquals("path:2* && name:\"txt\"", LeappUtils.globToLuceneQuery("root/2[0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*.txt"));
    }

    @Test
    public void testWindowsSeparators() {
        assertEquals("path:\"Default/Session Storage\"", LeappUtils.globToLuceneQuery("*\\Default\\Session Storage\\**"));
    }

    @Test
    public void testNullInput() {
        assertEquals("", LeappUtils.globToLuceneQuery(null));
    }

    @Test
    public void testEmptyInput() {
        assertEquals("", LeappUtils.globToLuceneQuery(""));
        assertEquals("", LeappUtils.globToLuceneQuery("   "));
    }

    @Test
    public void testFileSearchQueryEscapesBasePath() {
        assertEquals("path:\"my \\\"phone\\\"/Dump\" && (name:\"db\") && -fileFragment:true",
                LeappUtils.buildFileSearchQuery("my \"phone\"/Dump", "*.db"));
    }

    @Test
    public void testMatchesGlobCrossesSeparators() {
        assertTrue(LeappUtils.matchesGlob("data/data/com.whatsapp/databases/msgstore.db", "*/com.whatsapp/databases/*.db*"));
        assertTrue(LeappUtils.matchesGlob("data/data/com.whatsapp/databases/msgstore.db-wal", "*/com.whatsapp/databases/*.db*"));
        assertTrue(LeappUtils.matchesGlob("data/data/com.whatsapp/files/a/b.txt", "*com.whatsapp*"));
        assertFalse(LeappUtils.matchesGlob("data/data/com.whatsapp/databases/msgstore.db", "*/com.whatsapp/databases/*.xml"));
        // '?' matches exactly one char
        assertTrue(LeappUtils.matchesGlob("data/a1.db", "*/a?.db"));
        assertFalse(LeappUtils.matchesGlob("data/a12.db", "*/a?.db"));
    }

    @Test
    public void testMatchesGlobIsCaseInsensitive() {
        assertTrue(LeappUtils.matchesGlob("DATA/Data/COM.WhatsApp/x.DB", "*/data/com.whatsapp/*.db"));
    }

    @Test
    public void testMatchesGlobIsAnchoredAtExtractionRoot() {
        String glob = "root/2[0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*.txt";
        assertTrue(LeappUtils.matchesGlob("2024-01-31_report.txt", glob));
        assertFalse(LeappUtils.matchesGlob("logs/2024-01-31_report.txt", glob));
        // a file right at the extraction root still matches a "*/name" pattern, thanks to the root prefix
        assertTrue(LeappUtils.matchesGlob("build.prop", "*/build.prop"));
    }

    @Test
    public void testMatchesGlobCharacterClasses() {
        assertTrue(LeappUtils.matchesGlob("sdcard/DCIM/a.JPEG", "*.[jJ][pP][eE][gG]"));
        assertFalse(LeappUtils.matchesGlob("sdcard/DCIM/a.jpg", "*.[jJ][pP][eE][gG]"));
        assertTrue(LeappUtils.matchesGlob("data/com.discord/cache/http-cache/0af9.1", "*/http-cache/[0-9a-f][0-9a-f][0-9a-f][0-9a-f].[01]"));
        assertFalse(LeappUtils.matchesGlob("data/com.discord/cache/http-cache/0af9.2", "*/http-cache/[0-9a-f][0-9a-f][0-9a-f][0-9a-f].[01]"));
        assertFalse(LeappUtils.matchesGlob("data/com.discord/cache/http-cache/0ag9.1", "*/http-cache/[0-9a-f][0-9a-f][0-9a-f][0-9a-f].[01]"));
        // negated class, and a ']' right after the opening bracket is a member of the class
        assertTrue(LeappUtils.matchesGlob("a/xb", "*/[!a]b"));
        assertFalse(LeappUtils.matchesGlob("a/ab", "*/[!a]b"));
        assertTrue(LeappUtils.matchesGlob("a/]b", "*/[]x]b"));
        // no closing bracket: a literal '['
        assertTrue(LeappUtils.matchesGlob("a/IMG[1.jpg", "*/IMG[1.jpg"));
    }

    @Test
    public void testMatchesGlobRegexMetacharsAreLiteral() {
        assertTrue(LeappUtils.matchesGlob("a/file (1)+{x}$.txt", "*/file (1)+{x}$.txt"));
        assertFalse(LeappUtils.matchesGlob("a/fileX(1)+{x}$.txt", "*/file.(1)+{x}$.txt"));
    }

    @Test
    public void testMatchesGlobWindowsSeparators() {
        assertTrue(LeappUtils.matchesGlob("data/Default/Session Storage/000003.log", "*\\Default\\Session Storage\\**"));
    }

    @Test
    public void testGetExtension() {
        assertEquals("ufdr", LeappUtils.getExtension("case/phone.ufdr"));
        assertEquals("zip", LeappUtils.getExtension("EXTRACTION_FFS.zip"));
        assertEquals("", LeappUtils.getExtension("case.v1/Dump"));
        assertEquals("", LeappUtils.getExtension(""));
        assertEquals("", LeappUtils.getExtension(null));
        // FilenameUtils.getExtension throws for these names on Windows (NTFS alternate data stream check)
        assertEquals("android:answers", LeappUtils.getExtension("files/.Fabric/com.crashlytics.sdk.android:answers"));
        assertEquals("db", LeappUtils.getExtension("files/com.crashlytics.sdk.android:answers/store.db"));
        assertEquals("tap", LeappUtils.getExtension("C:\\dump\\session_analytics.tap"));
    }
}
