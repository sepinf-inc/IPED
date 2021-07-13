package dpf.mt.gpinf.security.parsers;


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

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import junit.framework.TestCase;

public class CryptoAPIBlobParserTest extends TestCase{


    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testCertificateParsingCAPI() throws IOException, SAXException, TikaException{

        CryptoAPIBlobParser parser = new CryptoAPIBlobParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                MediaType.application("crypto-api-file").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_server.pfx");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        assertEquals("false",metadata.get(CryptoAPIBlobParser.HASPUBLICKEY));
        assertEquals("",metadata.get(CryptoAPIBlobParser.ALIAS));
        assertEquals("false",metadata.get(CryptoAPIBlobParser.HASPRIVATEKEY));
    }

}
