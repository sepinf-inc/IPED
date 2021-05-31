package dpf.mt.gpinf.security.parsers;


import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import junit.framework.TestCase;

public class KeyStoreParserTest extends TestCase{


    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testCertificateJavaKeyStore() throws IOException, SAXException, TikaException{

        KeystoreParser parser = new KeystoreParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                MediaType.application("x-java-keystore").toString());
        ContentHandler handler = new ToTextContentHandler();
        InputStream stream = getStream("test-files/test_myRelease.keystore");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String mts = metadata.toString();
        assertTrue(mts.contains("Indexer-Content-Type=application/x-java-keystore"));
        assertTrue(mts.contains("keystore:password=changeit"));
        
    }
    
    
    @Test
    public void testCertificateP12() throws IOException, SAXException, TikaException{

        KeystoreParser parser = new KeystoreParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                MediaType.application("x-pkcs12").toString());
        ContentHandler handler = new ToTextContentHandler();
        InputStream stream = getStream("test-files/test_server.pfx");
        ParseContext context = new ParseContext();
        String alias = "server certificate";
        parser.getSupportedTypes(context);
        parser.parseCertificate(alias, stream, handler, metadata, context);
        String mts = metadata.toString();
        assertTrue(mts.contains("dc:title=server certificate"));
        assertTrue(mts.contains("Indexer-Content-Type=application/pkix-cert"));
        assertTrue(mts.contains("title=server certificate"));
        assertTrue(mts.contains("Content-Type=application/pkix-cert"));
        
    }

}
