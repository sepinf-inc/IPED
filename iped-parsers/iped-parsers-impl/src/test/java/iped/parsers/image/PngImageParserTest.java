package iped.parsers.image;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeMap;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.ImageParser;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

import iped.utils.ImageUtil;

public class PngImageParserTest {

    private static final int WIDTH = 64, HEIGHT = 48;

    @Test
    public void testValidPng() throws Exception {
        Metadata metadata = parse(new PngImageParser(), resource("test-files/test_lenaPng.png"));
        assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertNull(metadata.get(PngImageParser.METADATA_RECOVERED));
    }

    /**
     * Real partially overwritten PNG (842x244 RGB, #827): metadata chunks are
     * intact, image data goes bad at row 102, garbage after IDAT, no IEND.
     * 
     * Source: https://github.com/sepinf-inc/IPED/issues/827#issuecomment-970841663
     */
    private static final String DAMAGED_PNG = "test-files/test_damagedPng.png";

    @Test
    public void testDamagedPngFileStockParserFails() throws Exception {
        try {
            parse(new ImageParser(), resource(DAMAGED_PNG));
            fail("ImageParser was expected to fail on this file; if it no longer does, "
                    + "the PngImageParser fallback may not be needed anymore");
        } catch (TikaException e) {
            // expected
        }
    }

