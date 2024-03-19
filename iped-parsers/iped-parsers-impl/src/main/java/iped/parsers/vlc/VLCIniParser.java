package iped.parsers.vlc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class VLCIniParser implements Parser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(MediaType.application("x-vlc-ini"));
    private static final String RECENT_APPS_SECTION = "[RecentMedia]";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            boolean insideRecentAppsSection = false;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.equalsIgnoreCase(RECENT_APPS_SECTION)) {
                    insideRecentAppsSection = true;
                } else if (insideRecentAppsSection && !line.isEmpty()) {
                    xhtml.characters(line);
                }
            }
        } finally {
            xhtml.endDocument();
        }
    }

}
