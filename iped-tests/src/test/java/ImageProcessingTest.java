import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;

import static org.junit.Assert.*;

public class ImageProcessingTest {

    private static final String IMAGE_URL =
        "https://digitalcorpora.s3.amazonaws.com/corpora/drives/nps-2009-ntfs1/ntfs1-gen1.E01";
    private static final Path CACHE_DIR = Paths.get("./target/test-images");

    private static File imageFile;

    @BeforeClass
    public static void setup() throws Exception {
        Files.createDirectories(CACHE_DIR);
        imageFile = CACHE_DIR.resolve("ntfs1-gen1.E01").toFile();
        if (!imageFile.exists()) {
            System.out.println("        Downloading test image...");
            try (InputStream in = new URL(IMAGE_URL).openStream()) {
                Files.copy(in, imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            System.out.println("Using cached image: " + imageFile.getAbsolutePath());
        }
    }

    @Test
    public void testMimeTypeRecognition() throws IOException {
        // Attempt to detect MIME type via probeContentType
        String mime = Files.probeContentType(imageFile.toPath());

        // Fallback to URLConnection if probe returns null
        if (mime == null) {
            try (InputStream is = Files.newInputStream(imageFile.toPath())) {
                mime = URLConnection.guessContentTypeFromStream(is);
            }
        }

        // Default for unknown binary formats
        if (mime == null) {
            mime = "application/octet-stream";
        }

        System.out.println("Detected MIME: " + mime);
        assertEquals(
            "Expected E01 to be recognized as octet-stream",
            "application/octet-stream",
            mime
        );
    }

    // TODO: Add tests for:
    // - file allocation counts, deleted files
    // - carved files count and hashes
    // - sub-items per container
    // - extracted metadata (EXIF, Office headers, emails)
    // - thumbnails count & exceptions
    // - search results per format
}
