package iped.parsers.standard;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.LimitedContentHandler;
import iped.parsers.util.ToCSVContentHandler;
import junit.framework.TestCase;

public class RawStringParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testRawStringUTF8() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_utf8")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("issO é OUTR4 stRin8888 codificada em UTF8"));
            assertTrue(hts.contains("Essa stRin8888G esta´ sendÖOO utilizada n0 P4RSER"));
            assertTrue(hts.contains("do 1P3D para R4W STR1N85...!!111"));
        }

    }

    @Test
    public void testRawStringUTF16() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_utf16")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("issO é + um4 stRin8888 codificada em UTF16!!1!"));
            assertTrue(hts
                    .contains("Essa stRin8888G esta\n sendÖOO utilizada n0 P4RSER do 1P3D para R4W STR1N85...!!111"));
            assertTrue(hts.contains("Essa pa5te está em UTF16."));
        }
    }

    @Test
    public void testRawStringISO88591() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_iso8859-1")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("ssO é ÜM4 stRin8888 codificada em ISO8859-1."));
            assertTrue(hts.contains("Essa stRinÝGG888 esta\n sendo utilizada n0 P4RsÈR"));
            assertTrue(hts.contains("do 1P3D para R4W STR1N85...!!111"));
        }
    }

    @Test
    public void testRawStringUTF8ISO() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_utf8iso88591")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("issO é OUTR4 stRin8888 codificada em UTF8."));
            assertTrue(hts.contains("Essa part3 está em UTF8.issO é ÜM4 stRin8888 codificada em ISO8859-1."));
            assertTrue(hts.contains("Essa p4rte está em ISO8859-1"));
        }
    }

    @Test
    public void testRawStringUTF16ISO() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_utf16iso88591")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("Essa p4rte está em ISO8859-1"));
            assertTrue(hts.contains("Essa pa5te está em UTF16."));
        }
    }

    @Test
    public void testRawStringUTF8UTF16() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_utf8utf16")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("Essa part3 está em UTF8.i\nssO"));
            assertTrue(hts.contains("Essa pa5te está em UTF16."));
        }

    }

    @Test
    public void testRawStringUTF16ISOUTF8() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_utf16iso88591utf8")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("Essa p4rte está em ISO8859-1issO"));
            assertTrue(hts.contains("Essa part3 está em UTF8."));
            assertTrue(hts.contains("Essa pa5te está em UTF16."));
        }
    }

    @Test
    public void testRawStringCSV() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        OutputStream outStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
            }
        };
        ContentHandler handler = new ToCSVContentHandler(outStream, "UTF-8");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_utf16iso88591utf8")) {
            parser.parse(stream, handler, metadata, context);
        }
    }

    @Test
    public void testRawStringLimited() throws IOException, SAXException, TikaException {

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new LimitedContentHandler(500);
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_utf16iso88591utf8")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("Essa p4rte está em ISO8859-1issO"));
            assertTrue(hts.contains("Essa part3 está em UTF8."));
            assertTrue(hts.contains("Essa pa5te está em UTF16."));
        }
    }

    /**
     * Strings extracted from the data with the encodings of the rawStrings option
     * (null: option not set).
     */
    private static String extract(byte[] data, String encodings) throws IOException, SAXException, TikaException {
        String previous = encodings == null ? System.clearProperty(RawStringParser.ENCODINGS)
                : System.setProperty(RawStringParser.ENCODINGS, encodings);
        try {
            ContentHandler handler = new BodyContentHandler(-1);
            new RawStringParser().parse(new ByteArrayInputStream(data), handler, new Metadata(), new ParseContext());
            return handler.toString();
        } finally {
            if (previous == null) {
                System.clearProperty(RawStringParser.ENCODINGS);
            } else {
                System.setProperty(RawStringParser.ENCODINGS, previous);
            }
        }
    }

    /** Binary data between strings: controls, zeros and bytes that are not text. */
    private static byte[] binary(Random random, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (random.nextBoolean() ? random.nextInt(8) : 0x80 + random.nextInt(0x40));
        }
        return bytes;
    }

    /** UTF-16 strings are usually ended by U+0000 or padded: zeros and 0xFF. */
    private static byte[] padding(Random random, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (random.nextBoolean() ? 0 : 0xFF);
        }
        return bytes;
    }

    private static byte[] embed(Random random, String[] strings, Charset charset) throws IOException {
        boolean utf16 = charset.name().startsWith("UTF-16");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String s : strings) {
            // odd and even offsets
            int length = 15 + random.nextInt(10);
            out.write(utf16 ? padding(random, length) : binary(random, length));
            out.write(s.getBytes(charset));
        }
        out.write(utf16 ? padding(random, 16) : binary(random, 16));
        return out.toByteArray();
    }

    private static final String[] NON_LATIN = { "Привет, как дела?", "שלום עולם", "مرحبا بالعالم",
            "Γειά σου κόσμε", "नमस्ते दुनिया", "สวัสดีชาวโลก", "안녕하세요 세계 여러분", "これは日本語のテキストです。",
            "这是一个中文的测试，我们的时间", "Zażółć gęślą jaźń", "Tiếng Việt có dấu", "Kazakh: Қазақ тілі" };

    @Test
    public void testRawStringNonLatinUTF8() throws IOException, SAXException, TikaException {
        String text = extract(embed(new Random(1), NON_LATIN, StandardCharsets.UTF_8), RawStringParser.ALL);
        for (String s : NON_LATIN) {
            assertTrue(s, text.contains(s));
        }
    }

    @Test
    public void testRawStringNonLatinUTF16LE() throws IOException, SAXException, TikaException {
        String text = extract(embed(new Random(2), NON_LATIN, StandardCharsets.UTF_16LE), RawStringParser.ALL);
        for (String s : NON_LATIN) {
            assertTrue(s, text.contains(s));
        }
    }

    @Test
    public void testRawStringNonLatinUTF16BE() throws IOException, SAXException, TikaException {
        String text = extract(embed(new Random(3), NON_LATIN, StandardCharsets.UTF_16BE), RawStringParser.ALL);
        for (String s : NON_LATIN) {
            assertTrue(s, text.contains(s));
        }
    }

    @Test
    public void testRawStringDecomposedAndLigatures() throws IOException, SAXException, TikaException {
        // NFD strings are written in NFC; the ligature is kept, folded by the analyzer
        String[] strings = { "mañana", "libertà per tutti", "inﬁnity" };
        for (Charset charset : new Charset[] { StandardCharsets.UTF_8, StandardCharsets.UTF_16LE }) {
            String text = extract(embed(new Random(4), strings, charset), RawStringParser.ALL);
            assertTrue(charset + ": " + text, text.contains("mañana"));
            assertTrue(charset + ": " + text, text.contains("libertà per tutti"));
            assertTrue(charset + ": " + text, text.contains("inﬁnity"));
        }
    }

    @Test
    public void testRawStringUTF16AtWrongOffset() throws IOException, SAXException, TikaException {
        // Latin-1 UTF-16 read one byte off looks like CJK (U+4800 U+6500 U+6C00...)
        byte[] data = embed(new Random(5), new String[] { "Hello World from UTF-16 text", "Another string here" },
                StandardCharsets.UTF_16LE);
        String text = extract(data, RawStringParser.ALL);
        assertTrue(text, text.contains("Hello World from UTF-16 text"));
        assertTrue(text, text.contains("Another string here"));
        for (char c : text.toCharArray()) {
            assertTrue(text, c < 0x100);
        }
    }

    @Test
    public void testRawStringRandomData() throws IOException, SAXException, TikaException {
        // random bytes are valid UTF-8 or UTF-16 letters by chance: few must be taken as text
        // (random data is usually skipped by RandomFilterInputStream)
        byte[] data = new byte[1 << 20];
        new Random(6).nextBytes(data);
        String text = extract(data, RawStringParser.ALL);
        int nonLatin = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x250) {
                nonLatin++;
            }
        }
        assertTrue("chars above Latin: " + nonLatin, nonLatin < 5000);
    }

    @Test
    public void testRawStringLatin1ByDefault() throws IOException, SAXException, TikaException {
        // without the option or with "latin1": only ASCII and Latin-1 text, as before the option
        String[] strings = { "Hello World from Latin-1 text", "Ação e coração", "Привет, как дела?",
                "这是一个中文的测试，我们的时间" };
        for (Charset charset : new Charset[] { StandardCharsets.UTF_8, StandardCharsets.UTF_16LE }) {
            byte[] data = embed(new Random(7), strings, charset);
            String text = extract(data, null);
            assertEquals(charset.name(), text, extract(data, RawStringParser.LATIN1));
            assertTrue(charset + ": " + text, text.contains("Hello World from Latin-1 text"));
            assertTrue(charset + ": " + text, text.contains("Ação e coração"));
            for (char c : text.toCharArray()) {
                assertTrue(charset + ": " + text, c < 0x100);
            }
            String all = extract(data, RawStringParser.ALL);
            for (String s : strings) {
                assertTrue(charset + ": " + all, all.contains(s));
            }
        }
    }

    @Test
    public void testRawStringAllSameAsLatin1OnLatin1Text() throws IOException, SAXException, TikaException {
        // the decoders of the other encodings find nothing more in Latin-1 text
        for (String name : new String[] { "test_utf8", "test_utf16", "test_iso8859-1", "test_utf8iso88591",
                "test_utf16iso88591", "test_utf8utf16", "test_utf16iso88591utf8" }) {
            byte[] data;
            try (InputStream stream = getStream("test-files/" + name)) {
                data = stream.readAllBytes();
            }
            assertEquals(name, extract(data, RawStringParser.LATIN1), extract(data, RawStringParser.ALL));
        }
    }

}
