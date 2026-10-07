package iped.engine.webapi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * PHASE R5-4b unit tests for the design 07d text-range helpers (F-4): the
 * streaming code point slicer {@link Text.CodePointRangeStream} and the query
 * parameter validation {@link Text#parseOffsetParam(String)} /
 * {@link Text#parseMaxCharsParam(String)}. No server or index is required
 * (same offline pattern as SearchSnippetTest).
 */
public class TextRangeTest {

    /** ASCII + 2-byte + 3-byte + astral (surrogate pair) code points. */
    private static final String SAMPLE = "aáΞ😀b☃ tail";

    private static String slice(String text, long offset, long maxChars) throws IOException {
        return sliceChunked(text, offset, maxChars, Integer.MAX_VALUE);
    }

    /** Feeds the UTF-8 bytes of text in fixed-size writes, like a stream. */
    private static String sliceChunked(String text, long offset, long maxChars, int chunk) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Text.CodePointRangeStream filter = new Text.CodePointRangeStream(bos, offset, maxChars);
        byte[] in = text.getBytes(StandardCharsets.UTF_8);
        for (int off = 0; off < in.length; off += chunk) {
            filter.write(in, off, Math.min(chunk, in.length - off));
        }
        filter.finish();
        byte[] out = bos.toByteArray();
        // the filter must never emit a truncated sequence: round-trip proves
        // the output is valid UTF-8 (no U+FFFD re-encoded back differently)
        assertArrayEquals("filter emitted invalid UTF-8", out,
                new String(out, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        return new String(out, StandardCharsets.UTF_8);
    }

    /** Independent code-point reference implementation (test oracle). */
    private static String referenceSlice(String text, long offset, long maxChars) {
        int[] cps = text.codePoints().toArray();
        long start = Math.min(offset, cps.length);
        long end = maxChars < 0 ? cps.length
                : (offset > Long.MAX_VALUE - maxChars ? cps.length
                        : Math.min((long) cps.length, offset + maxChars));
        StringBuilder sb = new StringBuilder();
        for (long i = start; i < end; i++) {
            sb.appendCodePoint(cps[(int) i]);
        }
        return sb.toString();
    }

    // ---- slice: basic windows -------------------------------------------

    @Test
    public void identityWithoutRange() throws IOException {
        // offset=0 + no limit must reproduce the input byte for byte (AC1)
        assertEquals(SAMPLE, slice(SAMPLE, 0, Text.NO_MAX_CHARS));
        assertEquals(SAMPLE, sliceChunked(SAMPLE, 0, Text.NO_MAX_CHARS, 1));
    }

    @Test
    public void offsetZeroMiddleAndBeyondEnd() throws IOException {
        assertEquals("aáΞ😀b☃ tail", slice(SAMPLE, 0, Text.NO_MAX_CHARS));
        assertEquals("Ξ😀b☃ tail", slice(SAMPLE, 2, Text.NO_MAX_CHARS));
        assertEquals("", slice(SAMPLE, 1000, Text.NO_MAX_CHARS));
        // offset exactly at the text length is the empty suffix too
        assertEquals("", slice(SAMPLE, SAMPLE.codePointCount(0, SAMPLE.length()), Text.NO_MAX_CHARS));
    }

    @Test
    public void maxCharsSmallerThanText() throws IOException {
        assertEquals("aá", slice(SAMPLE, 0, 2));
        assertEquals("Ξ😀", slice(SAMPLE, 2, 2));
        assertEquals(" tail", slice(SAMPLE, 6, Text.NO_MAX_CHARS));
        // offset + maxChars past the end yields the real tail (edge case 6)
        assertEquals("tail", slice(SAMPLE, 7, 500));
    }

    @Test
    public void emptyText() throws IOException {
        assertEquals("", slice("", 0, Text.NO_MAX_CHARS));
        assertEquals("", slice("", 5, 10));
    }

    // ---- slice: astral code points and sequence integrity ----------------

    @Test
    public void surrogatePairNeverSplit() throws IOException {
        String emoji = "a😀b";
        // window cut right after the emoji: the whole 4-byte sequence stays
        assertEquals("a😀", slice(emoji, 0, 2));
        assertEquals(5, slice(emoji, 0, 2).getBytes(StandardCharsets.UTF_8).length);
        assertEquals("😀", slice(emoji, 1, 1));
        assertEquals(4, slice(emoji, 1, 1).getBytes(StandardCharsets.UTF_8).length);
        assertEquals("😀b", slice(emoji, 1, Text.NO_MAX_CHARS));
        // cutting "inside" the pair is impossible from the outside: window
        // sizes are code point sizes, never half a pair
        assertEquals("a", slice(emoji, 0, 1));
    }

    @Test
    public void matchesCodePointReferenceAtEveryBoundaryAndChunkSize() throws IOException {
        String[] texts = { SAMPLE, "", "😀😀😀", "áéíóú ẑ 中文 🏳️‍🌈 ok", "plain ascii" };
        int[] chunks = { 1, 2, 3, 7, 8192 };
        long[] offsets = { 0, 1, 2, 3, 4, 5, 8, 63, 1000, Long.MAX_VALUE };
        long[] maxes = { 1, 2, 3, 4, Text.NO_MAX_CHARS, Long.MAX_VALUE };
        for (String text : texts) {
            for (int chunk : chunks) {
                for (long offset : offsets) {
                    for (long max : maxes) {
                        String viaFilter = sliceChunked(text, offset, max, chunk);
                        String viaReference = referenceSlice(text, offset, max);
                        assertEquals("text=" + text + " chunk=" + chunk + " offset=" + offset + " max=" + max,
                                viaReference, viaFilter);
                    }
                }
            }
        }
    }

    @Test
    public void saturatingWindowEndDoesNotOverflow() throws IOException {
        // offset near Long.MAX_VALUE with maxChars beyond it still works
        assertEquals("", slice(SAMPLE, Long.MAX_VALUE - 1, 5));
        assertEquals("", slice(SAMPLE, Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals("", slice(SAMPLE, Long.MAX_VALUE - 100, Long.MAX_VALUE));
    }

    @Test
    public void writesAfterWindowKeepBeingDiscardedWithoutError() throws IOException {
        // D3/D7: after the window closes the filter only discards, it never
        // throws and never closes the producer early
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Text.CodePointRangeStream filter = new Text.CodePointRangeStream(bos, 0, 1);
        byte[] in = SAMPLE.getBytes(StandardCharsets.UTF_8);
        for (byte b : in) {
            filter.write(b);
        }
        filter.finish();
        assertEquals("a", new String(bos.toByteArray(), StandardCharsets.UTF_8));
    }

    // ---- parameter validation --------------------------------------------

    @Test
    public void offsetParamAcceptsUnsignedDecimal() {
        assertEquals(0, Text.parseOffsetParam(null));
        assertEquals(0, Text.parseOffsetParam("0"));
        assertEquals(7, Text.parseOffsetParam("7"));
        assertEquals(7, Text.parseOffsetParam("007"));
        assertEquals(Long.MAX_VALUE, Text.parseOffsetParam("9223372036854775807"));
    }

    @Test
    public void offsetParamRejectsMalformed() {
        for (String bad : new String[] { "", " ", "-1", "1.5", "1e3", "+1", "abc", "7 ", "9223372036854775808" }) {
            try {
                Text.parseOffsetParam(bad);
                fail("offset should reject '" + bad + "'");
            } catch (IllegalArgumentException e) {
                assertTrue("message must name the parameter: " + e.getMessage(),
                        e.getMessage().contains("offset"));
            }
        }
    }

    @Test
    public void maxCharsParamAcceptsPositiveDecimalOrNull() {
        assertEquals(Text.NO_MAX_CHARS, Text.parseMaxCharsParam(null));
        assertEquals(1, Text.parseMaxCharsParam("1"));
        assertEquals(100, Text.parseMaxCharsParam("100"));
        assertEquals(7, Text.parseMaxCharsParam("007"));
        assertEquals(Long.MAX_VALUE, Text.parseMaxCharsParam("9223372036854775807"));
    }

    @Test
    public void maxCharsParamRejectsZeroAndMalformed() {
        // 0 is a client bug: absence, not 0, expresses "no limit" (07a style)
        for (String bad : new String[] { "0", "", "-1", "-0", "1.5", "1e3", "1_0", "abc", "9223372036854775808" }) {
            try {
                Text.parseMaxCharsParam(bad);
                fail("maxChars should reject '" + bad + "'");
            } catch (IllegalArgumentException e) {
                assertTrue("message must name the parameter: " + e.getMessage(),
                        e.getMessage().contains("maxChars"));
            }
        }
    }
}
