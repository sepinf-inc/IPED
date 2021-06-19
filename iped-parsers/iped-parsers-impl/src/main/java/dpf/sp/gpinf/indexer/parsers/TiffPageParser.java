package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TiffPageParser extends AbstractParser {
    private static final long serialVersionUID = 2340523222085300794L;
    private static final String propNumPages = "tiff:NumPages";
    private static final String propExifPageCount = "exif:PageCount";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.image("tiff"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        ImageReader reader = null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(stream)) {
            reader = ImageIO.getImageReaders(iis).next();
            reader.setInput(iis, false, true);
            int numPages = reader.getNumImages(true);
            if (numPages > 0) {
                metadata.set(propNumPages, String.valueOf(numPages));
                metadata.remove(propExifPageCount);
            }
        } finally {
            if (reader != null)
                reader.dispose();
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }
}
