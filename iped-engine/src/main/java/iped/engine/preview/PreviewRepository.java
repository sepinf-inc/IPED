package iped.engine.preview;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import com.zaxxer.hikari.HikariDataSource;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.task.HashTask;
import iped.utils.HashValue;

/**
 * Handles storage and retrieval of gzipped item previews in an H2 database.
 * Instances of this class are managed by {@link PreviewRepositoryManager}.
 */
public class PreviewRepository implements Closeable {

    private static final String INSERT_DATA_SQL = "MERGE INTO previews (id, data) KEY(id) VALUES (?, ?)";
    private static final String SELECT_DATA_SQL = "SELECT data FROM previews WHERE id=?";

    private final HikariDataSource dataSource;

    /**
     * Package-private constructor. Instances should be obtained from {@link PreviewRepositoryManager}.
     * @param dataSource The configured data source for this repository.
     */
    PreviewRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Closes the underlying data source for this repository.
     */
    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * Stores a preview for an item. The provided stream is expected to be Gzipped.
     *
     * @param evidence The item to store the preview for.
     * @param gzippedValueStream An InputStream containing the *Gzipped* preview data.
     * @throws SQLException if a database error occurs.
     */
    public void storePreview(IItem evidence, InputStream gzippedValueStream) throws SQLException {
        byte[] key = getItemKey(evidence);

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(INSERT_DATA_SQL)) {
            pstmt.setBytes(1, key);
            pstmt.setBinaryStream(2, gzippedValueStream);
            pstmt.executeUpdate();
        }
    }

    @FunctionalInterface
    public interface InputStreamConsumer {
        void consume(InputStream is) throws IOException;
    }

    /**
     * Consumes a preview for a given key, decompressing it by default.
     *
     * @param key The binary key of the preview.
     * @param consumer The consumer to process the decompressed stream.
     * @return true if the preview was found and consumed, false otherwise.
     * @throws SQLException if a database error occurs.
     * @throws IOException if an I/O error occurs during streaming.
     */
    public boolean consumePreview(byte[] key, InputStreamConsumer consumer) throws SQLException, IOException {
        return consumePreview(key, true, consumer); // Default to decompress
    }

    /**
     * Consumes a preview for a given item, with an option to disable decompression.
     *
     * @param evidence The item whose preview is to be consumed.
     * @param decompress If true, the stream is decompressed (Gzip); if false, the raw stored stream is provided.
     * @param consumer The consumer to process the stream.
     * @return true if the preview was found and consumed, false otherwise.
     * @throws SQLException if a database error occurs.
     * @throws IOException if an I/O error occurs during streaming.
     */
    public boolean consumePreview(IItem evidence, boolean decompress, InputStreamConsumer consumer) throws SQLException, IOException {
        byte[] key = getItemKey(evidence);
        return consumePreview(key, decompress, consumer);
    }

    /**
     * Consumes a preview for a given key, with an option to disable decompression.
     *
     * @param key The binary key of the preview.
     * @param consumer The consumer to process the stream.
     * @param decompress If true, the stream is decompressed (Gzip); if false, the raw stored stream is provided.
     * @return true if the preview was found and consumed, false otherwise.
     * @throws SQLException if a database error occurs.
     * @throws IOException if an I/O error occurs during streaming.
     */
    public boolean consumePreview(byte[] key, boolean decompress, InputStreamConsumer consumer) throws SQLException, IOException {
        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(SELECT_DATA_SQL)) {
            pstmt.setBytes(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    try (InputStream dbInputStream = rs.getBinaryStream(1)) {
                        if (decompress) {
                            // Decompress the data stream from the DB
                            try (InputStream decompressedStream = new GzipCompressorInputStream(dbInputStream)) {
                                consumer.consume(decompressedStream);
                            }
                        } else {
                            // Pass the raw (gzipped) stream directly to the consumer
                            consumer.consume(dbInputStream);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Generates the primary key for storing/retrieving an item's preview.
     * Prioritizes MD5 hash, falls back to item ID.
     *
     * @param evidence The item to generate a key for.
     * @return A 16-byte key (if MD5) or 4-byte key (if ID).
     */
    static byte[] getItemKey(IItemReader evidence) {
        String hashString = (String) evidence.getExtraAttribute(HashTask.HASH.MD5.toString());
        if (hashString != null) {
            return new HashValue(hashString).getBytes();
        }

        // Fallback to item ID
        return ByteBuffer.allocate(Integer.BYTES).putInt(evidence.getId()).array();
    }

}