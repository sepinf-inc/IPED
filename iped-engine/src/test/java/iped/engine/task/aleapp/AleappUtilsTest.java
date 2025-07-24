package iped.engine.task.aleapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AleappUtilsTest {

    @Test
    public void testNoSeparator() {
        assertEquals("name:\"com.whatsapp\"", AleappUtils.globToLuceneQuery("*com.whatsapp*"));
    }

    @Test
    public void testFullPathNoWildcardInName() {
        assertEquals("(path:\"com.whatsapp/shared_prefs\") && (name:\"com.whatsapp_preferences_light.xml\")",
                AleappUtils.globToLuceneQuery("*/com.whatsapp/shared_prefs/com.whatsapp_preferences_light.xml"));
    }

    @Test
    public void testPathAndNameQuery() {
        assertEquals("(path:\"com.whatsapp/databases\") && (name:\".db\")", AleappUtils.globToLuceneQuery("*/com.whatsapp/databases/*.db*"));
    }

    @Test
    public void testPathAndSpecificNameQuery() {
        assertEquals("(path:\"WhatsApp Videos\") && (name:\"video.\")", AleappUtils.globToLuceneQuery("*/WhatsApp Videos/video.*"));
    }

    @Test
    public void testPathAndExtensionQuery() {
        assertEquals("(path:\"WhatsApp Audio\") && (name:\".ogg\")", AleappUtils.globToLuceneQuery("*/WhatsApp Audio/*.ogg"));
    }

    @Test
    public void testGenericWildcardInName() {
        assertEquals("path:\"WhatsApp Images\"", AleappUtils.globToLuceneQuery("*/WhatsApp Images/*.*"));
    }

    @Test
    public void testAnotherGenericWildcardInName() {
        assertEquals("path:\"WhatsApp Images\"", AleappUtils.globToLuceneQuery("*/WhatsApp Images/*"));
    }

    @Test
    public void testRootPathWithWildcard() {
        assertEquals("name:\".log\"", AleappUtils.globToLuceneQuery("/*.log"));
    }
    
    @Test
    public void testRootPathWithWildcardInTheMiddle() {
        assertEquals("(path:\"system/usagestats\") && (name:\"version\")", AleappUtils.globToLuceneQuery("*/system/usagestats/*/version"));
    }

    @Test
    public void testNullInput() {
        assertEquals("", AleappUtils.globToLuceneQuery(null));
    }

    @Test
    public void testEmptyInput() {
        assertEquals("", AleappUtils.globToLuceneQuery(""));
    }

    @Test
    public void testBlankInput() {
        assertEquals("", AleappUtils.globToLuceneQuery("   "));
    }
}
