package iped.parsers.misc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDXFAResource;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.exception.ZipBombException;
import iped.parsers.fork.EmbeddedDocumentParser;
import iped.parsers.security.CertificateParser;
import iped.parsers.security.SignatureMetadata;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.MetadataUtil;
import junit.framework.TestCase;

/**
 * Signature metadata produced by PDFTextParser, using the CC0 corpus in
 * test-files/pdf-signatures.
 */
public class PDFTextParserSignatureTest extends TestCase {

    private static final String CORPUS = "test-files/pdf-signatures/pdf/";

    private static Metadata parse(String file, PDFTextParser parser, BodyContentHandler handler) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file);
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CORPUS + file)) {
            parser.parse(in, handler, metadata, new ParseContext());
        }
        return metadata;
    }

    private static Metadata parse(String file) throws Exception {
        return parse(file, new PDFTextParser(), new BodyContentHandler(-1));
    }

    @Test
    public void testSingleSignature() throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = parse("A01_pades_bb_single.pdf", new PDFTextParser(), handler);

        assertEquals("true", metadata.get(TikaCoreProperties.HAS_SIGNATURE));
        assertEquals("1", metadata.get(SignatureMetadata.COUNT));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
        assertEquals("2026-01-15T10:00:00Z", metadata.get(SignatureMetadata.DATE));
        assertEquals("/M", metadata.get(SignatureMetadata.DATE_SOURCE));
        assertEquals("true", metadata.get(SignatureMetadata.COVERS_WHOLE_DOCUMENT));
        assertEquals("Alice Example (TEST)", metadata.get(SignatureMetadata.SIGNER_NAME));
        assertTrue(metadata.get(SignatureMetadata.SIGNER_SUBJECT_DN).contains("CN=Alice Example (TEST)"));
        assertTrue(metadata.get(SignatureMetadata.ISSUER_DN).contains("CN=Synthetic Test Root CA"));
        assertEquals("77382817ea280a2ed4ba9b4872d63f735c88931d43d034325324ede9d9e1cbfe",
                metadata.get(SignatureMetadata.SIGNER_CERT_SHA256));
        assertNull(metadata.get(SignatureMetadata.SIGNER_CPF));
        // per-signature details stay on the subitem
        assertNull(metadata.get(SignatureMetadata.BYTE_RANGE));

        // the text of the PDF is still extracted
        assertFalse(handler.toString().trim().isEmpty());
    }

    @Test
    public void testFieldsKeepTheirPrefixAfterNormalization() throws Exception {
        Metadata metadata = parse("A01_pades_bb_single.pdf");
        MetadataUtil.normalizeMetadata(metadata);
        assertEquals("1", metadata.get(SignatureMetadata.COUNT));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
        assertNull(metadata.get("pdf:" + SignatureMetadata.COUNT));
        assertEquals("true", metadata.get("pdf:hasSignature"));
    }

    @Test
    public void testTwoSignaturesInDocumentOrder() throws Exception {
        Metadata metadata = parse("C01_two_signatures.pdf");
        assertEquals("2", metadata.get(SignatureMetadata.COUNT));
        assertEquals(Arrays.asList("valid", "valid"), Arrays.asList(metadata.getValues(SignatureMetadata.STATUS)));
        assertEquals(Arrays.asList("false", "true"),
                Arrays.asList(metadata.getValues(SignatureMetadata.COVERS_WHOLE_DOCUMENT)));
        assertEquals(Arrays.asList("2026-01-16T09:00:00Z", "2026-01-16T10:00:00Z"),
                Arrays.asList(metadata.getValues(SignatureMetadata.DATE)));
        assertEquals(Arrays.asList("Alice Example (TEST)", "Bob Example (TEST)"),
                Arrays.asList(metadata.getValues(SignatureMetadata.SIGNER_NAME)));
    }

    @Test
    public void testIndexedValuesAreSetsAfterNormalization() throws Exception {
        // IPED sorts and de-duplicates metadata values: per-signature fields become sets
        Metadata metadata = parse("C01_two_signatures.pdf");
        MetadataUtil.normalizeMetadata(metadata);
        assertEquals("2", metadata.get(SignatureMetadata.COUNT));
        assertEquals(Arrays.asList("valid"), Arrays.asList(metadata.getValues(SignatureMetadata.STATUS)));
        assertEquals(Arrays.asList("false", "true"),
                Arrays.asList(metadata.getValues(SignatureMetadata.COVERS_WHOLE_DOCUMENT)));
        assertEquals(2, metadata.getValues(SignatureMetadata.SIGNER_NAME).length);
    }

    @Test
    public void testSignedXfaForm() throws Exception {
        // Tika extracts XFA and returns before walking the AcroForm fields, so it does
        // not set hasSignature on signed XFA forms
        byte[] pdf = PdfSignatureExtractorTest.createPdf(PdfSignatureExtractorTest.signatureDictionary(),
                bytes -> PdfSignatureExtractorTest.detachedCms(bytes).getEncoded(), doc -> {
                    PDAcroForm form = new PDAcroForm(doc);
                    doc.getDocumentCatalog().setAcroForm(form);
                    COSStream xfa = doc.getDocument().createCOSStream();
                    try (java.io.OutputStream out = xfa.createOutputStream()) {
                        out.write(("<xdp:xdp xmlns:xdp=\"http://ns.adobe.com/xdp/\"><template "
                                + "xmlns=\"http://www.xfa.org/schema/xfa-template/3.3/\"/></xdp:xdp>")
                                        .getBytes(StandardCharsets.UTF_8));
                    }
                    form.setXFA(new PDXFAResource(xfa));
                });
        Metadata metadata = new Metadata();
        new PDFTextParser().parse(new ByteArrayInputStream(pdf), new BodyContentHandler(-1), metadata,
                new ParseContext());
        assertEquals("true", metadata.get(PDF.HAS_XFA));
        assertNull(metadata.get(TikaCoreProperties.HAS_SIGNATURE));
        assertEquals("1", metadata.get(SignatureMetadata.COUNT));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
    }

    @Test
    public void testSignatureWithoutOptionalEntries() throws Exception {
        // no /Name, /M, /Reason, /Location nor /ContactInfo, as in document timestamps:
        // Tika still sets hasSignature, which the fast path for unsigned PDFs relies on
        org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature minimal =
                new org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature();
        minimal.setFilter(org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature.FILTER_ADOBE_PPKLITE);
        minimal.setSubFilter(
                org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        byte[] pdf = PdfSignatureExtractorTest.createPdf(minimal,
                bytes -> PdfSignatureExtractorTest.detachedCms(bytes).getEncoded());
        Metadata metadata = new Metadata();
        new PDFTextParser().parse(new ByteArrayInputStream(pdf), new BodyContentHandler(-1), metadata,
                new ParseContext());
        assertEquals("true", metadata.get(TikaCoreProperties.HAS_SIGNATURE));
        assertEquals("1", metadata.get(SignatureMetadata.COUNT));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
    }

    @Test
    public void testUnsignedXfaForm() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            COSStream xfa = doc.getDocument().createCOSStream();
            try (java.io.OutputStream out = xfa.createOutputStream()) {
                out.write("<xdp:xdp xmlns:xdp=\"http://ns.adobe.com/xdp/\"/>".getBytes(StandardCharsets.UTF_8));
            }
            form.setXFA(new PDXFAResource(xfa));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdf = out.toByteArray();
        }
        Metadata metadata = new Metadata();
        new PDFTextParser().parse(new ByteArrayInputStream(pdf), new BodyContentHandler(-1), metadata,
                new ParseContext());
        assertEquals("true", metadata.get(PDF.HAS_XFA));
        // no /ByteRange found, but a compressed rewrite could hide it: count not asserted
        assertNull(metadata.get(SignatureMetadata.COUNT));
    }

    @Test
    public void testZipBombFromSubitemExtractionIsNotSwallowed() throws Exception {
        // IPED throws ZipBombException from parseEmbedded to abort the parent parse
        Metadata metadata = new Metadata();
        ParseContext context = itemContext("A01_pades_bb_single.pdf", metadata);
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true;
            }

            @Override
            public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata,
                    boolean outputHtml) throws IOException {
                throw new ZipBombException("max subitem depth");
            }
        });
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CORPUS + "A01_pades_bb_single.pdf")) {
            new PDFTextParser().parse(in, new BodyContentHandler(-1), metadata, context);
            fail("expected ZipBombException");
        } catch (ZipBombException e) {
            // expected
        }
    }

    /** IItemReader of a test item: only name, length and temp file, counting temp file requests */
    private static IItemReader item(File file, String name, AtomicInteger tempFileRequests) {
        return (IItemReader) Proxy.newProxyInstance(IItemReader.class.getClassLoader(),
                new Class<?>[] { IItemReader.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return name;
                        case "getLength":
                            return file.length();
                        case "getTempFile":
                            tempFileRequests.incrementAndGet();
                            return file;
                        case "getBufferedInputStream":
                            return new java.io.BufferedInputStream(new java.io.FileInputStream(file));
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    /** parses a corpus file from a stream without file, as IPED does for items inside images */
    private static Metadata parseAsItem(String file, String itemName, AtomicInteger tempFileRequests)
            throws Exception {
        return parseAsItem(file, itemName, tempFileRequests, new PDFTextParser());
    }

    private static Metadata parseAsItem(String file, String itemName, AtomicInteger tempFileRequests,
            PDFTextParser parser) throws Exception {
        File pdf = new File(Thread.currentThread().getContextClassLoader().getResource(CORPUS + file).toURI());
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file);
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(pdf.length()));
        ParseContext context = new ParseContext();
        context.set(IItemReader.class, item(pdf, itemName, tempFileRequests));
        parser.parse(new ByteArrayInputStream(Files.readAllBytes(pdf.toPath())), new BodyContentHandler(-1),
                metadata, context);
        return metadata;
    }

    @Test
    public void testUnsignedPdfDoesNotNeedATempFile() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        Metadata metadata = parseAsItem("J01_unsigned.pdf", "J01_unsigned.pdf", requests);
        assertEquals("0", metadata.get(SignatureMetadata.COUNT));
        assertEquals(0, requests.get());
    }

    @Test
    public void testSignedPdfGetsItsFileFromTheItem() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        Metadata metadata = parseAsItem("A01_pades_bb_single.pdf", "A01_pades_bb_single.pdf", requests);
        assertEquals(1, requests.get());
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
    }

    @Test
    public void testItemOfAnotherDocumentIsNotUsed() throws Exception {
        // a PDF parsed inline shares its parent's context: the parent item must not be
        // read, the PDF stream is copied to a file before parsing instead
        AtomicInteger requests = new AtomicInteger();
        Metadata metadata = parseAsItem("A01_pades_bb_single.pdf", "parent.zip", requests);
        assertEquals(0, requests.get());
        assertEquals("1", metadata.get(SignatureMetadata.COUNT));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
    }

    @Test
    public void testItemWithSameNameButNoLengthIsNotUsed() throws Exception {
        // an attachment with the same name as its parent, without Content-Length
        File pdf = new File(Thread.currentThread().getContextClassLoader()
                .getResource(CORPUS + "A01_pades_bb_single.pdf").toURI());
        File other = new File(Thread.currentThread().getContextClassLoader()
                .getResource(CORPUS + "J01_unsigned.pdf").toURI());
        AtomicInteger requests = new AtomicInteger();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "contrato.pdf");
        ParseContext context = new ParseContext();
        context.set(IItemReader.class, item(other, "contrato.pdf", requests));
        new PDFTextParser().parse(new ByteArrayInputStream(Files.readAllBytes(pdf.toPath())),
                new BodyContentHandler(-1), metadata, context);
        assertEquals(0, requests.get());
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
    }

    @Test
    public void testInvalidAndChangedDocuments() throws Exception {
        assertEquals("invalid", parse("F01_signed_content_byte_flipped.pdf").get(SignatureMetadata.STATUS));
        assertEquals("invalid", parse("G01_contents_sigvalue_flipped.pdf").get(SignatureMetadata.STATUS));
        assertEquals("unknown", parse("G02_contents_der_broken.pdf").get(SignatureMetadata.STATUS));

        Metadata changed = parse("E01_trailing_bytes.pdf");
        assertEquals("valid", changed.get(SignatureMetadata.STATUS));
        assertEquals("false", changed.get(SignatureMetadata.COVERS_WHOLE_DOCUMENT));
    }

    @Test
    public void testSignatureTimestamp() throws Exception {
        Metadata metadata = parse("B01_pades_bt.pdf");
        assertEquals("signature", metadata.get(SignatureMetadata.TYPE));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
        // declared time and timestamp are kept apart
        assertEquals("2026-01-15T12:00:00Z", metadata.get(SignatureMetadata.DATE));
        assertEquals("2026-01-15T12:00:05Z", metadata.get(SignatureMetadata.TIMESTAMP_DATE));
        assertNotNull(metadata.get(SignatureMetadata.TIMESTAMP_AUTHORITY));

        Metadata subitem = parseWithSubitems("B01_pades_bt.pdf", new PDFTextParser()).metadata.get(0);
        assertEquals("true", subitem.get(SignatureMetadata.TIMESTAMP_IMPRINT_MATCHES));
        assertEquals("2.16.840.1.101.3.4.2.1", subitem.get(SignatureMetadata.TIMESTAMP_DIGEST_ALGORITHM));
        assertEquals("2026-01-15T12:00:05Z", subitem.get(SignatureMetadata.TIMESTAMP_DATE));

        Metadata noTimestamp = parse("A01_pades_bb_single.pdf");
        assertEquals("signature", noTimestamp.get(SignatureMetadata.TYPE));
        assertNull(noTimestamp.get(SignatureMetadata.TIMESTAMP_DATE));
    }

    @Test
    public void testCmsSigningTimeIsUsedOnlyWithoutM() throws Exception {
        // A02 has both /M and a CMS signingTime: /M is the declared date
        Metadata metadata = parse("A02_pkcs7_detached_single.pdf");
        assertEquals("/M", metadata.get(SignatureMetadata.DATE_SOURCE));
        assertEquals("2026-01-15T11:00:00Z", metadata.get(SignatureMetadata.DATE));
    }

    @Test
    public void testUnsignedDocuments() throws Exception {
        for (String file : new String[] { "J01_unsigned.pdf", "L01_empty_signature_field.pdf" }) {
            Metadata metadata = parse(file);
            assertEquals(file, "0", metadata.get(SignatureMetadata.COUNT));
            assertNull(file, metadata.get(SignatureMetadata.STATUS));
        }
    }

    @Test
    public void testExtractionCanBeDisabled() throws Exception {
        PDFTextParser parser = new PDFTextParser();
        parser.setExtractSignatures(false);
        Metadata metadata = parse("A01_pades_bb_single.pdf", parser, new BodyContentHandler(-1));
        assertNull(metadata.get(SignatureMetadata.COUNT));
        assertNull(metadata.get(SignatureMetadata.STATUS));
    }

    /** Records the .p7s subitems produced while parsing. */
    @SuppressWarnings("serial")
    private static class SubitemCapture extends AbstractParser {
        final List<Metadata> metadata = new ArrayList<>();
        final List<byte[]> bytes = new ArrayList<>();

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(PdfSignatureExtractor.PKCS7_SIGNATURE);
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata md, ParseContext context)
                throws IOException {
            if (PdfSignatureExtractor.PKCS7_SIGNATURE.toString().equals(md.get(Metadata.CONTENT_TYPE))) {
                metadata.add(md);
                bytes.add(IOUtils.toByteArray(stream));
            }
        }
    }

    private static SubitemCapture parseWithSubitems(String file, PDFTextParser parser) throws Exception {
        SubitemCapture capture = new SubitemCapture();
        Metadata metadata = new Metadata();
        ParseContext context = itemContext(file, metadata);
        context.set(Parser.class, capture);
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CORPUS + file)) {
            parser.parse(in, new BodyContentHandler(-1), metadata, context);
        }
        return capture;
    }

    /** context and metadata of a corpus file processed as an IPED item, as ParsingTask fills them */
    private static ParseContext itemContext(String file, Metadata metadata) throws Exception {
        File pdf = new File(Thread.currentThread().getContextClassLoader().getResource(CORPUS + file).toURI());
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file);
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(pdf.length()));
        ParseContext context = new ParseContext();
        context.set(IItemReader.class, item(pdf, file, new AtomicInteger()));
        return context;
    }

    private static byte[] corpusBytes(String file) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CORPUS + file)) {
            return IOUtils.toByteArray(in);
        }
    }

    @Test
    public void testSignatureSubitem() throws Exception {
        SubitemCapture capture = parseWithSubitems("A01_pades_bb_single.pdf", new PDFTextParser());
        assertEquals(1, capture.metadata.size());
        Metadata md = capture.metadata.get(0);
        assertEquals("Signature1.p7s", md.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("application/pkcs7-signature", md.get(StandardParser.INDEXER_CONTENT_TYPE));
        assertEquals("1", md.get(SignatureMetadata.COUNT));
        assertEquals("0", md.get(SignatureMetadata.INDEX));
        assertEquals("valid", md.get(SignatureMetadata.STATUS));
        assertEquals("Signature1", md.get(SignatureMetadata.FIELD_NAME));
        assertEquals("Alice Example (TEST)", md.get(SignatureMetadata.DECLARED_NAME));
        assertEquals("Synthetic test signature", md.get(SignatureMetadata.REASON));
        assertEquals("ETSI.CAdES.detached", md.get(SignatureMetadata.SUB_FILTER));
        assertEquals("2.16.840.1.101.3.4.2.1", md.get(SignatureMetadata.DIGEST_ALGORITHM));
        assertEquals("[0, 1548, 8692, 639]", md.get(SignatureMetadata.BYTE_RANGE));
        assertEquals("1001", md.get(SignatureMetadata.SIGNER_CERT_SERIAL));
        assertEquals("2025-01-01T00:00:00Z", md.get(SignatureMetadata.SIGNER_CERT_NOT_BEFORE));
        assertEquals("77382817ea280a2ed4ba9b4872d63f735c88931d43d034325324ede9d9e1cbfe",
                md.get(SignatureMetadata.SIGNER_CERT_SHA256));
    }

    @Test
    public void testSignatureSubitemCanBeVerifiedExternally() throws Exception {
        byte[] p7s = parseWithSubitems("A01_pades_bb_single.pdf", new PDFTextParser()).bytes.get(0);
        // exact DER, without the zero padding of /Contents
        assertEquals(p7s.length, PdfSignatureExtractor.encodedLength(p7s));
        // the /ByteRange gap reserves (8692 - 1548 - 2) / 2 bytes for /Contents
        assertTrue(p7s.length < (8692 - 1548 - 2) / 2);

        byte[] pdf = corpusBytes("A01_pades_bb_single.pdf");
        ByteArrayOutputStream signed = new ByteArrayOutputStream();
        signed.write(pdf, 0, 1548);
        signed.write(pdf, 8692, 639);
        CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signed.toByteArray()), p7s);
        SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
        @SuppressWarnings("unchecked")
        X509CertificateHolder cert = (X509CertificateHolder) cms.getCertificates().getMatches(signer.getSID())
                .iterator().next();
        assertTrue(signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert)));
    }

    @Test
    public void testExistingCertificateParserReadsSubitem() throws Exception {
        byte[] p7s = parseWithSubitems("A01_pades_bb_single.pdf", new PDFTextParser()).bytes.get(0);
        Metadata md = new Metadata();
        md.set(StandardParser.INDEXER_CONTENT_TYPE, PdfSignatureExtractor.PKCS7_SIGNATURE.toString());
        BodyContentHandler text = new BodyContentHandler(-1);
        new CertificateParser().parse(new ByteArrayInputStream(p7s), text, md, new ParseContext());
        // every certificate of the CMS is listed in the text
        assertTrue(text.toString().contains("CN=Alice Example (TEST)"));
        assertTrue(text.toString().contains("CN=Synthetic Test Root CA"));
        // but its metadata describes only the last certificate of the CMS (here the root
        // CA), not the signer: the signer fields come from the signature: metadata
        assertTrue(md.get(CertificateParser.SUBJECT), md.get(CertificateParser.SUBJECT).contains("CN=Synthetic Test Root CA"));
    }

    @Test
    public void testOneSubitemPerSignature() throws Exception {
        SubitemCapture capture = parseWithSubitems("C01_two_signatures.pdf", new PDFTextParser());
        assertEquals(2, capture.metadata.size());
        Metadata first = capture.metadata.get(0);
        Metadata second = capture.metadata.get(1);
        assertEquals("Signature1.p7s", first.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("Signature2.p7s", second.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        // each subitem keeps its own signer, date and coverage together
        assertEquals("Alice Example (TEST)", first.get(SignatureMetadata.SIGNER_NAME));
        assertEquals("2026-01-16T09:00:00Z", first.get(SignatureMetadata.DATE));
        assertEquals("false", first.get(SignatureMetadata.COVERS_WHOLE_DOCUMENT));
        assertEquals("Bob Example (TEST)", second.get(SignatureMetadata.SIGNER_NAME));
        assertEquals("2026-01-16T10:00:00Z", second.get(SignatureMetadata.DATE));
        assertEquals("true", second.get(SignatureMetadata.COVERS_WHOLE_DOCUMENT));
        assertEquals("1002", second.get(SignatureMetadata.SIGNER_CERT_SERIAL));
    }

    @Test
    public void testSubitemOfUnparsableSignature() throws Exception {
        SubitemCapture capture = parseWithSubitems("G02_contents_der_broken.pdf", new PDFTextParser());
        assertEquals(1, capture.metadata.size());
        assertEquals("unknown", capture.metadata.get(0).get(SignatureMetadata.STATUS));
        assertEquals("unparsable CMS", capture.metadata.get(0).get(SignatureMetadata.STATUS_DETAIL));
        assertTrue(capture.bytes.get(0).length > 0);
    }

    @Test
    public void testNoSubitemsWhenPdfsAreNotExpanded() throws Exception {
        // IPED's extractor for items whose category is not expanded parses embedded
        // documents into the parent text
        SubitemCapture capture = new SubitemCapture();
        Metadata metadata = new Metadata();
        ParseContext context = itemContext("A01_pades_bb_single.pdf", metadata);
        context.set(Parser.class, capture);
        context.set(ItemInfo.class, new ItemInfo(0, "0", null, null, "A01_pades_bb_single.pdf", false));
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentParser(context));
        BodyContentHandler text = new BodyContentHandler(-1);
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CORPUS + "A01_pades_bb_single.pdf")) {
            new PDFTextParser().parse(in, text, metadata, context);
        }
        assertTrue(capture.metadata.isEmpty());
        assertFalse(text.toString().contains("Signature1.p7s"));
        // the fields of the PDF item are still there
        assertEquals("1", metadata.get(SignatureMetadata.COUNT));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
    }

    @Test
    public void testNoSubitemsForPdfParsedInline() throws Exception {
        // a PDF inside a container that is not expanded: the context item is the
        // container, and the extractor is wrapped by another parser (as MSGParser does)
        SubitemCapture capture = new SubitemCapture();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "A01_pades_bb_single.pdf");
        ParseContext context = itemContext("C01_two_signatures.pdf", new Metadata());
        context.set(Parser.class, capture);
        EmbeddedDocumentExtractor inline = new ParsingEmbeddedDocumentExtractor(context);
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata md) {
                return inline.shouldParseEmbedded(md);
            }

            @Override
            public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata md, boolean outputHtml)
                    throws SAXException, IOException {
                inline.parseEmbedded(stream, handler, md, outputHtml);
            }
        });
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CORPUS + "A01_pades_bb_single.pdf")) {
            new PDFTextParser().parse(in, new BodyContentHandler(-1), metadata, context);
        }
        assertTrue(capture.metadata.isEmpty());
        assertEquals("1", metadata.get(SignatureMetadata.COUNT));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
    }

    @Test
    public void testSubitemsCanBeDisabled() throws Exception {
        PDFTextParser parser = new PDFTextParser();
        parser.setExtractSignatureFiles(false);
        assertTrue(parseWithSubitems("A01_pades_bb_single.pdf", parser).metadata.isEmpty());
    }

    @Test
    public void testIcePdfPath() throws Exception {
        // IcePDF does not set hasSignature: the /ByteRange check decides
        PDFTextParser parser = new PDFTextParser();
        parser.setUseIcePDF(true);
        Metadata signed = parse("A01_pades_bb_single.pdf", parser, new BodyContentHandler(-1));
        assertEquals("1", signed.get(SignatureMetadata.COUNT));
        assertEquals("valid", signed.get(SignatureMetadata.STATUS));

        Metadata unsigned = parse("J01_unsigned.pdf", parser, new BodyContentHandler(-1));
        assertNull(unsigned.get(SignatureMetadata.STATUS));
    }

    @Test
    public void testScanWithoutTempFileWhenTikaMayHaveMissed() throws Exception {
        // IcePDF path: the item stream is scanned, the temp file only requested if signed
        PDFTextParser parser = new PDFTextParser();
        parser.setUseIcePDF(true);
        AtomicInteger requests = new AtomicInteger();
        parseAsItem("J01_unsigned.pdf", "J01_unsigned.pdf", requests, parser);
        assertEquals(0, requests.get());

        Metadata signed = parseAsItem("A01_pades_bb_single.pdf", "A01_pades_bb_single.pdf", requests, parser);
        assertEquals(1, requests.get());
        assertEquals("valid", signed.get(SignatureMetadata.STATUS));
    }
}
