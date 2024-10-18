package iped.parsers.misc;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.DocumentNode;
import org.apache.tika.exception.TikaException;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

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

        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File file = tis.getFile();
        POIFSFileSystem poiFS = new POIFSFileSystem(file);

        // Placeholder for the recursive method that will navigate through directories and process files extracting metadata and image.
        // TODO: Implement recurseDir method
        recurseDir(poiFS.getRoot(), extractor, xhtml);

        xhtml.endDocument();
        tmp.close();
    }

    private void recurseDir(DirectoryNode dir, EmbeddedDocumentExtractor extractor, XHTMLContentHandler xhtml)
        throws IOException, SAXException, TikaException {
        for (Entry entry : dir) {
            if (entry instanceof DirectoryNode) {
                recurseDir((DirectoryNode) entry, extractor, xhtml);
            } else if (entry instanceof DocumentNode) {
                Metadata entryData = new Metadata();
                entryData.set(Metadata.CONTENT_TYPE, "thumbcache-entry");

                xhtml.startElement("div", "class", "thumbcache-entry");
                xhtml.element("h1", entry.getName()); // Nome do arquivo

                xhtml.startElement("div", "class", "thumbcache-entry-content");
                try (InputStream stream = new DocumentInputStream((DocumentNode) entry)) {
                    // Extraímos o conteúdo incorporado do arquivo
                    extractor.parseEmbedded(stream, xhtml, entryData, true);
                } catch (Exception e) {
                    xhtml.startElement("p");
                    xhtml.characters("Error processing document: " + e.getMessage());
                    xhtml.endElement("p");
                }

                xhtml.endElement("div"); // Fecha "thumbcache-entry-content"
                xhtml.endElement("div"); // Fecha "thumbcache-entry"
            }
        }
    }
}
