package iped.parsers.misc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

import junit.framework.TestCase;

/**
 * Measures parsing time of unsigned PDFs with signature extraction on and off,
 * printing the medians. Timing assertions are unreliable on shared machines, so
 * the test only runs with -Diped.perfTests=true; the deterministic checks that
 * unsigned PDFs are not loaded again nor copied to a temporary file are in
 * PDFTextParserSignatureTest.
 */
public class PDFSignaturePerformanceTest extends TestCase {

    private static final String[] UNSIGNED = { "test-files/test_pdfResumes.pdf", "test-files/test_pdfImages.pdf",
            "test-files/pdf-signatures/pdf/J01_unsigned.pdf" };
    private static final int RUNS = 7;

    private static File file(String resource) throws Exception {
        return new File(Thread.currentThread().getContextClassLoader().getResource(resource).toURI());
    }

    private static long parseNanos(File pdf, boolean extractSignatures, boolean fromFile) throws Exception {
        PDFTextParser parser = new PDFTextParser();
        parser.setExtractSignatures(extractSignatures);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, pdf.getName());
        byte[] bytes = fromFile ? null : Files.readAllBytes(pdf.toPath());
        long start = System.nanoTime();
        try (InputStream in = fromFile ? TikaInputStream.get(pdf.toPath()) : new ByteArrayInputStream(bytes)) {
            parser.parse(in, new DefaultHandler(), metadata, new ParseContext());
        }
        return System.nanoTime() - start;
    }

    private static long median(File pdf, boolean extractSignatures, boolean fromFile) throws Exception {
        long[] times = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            times[i] = parseNanos(pdf, extractSignatures, fromFile);
        }
        Arrays.sort(times);
        return times[RUNS / 2];
    }

    @Test
    public void testUnsignedPdfOverhead() throws Exception {
        if (!Boolean.getBoolean("iped.perfTests")) {
            System.out.println("PDFSignaturePerformanceTest skipped, run with -Diped.perfTests=true");
            return;
        }
        for (String resource : UNSIGNED) {
            File pdf = file(resource);
            // warm up both paths
            for (int i = 0; i < 2; i++) {
                parseNanos(pdf, false, true);
                parseNanos(pdf, true, false);
            }
            for (boolean fromFile : new boolean[] { true, false }) {
                long off = median(pdf, false, fromFile);
                long on = median(pdf, true, fromFile);
                System.out.printf("PDFSignaturePerformanceTest %-28s %-6s off=%6.1f ms on=%6.1f ms%n", pdf.getName(),
                        fromFile ? "file" : "stream", off / 1e6, on / 1e6);
                assertTrue(pdf.getName() + (fromFile ? " file" : " stream") + ": off=" + off + " on=" + on,
                        on <= off * 1.5 + 20_000_000L);
            }
        }
    }
}
