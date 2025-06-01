package iped.parsers.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import iped.parsers.util.Messages;
import junit.framework.TestCase;

public class CryptoObjectParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testCertificateParsingDER() throws IOException, SAXException, TikaException {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        CryptoObjectParser parser = new CryptoObjectParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, ASN1SequenceUtils.ASN1_SEQUENCE_MIME.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_serverCert.der")) {

            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.Subject")));
            assertTrue(hts.contains(
                    "C=BR,ST=Brasília,L=Asa Sul,O=Polícia Federal,OU=PF,CN=pf.gov.br,E=guilhermeandreuce@gmail.com"));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.SerialNumber")));
            assertTrue(hts.contains("12677675471164634689"));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.SignatureAlgorithm")));
            assertTrue(hts.contains("SHA1WITHRSA"));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.Issuer")));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.ValidFrom")));
            assertTrue(hts.contains("Jun 01 17:28:38 UTC 2021"));
            assertTrue(hts.contains("Jul 01 17:28:38 UTC 2021"));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.AlternativeNames")));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.NoAlternativeNamesFound")));

            assertEquals("12677675471164634689", metadata.get("crypto:certificate:serialNumber"));
            assertEquals("2021-06-01T17:28:38Z", metadata.get("crypto:certificate:notBefore"));
            assertEquals("2021-07-01T17:28:38Z", metadata.get("crypto:certificate:notAfter"));
            assertEquals("C=BR,ST=Brasília,L=Asa Sul,O=Polícia Federal,OU=PF,CN=pf.gov.br,E=guilhermeandreuce@gmail.com", metadata.get("crypto:certificate:subject"));
            assertEquals("C=BR,ST=Brasília,L=Asa Sul,O=Polícia Federal,OU=PF,CN=pf.gov.br,E=guilhermeandreuce@gmail.com", metadata.get("crypto:certificate:issuer"));
        }
    }

    @Test
    public void testCertificateParsingPEM() throws IOException, SAXException, TikaException {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        CryptoObjectParser parser = new CryptoObjectParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, CryptoObjectMimeTypes.X509_CERT_TYPE.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_serverPEM.pem")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.Subject")));
            assertTrue(hts.contains(
                    "C=BR,ST=Brasília,L=Asa Sul,O=Polícia Federal,OU=PF,CN=pf.gov.br,E=guilhermeandreuce@gmail.com"));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.SerialNumber")));
            assertTrue(hts.contains("12677675471164634689"));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.SignatureAlgorithm")));
            assertTrue(hts.contains("SHA1WITHRSA"));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.Issuer")));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.ValidFrom")));
            assertTrue(hts.contains("Jun 01 17:28:38 UTC 2021"));
            assertTrue(hts.contains("Jul 01 17:28:38 UTC 2021"));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.AlternativeNames")));
            assertTrue(hts.contains(Messages.getString("CryptoObjectParser.Cert.NoAlternativeNamesFound")));

            assertEquals("12677675471164634689", metadata.get("crypto:certificate:serialNumber"));
            assertEquals("2021-06-01T17:28:38Z", metadata.get("crypto:certificate:notBefore"));
            assertEquals("2021-07-01T17:28:38Z", metadata.get("crypto:certificate:notAfter"));
            assertEquals("C=BR,ST=Brasília,L=Asa Sul,O=Polícia Federal,OU=PF,CN=pf.gov.br,E=guilhermeandreuce@gmail.com", metadata.get("crypto:certificate:subject"));
            assertEquals("C=BR,ST=Brasília,L=Asa Sul,O=Polícia Federal,OU=PF,CN=pf.gov.br,E=guilhermeandreuce@gmail.com", metadata.get("crypto:certificate:issuer"));
        }
    }

    @Test
    public void testCertificateParsingPKCS7() throws IOException, SAXException, TikaException {

        CryptoObjectParser parser = new CryptoObjectParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, CryptoObjectMimeTypes.PKCS7_SIGNED_DATA_TYPE.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);

        List<Exception> exceptions = new ArrayList<>();
        List<X509CertificateHolder> certificates = new ArrayList<>();

        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata arg0) {
                return true;
            }

            @Override
            public void parseEmbedded(InputStream stream, ContentHandler arg1, Metadata metadata, boolean arg3)
                    throws SAXException, IOException {
                try {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509", new BouncyCastleProvider());
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(stream);
                    certificates.add(new X509CertificateHolder(cert.getEncoded()));
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                }
            }
        });

        try (InputStream stream = getStream("test-files/test_serverPKCS7.p7b")) {
            parser.parse(stream, handler, metadata, context);
            
            assertEquals(0, exceptions.size());
            assertEquals(1, certificates.size());
            assertEquals("C=BR,ST=Brasília,L=Asa Sul,O=Polícia Federal,OU=PF,CN=pf.gov.br,E=guilhermeandreuce@gmail.com", certificates.get(0).getSubject().toString());

        }

    }

}
