package iped.parsers.vlc;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;

import junit.framework.TestCase;

public class VLCIniParserTest extends TestCase {

    @Test
    public void testParse() throws Exception {

        VLCIniParser parser = new VLCIniParser();
        ToTextContentHandler handler = new ToTextContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = getClass().getResourceAsStream("/test-files/test_vlcParser.ini");) {
            assertNotNull("Input stream should not be null", stream);
            parser.parse(stream, handler, metadata, context);
            // Asserts
            String parsedContent = handler.toString().trim();
            String[] lines = parsedContent.lines().toArray(String[]::new);
            assertEquals("C:/Users/teste/Desktop/Um churrasco diferenciado ｜ Shark Tank Brasil [YRMN8tm1zJM].webm [LastSkipPosition=494728ms]", lines[2].trim());
            assertEquals("C:/Users/teste/Desktop/TESTEI INTELIGÊNCIA ARTIFICIAL DO S24 ULTRA ‹ EduKof › [x2TOevkEpsk].webm [LastSkipPosition=410790ms]", lines[3].trim());
            assertEquals("C:/Users/teste/Desktop/App MyGoals ｜ React Native na Prática ｜ Aula 02 [Fdoq7I39xOI].mkv [LastSkipPosition=4461088ms]", lines[4].trim());
            assertEquals("C:/Users/teste/Desktop/Cook app ｜ React Native na Prática ｜ Aula #01 [xmtDatG6SZk].mkv [LastSkipPosition=7588506ms]", lines[5].trim());
            assertEquals("C:/Users/teste/Desktop/PILL： ＂Não existe estabilidade na área de tecnologia＂ [xA8g1mappsM].webm [LastSkipPosition=575083ms]", lines[6].trim());
        }
    }
}
