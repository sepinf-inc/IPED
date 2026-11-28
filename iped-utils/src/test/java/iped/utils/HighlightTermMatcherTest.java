package iped.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class HighlightTermMatcherTest {

    /** Returns the matched parts of the text, each one in brackets. */
    private static String matches(String text, String... terms) {
        int[] matches = new HighlightTermMatcher(Arrays.asList(terms)).find(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matches.length; i += 2) {
            sb.append('[').append(text, matches[i], matches[i + 1]).append(']');
        }
        return sb.toString();
    }

    @Test
    public void testFindsAllOccurrencesIgnoringCase() {
        assertEquals("[Evidence][evidence][EVIDENCE]", matches("Evidence, evidence and EVIDENCE", "evidence"));
        assertEquals("[Москве][москве]", matches("В Москве и москве", "москве"));
        assertEquals("", matches("nothing here", "evidence"));
    }

    @Test
    public void testLongestTermWinsAndMatchesDontOverlap() {
        assertEquals("[evidences][evid]", matches("evidences evid", "evid", "evidences", "dences"));
        assertEquals("[aa][aa][a]", matches("aaaaa", "aa", "a"));
    }

    @Test
    public void testBlankTermsAreIgnored() {
        HighlightTermMatcher matcher = new HighlightTermMatcher(Arrays.asList("", " ", "　"));
        assertTrue(matcher.isEmpty());
        assertEquals(0, matcher.find("some text").length);
        assertEquals("[foo]", matches("a foo b", "", " ", "foo"));
    }

    @Test
    public void testOffsetsStayValidWhenLowerCasingChangesLength() {
        // "İ".toLowerCase() has two chars, which shifted the hits after it
        assertEquals("[evidence]", matches("İstanbul evidence", "evidence"));
        assertEquals("[İstanbul]", matches("İstanbul", "istanbul"));
    }

    @Test
    public void testScriptsWithoutSpaces() {
        assertEquals("[ผู้ผลิตสามารถนำเข้า]", matches("ยังมีผู้ผลิตสามารถนำเข้าได้", "ผู้ผลิตสามารถนำเข้า"));
        assertEquals("[杂志][杂志]", matches("杂志很好，我喜欢杂志", "杂志"));
    }

    @Test
    public void testCombiningMarksStayWithTheirBaseCharacter() {
        // sara ii and mai ek belong to ko kai, a hit must not split them
        assertEquals("[กี่][ก]", matches("กี่ ก", "ก"));
        assertEquals("[é]", matches("café", "e"));
    }

    @Test
    public void testSupplementaryCharacters() {
        assertEquals("[😀][😀]", matches("a😀b😀", "😀"));
        // DESERET CAPITAL LETTER LONG I matches its small letter
        assertEquals("[𐐀]", matches("x𐐀", "𐐨"));
    }
}
