package gpinf.emule;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.inc.sepinf.UsnJrnl.Util;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import iped3.io.IItemBase;
public class PreferencesDatParser extends AbstractParser {

    public static final String EMULE_PREFERENCES_MIME_TYPE = "application/x-emule-preferences-dat"; //$NON-NLS-1$
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.parse(EMULE_PREFERENCES_MIME_TYPE));

    /** 
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream is, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // TODO Auto-generated method stub
        IItemBase item = context.get(IItemBase.class);
        try (iped3.io.SeekableInputStream stream = item.getStream()) {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            int version = -1;
            byte b[] = new byte[16];
            if (stream.size() > 16) {
                version = stream.read();
                for (int i = 0; i < b.length;) {
                    i += stream.read(b, i, b.length - i);
                }

                xhtml.startElement("table", "border", "1");
                xhtml.startElement("tr");

                xhtml.startElement("td");
                xhtml.characters(Messages.getString("PreferencesDat.Version"));
                xhtml.endElement("td");
                xhtml.startElement("td");
                xhtml.characters(Integer.toString(version));
                xhtml.endElement("td");

                xhtml.endElement("tr");

                xhtml.startElement("tr");

                xhtml.startElement("td");
                xhtml.characters(Messages.getString("PreferencesDat.UserHash"));
                xhtml.endElement("td");
                xhtml.startElement("td");

                xhtml.characters(Util.byteArrayToHex(b));

                xhtml.endElement("td");

                xhtml.endElement("tr");

                xhtml.endElement("table");
            } else {
                xhtml.characters("Invalid file");
            }

            xhtml.endDocument();
        }

    }

}
