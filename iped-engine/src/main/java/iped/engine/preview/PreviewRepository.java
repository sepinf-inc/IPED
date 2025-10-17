package iped.engine.preview;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import iped.data.IItem;
import iped.utils.SeekableFileInputStream;

/**
 * Handles storage and retrieval of item previews in an H2 database.
 * Instances of this class are managed by {@link PreviewRepositoryManager}.
 */
public class PreviewRepository {

    private static final Logger logger = LoggerFactory.getLogger(PreviewRepository.class);

    private static final String INSERT_DATA_SQL = "MERGE INTO previews (id, data) VALUES (?, ?)";
    private static final String SELECT_DATA_SQL = "SELECT data FROM previews WHERE id=?";
    private static final String CHECK_EXISTS_SQL = "SELECT 1 FROM previews WHERE id=?";

    private final HikariDataSource dataSource;
    private final boolean readOnly;

    /**
     * Package-private constructor. Instances should be obtained from {@link PreviewRepositoryManager}.
     * @param dataSource The configured data source for this repository.
     */
    PreviewRepository(HikariDataSource dataSource, boolean readOnly) {
        this.dataSource = dataSource;
        this.readOnly = readOnly;
    }

    /**
     * Closes the underlying data source for this repository.
     */
    public void close() {
        if (dataSource != null) {
            if (!readOnly) {
                try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                    stmt.execute("SHUTDOWN COMPACT");
                } catch (SQLException e) {
                    // After a shutdown command, H2 throws an exception because the connection is closed.
                    // This is expected behavior. The error code for a successful shutdown is 90121.
                    if ("90121".equals(e.getSQLState())) {
                        logger.info("Database has been shut down and compacted successfully.");
                    } else {
                        logger.error("An error occurred during shutdown: " + e.getSQLState(), e);
                    }
                }
            }
            dataSource.close();
        }
    }

    /**
     * Checks if a preview exists for the given item.
     *
     * @param evidence The item to check.
     * @return true if a preview exists, false otherwise.
     * @throws SQLException if a database error occurs.
     */
    public boolean previewExists(IItem evidence) throws SQLException {
        PreviewKey key = PreviewKey.create(evidence);
        return previewExists(key);
    }

    /**
     * Checks if a preview exists for the given key.
     *
     * @param key The key to check.
     * @return true if a preview exists, false otherwise.
     * @throws SQLException if a database error occurs.
     */
    public boolean previewExists(PreviewKey key) throws SQLException {
        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(CHECK_EXISTS_SQL)) {
            pstmt.setBytes(1, key.getBytes());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next(); // True if a row was found
            }
        }
    }

    /**
     * Stores a preview for an item from a stream that is *already compressed* with DEFLATE.
     * This method is suitable for high-performance streaming when the caller produces a compressed stream directly.
     *
     * @param evidence The item to store the preview for.
     * @param compressedValueStream An InputStream containing the *DEFLATE compressed* preview data.
     * @throws SQLException if a database error occurs.
     * @throws IOException
     */
    public void storeCompressedPreview(IItem evidence, InputStream compressedValueStream) throws SQLException, IOException {
        PreviewKey key = PreviewKey.create(evidence);

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(INSERT_DATA_SQL)) {
            pstmt.setBytes(1, key.getBytes());
            pstmt.setBinaryStream(2, compressedValueStream);
            pstmt.executeUpdate();
        }
    }

    /**
     * Stores a preview for an item from a *raw, uncompressed* stream.
     * This method compresses the content on-the-fly using DEFLATE in a
     * fully streaming, thread-less, constant-memory way.
     *
     * @param evidence The item to store the preview for.
     * @param rawValueStream An InputStream containing the *raw, uncompressed* preview data.
     * @throws SQLException if a database error occurs.
     * @throws IOException if an I/O error occurs during compression.
     */
    public void storeRawPreview(IItem evidence, InputStream rawValueStream) throws SQLException, IOException {
        PreviewKey key = PreviewKey.create(evidence);

        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_DATA_SQL);
                InputStream streamToStore = new DeflaterInputStream(rawValueStream, deflater)) {

            pstmt.setBytes(1, key.getBytes());
            pstmt.setBinaryStream(2, streamToStore);

            // executeUpdate() triggers the entire pull-based stream chain
            pstmt.executeUpdate();

        } finally {
            deflater.end();
        }
    }

    @FunctionalInterface
    public interface InputStreamConsumer {
        void consume(InputStream is) throws IOException, SQLException;
    }

    /**
     * Consumes a preview for a given key, decompressing it by default.
     *
     * @param key The key of the preview.
     * @param consumer The consumer to process the decompressed stream.
     * @return true if the preview was found and consumed, false otherwise.
     * @throws SQLException if a database error occurs.
     * @throws IOException if an I/O error occurs during streaming.
     */
    public boolean consumePreview(PreviewKey key, InputStreamConsumer consumer) throws SQLException, IOException {
        return consumePreview(key, true, consumer); // Default to decompress
    }

    /**
     * Consumes a preview for a given item, decompressing it by default.
     *
     * @param evidence The item whose preview is to be consumed.
     * @param consumer The consumer to process the decompressed stream.
     * @return true if the preview was found and consumed, false otherwise.
     * @throws SQLException if a database error occurs.
     * @throws IOException if an I/O error occurs during streaming.
     */
    public boolean consumePreview(IItem evidence, InputStreamConsumer consumer) throws SQLException, IOException {
        return consumePreview(evidence, true, consumer); // Default to decompress
    }

    /**
     * Consumes a preview for a given item, with an option to disable decompression.
     *
     * @param evidence The item whose preview is to be consumed.
     * @param decompress If true, the stream will be decompressed (DEFLATE); if false, the raw stored stream is provided.
     * @param consumer The consumer to process the stream.
     * @return true if the preview was found and consumed, false otherwise.
     * @throws SQLException if a database error occurs.
     * @throws IOException if an I/O error occurs during streaming.
     */
    public boolean consumePreview(IItem evidence, boolean decompress, InputStreamConsumer consumer) throws SQLException, IOException {
        PreviewKey key = PreviewKey.create(evidence);
        return consumePreview(key, decompress, consumer);
    }

    /**
    * Consumes a preview for a given key, with an option to disable decompression.
    *
    * @param key The key of the preview.
    * @param consumer The consumer to process the stream.
    * @param decompress If true, the stream will be decompressed (DEFLATE); if false, the raw stored stream is provided.
    * @return true if the preview was found and consumed, false otherwise.
    * @throws SQLException if a database error occurs.
    * @throws IOException if an I/O error occurs during streaming.
    */
   public boolean consumePreview(PreviewKey key, boolean decompress, InputStreamConsumer consumer) throws SQLException, IOException {
       try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(SELECT_DATA_SQL)) {
           pstmt.setBytes(1, key.getBytes());
           try (ResultSet rs = pstmt.executeQuery()) {
               if (rs.next()) {
                   try (InputStream dbInputStream = rs.getBinaryStream(1)) {
                       if (decompress) {
                           // Decompress the data stream from the DB using DEFLATE
                           try (InputStream decompressedStream = new InflaterInputStream(dbInputStream)) {
                               consumer.consume(decompressedStream);
                           }
                       } else {
                           // Pass the raw (compressed) stream directly to the consumer
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
    * Consumes a preview for an IItem from the repository and converts it to a SeekableInputStream.
    *
    * @param item The item whose preview is to be consumed.
    * @param forceFile If true, always use a temp file instead of memory.
    * @return A SeekableFileInputStream, or null if the preview is not found.
    * @throws SQLException
    * @throws IOException
    */
    public SeekableFileInputStream readPreview(IItem item, boolean forceFile) throws SQLException, IOException {
        PreviewKey key = PreviewKey.create(item);
        return PreviewInputStreamFactory.consumePreviewToSeekableInputStream(this, key, item.getPreviewExt(), forceFile);
    }
}