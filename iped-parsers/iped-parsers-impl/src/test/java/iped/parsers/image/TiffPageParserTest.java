package iped.parsers.image;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.BaseItemSearchContext;
import iped.parsers.util.ToXMLContentHandler;

public class TiffPageParserTest extends BaseItemSearchContext {

    @Test
    public void testImageOCRMetadataParsingTIFF() throws IOException, SAXException, TikaException {

        String file = "test-files/test_lenaTiff.tiff";
        ParseContext context = getContext(file);
        TiffPageParser parser = new TiffPageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToXMLContentHandler();
        try (InputStream stream = getStream(file)) {
            metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
            parser.parse(stream, handler, metadata, context);

            assertEquals("tiff:NumPages=1", "tiff:NumPages=" + metadata.get(TiffPageParser.propNumPages));

            String hts = handler.toString();
            assertTrue(hts.contains("xmlns=\"http://www.w3.org/1999/xhtml\""));
            assertTrue(hts.contains("<meta name=\"tiff:NumPages\" content=\"1\" />"));
            assertTrue(hts.contains("<meta name=\"Content-Type\" content=\"image/tiff\" />"));
        }
    }
}
