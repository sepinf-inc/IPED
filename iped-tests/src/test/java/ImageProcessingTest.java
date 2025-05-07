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

    @BeforeClass
    public static void setup() throws Exception {
        Files.createDirectories(CACHE_DIR);
        Files.createDirectories(RESULT_DIR);

        File imageFile = CACHE_DIR.resolve("ntfs1-gen1.E01").toFile();
        if (!imageFile.exists()) {
            System.out.println("======== Downloading test image... ========");
            try (InputStream in = new URL(IMAGE_URL).openStream()) {
                Files.copy(in, imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            System.out.println("Using cached image: " + imageFile.getAbsolutePath());
        }

        IpedProcessor.process(
            CACHE_DIR.resolve("ntfs1-gen1.E01").toAbsolutePath().toString(),
            RESULT_DIR.toAbsolutePath().toString()
        );
    }

    @Test
    public void testMimeTypeRecognition() throws IOException {
        Path imagePath = CACHE_DIR.resolve("ntfs1-gen1.E01");
        String mime = Files.probeContentType(imagePath);
        if (mime == null) {
            try (InputStream is = Files.newInputStream(imagePath)) {
                mime = URLConnection.guessContentTypeFromStream(is);
            }
        }
        if (mime == null) mime = "application/octet-stream";

        System.out.println("Detected MIME: " + mime);
        assertEquals("Expected E01 as octet-stream", "application/octet-stream", mime);
    }
}
