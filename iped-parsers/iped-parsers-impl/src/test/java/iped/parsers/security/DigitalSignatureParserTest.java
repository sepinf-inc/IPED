/*
 * Copyright 2012-2024, IPED Contributors
 *
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED. If not, see <http://www.gnu.org/licenses/>.
 */
package iped.parsers.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import junit.framework.TestCase;

/**
 * Unit tests for DigitalSignatureParser.
 * Uses synthetically generated PDFs to avoid real personal data.
 */
public class DigitalSignatureParserTest extends TestCase {

    private static KeyPair testKeyPair;
    private static X509Certificate testCert;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            testKeyPair = kpg.generateKeyPair();

            X500Name issuer = new X500Name("CN=Test Signer, O=IPED Test, C=BR");
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date notBefore = new Date(System.currentTimeMillis() - 86400000L);
            Date notAfter = new Date(System.currentTimeMillis() + 365L * 86400000L);

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, issuer,
                    testKeyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                    .build(testKeyPair.getPrivate());
            testCert = new JcaX509CertificateConverter()
                    .getCertificate(certBuilder.build(signer));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize test certificate", e);
        }
    }

    /**
     * Creates a minimal unsigned PDF in memory.
     */
    private InputStream createUnsignedPdf() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(baos);
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Creates a properly signed PDF with embedded CMS content using PDFBox's
     * external signing support and BouncyCastle CMS generation.
     * Saves to a temp file first because saveIncrementalForExternalSigning
     * requires the document to be loaded from a file or stream.
     */
    private InputStream createSignedPdf() throws Exception {
        File tempFile = Files.createTempFile("iped-test-signed-", ".pdf").toFile();
        tempFile.deleteOnExit();
        try {
            // First save an unsigned PDF to the temp file
            try (PDDocument doc = new PDDocument()) {
                doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
                doc.save(tempFile);
            }

            // Now reload from file and sign incrementally
            try (PDDocument doc = PDDocument.load(tempFile)) {
                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
                signature.setName("Test Signer");
                signature.setReason("Testing");
                signature.setLocation("Brasilia");
                signature.setContactInfo("test@iped.local");
                Calendar cal = Calendar.getInstance();
                cal.set(2024, Calendar.JANUARY, 15, 10, 30, 0);
                signature.setSignDate(cal);
                doc.addSignature(signature);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ExternalSigningSupport externalSigning = doc.saveIncrementalForExternalSigning(baos);
                byte[] dataToSign = externalSigning.getContent().readAllBytes();

                CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
                        .build(testKeyPair.getPrivate());
                gen.addSignerInfoGenerator(
                        new JcaSignerInfoGeneratorBuilder(
                                new JcaDigestCalculatorProviderBuilder().build())
                                .build(contentSigner, testCert));
                gen.addCertificates(new org.bouncycastle.cert.jcajce.JcaCertStore(Collections.singletonList(testCert)));

                CMSTypedData cmsData = new org.bouncycastle.cms.CMSProcessableByteArray(dataToSign);
                CMSSignedData signedData = gen.generate(cmsData, false);
                externalSigning.setSignature(signedData.getEncoded());

                return new ByteArrayInputStream(baos.toByteArray());
            }
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void testUnsignedPdf() throws IOException, SAXException, TikaException {
        DigitalSignatureParser parser = new DigitalSignatureParser();
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, "application/pdf");
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = createUnsignedPdf()) {
            parser.parse(stream, handler, metadata, context);
        }
        assertEquals("0", metadata.get(DigitalSignatureParser.SIG_COUNT));
    }

    @Test
    public void testSignedPdfMetadataExtraction() throws Exception {
        DigitalSignatureParser parser = new DigitalSignatureParser();
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, "application/pdf");
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = createSignedPdf()) {
            parser.parse(stream, handler, metadata, context);
        }

        // Verify signature count
        String count = metadata.get(DigitalSignatureParser.SIG_COUNT);
        assertNotNull("Signature count should not be null", count);
        assertEquals("Should detect exactly 1 signature", "1", count);

        // Check basic fields are populated for first signature
        String prefix = DigitalSignatureParser.SIG_PREFIX + "[0]";
        assertNotNull("Signer name should be extracted",
                metadata.get(prefix + DigitalSignatureParser.SIGNER_NAME));
        assertEquals("Test Signer", metadata.get(prefix + DigitalSignatureParser.SIGNER_NAME));
        assertEquals("Testing", metadata.get(prefix + DigitalSignatureParser.REASON));
        assertEquals("Brasilia", metadata.get(prefix + DigitalSignatureParser.LOCATION));
        assertEquals("test@iped.local", metadata.get(prefix + DigitalSignatureParser.CONTACT_INFO));

        // Certificate metadata
        assertNotNull("Certificate subject should be extracted",
                metadata.get(prefix + DigitalSignatureParser.CERT_SUBJECT));
        assertTrue("Subject should contain CN=Test Signer",
                metadata.get(prefix + DigitalSignatureParser.CERT_SUBJECT).contains("CN=Test Signer"));
        assertNotNull("Certificate issuer should be extracted",
                metadata.get(prefix + DigitalSignatureParser.CERT_ISSUER));
        assertNotNull("Certificate serial should be extracted",
                metadata.get(prefix + DigitalSignatureParser.CERT_SERIAL));
        assertNotNull("Certificate valid from should be extracted",
                metadata.get(prefix + DigitalSignatureParser.CERT_VALID_FROM));
        assertNotNull("Certificate valid to should be extracted",
                metadata.get(prefix + DigitalSignatureParser.CERT_VALID_TO));
        assertNotNull("Certificate fingerprint should be extracted",
                metadata.get(prefix + DigitalSignatureParser.CERT_FINGERPRINT_SHA256));

        // Cryptographic match status
        assertNotNull("Cryptographic match should be set",
                metadata.get(prefix + DigitalSignatureParser.CRYPTOGRAPHIC_MATCH));
    }

    @Test
    public void testCorruptedPdfDoesNotCrash() throws IOException, SAXException, TikaException {
        DigitalSignatureParser parser = new DigitalSignatureParser();
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, "application/pdf");
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        byte[] corrupted = "%PDF-1.4 CORRUPTED CONTENT".getBytes();
        try (InputStream stream = new ByteArrayInputStream(corrupted)) {
            parser.parse(stream, handler, metadata, context);
        }
        // Parser should handle gracefully and set count to 0
        assertEquals("0", metadata.get(DigitalSignatureParser.SIG_COUNT));
    }

    @Test
    public void testEmptyStreamDoesNotCrash() throws IOException, SAXException, TikaException {
        DigitalSignatureParser parser = new DigitalSignatureParser();
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, "application/pdf");
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = new ByteArrayInputStream(new byte[0])) {
            parser.parse(stream, handler, metadata, context);
        }
        assertEquals("0", metadata.get(DigitalSignatureParser.SIG_COUNT));
    }
}