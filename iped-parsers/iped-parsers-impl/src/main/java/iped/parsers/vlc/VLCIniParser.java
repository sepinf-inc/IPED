package iped.parsers.vlc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
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
    private static final String RECENT_SECTION = "[RecentsMRL]";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            boolean insideRecentMRL = false;
            String[] paths = null;
            String[] times = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.equalsIgnoreCase(RECENT_SECTION)) {
                    insideRecentMRL = true;
                } else if (line.startsWith("[") && line.endsWith("]")) {
                    if (insideRecentMRL && paths != null && !paths[0].equals("@Invalid()")) {
                        xhtml.characters("Recent Medias:");
                        xhtml.newline();
                        xhtml.newline();
                        for (int i = 0; i < paths.length; i++) {
                            String LastSkipPosition = "";
                            if (times != null && i < times.length && !times[i].strip().equals("0")) {
                                LastSkipPosition = " [LastSkipPosition=" + times[i].strip() + "ms]";
                            }
                            String path = paths[i].strip().replace("\\\\", "/");
                            if (path.startsWith("file://")) {
                                path = URI.create(path).getPath();
                                // absolute windows path adjustment
                                if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                                    path = path.substring(1);
                                }
                            }
                            xhtml.characters(path + LastSkipPosition);
                            xhtml.newline();
                        }
                    }
                    insideRecentMRL = false;
                } else if (insideRecentMRL) {
                    if (line.startsWith("list=")) {
                        paths = line.substring(5).split(",");
                    } else if (line.startsWith("times=")) {
                        times = line.substring(6).split(",");
                    }
                }
            }
        } finally {
            xhtml.endDocument();
        }
    }

}
