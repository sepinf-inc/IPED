package iped.parsers.misc;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
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
        // recurseDir(poiFS.getRoot(), extractor, xhtml);

        xhtml.endDocument();
        tmp.close();
    }
}
