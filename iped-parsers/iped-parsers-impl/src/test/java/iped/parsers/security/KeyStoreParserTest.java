package iped.parsers.security;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import junit.framework.TestCase;

public class KeyStoreParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testCertificateJavaKeyStore() throws IOException, SAXException, TikaException {

        KeystoreParser parser = new KeystoreParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, KeystoreParser.JAVA_KEYSTORE.toString());
        ContentHandler handler = new ToTextContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_serverMyRelease.keystore")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals(KeystoreParser.JAVA_KEYSTORE.toString(), metadata.get(StandardParser.INDEXER_CONTENT_TYPE));
            assertEquals("changeit", metadata.get(KeystoreParser.PASSWORD));

        }

    }

    @Test
    public void testCertificateP12() throws IOException, SAXException, TikaException {

        KeystoreParser parser = new KeystoreParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, KeystoreParser.PKCS12_MIME.toString());
        ContentHandler handler = new ToTextContentHandler();
        ParseContext context = new ParseContext();
        String alias = "server certificate";
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_server.pfx")) {
            parser.parseCertificate(alias, stream, handler, metadata, context);
            assertEquals("server certificate", metadata.get(TikaCoreProperties.TITLE));
            assertEquals(CertificateParser.DER_MIME.toString(), metadata.get(Metadata.CONTENT_TYPE));

        }
    }

}
