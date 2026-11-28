package iped.parsers.misc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSSignedDataStreamGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;

import iped.parsers.misc.PdfSignatureExtractor.SignatureInfo;
import iped.parsers.misc.PdfSignatureExtractor.Status;
import iped.parsers.security.SignatureMetadata;
import junit.framework.TestCase;

/**
 * Uses synthetic PDFs only: the ones generated here with a throwaway key, and
 * the CC0 corpus in test-files/pdf-signatures (see its README.md).
 */
public class PdfSignatureExtractorTest extends TestCase {

    private static final String CORPUS = "test-files/pdf-signatures/pdf/";

    private static KeyPair testKeyPair;
    private static X509Certificate testCert;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            testKeyPair = kpg.generateKeyPair();
            X500Name subject = new X500Name("CN=Test Signer, O=IPED Test, C=BR");
            Date notBefore = new Date(System.currentTimeMillis() - 86400000L);
            Date notAfter = new Date(System.currentTimeMillis() + 365L * 86400000L);
            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(42),
                    notBefore, notAfter, subject, testKeyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(testKeyPair.getPrivate());
            testCert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize test certificate", e);
        }
    }

    private static File corpusFile(String name) throws Exception {
        return new File(Thread.currentThread().getContextClassLoader().getResource(CORPUS + name).toURI());
    }

    private static List<SignatureInfo> extract(File file) throws IOException {
        try (PDDocument doc = PDDocument.load(file)) {
            return new PdfSignatureExtractor().extract(doc, file);
        }
    }

    private static List<SignatureInfo> extractCorpus(String name) throws Exception {
        return extract(corpusFile(name));
    }

    private static Instant instant(Calendar cal) {
        return cal.toInstant();
    }

    interface SignatureValue {
        byte[] sign(byte[] signedBytes) throws Exception;
    }

    interface DocumentSetup {
        void prepare(PDDocument doc) throws Exception;
    }

    /**
     * Creates a one page PDF with the given signature dictionary, using PDFBox
     * external signing: the value receives the /ByteRange bytes and returns the
     * /Contents bytes.
     */
    static byte[] createPdf(PDSignature signature, SignatureValue value) throws Exception {
        return createPdf(signature, value, doc -> {
        });
    }

    /** as {@link #createPdf(PDSignature, SignatureValue)}, changing the document before it is signed */
    static byte[] createPdf(PDSignature signature, SignatureValue value, DocumentSetup setup) throws Exception {
        File unsigned = Files.createTempFile("iped-test-unsigned-", ".pdf").toFile();
        try {
            try (PDDocument doc = new PDDocument()) {
                doc.addPage(new PDPage());
                setup.prepare(doc);
                doc.save(unsigned);
            }
            try (PDDocument doc = PDDocument.load(unsigned)) {
                doc.addSignature(signature);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ExternalSigningSupport externalSigning = doc.saveIncrementalForExternalSigning(baos);
                externalSigning.setSignature(value.sign(externalSigning.getContent().readAllBytes()));
                return baos.toByteArray();
            }
        } finally {
            unsigned.delete();
        }
    }

    static PDSignature signatureDictionary() {
        PDSignature signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        signature.setName("Test Signer");
        signature.setReason("Testing");
        signature.setLocation("Brasilia");
        signature.setContactInfo("test@iped.local");
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(2024, Calendar.JANUARY, 15, 10, 30, 0);
        signature.setSignDate(cal);
        return signature;
    }

    static CMSSignedData detachedCms(byte[] signedBytes) throws Exception {
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(testKeyPair.getPrivate());
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                .build(contentSigner, testCert));
        gen.addCertificates(new JcaCertStore(Collections.singletonList(testCert)));
        return gen.generate(new CMSProcessableByteArray(signedBytes), false);
    }

    /** CMS encapsulating the given content, as adbe.pkcs7.sha1 uses for the SHA-1 of the /ByteRange */
    static CMSSignedData encapsulatedCms(byte[] content) throws Exception {
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(testKeyPair.getPrivate());
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                .build(contentSigner, testCert));
        gen.addCertificates(new JcaCertStore(Collections.singletonList(testCert)));
        return gen.generate(new CMSProcessableByteArray(content), true);
    }

    /** Creates a PDF signed with the test key (adbe.pkcs7.detached). */
    static byte[] createSignedPdf() throws Exception {
        return createPdf(signatureDictionary(), bytes -> detachedCms(bytes).getEncoded());
    }

    // synthetic timestamp authority

    private static final Date TSA_TIME = Date.from(Instant.parse("2024-01-15T10:31:00Z"));
    private static KeyPair tsaKeyPair;
    private static X509Certificate tsaCert;

    private static synchronized void initTsa() throws Exception {
        if (tsaCert != null) {
            return;
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        tsaKeyPair = kpg.generateKeyPair();
        X500Name name = new X500Name("CN=Synthetic Test TSA, O=IPED Test, C=BR");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(77),
                new Date(0), new Date(4102444800000L), name, tsaKeyPair.getPublic());
        builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        tsaCert = new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256WithRSA").build(tsaKeyPair.getPrivate())));
    }

    /** RFC 3161 token over the SHA-256 of the given bytes, carrying the authority certificate */
    private static TimeStampToken timestamp(byte[] covered) throws Exception {
        return timestamp(covered, true);
    }

    private static TimeStampToken timestamp(byte[] covered, boolean certReq) throws Exception {
        initTsa();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(covered);
        TimeStampTokenGenerator gen = new TimeStampTokenGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", tsaKeyPair.getPrivate(), tsaCert),
                new JcaDigestCalculatorProviderBuilder().build().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1)),
                new ASN1ObjectIdentifier("1.2.3.4.1"));
        gen.addCertificates(new JcaCertStore(Collections.singletonList(tsaCert)));
        TimeStampRequestGenerator requestGenerator = new TimeStampRequestGenerator();
        requestGenerator.setCertReq(certReq);
        return gen.generate(requestGenerator.generate(TSPAlgorithms.SHA256, digest), BigInteger.ONE, TSA_TIME);
    }

    /** a detached CMS whose signer has a signature timestamp over the given bytes, or over its own signature value */
    private static byte[] cmsWithTimestamp(byte[] signedBytes, byte[] coveredByTimestamp) throws Exception {
        CMSSignedData cms = detachedCms(signedBytes);
        SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
        byte[] covered = coveredByTimestamp != null ? coveredByTimestamp : signer.getSignature();
        Attribute attr = new Attribute(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                new DERSet(timestamp(covered).toCMSSignedData().toASN1Structure()));
        signer = SignerInformation.replaceUnsignedAttributes(signer, new AttributeTable(attr));
        return CMSSignedData.replaceSigners(cms, new SignerInformationStore(signer)).getEncoded();
    }

    private static byte[] createDocumentTimestampPdf(boolean certReq) throws Exception {
        PDSignature signature = new PDSignature();
        signature.setType(COSName.DOC_TIME_STAMP);
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(COSName.getPDFName("ETSI.RFC3161"));
        return createPdf(signature, bytes -> timestamp(bytes, certReq).getEncoded());
    }

    private static List<SignatureInfo> extractBytes(byte[] pdf) throws IOException {
        File file = Files.createTempFile("iped-test-signed-", ".pdf").toFile();
        try {
            Files.write(file.toPath(), pdf);
            return extract(file);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testGeneratedSignatureIsValid() throws Exception {
        List<SignatureInfo> sigs = extractBytes(createSignedPdf());
        assertEquals(1, sigs.size());
        SignatureInfo s = sigs.get(0);
        assertEquals(Status.VALID, s.status);
        assertNull(s.statusDetail);
        assertEquals("Test Signer", s.name);
        assertEquals("Testing", s.reason);
        assertEquals("Brasilia", s.location);
        assertEquals("test@iped.local", s.contactInfo);
        assertEquals(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED.getName(), s.subFilter);
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), instant(s.signDate));
        assertEquals("2.16.840.1.101.3.4.2.1", s.digestAlgorithmOid);
        assertEquals(new JcaX509CertificateHolder(testCert), s.signerCertificate);
        assertTrue(s.byteRangeValid);
        assertTrue(s.gapIsContentsOnly);
        assertTrue(s.coversWholeDocument);
    }

    @Test
    public void testGeneratedSignatureWithChangedSignedByteIsInvalid() throws Exception {
        byte[] pdf = createSignedPdf();
        // the second line of a PDFBox file is a binary comment: changing it keeps the
        // file loadable but alters the signed bytes
        int pos = new String(pdf, 0, 32, "ISO-8859-1").indexOf('\n') + 2;
        pdf[pos] ^= 0x01;
        List<SignatureInfo> sigs = extractBytes(pdf);
        assertEquals(1, sigs.size());
        assertEquals(Status.INVALID, sigs.get(0).status);
        assertEquals("message digest does not match /ByteRange content", sigs.get(0).statusDetail);
        assertEquals(new JcaX509CertificateHolder(testCert), sigs.get(0).signerCertificate);
    }

    private static byte[] createSha1SubFilterPdf() throws Exception {
        PDSignature signature = signatureDictionary();
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_SHA1);
        return createPdf(signature,
                bytes -> encapsulatedCms(MessageDigest.getInstance("SHA-1").digest(bytes)).getEncoded());
    }

    @Test
    public void testSha1SubFilterIsValid() throws Exception {
        SignatureInfo s = extractBytes(createSha1SubFilterPdf()).get(0);
        assertEquals("adbe.pkcs7.sha1", s.subFilter);
        assertEquals(Status.VALID, s.status);
        assertNull(s.statusDetail);
        assertEquals(new JcaX509CertificateHolder(testCert), s.signerCertificate);
    }

    @Test
    public void testSha1SubFilterWithChangedSignedByteIsInvalid() throws Exception {
        byte[] pdf = createSha1SubFilterPdf();
        int pos = new String(pdf, 0, 32, "ISO-8859-1").indexOf('\n') + 2;
        pdf[pos] ^= 0x01;
        SignatureInfo s = extractBytes(pdf).get(0);
        assertEquals(Status.INVALID, s.status);
        assertEquals("message digest does not match /ByteRange content", s.statusDetail);
    }

    @Test
    public void testEncapsulatedDigestWhateverTheSubFilter() throws Exception {
        // some signers encapsulate a digest of the /ByteRange but declare adbe.pkcs7.detached
        byte[] pdf = createPdf(signatureDictionary(),
                bytes -> encapsulatedCms(MessageDigest.getInstance("SHA-256").digest(bytes)).getEncoded());
        SignatureInfo s = extractBytes(pdf).get(0);
        assertEquals("adbe.pkcs7.detached", s.subFilter);
        assertEquals(Status.VALID, s.status);
    }

    @Test
    public void testEncapsulatedDigestOfOtherAlgorithmWithSameLength() throws Exception {
        // SHA3-256 has the length of SHA-256: it must not be reported as a mismatch
        byte[] pdf = createPdf(signatureDictionary(),
                bytes -> encapsulatedCms(MessageDigest.getInstance("SHA3-256").digest(bytes)).getEncoded());
        assertEquals(Status.VALID, extractBytes(pdf).get(0).status);

        byte[] other = createPdf(signatureDictionary(), bytes -> encapsulatedCms(
                MessageDigest.getInstance("SHA3-256").digest("other".getBytes(StandardCharsets.US_ASCII)))
                        .getEncoded());
        SignatureInfo s = extractBytes(other).get(0);
        assertEquals(Status.INVALID, s.status);
        assertEquals("message digest does not match /ByteRange content", s.statusDetail);
    }

    @Test
    public void testEncapsulatedContentThatIsNotADigest() throws Exception {
        byte[] pdf = createPdf(signatureDictionary(),
                bytes -> encapsulatedCms("not a digest".getBytes(StandardCharsets.US_ASCII)).getEncoded());
        SignatureInfo s = extractBytes(pdf).get(0);
        assertEquals(Status.UNKNOWN, s.status);
        assertEquals("encapsulated content is not a known digest", s.statusDetail);
    }

    @Test
    public void testByteRangeScanAcrossReadChunks() throws Exception {
        File file = Files.createTempFile("iped-test-scan-", ".pdf").toFile();
        try {
            byte[] data = new byte[65536 + 100];
            Arrays.fill(data, (byte) 'a');
            // "/ByteRange" split between the first and the second 64 KiB chunk
            byte[] key = "/ByteRange".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(key, 0, data, 65536 - 4, key.length);
            Files.write(file.toPath(), data);
            assertTrue(PdfSignatureExtractor.mayContainSignature(file));

            Arrays.fill(data, (byte) 'a');
            System.arraycopy("/ByteRang/ByteRangX".getBytes(StandardCharsets.US_ASCII), 0, data, 10, 19);
            Files.write(file.toPath(), data);
            assertFalse(PdfSignatureExtractor.mayContainSignature(file));
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCmsBytesKeepIndefiniteLengthEncoding() throws Exception {
        // BER with indefinite lengths ends with 00 00 markers: they must not be taken for padding
        CMSSignedDataStreamGenerator gen = new CMSSignedDataStreamGenerator();
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                .build(new JcaContentSignerBuilder("SHA256WithRSA").build(testKeyPair.getPrivate()), testCert));
        gen.addCertificates(new JcaCertStore(Collections.singletonList(testCert)));
        ByteArrayOutputStream ber = new ByteArrayOutputStream();
        try (java.io.OutputStream out = gen.open(ber, false)) {
            out.write("content".getBytes(StandardCharsets.US_ASCII));
        }
        byte[] original = ber.toByteArray();
        assertEquals("indefinite length", 0x80, original[1] & 0xff);
        assertEquals(0, original[original.length - 1]);

        SignatureInfo info = new SignatureInfo();
        info.contents = Arrays.copyOf(original, original.length + 1000);
        byte[] extracted = PdfSignatureExtractor.cmsBytes(info);
        assertTrue(Arrays.equals(original, extracted));
        assertNotNull(new CMSSignedData(extracted).getSignerInfos());

        // DER, as in the corpus: the padding is removed
        byte[] der = detachedCms("content".getBytes(StandardCharsets.US_ASCII)).getEncoded();
        info.contents = Arrays.copyOf(der, der.length + 1000);
        assertTrue(Arrays.equals(der, PdfSignatureExtractor.cmsBytes(info)));

        // a reserved signature never filled: only padding, no CMS
        info.contents = new byte[1000];
        assertEquals(0, PdfSignatureExtractor.cmsBytes(info).length);
    }

    @Test
    public void testCorpusSignatureTimestamp() throws Exception {
        SignatureInfo s = extractCorpus("B01_pades_bt.pdf").get(0);
        assertEquals(Status.VALID, s.status);
        assertFalse(s.documentTimestamp);
        assertEquals(Instant.parse("2026-01-15T12:00:05Z"), s.timestampGenTime.toInstant());
        assertEquals(Boolean.TRUE, s.timestampImprintMatches);
        assertEquals("2.16.840.1.101.3.4.2.1", s.timestampDigestAlgorithmOid);
        assertNotNull(s.timestampAuthority);
        // /M stays the declared signing time
        assertEquals(Instant.parse("2026-01-15T12:00:00Z"), instant(s.signDate));

        SignatureInfo noTimestamp = extractCorpus("A01_pades_bb_single.pdf").get(0);
        assertNull(noTimestamp.timestampImprintMatches);
        assertNull(noTimestamp.timestampGenTime);
    }

    @Test
    public void testGeneratedSignatureTimestamp() throws Exception {
        SignatureInfo s = extractBytes(createPdf(signatureDictionary(), bytes -> cmsWithTimestamp(bytes, null))).get(0);
        assertEquals(Status.VALID, s.status);
        assertEquals(TSA_TIME, s.timestampGenTime);
        assertEquals(Boolean.TRUE, s.timestampImprintMatches);
        assertTrue(s.timestampAuthority.contains("CN=Synthetic Test TSA"));

        Metadata subitem = PdfSignatureExtractor.subitemMetadata(s);
        assertEquals("Adobe.PPKLite", subitem.get(SignatureMetadata.FILTER));
        byte[] signatureValue = new CMSSignedData(PdfSignatureExtractor.cmsBytes(s)).getSignerInfos().getSigners()
                .iterator().next().getSignature();
        assertEquals(Hex.toHexString(MessageDigest.getInstance("SHA-256").digest(signatureValue)),
                subitem.get(SignatureMetadata.TIMESTAMP_IMPRINT));
    }

    @Test
    public void testSignatureTimestampOverOtherData() throws Exception {
        byte[] pdf = createPdf(signatureDictionary(),
                bytes -> cmsWithTimestamp(bytes, "something else".getBytes(StandardCharsets.US_ASCII)));
        SignatureInfo s = extractBytes(pdf).get(0);
        // the signature itself is fine, but the token does not timestamp it
        assertEquals(Status.VALID, s.status);
        assertEquals(Boolean.FALSE, s.timestampImprintMatches);
        assertEquals(TSA_TIME, s.timestampGenTime);

        // the PDF item does not list a timestamp that does not cover the signature
        Metadata parent = new Metadata();
        PdfSignatureExtractor.addMetadata(Collections.singletonList(s), parent);
        assertNull(parent.get(SignatureMetadata.TIMESTAMP_DATE));
        // the subitem keeps the token time next to the flag
        Metadata subitem = PdfSignatureExtractor.subitemMetadata(s);
        assertEquals("false", subitem.get(SignatureMetadata.TIMESTAMP_IMPRINT_MATCHES));
        assertEquals("2024-01-15T10:31:00Z", subitem.get(SignatureMetadata.TIMESTAMP_DATE));
    }

    @Test
    public void testDocumentTimestamp() throws Exception {
        SignatureInfo s = extractBytes(createDocumentTimestampPdf(true)).get(0);
        assertTrue(s.documentTimestamp);
        assertEquals(Status.VALID, s.status);
        assertNull(s.statusDetail);
        assertEquals(TSA_TIME, s.timestampGenTime);
        assertEquals(Boolean.TRUE, s.timestampImprintMatches);
        assertEquals(new JcaX509CertificateHolder(tsaCert), s.signerCertificate);
        assertTrue(s.coversWholeDocument);

        Metadata parent = new Metadata();
        PdfSignatureExtractor.addMetadata(Collections.singletonList(s), parent);
        assertEquals("documentTimestamp", parent.get(SignatureMetadata.TYPE));
        assertEquals("valid", parent.get(SignatureMetadata.STATUS));
        assertEquals("2024-01-15T10:31:00Z", parent.get(SignatureMetadata.TIMESTAMP_DATE));
        assertTrue(parent.get(SignatureMetadata.TIMESTAMP_AUTHORITY).contains("CN=Synthetic Test TSA"));
        // the timestamp authority is not reported as a signer of the document
        assertNull(parent.get(SignatureMetadata.SIGNER_NAME));
        assertNull(parent.get(SignatureMetadata.SIGNER_SUBJECT_DN));
        // nor on the subitem: the TSA certificate is not a signer certificate
        Metadata subitem = PdfSignatureExtractor.subitemMetadata(s);
        assertNull(subitem.get(SignatureMetadata.SIGNER_CERT_SERIAL));
        assertNull(subitem.get(SignatureMetadata.SIGNER_CERT_NOT_AFTER));
        assertTrue(subitem.get(SignatureMetadata.TIMESTAMP_AUTHORITY).contains("CN=Synthetic Test TSA"));
    }

    @Test
    public void testUnfilledSignaturePlaceholderIsIgnored() throws Exception {
        // external signing that never completed: /Contents is only zero padding
        assertTrue(extractBytes(createPdf(signatureDictionary(), bytes -> new byte[0])).isEmpty());
    }

    @Test
    public void testHexContentsWithWhitespaceCoversWholeDocument() throws Exception {
        File file = Files.createTempFile("iped-test-gap-", ".pdf").toFile();
        try {
            Files.write(file.toPath(), "abc<30 82\r\n0a>xyz".getBytes(StandardCharsets.US_ASCII));
            assertTrue(PdfSignatureExtractor.isGapContentsOnly(file, new int[] { 0, 3, 14, 3 }));
            Files.write(file.toPath(), "abc<30 8x\r\n0a>xyz".getBytes(StandardCharsets.US_ASCII));
            assertFalse(PdfSignatureExtractor.isGapContentsOnly(file, new int[] { 0, 3, 14, 3 }));
        } finally {
            file.delete();
        }
    }

    @Test
    public void testFieldsSharingOneSignatureAreOneSignature() throws Exception {
        byte[] shared;
        try (PDDocument doc = PDDocument.load(createSignedPdf())) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            PDSignatureField first = (PDSignatureField) form.getFields().get(0);
            PDSignatureField second = new PDSignatureField(form);
            second.getCOSObject().setItem(COSName.V, first.getCOSObject().getDictionaryObject(COSName.V));
            second.setPartialName("Copy");
            form.getFields().add(second);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            shared = out.toByteArray();
        }
        assertEquals(1, extractBytes(shared).size());
    }

    @Test
    public void testDocumentTimestampWithoutCertificate() throws Exception {
        // a token requested without certReq does not carry the authority certificate
        SignatureInfo s = extractBytes(createDocumentTimestampPdf(false)).get(0);
        assertEquals(Status.UNKNOWN, s.status);
        assertEquals("timestamp authority certificate not found in token", s.statusDetail);
        assertEquals(Boolean.TRUE, s.timestampImprintMatches);
        assertEquals(TSA_TIME, s.timestampGenTime);
    }

    @Test
    public void testChangedDocumentTimestampIsInvalid() throws Exception {
        byte[] pdf = createDocumentTimestampPdf(true);
        int pos = new String(pdf, 0, 32, "ISO-8859-1").indexOf('\n') + 2;
        pdf[pos] ^= 0x01;
        SignatureInfo s = extractBytes(pdf).get(0);
        assertEquals(Status.INVALID, s.status);
        assertEquals("timestamp imprint does not match /ByteRange content", s.statusDetail);
        assertEquals(Boolean.FALSE, s.timestampImprintMatches);
    }

    @Test
    public void testPadesSingleSignature() throws Exception {
        List<SignatureInfo> sigs = extractCorpus("A01_pades_bb_single.pdf");
        assertEquals(1, sigs.size());
        SignatureInfo s = sigs.get(0);
        assertEquals(Status.VALID, s.status);
        assertEquals("Signature1", s.fieldName);
        assertEquals("Alice Example (TEST)", s.name);
        assertEquals("ETSI.CAdES.detached", s.subFilter);
        assertEquals(Instant.parse("2026-01-15T10:00:00Z"), instant(s.signDate));
        assertNull(s.cmsSigningTime);
        assertEquals(new BigInteger("1001", 16), s.signerCertificate.getSerialNumber());
        assertTrue(s.coversWholeDocument);
    }

    @Test
    public void testPkcs7DetachedHasCmsSigningTime() throws Exception {
        SignatureInfo s = extractCorpus("A02_pkcs7_detached_single.pdf").get(0);
        assertEquals(Status.VALID, s.status);
        assertEquals("adbe.pkcs7.detached", s.subFilter);
        assertEquals(Instant.parse("2026-01-15T11:00:00Z"), s.cmsSigningTime.toInstant());
    }

    @Test
    public void testTwoSignaturesInDocumentOrder() throws Exception {
        List<SignatureInfo> sigs = extractCorpus("C01_two_signatures.pdf");
        assertEquals(2, sigs.size());
        assertEquals("Alice Example (TEST)", sigs.get(0).name);
        assertEquals("Bob Example (TEST)", sigs.get(1).name);
        assertEquals(0, sigs.get(0).index);
        assertEquals(1, sigs.get(1).index);
        assertEquals(Status.VALID, sigs.get(0).status);
        assertEquals(Status.VALID, sigs.get(1).status);
        assertFalse(sigs.get(0).coversWholeDocument);
        assertTrue(sigs.get(1).coversWholeDocument);
    }

    @Test
    public void testValidSignatureOnDocumentChangedAfterwards() throws Exception {
        for (String file : new String[] { "D01_incremental_after_signing.pdf", "E01_trailing_bytes.pdf" }) {
            SignatureInfo s = extractCorpus(file).get(0);
            assertEquals(file, Status.VALID, s.status);
            assertTrue(file, s.gapIsContentsOnly);
            assertFalse(file, s.coversWholeDocument);
        }
    }

    @Test
    public void testByteRangeGapWiderThanContents() throws Exception {
        SignatureInfo s = extractCorpus("E02_byterange_gap_wider.pdf").get(0);
        // the signature verifies over the declared ranges, but unsigned bytes other
        // than /Contents sit in the gap
        assertEquals(Status.VALID, s.status);
        assertFalse(s.gapIsContentsOnly);
        assertFalse(s.coversWholeDocument);
    }

    @Test
    public void testChangedSignedContentIsInvalid() throws Exception {
        SignatureInfo s = extractCorpus("F01_signed_content_byte_flipped.pdf").get(0);
        assertEquals(Status.INVALID, s.status);
        assertEquals("message digest does not match /ByteRange content", s.statusDetail);
    }

    @Test
    public void testChangedSignatureValueIsInvalid() throws Exception {
        SignatureInfo s = extractCorpus("G01_contents_sigvalue_flipped.pdf").get(0);
        assertEquals(Status.INVALID, s.status);
        assertEquals("signature value does not match", s.statusDetail);
    }

    @Test
    public void testUnparsableCmsIsUnknown() throws Exception {
        List<SignatureInfo> sigs = extractCorpus("G02_contents_der_broken.pdf");
        assertEquals(1, sigs.size());
        assertEquals(Status.UNKNOWN, sigs.get(0).status);
        assertEquals("unparsable CMS", sigs.get(0).statusDetail);
    }

    @Test
    public void testExpiredCertificateStillVerifies() throws Exception {
        SignatureInfo s = extractCorpus("H01_expired_cert.pdf").get(0);
        // certificate validity is not part of the status
        assertEquals(Status.VALID, s.status);
        assertEquals("Expired Signer (TEST)", s.name);
    }

    @Test
    public void testNonAsciiSigner() throws Exception {
        SignatureInfo s = extractCorpus("I01_non_ascii_signer.pdf").get(0);
        String expected = "José Ñandú Łukasz Øre 测试";
        assertEquals(Status.VALID, s.status);
        assertEquals(expected, s.name);
        assertTrue(s.signerCertificate.getSubject().toString().contains(expected));
    }

    @Test
    public void testEcdsaSignature() throws Exception {
        SignatureInfo s = extractCorpus("M01_ecdsa_p256_sha384.pdf").get(0);
        assertEquals(Status.VALID, s.status);
        assertEquals("2.16.840.1.101.3.4.2.2", s.digestAlgorithmOid);
        assertEquals("1.2.840.10045.4.3.3", s.signatureAlgorithmOid);
    }

    @Test
    public void testDocumentsWithoutSignatureValue() throws Exception {
        // unsigned, an empty signature field (no signature value) and a truncated file
        // that PDFBox recovers without its signature
        for (String file : new String[] { "J01_unsigned.pdf", "L01_empty_signature_field.pdf", "K01_truncated.pdf" }) {
            assertTrue(file, extractCorpus(file).isEmpty());
        }
    }

    @Test
    public void testNotAPdfFailsToLoad() throws Exception {
        try {
            extractCorpus("K02_not_a_pdf.pdf");
            fail("expected IOException");
        } catch (IOException e) {
            // the caller (PDFTextParser) handles documents that do not load
        }
    }

    @Test
    public void testByteRangeValidation() {
        long len = 1000;
        assertTrue(PdfSignatureExtractor.isByteRangeValid(new int[] { 0, 100, 200, 800 }, len));
        assertFalse(PdfSignatureExtractor.isByteRangeValid(null, len));
        assertFalse(PdfSignatureExtractor.isByteRangeValid(new int[] { 0, 100, 200 }, len));
        assertFalse(PdfSignatureExtractor.isByteRangeValid(new int[] { 0, -1, 200, 800 }, len));
        // overlapping ranges
        assertFalse(PdfSignatureExtractor.isByteRangeValid(new int[] { 0, 300, 200, 800 }, len));
        // beyond the end of the file
        assertFalse(PdfSignatureExtractor.isByteRangeValid(new int[] { 0, 100, 200, 801 }, len));
        // no int overflow
        assertFalse(PdfSignatureExtractor.isByteRangeValid(
                new int[] { 0, 100, Integer.MAX_VALUE, Integer.MAX_VALUE }, len));
    }

    /** a detached CMS whose unsigned attributes hold something that is not an Attribute */
    private static byte[] cmsWithMalformedUnsignedAttribute(byte[] signedBytes) throws Exception {
        SignedData sd = SignedData.getInstance(detachedCms(signedBytes).toASN1Structure().getContent());
        SignerInfo si = SignerInfo.getInstance(sd.getSignerInfos().getObjectAt(0));
        SignerInfo malformed = new SignerInfo(si.getSID(), si.getDigestAlgorithm(), si.getAuthenticatedAttributes(),
                si.getDigestEncryptionAlgorithm(), si.getEncryptedDigest(), new DERSet(new ASN1Integer(1)));
        SignedData changed = new SignedData(sd.getDigestAlgorithms(), sd.getEncapContentInfo(), sd.getCertificates(),
                sd.getCRLs(), new DERSet(malformed));
        return new ContentInfo(CMSObjectIdentifiers.signedData, changed).getEncoded(ASN1Encoding.DER);
    }

    @Test
    public void testMalformedUnsignedAttributeKeepsOtherFields() throws Exception {
        SignatureInfo s = extractBytes(
                createPdf(signatureDictionary(), PdfSignatureExtractorTest::cmsWithMalformedUnsignedAttribute)).get(0);
        // BouncyCastle reads the unsigned attributes when verifying, so the status stays
        // unknown, but the fields read before are kept
        assertEquals(Status.UNKNOWN, s.status);
        assertEquals("verification error: IllegalArgumentException", s.statusDetail);
        assertNull(s.timestampImprintMatches);
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), instant(s.signDate));
        assertEquals("2.16.840.1.101.3.4.2.1", s.digestAlgorithmOid);
        assertEquals(new JcaX509CertificateHolder(testCert), s.signerCertificate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeeplyNestedContentsDoNotStopExtraction() throws Exception {
        // indefinite-length SEQUENCEs nested 4000 times, within the default /Contents size
        byte[] nested = new byte[8000];
        for (int i = 0; i < nested.length; i += 2) {
            nested[i] = 0x30;
            nested[i + 1] = (byte) 0x80;
        }
        File file = Files.createTempFile("iped-test-nested-", ".pdf").toFile();
        try {
            Files.write(file.toPath(), createPdf(signatureDictionary(), bytes -> nested));
            // BouncyCastle 1.70 parses nested ASN.1 recursively: a small stack makes it
            // overflow whatever the JVM default
            AtomicReference<Object> result = new AtomicReference<>();
            Thread thread = new Thread(null, () -> {
                try {
                    List<SignatureInfo> sigs = extract(file);
                    PdfSignatureExtractor.cmsBytes(sigs.get(0));
                    result.set(sigs);
                } catch (Throwable t) {
                    result.set(t);
                }
            }, "small-stack", 256 * 1024);
            thread.start();
            thread.join();
            assertTrue(String.valueOf(result.get()), result.get() instanceof List);
            List<SignatureInfo> sigs = (List<SignatureInfo>) result.get();
            assertEquals(1, sigs.size());
            assertEquals(Status.UNKNOWN, sigs.get(0).status);
            assertTrue(sigs.get(0).statusDetail, sigs.get(0).statusDetail.contains("StackOverflowError"));
        } finally {
            file.delete();
        }
    }
}
