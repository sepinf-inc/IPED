package iped.parsers.misc;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.DocumentNode;
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

        TemporaryResources tmp = new TemporaryResources();
        try (TikaInputStream tis = TikaInputStream.get(stream, tmp)) {
            File file = tis.getFile();
            try (POIFSFileSystem poiFS = new POIFSFileSystem(file)) {
                recurseDir(poiFS.getRoot(), xhtml);
            }
        } finally {
            xhtml.endDocument();
            tmp.close();
        }
    }

    private void recurseDir(DirectoryNode dir, XHTMLContentHandler xhtml) throws SAXException {
        for (Entry entry : dir) {
            if (entry instanceof DirectoryNode) {
                recurseDir((DirectoryNode) entry, xhtml);
            } else {
                try (DocumentInputStream docStream = new DocumentInputStream((DocumentNode) entry)) {
                    byte[] buffer = new byte[80];
                    int bytesRead = docStream.read(buffer);
                    if (bytesRead != buffer.length) {
                        continue;
                    }

                    ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

                    int size = bb.getInt();
                    long entryHash = bb.getLong();
                    int identifierStringSize = bb.getInt();
                    int paddingSize = bb.getInt();
                    int dataSize = bb.getInt();
                    int unknown1 = bb.getInt();
                    long dataChecksum = bb.getInt() & 0xFFFFFFFFL;
                    long headerChecksum = bb.getLong();

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

                } catch (IOException e) {
                    xhtml.characters("Error processing entry: " + entry.getName() + "\n");
                }
            }
        }
    }
}