    @Test
    public void testDamagedPngFileMetadataRecovered() throws Exception {
        Metadata metadata = parse(new PngImageParser(), resource(DAMAGED_PNG));
        assertEquals("true", metadata.get(PngImageParser.METADATA_RECOVERED));
        assertEquals("842", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("244", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("width=842, height=244, bitDepth=8, colorType=RGB, compressionMethod=deflate, "
                + "filterMethod=adaptive, interlaceMethod=none", metadata.get("IHDR"));
        assertEquals("pixelsPerUnitXAxis=8661, pixelsPerUnitYAxis=8661, unitSpecifier=meter", metadata.get("pHYs"));
        assertEquals("whitePointX=31270, whitePointY=32900, redX=64000, redY=33000, greenX=30000, "
                + "greenY=60000, blueX=15000, blueY=6000", metadata.get("cHRM"));
        assertEquals("45455", metadata.get("gAMA"));
        assertEquals("Perceptual", metadata.get("sRGB"));
    }

    /**
     * Pixels don't need PngImageParser: ImageUtil reads into a preallocated image
     * and keeps the rows decoded before the error (same result as png-reader).
     */
    @Test
    public void testDamagedPngFilePartialThumbnail() throws Exception {
        BufferedImage image = ImageUtil.getSubSampledImage(new ByteArrayInputStream(resource(DAMAGED_PNG)), 1024);
        assertNotNull("partial image expected", image);
        assertEquals(842, image.getWidth());
        assertEquals(244, image.getHeight());
        int lastDecodedRow = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    lastDecodedRow = y;
                    break;
                }
            }
        }
        // rows 0-101 are decoded, the remaining ones are left black
        assertEquals(101, lastDecodedRow);
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream is = PngImageParserTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull("missing test resource " + name, is);
            return is.readAllBytes();
        }
    }

    @Test
    public void testSameMetadataAsImageParserOnValidPngs() throws Exception {
        for (byte[] png : validPngs()) {
            assertEquals(asMap(parse(new ImageParser(), png)), asMap(parse(new PngImageParser(), png)));
        }
    }

    @Test
    public void testGarbageAfterImageData() throws Exception {
        for (byte[] png : validPngs()) {
            // replaces the IEND chunk by garbage, like a partially overwritten file
            int iend = indexOfChunk(png, "IEND");
            byte[] damaged = Arrays.copyOf(png, iend + 64);
            Arrays.fill(damaged, iend, damaged.length, (byte) 0xFF);
            assertRecovered(png, damaged, true);
        }
    }

    @Test
    public void testTruncatedInImageData() throws Exception {
        for (byte[] png : validPngs()) {
            int idat = indexOfChunk(png, "IDAT");
            int idatLength = readInt(png, idat);
            assertRecovered(png, Arrays.copyOf(png, idat + 8 + idatLength / 2), true);
        }
    }

    @Test
    public void testTruncatedInMetadataChunk() throws Exception {
        for (byte[] png : validPngs()) {
            // cut in the middle of pHYs: only IHDR (and PLTE, if before) survives
            int phys = indexOfChunk(png, "pHYs");
            Metadata metadata = parse(new PngImageParser(), Arrays.copyOf(png, phys + 10));
            assertEquals("true", metadata.get(PngImageParser.METADATA_RECOVERED));
            assertEquals(String.valueOf(WIDTH), metadata.get(Metadata.IMAGE_WIDTH));
            assertEquals(String.valueOf(HEIGHT), metadata.get(Metadata.IMAGE_LENGTH));
            assertNull(metadata.get("pHYs"));
        }
    }

    @Test
    public void testNotAPng() throws Exception {
        try {
            parse(new PngImageParser(), "this is not a png file".getBytes(StandardCharsets.US_ASCII));
            fail("TikaException expected");
        } catch (TikaException e) {
            // expected, same behavior as ImageParser
        }
    }

    @Test
    public void testReadChunksBeforeImageData() throws Exception {
        byte[] png = validPngs()[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int end = PngImageParser.readChunksBeforeImageData(new ByteArrayInputStream(png), out);
        assertEquals(indexOfChunk(png, "IDAT"), end);
        // consumed bytes must be exactly the original ones, so they can be replayed
        assertArrayEquals(Arrays.copyOf(png, end + 8), out.toByteArray());
    }

    /**
     * Metadata recovered from the damaged file must be equal to the metadata of
     * the intact file (all chunks are before IDAT in the generated files), and the
     * stock parser must fail, otherwise the fallback is not being exercised.
     */
    private static void assertRecovered(byte[] intact, byte[] damaged, boolean stockMustFail) throws Exception {
        boolean stockFailed = false;
        try {
            parse(new ImageParser(), damaged);
        } catch (TikaException | RuntimeException e) {
            stockFailed = true;
        }
        if (stockMustFail) {
            assertTrue("ImageParser should fail on this damaged PNG", stockFailed);
        }
        Metadata recovered = parse(new PngImageParser(), damaged);
        assertEquals("true", recovered.get(PngImageParser.METADATA_RECOVERED));
        recovered.remove(PngImageParser.METADATA_RECOVERED);
        assertEquals(asMap(parse(new ImageParser(), intact)), asMap(recovered));
    }

    private static Metadata parse(Parser parser, byte[] data) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        parser.parse(new ByteArrayInputStream(data), new DefaultHandler(), metadata, new ParseContext());
        return metadata;
    }

    private static TreeMap<String, String> asMap(Metadata metadata) {
        TreeMap<String, String> map = new TreeMap<>();
        for (String name : metadata.names()) {
            map.put(name, String.join("|", metadata.getValues(name)));
        }
        return map;
    }

    /** Truecolor, palette, gray and interlaced truecolor PNGs, all with a pHYs chunk. */
    private static byte[][] validPngs() throws IOException {
        return new byte[][] { png(BufferedImage.TYPE_3BYTE_BGR, false), png(BufferedImage.TYPE_BYTE_INDEXED, false),
                png(BufferedImage.TYPE_BYTE_GRAY, false), png(BufferedImage.TYPE_3BYTE_BGR, true) };
    }

    private static byte[] png(int imageType, boolean interlaced) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, imageType);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setRGB(x, y, (x * 4) << 16 | (y * 5) << 8 | ((x * y) & 0xFF));
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (interlaced) {
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        }
        IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), param);
        IIOMetadataNode phys = new IIOMetadataNode("pHYs");
        phys.setAttribute("pixelsPerUnitXAxis", "3780");
        phys.setAttribute("pixelsPerUnitYAxis", "3780");
        phys.setAttribute("unitSpecifier", "meter");
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
        root.appendChild(phys);
        metadata.mergeTree("javax_imageio_png_1.0", root);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, metadata), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static int indexOfChunk(byte[] png, String type) {
        int pos = 8;
        while (pos + 8 <= png.length) {
            if (new String(png, pos + 4, 4, StandardCharsets.US_ASCII).equals(type)) {
                return pos;
            }
            pos += 12 + readInt(png, pos);
        }
        throw new IllegalArgumentException("chunk not found: " + type);
    }

    private static int readInt(byte[] b, int off) {
        return (b[off] & 0xFF) << 24 | (b[off + 1] & 0xFF) << 16 | (b[off + 2] & 0xFF) << 8 | (b[off + 3] & 0xFF);
    }
}
