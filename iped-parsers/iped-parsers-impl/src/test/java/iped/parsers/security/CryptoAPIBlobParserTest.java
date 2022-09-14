package iped.parsers.security;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import junit.framework.TestCase;

public class CryptoAPIBlobParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testCertificateParsingCAPI() throws IOException, SAXException, TikaException {

        CryptoAPIBlobParser parser = new CryptoAPIBlobParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, CryptoAPIBlobParser.CAPI_MIME.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_server.pfx")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals("false", metadata.get(CryptoAPIBlobParser.HASPUBLICKEY));
            assertEquals("", metadata.get(CryptoAPIBlobParser.ALIAS));
            assertEquals("false", metadata.get(CryptoAPIBlobParser.HASPRIVATEKEY));

        }
    }

}
