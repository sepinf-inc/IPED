package iped.engine.task.yara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Covers {@link YaraHighlightSupport#decodeHexForFacet} and the internal helper
 * {@link YaraHighlightSupport#decodePrintable}. These are the two points where the
 * hex representation of a {@link MatchedString} becomes (a) a value for the
 * {@code yara:match:<rule_id>} UI facet field and (b) a highlight term for
 * the text viewer.
 */
public class YaraHighlightSupportTest {

    /* decodeHexForFacet — used by YaraScanTask.persistMatches() -------------- */

    @Test
    public void facet_returnsDecodedTextForPrintableAscii() {
        // "hello" → 0x68 0x65 0x6c 0x6c 0x6f
        assertEquals("hello", YaraHighlightSupport.decodeHexForFacet("68656c6c6f"));
    }

    @Test
    public void facet_returnsRawHexForBinaryBytes() {
        // "ab" + NUL — not printable, fall back to hex
        assertEquals("616200", YaraHighlightSupport.decodeHexForFacet("616200"));
    }

    @Test
    public void facet_returnsRawHexForHighBitBytes() {
        // 0xE2 0x80 0x99 (UTF-8 curly quote) — outside printable ASCII
        assertEquals("e28099", YaraHighlightSupport.decodeHexForFacet("e28099"));
    }

    @Test
    public void facet_normalizesHexCaseToLowercase() {
        // input uppercase, but printable check fails for "MZ" \x90 \x00 \x03 → hex fallback
        assertEquals("4d5a900003", YaraHighlightSupport.decodeHexForFacet("4D5A900003"));
    }

    @Test
    public void facet_emptyForNullOrEmptyOrMalformed() {
        assertEquals("", YaraHighlightSupport.decodeHexForFacet(null));
        assertEquals("", YaraHighlightSupport.decodeHexForFacet(""));
        assertEquals("", YaraHighlightSupport.decodeHexForFacet("abc")); // odd length
    }

    @Test
    public void facet_trimsWhitespaceWhenDecodedToText() {
        // "  ab  " → trim → "ab"
        assertEquals("ab", YaraHighlightSupport.decodeHexForFacet("202061622020"));
    }

    @Test
    public void facet_fallsBackToHexForOnlyWhitespace() {
        // three spaces — trim leaves empty, so fall back to hex
        // (text would be "" which is useless; hex preserves the signal)
        assertEquals("202020", YaraHighlightSupport.decodeHexForFacet("202020"));
    }

    /* decodePrintable — strict printable-only path -------------------------- */

    @Test
    public void printable_acceptsPrintableAscii() {
        assertEquals("hello", YaraHighlightSupport.decodePrintable("68656c6c6f"));
    }

    @Test
    public void printable_acceptsControlChars_TabNewlineCR() {
        // "a\tb\nc\rd"
        assertEquals("a\tb\nc\rd", YaraHighlightSupport.decodePrintable("6109620a630d64"));
    }

    @Test
    public void printable_rejectsBinaryNul() {
        assertNull(YaraHighlightSupport.decodePrintable("616200"));
    }

    @Test
    public void printable_rejectsHighBitBytes() {
        assertNull(YaraHighlightSupport.decodePrintable("e28099"));
    }

    @Test
    public void printable_rejectsMalformedHex() {
        assertNull(YaraHighlightSupport.decodePrintable("zz"));
        assertNull(YaraHighlightSupport.decodePrintable("abc"));
    }

    @Test
    public void printable_returnsNullForOnlyWhitespaceTrimmedAway() {
        // trim() reduces "   " to "", which becomes null per contract
        assertNull(YaraHighlightSupport.decodePrintable("202020"));
    }
}
