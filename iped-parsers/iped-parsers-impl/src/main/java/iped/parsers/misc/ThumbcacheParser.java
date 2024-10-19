package iped.parsers.misc;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;

public class ThumbcacheParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set("application/x-thumbcache");
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (TemporaryResources tmp = new TemporaryResources();
             TikaInputStream tis = TikaInputStream.get(stream, tmp)) {

            File file = tis.getFile();
            try (FileInputStream fis = new FileInputStream(file)) {
                parseThumbcacheFile(fis, xhtml);
            }

        } catch (IOException e) {
            xhtml.characters("Error processing thumbcache file: " + e.getMessage() + "\n");
        } finally {
            xhtml.endDocument();
        }
    }

    private void parseThumbcacheFile(InputStream stream, XHTMLContentHandler xhtml) throws IOException, SAXException {
        byte[] buffer = new byte[80]; // Buffer para a leitura de cada entrada

        while (stream.read(buffer) == buffer.length) {
            ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            int size = bb.getInt();
            long entryHash = bb.getLong();
            int identifierStringSize = bb.getInt();
            int paddingSize = bb.getInt();
            int dataSize = bb.getInt();
            int unknown1 = bb.getInt();
            long dataChecksum = bb.getInt() & 0xFFFFFFFFL;
            long headerChecksum = bb.getLong();

            // Exibir os detalhes do cache
            xhtml.startElement("pre");
            xhtml.characters("size                           : " + size + "\n");
            xhtml.characters("entry hash                     : 0x" + Long.toHexString(entryHash) + "\n");
            xhtml.characters("identifier string size         : " + identifierStringSize + "\n");
            xhtml.characters("padding size                   : " + paddingSize + "\n");
            xhtml.characters("data size                      : " + dataSize + "\n");
            xhtml.characters("unknown1                       : 0x" + Integer.toHexString(unknown1) + "\n");
            xhtml.characters("data checksum                  : 0x" + Long.toHexString(dataChecksum) + "\n");
            xhtml.characters("header checksum                : 0x" + Long.toHexString(headerChecksum) + "\n");
            xhtml.characters("\nidentifier string              : " + Long.toHexString(entryHash) + "\n");
            xhtml.endElement("pre");
        }
    }
}
