package iped.app.ui;

import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import iped.viewers.ATextViewer;

public class TextParserLineFeedTest {

    /**
     * Offsets (in chars) where new rows start, found the way TextParser scans its
     * UTF-32BE fragments: the position after each line feed.
     */
    private static List<Integer> lineBreaks(String text) {
        byte[] data = text.getBytes(Charset.forName(ATextViewer.TEXT_ENCODING));
        List<Integer> result = new ArrayList<>();
        for (int i = ATextViewer.CHAR_BYTE_COUNT - 1; i < data.length - 1; i += ATextViewer.CHAR_BYTE_COUNT) {
            if (TextParser.isLineFeed(data, i)) {
                result.add((i + 1) / ATextViewer.CHAR_BYTE_COUNT);
            }
        }
        return result;
    }

    @Test
    public void testOnlyRealLineFeedsBreakLines() {
        // 上 (U+4E0A), ช (U+0E0A) and Њ (U+040A) have a 0x0A byte in UTF-32BE
        assertEquals(Arrays.asList(5), lineBreaks("上海ชЊ\nabc"));
        // the whole Gujarati block (U+0A80-U+0AFF) has a 0x0A byte at a misaligned position
        assertEquals(Arrays.asList(), lineBreaks("ઊંટ રણમાં ચાલે છે"));
        assertEquals(Arrays.asList(2, 4), lineBreaks("a\nb\nc"));
    }
}
