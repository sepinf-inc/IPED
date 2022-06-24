package iped.parsers.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RepoToolDownloader {

    private static final String MVN_REPO_URL = "https://gitlab.com/iped-project/iped-maven/raw/master/";
    private static final String ZIP_FILE_NAME = "tmp.zip";

    /**
     * Extracts a .zip file tool from the maven repository specified path into the output directory
     *
     * @param repoPath relative path from the repository url to the .zip file
     * @param outputDir output directory for the unzipped files
     * @throws IOException
     */
    public static void unzipFromUrl(String repoPath, String outputDir) throws IOException {

        URL url = new URL(MVN_REPO_URL + repoPath.replaceAll("^/+", ""));
        downloadZipFromUrl(url, outputDir);

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(outputDir + ZIP_FILE_NAME))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                final Path toPath = Paths.get(outputDir).resolve(entry.getName());
                if (entry.isDirectory()) {
                    if (!Files.exists(toPath)) {
                        Files.createDirectory(toPath);
                    }
                } else {
                    if (!Files.exists(toPath.getParent())) {
                        Files.createDirectories(toPath.getParent());
                    }
                    if (!Files.exists(toPath)) {
                        Files.copy(zipInputStream, toPath);
                    }
                }
            }
        }
    }

    private static void downloadZipFromUrl(URL url, String outputDir) throws IOException {
        new File(outputDir).mkdirs();
        try (InputStream in = url.openStream();
            FileOutputStream fos = new FileOutputStream(outputDir + ZIP_FILE_NAME)) {
                ReadableByteChannel rbc = Channels.newChannel(in);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }
}
