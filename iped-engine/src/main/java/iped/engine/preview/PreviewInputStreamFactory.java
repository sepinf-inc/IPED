package iped.engine.preview;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import iped.data.IItem;
import iped.io.SeekableInputStream;
import iped.utils.EmptyInputStream;
import iped.utils.SeekableFileInputStream;
import iped.utils.SeekableInputStreamFactory;

/**
 * This class is not indexed as value of IndexItem.SOURCE_DECODER field.
 * It is used in retrieved item from index, when:
 * - the read item has no file AND the read item has no reference in evidence
 * - we want to read item preview
 */
public class PreviewInputStreamFactory extends SeekableInputStreamFactory {

    private static final String IDENTIFIER_SEPARATOR = ":";
    private static final String PREVIEW_FILENAME_PREFIX = "preview-";

    // threshold for using memory vs. temp file (10 MB default)
    private static final int MEMORY_THRESHOLD = 10 * 1024 * 1024;

    public PreviewInputStreamFactory(URI databaseFolderURI) {
        super(databaseFolderURI);
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {

        String[] identifierParts = identifier.split(IDENTIFIER_SEPARATOR);
        if (identifierParts.length != 2) {
            return new EmptyInputStream();
        }

        PreviewKey key = new PreviewKey(Base64.decodeBase64(identifierParts[0]));
        String ext = identifierParts[1];

        File databaseFolder = Paths.get(getDataSourceURI()).toFile();

        try {
            PreviewRepository repo = PreviewRepositoryManager.get(databaseFolder);
            return consumePreviewToSeekableInputStream(repo, key, ext, false);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Generates a unique string identifier for an item's preview, used by
     * {@link PreviewInputStreamFactory}.
     *
     * @param evidence The item to generate an ID for.
     * @return A string in the format "Base64(key).extension".
     */
    public static String getIdentifierForPreview(IItem item) {
        PreviewKey key = PreviewKey.create(item);
        return Base64.encodeBase64String(key.getBytes()) + IDENTIFIER_SEPARATOR + item.getPreviewExt();
    }

    /**
     * Consumes a preview from the repository and converts it to a SeekableInputStream.
     *
     * @param repo The PreviewRepository instance to use.
     * @param key The key of the preview.
     * @param ext The file extension for the temporary file, if created.
     * @param forceFile If true, always use a temp file instead of memory.
     * @return A SeekableFileInputStream, or null if the preview is not found.
     * @throws SQLException
     * @throws IOException
     */
    static SeekableFileInputStream consumePreviewToSeekableInputStream(PreviewRepository repo, PreviewKey key, String ext, boolean forceFile)
            throws SQLException, IOException {

        AtomicReference<SeekableFileInputStream> reference = new AtomicReference<>();

        repo.consumePreview(key, inputStream -> {
            reference.set(toSeekableInputStream(inputStream, ext, forceFile));
        });
        return reference.get();
    }

    /**
     * Converts a standard InputStream into a SeekableFileInputStream, using either
     * memory or a temporary file based on a threshold.
     */
    private static SeekableFileInputStream toSeekableInputStream(InputStream sourceStream, String ext, boolean forceFile) throws IOException {

        if (forceFile) {
            Path tempFile = Files.createTempFile(PREVIEW_FILENAME_PREFIX, "." + ext);
            tempFile.toFile().deleteOnExit();

            Files.copy(sourceStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            return new SeekableFileInputStream(tempFile);
        }

        // Read into byte[] up to threshold
        byte[] buffer = sourceStream.readNBytes(MEMORY_THRESHOLD + 1);

        if (buffer.length <= MEMORY_THRESHOLD) {
            // Small input → keep in memory
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(buffer));

        } else {
            // Large input → spill to temp file
            Path tempFile = Files.createTempFile(PREVIEW_FILENAME_PREFIX, "." + ext);
            tempFile.toFile().deleteOnExit();

            // Write the already read part
            Files.write(tempFile, buffer);

            // Write the rest of the stream
            try (OutputStream output = Files.newOutputStream(tempFile, StandardOpenOption.APPEND)) {
                sourceStream.transferTo(output);
            }

            return new SeekableFileInputStream(tempFile);
        }
    }
}