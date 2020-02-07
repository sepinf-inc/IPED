package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Set;

import org.apache.poi.hpsf.Property;
import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.hpsf.Section;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
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

public class GenericOLEParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static Set<MediaType> SUPPORTED_MIMES = MediaType.set("application/x-tika-msoffice");

    private final RawStringParser rawParser = new RawStringParser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_MIMES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);

        try (POIFSFileSystem poiFS = new POIFSFileSystem(tis.getFile())) {
            recurseDir(poiFS.getRoot(), extractor, xhtml);

            rawParser.parse(tis, handler, metadata, context);

        } finally {
            xhtml.endDocument();
            tmp.close();
        }

    }

    private void recurseDir(DirectoryEntry dir, EmbeddedDocumentExtractor extractor, XHTMLContentHandler handler) {
        for (Entry entry : dir) {
            try {
                if (entry instanceof DirectoryEntry) {
                    // System.out.println("dir=" + entry.getName());
                    recurseDir((DirectoryEntry) entry, extractor, handler);
                }
                if (entry instanceof DocumentEntry) {
                    // System.out.println("doc=" + entry.getName());
                    DocumentEntry de = (DocumentEntry) entry;
                    try (DocumentInputStream dis = new DocumentInputStream(de)) {
                        Metadata metadata = new Metadata();
                        metadata.add(TikaCoreProperties.TITLE, de.getName());

                        if (PropertySet.isPropertySetStream(dis)) {
                            dis.mark(10000000);
                            PropertySet ps = null;
                            try {
                                ps = new PropertySet(dis);

                            } catch (UnsupportedEncodingException e) {
                                // ignore
                            }
                            if (ps != null) {
                                for (Section section : ps.getSections())
                                    for (Property p : section.getProperties()) {
                                        String prop = section.getDictionary() != null
                                                ? section.getDictionary().get(p.getID())
                                                : String.valueOf(p.getID());
                                        if (p.getValue() != null)
                                            metadata.add("property_" + prop, p.getValue().toString());
                                    }
                            }
                            dis.reset();
                        }
                        /*
                         * if(de instanceof POIFSViewable) { List<String> props =
                         * POIFSViewEngine.inspectViewable(de, true, 4, " "); for(String prop : props)
                         * metadata.add("oleProp", prop); }
                         */
                        extractor.parseEmbedded(dis, handler, metadata, true);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

}
