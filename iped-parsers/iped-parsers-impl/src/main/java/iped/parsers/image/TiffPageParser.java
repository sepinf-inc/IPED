package iped.parsers.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


public class TiffPageParser extends AbstractParser {
    private static final long serialVersionUID = 2340523222085300794L;
    public static final String propNumPages = "tiff:NumPages";
    public static final String propExifPageCount = "exif:PageCount";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.image("tiff"));

    private static final String preferredReader = "com.sun.imageio.plugins.tiff.TIFFImageReader";

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        ImageReader reader = null;
        // The context item can be the containing Office document, not this TIFF.
        // Use the supplied stream and disk-backed random access for large images.
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            try (ImageInputStream iis = ImageIO.createImageInputStream(tis.getFile())) {
                if (iis == null) {
                    throw new TikaException("Could not open the supplied TIFF stream");
                }
                Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
                while (it.hasNext()) {
                    ImageReader candidate = it.next();
                    if (reader != null) {
                        reader.dispose();
                    }
                    reader = candidate;
                    if (reader.getClass().getName().equals(preferredReader)) {
                        break;
                    }
                }
                if (reader == null) {
                    throw new TikaException("No image reader accepted the supplied TIFF stream");
                }
                reader.setInput(iis, false, true);
                int numPages = reader.getNumImages(true);
                if (numPages > 0) {
                    metadata.set(propNumPages, String.valueOf(numPages));
                }
            } finally {
                if (reader != null) {
                    reader.dispose();
                }
            }
        }
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }
}
