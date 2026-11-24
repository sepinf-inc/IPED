package iped.engine.lucene.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.junit.Test;

public class UnicodeAnalysisTest {

    private static final String FIELD = "content";

    /** Analyzer as configured by default in IndexTaskConfig.txt */
    private static StandardASCIIAnalyzer analyzer(int version) {
        StandardASCIIAnalyzer analyzer = new StandardASCIIAnalyzer();
        analyzer.setConvertCharsToLower(true);
        analyzer.setConvertCharsToAscii(true);
        analyzer.setAnalyzerVersion(version);
        return analyzer;
    }

    private static List<String> tokens(Analyzer analyzer, String text) throws IOException {
        List<String> result = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream(FIELD, text)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                result.add(term.toString());
            }
            ts.end();
        }
        return result;
    }

    private static List<String> v2(String text) throws IOException {
        return tokens(analyzer(AnalyzerVersion.UNICODE), text);
    }

    private static List<String> nfc(String... expected) {
        List<String> result = new ArrayList<>();
        for (String s : expected) {
            result.add(Normalizer.normalize(s, Normalizer.Form.NFC));
        }
        return result;
    }

    @Test
    public void testLegacyAnalysisIsUnchanged() throws IOException {
        Analyzer legacy = analyzer(AnalyzerVersion.LEGACY);
        // combining marks split words and scripts without spaces are not segmented in the legacy analysis
        assertEquals(Arrays.asList("ה", "א", "ר", "ץ"), tokens(legacy, "הָאָרֶץ"));
        assertEquals(Arrays.asList("这是什么"), tokens(legacy, "这是什么"));
        assertEquals(Arrays.asList("cafe", "resume"), tokens(legacy, "Café résumé"));
    }

    @Test
    public void testLatin() throws IOException {
        assertEquals(Arrays.asList("cafe", "acao", "strasse"), v2("Café AÇÃO Straße"));
        // decomposed text, as in macOS file names
        assertEquals(v2("résumé Tiếng Việt"), v2(Normalizer.normalize("résumé Tiếng Việt", Normalizer.Form.NFD)));
        assertEquals(Arrays.asList("resume", "tieng", "viet"), v2("résumé Tiếng Việt"));
        // soft hyphen inside a word
        assertEquals(Arrays.asList("information"), v2("infor­mation"));
    }

    @Test
    public void testHebrewPointsAreRemoved() throws IOException {
        assertEquals(Arrays.asList("בראשית", "ברא", "אלהים"), v2("בְּרֵאשִׁית בָּרָא אֱלֹהִים"));
        assertEquals(v2("שלום"), v2("שָׁלוֹם"));
    }

    @Test
    public void testArabic() throws IOException {
        // harakat, tanween and tatweel are removed, alef variants normalized
        assertEquals(v2("تقرأ"), v2("تُقرأ"));
        assertEquals(v2("مرحبا"), v2("مرحباً"));
        assertEquals(v2("كتاب"), v2("كتــــاب"));
        assertEquals(v2("احمد"), v2("أحمد"));
        // presentation forms (common in text extracted from PDFs)
        assertEquals(v2("سلام"), v2("ﺳﻼﻡ"));
        // persian letters and zero width non-joiner
        assertEquals(v2("میخواهم"), v2("می‌خواهم"));
        assertEquals(v2("كتاب"), v2("کتاب"));
    }

    @Test
    public void testIndicMarksAreKept() throws IOException {
        assertEquals(nfc("हिन्दी", "भाषा"), v2("हिन्दी भाषा"));
        assertEquals(nfc("தமிழ்", "மொழி"), v2("தமிழ் மொழி"));
        assertEquals(nfc("ગુજરાતી"), v2("ગુજરાતી"));
    }

    @Test
    public void testGreekAndCyrillic() throws IOException {
        assertEquals(Arrays.asList("οδοσ", "οδοσ", "αθηνα"), v2("ΟΔΟΣ οδός Αθήνα"));
        // stress marks and yo
        assertEquals(Arrays.asList("московский", "елка"), v2("моско́вский Ёлка"));
        assertEquals(Arrays.asList("ⰲⱁⰾⱀⰻ"), v2("Ⰲⱁⰾⱀⰻ"));
    }

    @Test
    public void testScriptsWithoutSpacesAreSplitInClusters() throws IOException {
        assertEquals(Arrays.asList("这", "是", "什", "么"), v2("这是什么？"));
        assertEquals(Arrays.asList("ส", "วั", "ส", "ดี", "ค", "รั", "บ"), v2("สวัสดีครับ"));
        assertEquals(Arrays.asList("이", "름", "은"), v2("이름은"));
        assertEquals(Arrays.asList("コ", "ー", "ヒ", "ー"), v2("コーヒー"));
        // khmer coeng stacks the next consonant in the same cluster
        assertEquals(Arrays.asList("ខ្មែ", "រ"), v2("ខ្មែរ"));
        // mixed with latin and digits
        assertEquals(Arrays.asList("iphone", "手", "机", "15"), v2("iPhone手机15"));
        // halfwidth katakana with voiced marks, decomposed hangul
        assertEquals(Arrays.asList("ガ", "ギ"), v2("ｶﾞｷﾞ"));
        assertEquals(v2("한국"), v2(Normalizer.normalize("한국", Normalizer.Form.NFD)));
    }

    @Test
    public void testEmoji() throws IOException {
        assertEquals(Arrays.asList("party", "🎉", "time"), v2("party🎉time"));
        // skin tones and variation selectors are folded
        assertEquals(v2("👍"), v2("👍🏽"));
        assertEquals(v2("❤"), v2("❤️"));
        // flags and ZWJ sequences are single tokens
        assertEquals(1, v2("🇧🇷").size());
        assertEquals(1, v2("👨‍👩‍👧").size());
        assertEquals(2, v2("🇧🇷🇵🇹").size());
    }

    @Test
    public void testCompatibilityForms() throws IOException {
        assertEquals(Arrays.asList("abc", "123"), v2("ＡＢＣ　１２３"));
    }

    @Test
    public void testLongRunsAndBufferBoundaries() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4095; i++) {
            sb.append('a');
        }
        sb.append("🎉b 这是");
        List<String> tokens = v2(sb.toString());
        // the 4095 letters are split in tokens of 255 chars like CharTokenizer does
        assertEquals("🎉", tokens.get(tokens.size() - 4));
        assertEquals(Arrays.asList("b", "这", "是"), tokens.subList(tokens.size() - 3, tokens.size()));
        for (String token : tokens) {
            assertTrue(token.length() <= UnicodeTokenizer.DEFAULT_MAX_TOKEN_LENGTH);
        }
    }

    @Test
    public void testOffsetsPointToOriginalText() throws IOException {
        String text = "Ação: בְּרֵאשִׁית 这是 👍🏽!";
        List<String> originals = new ArrayList<>();
        try (TokenStream ts = analyzer(AnalyzerVersion.UNICODE).tokenStream(FIELD, text)) {
            OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                originals.add(text.substring(offset.startOffset(), offset.endOffset()));
            }
            ts.end();
            assertEquals(text.length(), offset.endOffset());
        }
        assertEquals(Arrays.asList("Ação", "בְּרֵאשִׁית", "这", "是", "👍🏽"), originals);
    }

    @Test
    public void testAnalyzerReuse() throws IOException {
        Analyzer analyzer = analyzer(AnalyzerVersion.UNICODE);
        assertEquals(Arrays.asList("这", "是"), tokens(analyzer, "这是"));
        assertEquals(Arrays.asList("abc"), tokens(analyzer, "abc"));
        assertEquals(Arrays.asList("שלום"), tokens(analyzer, "שָׁלוֹם"));
    }

    @Test
    public void testNormalizeForMultiTermQueries() {
        Analyzer analyzer = analyzer(AnalyzerVersion.UNICODE);
        assertEquals("cafe*", analyzer.normalize(FIELD, "CAFÉ*").utf8ToString());
        assertEquals("שלו*", analyzer.normalize(FIELD, "שָׁלוֹ*").utf8ToString());
        assertEquals("abc", analyzer.normalize(FIELD, "ＡＢＣ").utf8ToString());
    }

    @Test
    public void testWithoutFolding() throws IOException {
        StandardASCIIAnalyzer analyzer = analyzer(AnalyzerVersion.UNICODE);
        analyzer.setConvertCharsToAscii(false);
        // accents are kept, but composed and decomposed forms still match
        assertEquals(Arrays.asList("café"), tokens(analyzer, "Café"));
        assertEquals(tokens(analyzer, "café"), tokens(analyzer, Normalizer.normalize("café", Normalizer.Form.NFD)));
        assertEquals(nfc("בְּרֵאשִׁית"), tokens(analyzer, "בְּרֵאשִׁית"));
    }

    @Test
    public void testClusterScriptDetection() {
        assertTrue(UnicodeTokenizer.containsClusterScript("abc这"));
        assertTrue(UnicodeTokenizer.containsClusterScript("สวัสดี"));
        assertTrue(UnicodeTokenizer.containsClusterScript("이름"));
        assertFalse(UnicodeTokenizer.containsClusterScript("Привет"));
        assertFalse(UnicodeTokenizer.containsClusterScript("שלום"));
    }

    @Test
    public void testAnalyzerVersionOfAnalyzer() {
        assertEquals(AnalyzerVersion.UNICODE, AppAnalyzer.getVersion(analyzer(AnalyzerVersion.UNICODE)));
        assertEquals(AnalyzerVersion.LEGACY, AppAnalyzer.getVersion(new StandardASCIIAnalyzer()));
    }
}
