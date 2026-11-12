package iped.parsers.image;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.image.ImageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Extracts PNG metadata with Tika's {@link ImageParser}, recovering it from
 * damaged files (e.g. partially overwritten or truncated).
 * <p>
 * Since JDK 10, the JDK PNG reader needs to find the IEND chunk to return image
 * metadata, so it fails on files whose image data is damaged, even if all
 * metadata chunks before the image data are intact. When that happens, this
 * parser parses again a rebuilt PNG made of the original signature and the
 * chunks before the first IDAT, followed by an empty IDAT and an IEND chunk.
 * Metadata chunks after the image data are lost, as with the former
 * png-reader plugin (#827), which this parser replaces (#1522).
 */
public class PngImageParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(PngImageParser.class);

    public static final MediaType PNG_MIME = MediaType.image("png");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PNG_MIME);

    /** Set to "true" when the metadata was recovered from a damaged PNG. */
    public static final String METADATA_RECOVERED = "pngMetadataRecoveredFromDamagedFile";

    /**
     * Max bytes buffered before the first IDAT chunk. Metadata chunks are usually
     * small, this just bounds memory usage on hostile or garbage input.
     */
    static final int MAX_METADATA_BYTES = 8 << 20;

    static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

    private static final int IDAT = 0x49444154;
    private static final int IEND = 0x49454E44;

    /** Empty IDAT chunk: the JDK reader needs one to locate the image data. */
    private static final byte[] EMPTY_IDAT = { 0, 0, 0, 0, 'I', 'D', 'A', 'T', 0x35, (byte) 0xAF, 0x06, 0x1E };
    private static final byte[] EMPTY_IEND = { 0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };

    private final ImageParser imageParser = new ImageParser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        Buffer prefix = new Buffer();
        int metadataEnd = readChunksBeforeImageData(stream, prefix);

        try {
            imageParser.parse(prefix.followedBy(stream), handler, metadata, context);

        } catch (TikaException | RuntimeException e) {
            if (metadataEnd < 0) {
                throw e;
            }
            try {
                imageParser.parse(prefix.metadataOnlyPng(metadataEnd), handler, metadata, context);
            } catch (TikaException | RuntimeException e2) {
                e.addSuppressed(e2);
                throw e;
            }
            metadata.set(METADATA_RECOVERED, Boolean.TRUE.toString());
            logger.debug("PNG metadata recovered from damaged file: {}", e.toString());
        }
    }

    /**
     * Copies to {@code out} everything read from {@code in}: the signature and
     * the chunks before the first IDAT (or IEND), plus the header of that chunk.
     *
     * @return the offset where the image data starts, i.e. the end of the
     *         complete metadata chunks, or -1 if the input is not a PNG or the
     *         metadata chunks are too big to buffer.
     */
    static int readChunksBeforeImageData(InputStream in, ByteArrayOutputStream out) throws IOException {
        byte[] header = new byte[8];
        int n = in.readNBytes(header, 0, 8);
        out.write(header, 0, n);
        if (n < 8 || !Arrays.equals(header, SIGNATURE)) {
            return -1;
        }
        int pos = 8;
        while (true) {
            n = in.readNBytes(header, 0, 8);
            out.write(header, 0, n);
            if (n < 8) {
                // truncated before image data, the complete chunks are still useful
                return pos;
            }
            long length = readUInt(header, 0);
            int type = (int) readUInt(header, 4);
            if (type == IDAT || type == IEND) {
                return pos;
            }
            long chunkEnd = pos + 12L + length;
            if (chunkEnd > MAX_METADATA_BYTES) {
                return -1;
            }
            int dataAndCrc = (int) length + 4;
            byte[] chunk = in.readNBytes(dataAndCrc);
            out.write(chunk, 0, chunk.length);
            if (chunk.length < dataAndCrc) {
                // truncated in the middle of this chunk, drop it
                return pos;
            }
            pos = (int) chunkEnd;
        }
    }

    private static long readUInt(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16) | ((b[off + 2] & 0xFFL) << 8)
                | (b[off + 3] & 0xFFL);
    }

    /** Exposes the internal buffer to avoid copying it. */
    private static class Buffer extends ByteArrayOutputStream {

        Buffer() {
            super(1024);
        }

        /**
         * Returns the buffered bytes followed by the rest of {@code rest}. Unlike
         * SequenceInputStream, it never closes {@code rest}, which belongs to the
         * caller.
         */
        InputStream followedBy(InputStream rest) {
            byte[] data = buf;
            int size = count;
            return new InputStream() {
                private int pos;

                @Override
                public int read() throws IOException {
                    return pos < size ? data[pos++] & 0xFF : rest.read();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (len == 0) {
                        return 0;
                    }
                    if (pos < size) {
                        int n = Math.min(len, size - pos);
                        System.arraycopy(data, pos, b, off, n);
                        pos += n;
                        return n;
                    }
                    return rest.read(b, off, len);
                }

                @Override
                public int available() throws IOException {
                    return pos < size ? size - pos : rest.available();
                }
            };
        }

        InputStream metadataOnlyPng(int metadataEnd) {
            byte[] png = Arrays.copyOf(buf, metadataEnd + EMPTY_IDAT.length + EMPTY_IEND.length);
            System.arraycopy(EMPTY_IDAT, 0, png, metadataEnd, EMPTY_IDAT.length);
            System.arraycopy(EMPTY_IEND, 0, png, metadataEnd + EMPTY_IDAT.length, EMPTY_IEND.length);
            return new ByteArrayInputStream(png);
        }
    }
}
