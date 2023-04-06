package iped.parsers.security;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.common.net.HttpHeaders;

import iped.parsers.standard.StandardParser;
import junit.framework.TestCase;

public class CertificateParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    private static int getVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    @Test
    public void testCertificateParsingDER() throws IOException, SAXException, TikaException {

        CertificateParser parser = new CertificateParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, CertificateParser.DER_MIME.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_serverCert.der")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("Propriedade"));
            assertTrue(hts.contains("Valor"));
            assertTrue(hts.contains("Subject"));
            assertTrue(hts.contains(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR"));
            assertTrue(hts.contains("Version"));
            assertTrue(hts.contains("3"));
            assertTrue(hts.contains("Serial Number"));
            assertTrue(hts.contains("12677675471164634689"));
            assertTrue(hts.contains("Signature Algorithm"));
            assertTrue(hts.contains("SHA1withRSA"));
            assertTrue(hts.contains("Issuer"));
            assertTrue(hts.contains(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR"));
            assertTrue(hts.contains("Valid from"));
            DateFormat df = DateFormat.getDateTimeInstance();
            assertTrue(hts.contains(df.format(new Date(1622568518000L))));
            assertTrue(hts.contains("Valid to"));
            assertTrue(hts.contains(df.format(new Date(1625160518000L))));
            assertTrue(hts.contains("Alternative Names:"));
            assertTrue(hts.contains("This certificate has no alternative names."));

            assertEquals(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(CertificateParser.SUBJECT));
            assertEquals("2021-07-01", metadata.get(CertificateParser.NOTAFTER).substring(0, 10));
            assertEquals(
                    "Certificado:1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals(CertificateParser.DER_MIME.toString(), metadata.get(StandardParser.INDEXER_CONTENT_TYPE));
            assertEquals("true", metadata.get(CertificateParser.ISSUBJECTAUTHORITY));
            assertEquals(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(CertificateParser.ISSUER));
            assertEquals("2021-06-01", metadata.get(CertificateParser.NOTBEFORE).substring(0, 10));
            assertEquals("text/plain", metadata.get(HttpHeaders.CONTENT_TYPE));

        }

    }

    @Test
    public void testCertificateParsingPEM() throws IOException, SAXException, TikaException {

        CertificateParser parser = new CertificateParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, CertificateParser.PEM_MIME.toString().toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_serverPEM.pem")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("Propriedade"));
            assertTrue(hts.contains("Valor"));
            assertTrue(hts.contains("Subject"));
            assertTrue(hts.contains(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR"));
            assertTrue(hts.contains("Version"));
            assertTrue(hts.contains("3"));
            assertTrue(hts.contains("Serial Number"));
            assertTrue(hts.contains("12677675471164634689"));
            assertTrue(hts.contains("Signature Algorithm"));
            assertTrue(hts.contains("SHA1withRSA"));
            assertTrue(hts.contains("Issuer"));
            assertTrue(hts.contains(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR"));
            assertTrue(hts.contains("Valid from"));
            DateFormat df = DateFormat.getDateTimeInstance();
            assertTrue(hts.contains(df.format(new Date(1622568518000L))));
            assertTrue(hts.contains("Valid to"));
            assertTrue(hts.contains(df.format(new Date(1625160518000L))));
            assertTrue(hts.contains("Alternative Names:"));
            assertTrue(hts.contains("This certificate has no alternative names."));

            assertEquals(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(CertificateParser.SUBJECT));
            assertEquals("2021-07-01", metadata.get(CertificateParser.NOTAFTER).substring(0, 10));
            assertEquals(
                    "Certificado:1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals(CertificateParser.PEM_MIME.toString(), metadata.get(StandardParser.INDEXER_CONTENT_TYPE));
            assertEquals("true", metadata.get(CertificateParser.ISSUBJECTAUTHORITY));
            assertEquals(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(CertificateParser.ISSUER));
            assertEquals("2021-06-01", metadata.get(CertificateParser.NOTBEFORE).substring(0, 10));
            assertEquals("text/plain", metadata.get(HttpHeaders.CONTENT_TYPE));

        }

    }

    @Test
    public void testCertificateParsingPKCS7() throws IOException, SAXException, TikaException {

        CertificateParser parser = new CertificateParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, CertificateParser.PKCS7_SIGNATURE.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_serverPKCS7.p7b")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("Propriedade"));
            assertTrue(hts.contains("Valor"));
            assertTrue(hts.contains("Subject"));
            assertTrue(hts.contains(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR"));
            assertTrue(hts.contains("Version"));
            assertTrue(hts.contains("3"));
            assertTrue(hts.contains("Serial Number"));
            assertTrue(hts.contains("12677675471164634689"));
            assertTrue(hts.contains("Signature Algorithm"));
            assertTrue(hts.contains("SHA1withRSA"));
            assertTrue(hts.contains("Issuer"));
            assertTrue(hts.contains(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR"));
            assertTrue(hts.contains("Valid from"));
            DateFormat df = DateFormat.getDateTimeInstance();
            assertTrue(hts.contains(df.format(new Date(1622568518000L))));
            assertTrue(hts.contains("Valid to"));
            assertTrue(hts.contains(df.format(new Date(1625160518000L))));
            assertTrue(hts.contains("Alternative Names:"));
            assertTrue(hts.contains("This certificate has no alternative names."));

            assertEquals(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(CertificateParser.SUBJECT));
            assertEquals("2021-07-01", metadata.get(CertificateParser.NOTAFTER).substring(0, 10));
            assertEquals(
                    "Certificado:1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals(CertificateParser.PKCS7_SIGNATURE.toString(), metadata.get(StandardParser.INDEXER_CONTENT_TYPE));
            assertEquals("true", metadata.get(CertificateParser.ISSUBJECTAUTHORITY));
            assertEquals(
                    "1.2.840.113549.1.9.1=#161b6775696c6865726d65616e64726575636540676d61696c2e636f6d,CN=pf.gov.br,OU=PF,O=Polícia Federal,L=Asa Sul,ST=Brasília,C=BR",
                    metadata.get(CertificateParser.ISSUER));
            assertEquals("2021-06-01", metadata.get(CertificateParser.NOTBEFORE).substring(0, 10));
            assertEquals("text/plain", metadata.get(HttpHeaders.CONTENT_TYPE));

        }

    }

}
