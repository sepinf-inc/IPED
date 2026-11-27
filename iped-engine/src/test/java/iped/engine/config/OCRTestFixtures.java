package iped.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import iped.utils.UTF8Properties;

/** Synthetic configuration only: no installed profile or case is opened. */
public final class OCRTestFixtures {

    private OCRTestFixtures() {
    }

    public static UTF8Properties properties(String value) {
        UTF8Properties properties = new UTF8Properties();
        if (value != null) {
            properties.setProperty("enableOCR", value);
        }
        return properties;
    }

    public static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    public static OCRConfig configured(String value) {
        UTF8Properties properties = properties(value);
        properties.setProperty("OCRLanguage", "eng");
        properties.setProperty("minFileSize2OCR", "10000");
        properties.setProperty("maxFileSize2OCR", "100000000");
        properties.setProperty("pageSegMode", "1");
        properties.setProperty("pdfToImgResolution", "150");
        properties.setProperty("pdfToImgLib", "pdfbox");
        properties.setProperty("externalConvMaxMem", "128");
        properties.setProperty("maxPDFTextSize2OCR", "100");
        properties.setProperty("processNonStandard", "true");
        properties.setProperty("maxConvImageSize", "3000");
        OCRConfig config = new OCRConfig();
        config.processProperties(properties);
        return config;
    }
}
