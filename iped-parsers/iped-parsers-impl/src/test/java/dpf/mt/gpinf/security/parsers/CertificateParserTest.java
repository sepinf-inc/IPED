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

public class CertificateParserTest extends TestCase{


    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testCertificateParsingPEM() throws IOException, SAXException, TikaException{

        CertificateParser parser = new CertificateParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                MediaType.application("pkcs7-signature").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_certificate.p7b");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        String mts = metadata.toString();
//        System.out.println("hts:" + hts + "\nmts:" + mts);
        
    }
    
    @Test
    public void testCertificate() throws IOException, SAXException, TikaException{

        CryptoAPIBlobParser parser = new CryptoAPIBlobParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_fileEncrypted.txt");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        String mts = metadata.toString();
//        System.out.println("hts:" + hts + "\nmts:" + mts);
        
    }

}
