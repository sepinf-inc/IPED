package iped.parsers.misc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import iped.data.IItemReader;
import iped.parsers.ocr.OCRParser;
import iped.parsers.security.SignatureMetadata;
import iped.parsers.util.MetadataUtil;
import junit.framework.TestCase;

/**
 * Parses signed PDFs with the parser configuration distributed with IPED
 * (iped-app/resources/config/conf/ParserConfig.xml), to check that PDFs still
 * reach PDFTextParser and that its signature parameters are applied.
 *
 * All Tika parsers are kept in the distributed order. Of the IPED parsers, only
 * the ones that declare application/pdf (OCRParser and PDFTextParser) are kept,
 * because others keep environment-dependent state in static fields. Entries
 * whose classes are not on this module's classpath are removed.
 */
public class PDFSignatureParserConfigTest extends TestCase {

    private static final File PARSER_CONFIG = new File("../../iped-app/resources/config/conf/ParserConfig.xml");
    private static final String CORPUS = "test-files/pdf-signatures/pdf/";

    /** IPED parsers that declare application/pdf, in the distributed order OCRParser comes first */
    private static final Set<String> IPED_PDF_PARSERS = new HashSet<>(
            Arrays.asList(OCRParser.class.getName(), PDFTextParser.class.getName()));

    /** the distributed ParserConfig.xml without entries whose class is not on the classpath */
    private static String loadableConfig(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        for (String tag : new String[] { "parser", "encodingDetector" }) {
            NodeList nodes = doc.getElementsByTagName(tag);
            List<Element> toRemove = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                String cls = e.getAttribute("class");
                // several IPED parsers keep tool paths or scripts found at first use in static
                // fields (e.g. RegRipperParser, PythonParser), which would break their own tests
                // when created here first. Only the IPED parsers that handle PDFs are kept.
                if (cls.startsWith("iped.") && !IPED_PDF_PARSERS.contains(cls)) {
                    toRemove.add(e);
                    continue;
                }
                try {
                    Class.forName(e.getAttribute("class"));
                } catch (ClassNotFoundException | NoClassDefFoundError ex) {
                    toRemove.add(e);
                }
            }
            toRemove.forEach(e -> e.getParentNode().removeChild(e));
        }
        StringWriter out = new StringWriter();
        javax.xml.transform.Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toString();
    }

    private static TikaConfig config(String xml) throws Exception {
        return new TikaConfig(new ByteArrayInputStream(loadableConfig(xml).getBytes(StandardCharsets.UTF_8)));
    }

    private static String distributedXml() throws Exception {
        assertTrue("not found: " + PARSER_CONFIG.getAbsolutePath(), PARSER_CONFIG.isFile());
        return new String(Files.readAllBytes(PARSER_CONFIG.toPath()), StandardCharsets.UTF_8);
    }

    private static Parser pdfParser(TikaConfig config) {
        Parser parser = ((CompositeParser) config.getParser()).getParsers().get(MediaType.application("pdf"));
        while (parser instanceof ParserDecorator) {
            parser = ((ParserDecorator) parser).getWrappedParser();
        }
        return parser;
    }

    /** parses a corpus file as an IPED item: ParsingTask sets name, length and IItemReader */
    private static Metadata parse(TikaConfig config, String file, ParseContext context) throws Exception {
        File pdf = new File(Thread.currentThread().getContextClassLoader().getResource(CORPUS + file).toURI());
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file);
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(pdf.length()));
        context.set(IItemReader.class, (IItemReader) Proxy.newProxyInstance(IItemReader.class.getClassLoader(),
                new Class<?>[] { IItemReader.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return file;
                        case "getLength":
                            return pdf.length();
                        case "getTempFile":
                            return pdf;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                }));
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CORPUS + file)) {
            config.getParser().parse(in, new BodyContentHandler(-1), metadata, context);
        }
        MetadataUtil.normalizeMetadata(metadata);
        return metadata;
    }

    @Test
    public void testPdfsStillReachPDFTextParser() throws Exception {
        TikaConfig config = config(distributedXml());
        assertEquals(PDFTextParser.class, pdfParser(config).getClass());
    }

    @Test
    public void testDistributedConfigExtractsSignatures() throws Exception {
        TikaConfig config = config(distributedXml());
        List<Metadata> subitems = new ArrayList<>();
        ParseContext context = new ParseContext();
        context.set(Parser.class, new org.apache.tika.parser.AbstractParser() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.util.Set<MediaType> getSupportedTypes(ParseContext c) {
                return java.util.Collections.singleton(PdfSignatureExtractor.PKCS7_SIGNATURE);
            }

            @Override
            public void parse(InputStream stream, org.xml.sax.ContentHandler handler, Metadata md, ParseContext c) {
                subitems.add(md);
            }
        });

        Metadata metadata = parse(config, "C01_two_signatures.pdf", context);
        assertEquals("2", metadata.get(SignatureMetadata.COUNT));
        assertEquals("valid", metadata.get(SignatureMetadata.STATUS));
        assertEquals(2, metadata.getValues(SignatureMetadata.SIGNER_NAME).length);
        assertEquals("true", metadata.get("pdf:hasSignature"));
        assertEquals(2, subitems.size());
        assertEquals("Signature1.p7s", subitems.get(0).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testParametersAreApplied() throws Exception {
        String xml = distributedXml();
        String noFiles = xml.replace("<param name=\"extractSignatureFiles\" type=\"bool\">true</param>",
                "<param name=\"extractSignatureFiles\" type=\"bool\">false</param>");
        assertFalse("extractSignatureFiles not found in ParserConfig.xml", noFiles.equals(xml));
        String off = xml.replace("<param name=\"extractSignatures\" type=\"bool\">true</param>",
                "<param name=\"extractSignatures\" type=\"bool\">false</param>");
        assertFalse("extractSignatures not found in ParserConfig.xml", off.equals(xml));

        List<Metadata> subitems = new ArrayList<>();
        ParseContext context = new ParseContext();
        context.set(Parser.class, new org.apache.tika.parser.AbstractParser() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.util.Set<MediaType> getSupportedTypes(ParseContext c) {
                return java.util.Collections.singleton(PdfSignatureExtractor.PKCS7_SIGNATURE);
            }

            @Override
            public void parse(InputStream stream, org.xml.sax.ContentHandler handler, Metadata md, ParseContext c) {
                subitems.add(md);
            }
        });
        Metadata withoutFiles = parse(config(noFiles), "A01_pades_bb_single.pdf", context);
        assertEquals("1", withoutFiles.get(SignatureMetadata.COUNT));
        assertTrue(subitems.isEmpty());

        Metadata disabled = parse(config(off), "A01_pades_bb_single.pdf", new ParseContext());
        assertNull(disabled.get(SignatureMetadata.COUNT));
        // Tika still flags the document
        assertEquals("true", disabled.get("pdf:hasSignature"));
    }
}
