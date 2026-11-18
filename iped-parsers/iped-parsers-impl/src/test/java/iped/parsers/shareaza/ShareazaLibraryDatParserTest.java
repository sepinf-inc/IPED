package iped.parsers.shareaza;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.properties.ExtraProperties;
import junit.framework.TestCase;

public class ShareazaLibraryDatParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testShareazaLibrary1DatParser() throws IOException, SAXException, TikaException {

        ShareazaLibraryDatParser parser = new ShareazaLibraryDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToTextContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_shareazaLibrary1.dat")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            String[] sharedhashes;
            String[] p2pregistrycount;
            String[] contenttype;
            sharedhashes = metadata.getValues(ExtraProperties.SHARED_HASHES);
            p2pregistrycount = metadata.getValues(ExtraProperties.P2P_REGISTRY_COUNT);
            contenttype = metadata.getValues(Metadata.CONTENT_TYPE);

            assertTrue(hts.contains("Cosmos Laundromat.torrent"));
            assertTrue(hts.contains("Termo-de-compromisso-de-estgio-nao-obrigatorio-2.odt"));
            assertTrue(hts.contains("Licor de Banana.png"));
            assertTrue(hts.contains("ImprimeGRUSimples.pdf"));
            assertTrue(hts.contains("Michael Jackson - Bad.mp3"));

            assertEquals(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE.toString(), contenttype[0]);
            assertEquals("228", p2pregistrycount[0]);
            assertEquals(459, sharedhashes.length);
            assertEquals("5ff811cbb56fa306f01aca1890f1a70a", sharedhashes[0]);
            assertEquals("07dec8692e10ccfcf765e0807f3d94ce", sharedhashes[3]);
            assertEquals("8182ebb4ea93ae9dafaa7cf5b7374bce", sharedhashes[6]);

        }
    }

    @Test
    public void testShareazaLibrary2DatParser() throws IOException, SAXException, TikaException {

        ShareazaLibraryDatParser parser = new ShareazaLibraryDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToTextContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_shareazaLibrary2.dat")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            String[] sharedhashes;
            String[] p2pregistrycount;
            String[] contenttype;
            sharedhashes = metadata.getValues(ExtraProperties.SHARED_HASHES);
            p2pregistrycount = metadata.getValues(ExtraProperties.P2P_REGISTRY_COUNT);
            contenttype = metadata.getValues(Metadata.CONTENT_TYPE);

            assertTrue(hts.contains("High and Dry.mp3"));
            assertTrue(hts.contains("abacaxihortela.png"));
            assertTrue(hts.contains("857b8b7d09e96d26d23fe5df3492c07a57de373a"));
            assertTrue(hts.contains("Guilherme Andreuce.pdf"));
            assertTrue(hts.contains("Big Buck Bunny.torrent"));

            assertEquals(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE.toString(), contenttype[0]);
            assertEquals("218", p2pregistrycount[0]);
            assertEquals(414, sharedhashes.length);
            assertEquals("5ff811cbb56fa306f01aca1890f1a70a", sharedhashes[0]);
            assertEquals("07dec8692e10ccfcf765e0807f3d94ce", sharedhashes[3]);
            assertEquals("025cfa06883c33bcca9b7000e7196718", sharedhashes[6]);

        }
    }

}
