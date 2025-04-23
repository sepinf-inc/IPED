package iped.parsers.video;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.mp4.TikaMp4BoxHandler;
import org.apache.tika.sax.XHTMLContentHandler;

import com.drew.imaging.mp4.Mp4Reader;
import com.drew.metadata.mp4.Mp4BoxHandler;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4SoundDirectory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;

import iped.parsers.util.IgnoreContentHandler;
import iped.utils.SimpleInputStreamFactory;

public class MP4Detector implements Detector {

    private static final long serialVersionUID = 1L;

    private static final String[] HEADERS = { "ftypmp41", "ftypmp42", "ftypiso" };

    private static byte[][] headers;

    static {
        headers = new byte[HEADERS.length][];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = HEADERS[i].getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    @Override
    public MediaType detect(InputStream stream, Metadata metadata) throws IOException {

        if (stream == null) {
            return MediaType.OCTET_STREAM;
        }

        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            byte[] prefix = new byte[12];
            int len = tis.peek(prefix);
            if (len < prefix.length)
                return MediaType.OCTET_STREAM;

            boolean match = true;
            for (byte[] header : headers) {
                match = true;
                for (int i = 0; i < header.length; i++) {
                    if (prefix[i + 4] != header[i]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    break;
                }
            }
            if (!match) {
                return MediaType.OCTET_STREAM;
            }

            XHTMLContentHandler xhtml = new XHTMLContentHandler(new IgnoreContentHandler(), metadata);
            com.drew.metadata.Metadata mp4Metadata = new com.drew.metadata.Metadata();
            Mp4BoxHandler boxHandler = new TikaMp4BoxHandler(mp4Metadata, metadata, xhtml);
            Mp4Reader.extract(tis, boxHandler);

            boolean hasErrors = false;
            boolean hasAudio = false;
            for (Mp4Directory mp4Directory : mp4Metadata.getDirectoriesOfType(Mp4Directory.class)) {
                if (mp4Directory instanceof Mp4VideoDirectory) {
                    return MediaType.video("mp4");
                }
                if (mp4Directory instanceof Mp4SoundDirectory) {
                    hasAudio = true;
                }
                if (!hasErrors && mp4Directory.hasErrors()) {
                    hasErrors = true;
                }
            }

            if (hasErrors) {
                if (tis.getOpenContainer() instanceof SimpleInputStreamFactory) {
                    try (InputStream is = new BufferedInputStream(((SimpleInputStreamFactory) tis.getOpenContainer()).getInputStream())) {
                        if (hasVideoBlock(is)) {
                            return MediaType.video("mp4");
                        }
                    }
                }
            }
            if (hasAudio) {
                return MediaType.audio("mp4");
            }

            // fallback to the generic mp4 container mime
            return MediaType.application("mp4");
        }
    }

    private static boolean hasVideoBlock(InputStream is) throws IOException {
        final byte[] buffer = new byte[4096];
        final char[] sig = "hdlrvide".toCharArray();
        final int maxGap = 32;
        final int middle = 4;

        // Matches "hdlr[0 to 32 bytes]vide"
        int match = 0;
        int gap = 0;
        while (true) {
            int n = is.read(buffer);
            if (n < 0) {
                break;
            }
            for (int i = 0; i < n; i++) {
                byte b = buffer[i];
                if (b == sig[match]) {
                    gap = 0;
                    if (++match == sig.length) {
                        return true;
                    }
                } else {
                    if (match != middle || ++gap > maxGap) {
                        match = 0;
                    }
                }
            }
        }
        return false;
    }
}
