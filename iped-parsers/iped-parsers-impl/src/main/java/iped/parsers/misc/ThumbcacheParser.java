package iped.parsers.misc;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ThumbcacheParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set("application/x-thumbcache");
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (TemporaryResources tmp = new TemporaryResources();
             TikaInputStream tis = TikaInputStream.get(stream, tmp)) {

            File file = tis.getFile();
            String outputDir = file.getParent();
            String imagesDir = outputDir + File.separator + "images";

            File imagesDirectory = new File(imagesDir);
            if (!imagesDirectory.exists()) {
                imagesDirectory.mkdirs();
            }

            byte[] imageData = null;
            String fileName = null;

            try (FileInputStream fis = new FileInputStream(file)) {
                imageData = parseThumbcacheFile(fis, xhtml, imagesDir);
                fileName = getLastSavedFileName(imagesDir);
            }

            if (extractor.shouldParseEmbedded(metadata) && imageData != null && fileName != null) {
                Metadata imageMetadata = new Metadata();
                imageMetadata.set(Metadata.CONTENT_TYPE, "image/" + detectImageExtension(imageData));
                imageMetadata.set("resourceName", fileName);

                try (ByteArrayInputStream imageStream = new ByteArrayInputStream(imageData)) {
                    extractor.parseEmbedded(imageStream, xhtml, imageMetadata, true);
                }
            }

        } catch (IOException e) {
            xhtml.characters("Error processing thumbcache file: " + e.getMessage() + "\n");
        } finally {
            xhtml.endDocument();
        }
    }

    private byte[] parseThumbcacheFile(InputStream stream, XHTMLContentHandler xhtml, String imagesDir) throws IOException, SAXException {
        byte[] buffer = new byte[56];
        byte[] imageData = null;

        ByteBuffer fileHeader = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        stream.read(fileHeader.array());

        String signature = new String(fileHeader.array(), 0, 4);
        if (!"CMMM".equals(signature)) {
            xhtml.characters("Error processing cache file: Invalid header signature; expected 'CMMM'.\n");
            return null;
        }

        int formatVersion = fileHeader.getInt(4);
        int cacheType = fileHeader.getInt(8);
        int firstCacheEntryOffset = fileHeader.getInt(12);
        int firstAvailableCacheEntryOffset = fileHeader.getInt(16);
        int numberOfCacheEntries = fileHeader.getInt(20);

        xhtml.startElement("pre");
        xhtml.characters("Cache file format version      : " + formatVersion + "\n");
        xhtml.characters("Cache type                    : " + cacheType + "\n");
        xhtml.characters("Offset to first cache entry    : " + firstCacheEntryOffset + "\n");
        xhtml.characters("Offset to first available entry: " + firstAvailableCacheEntryOffset + "\n");
        xhtml.characters("Number of cache entries        : " + numberOfCacheEntries + "\n");

        String windowsVersion = "";
        switch (formatVersion) {
            case 20:
                windowsVersion = "Windows Vista";
                break;
            case 21:
                windowsVersion = "Windows 7";
                break;
            case 30:
                windowsVersion = "Windows 8.0";
                break;
            case 31:
                windowsVersion = "Windows 8.1";
                break;
            case 32:
                windowsVersion = "Windows 10 / 11";
                break;
            default:
                windowsVersion = "Unknown version";
        }
        xhtml.characters("Seen on Windows version        : " + windowsVersion + "\n");
        xhtml.endElement("pre");

        while (stream.read(buffer) == buffer.length) {
            ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            String entrySignature = new String(buffer, 0, 4);

            if (!"CMMM".equals(entrySignature)) {
                continue;
            }

            int entrySize = bb.getInt(4);
            long entryHash = bb.getLong(8);
            int identifierStringSize = bb.getInt(16);
            int paddingSize = bb.getInt(20);
            int dataSize = bb.getInt(24);
            long dataChecksum = bb.getLong(40);
            long headerChecksum = bb.getLong(48);

            if (dataSize > 0 && identifierStringSize > 0) {
                byte[] identifierBytes = new byte[identifierStringSize];
                stream.read(identifierBytes);
                String identifierString = new String(identifierBytes, StandardCharsets.UTF_16LE);

                xhtml.startElement("pre");
                xhtml.characters("Entry hash                    : 0x" + Long.toHexString(entryHash) + "\n");
                xhtml.characters("Entry size                    : " + entrySize + "\n");
                xhtml.characters("Identifier string             : " + identifierString + "\n");
                xhtml.characters("Data size                     : " + dataSize + "\n");
                xhtml.characters("Data checksum                 : 0x" + Long.toHexString(dataChecksum) + "\n");
                xhtml.characters("Header checksum               : 0x" + Long.toHexString(headerChecksum) + "\n");
                xhtml.endElement("pre");
            }

            if (paddingSize > 0) {
                stream.skip(paddingSize);
            }

            if (dataSize > 0) {
                imageData = new byte[dataSize];
                stream.read(imageData);

                String ext = detectImageExtension(imageData);
                String fileName = "thumb_" + Long.toHexString(entryHash) + "." + ext;

                saveImage(imagesDir, fileName, imageData);
            }
        }

        return imageData;
    }

    private String detectImageExtension(byte[] data) {
        if (data.length >= 4) {
            if (data[0] == (byte) 0x42 && data[1] == (byte) 0x4D) {
                return "bmp";
            } else if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
                return "jpg";
            } else if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50) {
                return "png";
            }
        }
        return "img";
    }

    private void saveImage(String imagesDir, String fileName, byte[] imageData) {
        try (FileOutputStream fos = new FileOutputStream(imagesDir + File.separator + fileName)) {
            fos.write(imageData);
            System.out.println("Saved image: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getLastSavedFileName(String imagesDir) {
        File directory = new File(imagesDir);
        File[] files = directory.listFiles((dir, name) -> name.startsWith("thumb_"));

        if (files != null && files.length > 0) {
            return files[files.length - 1].getName();
        }
        return null;
    }
}
