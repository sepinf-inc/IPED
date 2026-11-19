package iped.parsers.image;

import static org.junit.Assert.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Proxy;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import iped.data.IItemReader;

public class TiffPageParserStreamTest {
    private byte[] createTiff(int pages) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("TIFF").next();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < pages; i++) {
                writer.writeToSequence(new IIOImage(new BufferedImage(16, 16,
                        BufferedImage.TYPE_INT_RGB), null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private Metadata parse(byte[] bytes, ParseContext context) throws Exception {
        Metadata metadata = new Metadata();
        new TiffPageParser().parse(new ByteArrayInputStream(bytes), new BodyContentHandler(), metadata, context);
        return metadata;
    }

    @Test public void countsStandaloneTiffWithoutItemContext() throws Exception {
        assertEquals("1", parse(createTiff(1), new ParseContext()).get(TiffPageParser.propNumPages));
    }

    @Test public void countsMultiplePages() throws Exception {
        assertEquals("2", parse(createTiff(2), new ParseContext()).get(TiffPageParser.propNumPages));
    }

    @Test public void doesNotReadTheContainingDocument() throws Exception {
        ParseContext context = new ParseContext();
        IItemReader parent = (IItemReader) Proxy.newProxyInstance(IItemReader.class.getClassLoader(),
                new Class<?>[]{IItemReader.class}, (proxy, method, args) -> {
                    throw new AssertionError("Must read TIFF stream, not parent item: " + method.getName());
                });
        context.set(IItemReader.class, parent);
        assertEquals("1", parse(createTiff(1), context).get(TiffPageParser.propNumPages));
    }

    @Test(expected = TikaException.class)
    public void invalidInputStillFails() throws Exception {
        parse(new byte[]{1, 2, 3, 4}, new ParseContext());
    }
}
