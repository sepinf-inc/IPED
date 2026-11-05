package iped.engine.task;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.*;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.gagravarr.tika.SpeexParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;

public class SpxDetectionTest {
    private MediaType detect(byte[] bytes, String name) throws Exception {
        Detector detector = TikaConfig.getDefaultConfig().getDetector();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        try (TikaInputStream stream = TikaInputStream.get(new ByteArrayInputStream(bytes))) {
            MediaType original = detector.detect(stream, metadata).getBaseType();
            MediaType refined = SignatureTask.refineSpeexType(detector, stream, original);
            assertEquals(name, metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
            assertEquals(bytes.length == 0 ? -1 : bytes[0] & 255, stream.read());
            return refined;
        }
    }
    @Test public void binarySpxIsNotSpeex() throws Exception {
        assertEquals(MediaType.OCTET_STREAM, detect(new byte[]{1,2,3,4,5,6,7,8}, "synthetic.spx"));
    }
    @Test public void textSpxKeepsContentType() throws Exception {
        assertEquals(MediaType.TEXT_PLAIN, detect("Synthetic plain text.".getBytes(StandardCharsets.UTF_8), "synthetic.spx"));
    }
    @Test public void realSpeexStillParses() throws Exception {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/synthetic-speex.spx")) {
            assertNotNull(in); bytes = in.readAllBytes();
        }
        assertEquals(MediaType.audio("speex"), detect(bytes, "synthetic.spx"));
        assertEquals(MediaType.audio("speex"), detect(bytes, "synthetic.ogg"));
        Metadata metadata = new Metadata();
        new SpeexParser().parse(new ByteArrayInputStream(bytes), new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("audio/speex", metadata.get(Metadata.CONTENT_TYPE));
    }
    @Test public void unrelatedTypeIsUnchanged() throws Exception {
        assertEquals(MediaType.TEXT_PLAIN, detect("Synthetic text".getBytes(StandardCharsets.UTF_8), "synthetic.txt"));
    }
}
