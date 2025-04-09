package iped.parsers.misc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ThumbcacheParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set("application/x-thumbcache");
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (TemporaryResources tmp = new TemporaryResources()) {

            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            parseThumbcacheFile(tis, xhtml, extractor);

        } finally {
            xhtml.endDocument();
        }
    }

    private void parseThumbcacheFile(InputStream stream, XHTMLContentHandler xhtml, EmbeddedDocumentExtractor extractor) throws IOException, SAXException, TikaException {

        ByteBuffer fileHeader = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        if (stream.readNBytes(fileHeader.array(), 0, fileHeader.capacity()) != fileHeader.capacity()) {
            throw new IOException("Premature EOF reached reading header.");
        }

        String signature = new String(fileHeader.array(), 0, 4);
        if (!"CMMM".equals(signature)) {
            throw new TikaException("Invalid header signature '" + signature + "' expected 'CMMM'.");
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

        String windowsVersion;
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

        byte[] buffer = formatVersion == 21 ? new byte[48] : new byte[56];

        while (stream.readNBytes(buffer, 0, buffer.length) == buffer.length) {
            ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            String entrySignature = new String(buffer, 0, 4);

            if (!"CMMM".equals(entrySignature)) {
                throw new TikaException("Invalid cache entry signature found '" + entrySignature + "' expected 'CMMM'.");
            }

            int i = formatVersion == 20 ? 8 : 0;
            int j = formatVersion == 21 ? 8 : 0;

            int entrySize = bb.getInt(4);
            long entryHash = bb.getLong(8);
            int identifierStringSize = bb.getInt(16 + i);
            int paddingSize = bb.getInt(20 + i);
            int dataSize = bb.getInt(24 + i);
            long dataChecksum = bb.getLong(40 - j);
            long headerChecksum = bb.getLong(48 - j);

            String identifierString = "null";
            if (identifierStringSize > 0) {
                byte[] identifierBytes = new byte[identifierStringSize];
                if (stream.readNBytes(identifierBytes, 0, identifierBytes.length) != identifierBytes.length) {
                    throw new IOException("Premature EOF reached reading identifier string.");
                }
                identifierString = new String(identifierBytes, StandardCharsets.UTF_16LE);
            }

            if (paddingSize > 0) {
                // safer skip
                stream.readNBytes(paddingSize);
            }

            if (dataSize > 0) {
                xhtml.startElement("pre");
                xhtml.characters("Entry hash                    : 0x" + Long.toHexString(entryHash) + "\n");
                xhtml.characters("Entry size                    : " + entrySize + "\n");
                xhtml.characters("Identifier string             : " + identifierString + "\n");
                xhtml.characters("Data size                     : " + dataSize + "\n");
                xhtml.characters("Data checksum                 : 0x" + Long.toHexString(dataChecksum) + "\n");
                xhtml.characters("Header checksum               : 0x" + Long.toHexString(headerChecksum) + "\n");
                xhtml.endElement("pre");

                byte[] imageData = new byte[dataSize];
                if (stream.readNBytes(imageData, 0, imageData.length) != imageData.length) {
                    throw new IOException("Premature EOF reached reading image data.");
                }

                Metadata imageMetadata = new Metadata();
                imageMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "thumb_" + Long.toHexString(entryHash));

                try (ByteArrayInputStream imageStream = new ByteArrayInputStream(imageData)) {
                    extractor.parseEmbedded(imageStream, xhtml, imageMetadata, true);
                }
            }
        }
    }

}
