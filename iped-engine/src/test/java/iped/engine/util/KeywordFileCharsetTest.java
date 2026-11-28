package iped.engine.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

public class KeywordFileCharsetTest {

    private static final String TEXT = "москве\n这是什么\nação\n🎉";

    @Test
    public void testUtf8WithAndWithoutBom() {
        byte[] utf8 = TEXT.getBytes(StandardCharsets.UTF_8);
        assertEquals(TEXT, Util.decodeTextDetectingCharset(utf8));
        byte[] bom = new byte[utf8.length + 3];
        bom[0] = (byte) 0xEF;
        bom[1] = (byte) 0xBB;
        bom[2] = (byte) 0xBF;
        System.arraycopy(utf8, 0, bom, 3, utf8.length);
        assertEquals(TEXT, Util.decodeTextDetectingCharset(bom));
    }

    @Test
    public void testUtf16WithBom() {
        byte[] le = ("﻿" + TEXT).getBytes(StandardCharsets.UTF_16LE);
        assertEquals(TEXT, Util.decodeTextDetectingCharset(le));
        byte[] be = ("﻿" + TEXT).getBytes(StandardCharsets.UTF_16BE);
        assertEquals(TEXT, Util.decodeTextDetectingCharset(be));
    }

    @Test
    public void testLegacyListsInWindows1252StillWork() {
        // not valid UTF-8: decoded with the platform charset, or windows-1252 if the platform is UTF-8
        byte[] legacy = "ação\ncafé".getBytes(Charset.forName("windows-1252"));
        Charset expected = StandardCharsets.UTF_8.equals(Charset.defaultCharset()) ? Charset.forName("windows-1252")
                : Charset.defaultCharset();
        assertEquals(new String(legacy, expected), Util.decodeTextDetectingCharset(legacy));
    }

    @Test
    public void testLoadKeywords() throws Exception {
        File file = File.createTempFile("keywords", ".txt");
        try {
            Files.write(file.toPath(), ("  москве \r\n\r\n这是什么\r\n").getBytes(StandardCharsets.UTF_8));
            assertEquals(Arrays.asList("москве", "这是什么"), Util.loadKeywordsDetectingCharset(file));
        } finally {
            file.delete();
        }
    }
}
