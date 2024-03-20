package iped.parsers.vlc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.io.StringWriter;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class VLCIniParserTest {

    private VLCIniParser parser;
    private StringWriter writer;

    @Before
    public void setUp() {
        parser = new VLCIniParser();
        writer = new StringWriter();
    }

    @Test
    public void testParse() throws Exception {
        // Arrange
        InputStream stream = getClass().getResourceAsStream("/test-files/test_vlcParser.ini");
        assertNotNull("Input stream should not be null", stream);

        // Act
        ContentHandler handler = new ToTextContentHandler(writer);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        parser.parse(stream, handler, metadata, context);

        // Assert
        String parsedContent = writer.toString().trim();
        String[] lines = parsedContent.split("\n");
        String actualTitle = lines[0].trim();
        String expectedTitle = "Recent Medias:";
        assertEquals(expectedTitle, actualTitle);
    }
}
