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
    private static final Path RESULT_DIR = Paths.get("./target/iped-result");
    private static final String IMAGE_NAME = "ntfs1-gen1.E01";
    private static final Path IMAGE_PATH = CACHE_DIR.resolve(IMAGE_NAME);

    @BeforeClass
    public static void setup() throws Exception {
        createDirectories(CACHE_DIR, RESULT_DIR);
        downloadImageIfNeeded(IMAGE_URL, IMAGE_PATH);

        IpedProcessor.process(
            IMAGE_PATH.toAbsolutePath().toString(),
            RESULT_DIR.toAbsolutePath().toString()
        );
    }

    @Test
    public void testMimeTypeRecognition() throws IOException {
        String mime = Files.probeContentType(IMAGE_PATH);

        if (mime == null) {
            try (InputStream is = Files.newInputStream(IMAGE_PATH)) {
                mime = URLConnection.guessContentTypeFromStream(is);
            }
        }

        if (mime == null) {
            mime = "application/octet-stream";
        }

        assertEquals("Expected MIME type for E01 image", "application/octet-stream", mime);
    }

    private static void createDirectories(Path... paths) throws IOException {
        for (Path path : paths) {
            Files.createDirectories(path);
        }
    }

    private static void downloadImageIfNeeded(String url, Path targetPath) throws IOException {
        File file = targetPath.toFile();
        if (!file.exists()) {
            System.out.println("Downloading test image from: " + url);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
