package iped.app.ui;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Optional page rendering with the LibreOffice and PDFBox already used by IPED. */
public class WordReportPreview implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordReportPreview.class);
    private final PDDocument pdf;
    private final PDFRenderer renderer;
    private final List<String> text = new ArrayList<>();
    private final List<String[]> lines = new ArrayList<>();
    private final Map<Integer, BufferedImage> pages = new LinkedHashMap<Integer, BufferedImage>(4, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> entry) {
            return size() > 3;
        }
    };

    public WordReportPreview(Path document, Path work) throws Exception {
        this(document, work, null);
    }

    WordReportPreview(Path document, Path work, WordReportOffice office) throws Exception {
        Path output = Files.createDirectory(work.resolve("pdf"));
        String filename = document.getFileName().toString();
        Path pdfFile = output.resolve(filename.substring(0, filename.lastIndexOf('.')) + ".pdf");
        if (office == null) {
            convertWithCommand(document, work, output, pdfFile);
        } else {
            try {
                office.convert(document, pdfFile);
            } catch (Exception error) {
                LOGGER.warn("Could not reuse Word report renderer: {}", error.getClass().getSimpleName());
                convertWithCommand(document, work, output, pdfFile);
            }
        }
        pdf = PDDocument.load(pdfFile.toFile(), MemoryUsageSetting.setupMixed(32 * 1024 * 1024).setTempDir(work.toFile()));
        renderer = new PDFRenderer(pdf);
        initializePages();
    }

    private static void convertWithCommand(Path document, Path work, Path output, Path pdfFile) throws Exception {
        Path executable = WordReportOffice.executable();
        Path profile = Files.createDirectory(work.resolve("office-profile"));
        Process process = new ProcessBuilder(executable.toString(), "-env:UserInstallation=" + profile.toUri(),
                "--headless", "--norestore", "--convert-to", "pdf", "--outdir", output.toString(), document.toString())
                .redirectErrorStream(true).redirectOutput(work.resolve("conversion.log").toFile()).start();
        try {
            if (!process.waitFor(300, TimeUnit.SECONDS)) {
                throw new IOException(Messages.getString("WordReport.PreviewFailed"));
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(pdfFile)) {
            throw new IOException(Messages.getString("WordReport.PreviewFailed"));
        }
    }

    WordReportPreview(PDDocument document) {
        pdf = document;
        renderer = new PDFRenderer(pdf);
        initializePages();
    }

    private void initializePages() {
        text.addAll(Collections.nCopies(pdf.getNumberOfPages(), null));
        lines.addAll(Collections.nCopies(pdf.getNumberOfPages(), null));
    }

    /** Read only as far as the requested anchor; later pages are loaded when needed. */
    private void readText(int page) throws IOException {
        if (text.get(page) == null) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page + 1);
            stripper.setEndPage(page + 1);
            String pageText = stripper.getText(pdf);
            text.set(page, normalize(pageText));
            lines.set(page, pageText.split("\\R"));
        }
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public int findPage(String anchor) throws IOException {
        return findPage(anchor, 0);
    }

    public int findPage(String anchor, int occurrence) throws IOException {
        String needle = normalize(anchor);
        if (needle.isEmpty()) {
            return 0;
        }
        int matches = 0;
        for (int i = 0; i < lines.size(); i++) {
            readText(i);
            for (String line : lines.get(i)) {
                String candidate = normalize(line);
                if (candidate.equals(needle) || candidate.replaceFirst("^[0-9.]+", "").equals(needle)) {
                    if (matches++ == occurrence) {
                        return i;
                    }
                }
            }
        }
        // Wrapped paragraphs, tables and image-only anchors may lack a unique text match.
        needle = needle.substring(0, Math.min(needle.length(), 80));
        for (int i = 0; i < text.size(); i++) {
            readText(i);
            if (text.get(i).contains(needle)) {
                return i;
            }
        }
        return 0;
    }

    public int getPageCount() {
        return pdf.getNumberOfPages();
    }

    /** Locate the inserted item rather than the paragraph that precedes it. */
    public int findPageContaining(String anchor, int occurrence) throws IOException {
        String needle = normalize(anchor);
        if (needle.isEmpty()) {
            return -1;
        }
        int matches = 0;
        for (int i = 0; i < text.size(); i++) {
            readText(i);
            for (int start = 0; (start = text.get(i).indexOf(needle, start)) >= 0; start += needle.length()) {
                if (matches++ == occurrence) {
                    return i;
                }
            }
        }
        return -1;
    }

    public BufferedImage render(int page) throws IOException {
        BufferedImage image = pages.get(page);
        if (image == null) {
            image = renderer.renderImageWithDPI(page, 90);
            pages.put(page, image);
        }
        return image;
    }

    @Override
    public void close() throws IOException {
        pages.clear();
        pdf.close();
    }
}
