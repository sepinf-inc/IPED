package dpf.mg.udi.gpinf.bittorrent;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.dampcake.bencode.BencodeInputStream;

import dpf.sp.gpinf.indexer.parsers.util.Messages;

/**
 * Parser for BitTorrent Client Artifacts
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class BitTorrentResumeDatParser extends AbstractParser {

    private static final long serialVersionUID = -203319828888512196L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("x-bittorrent-resume-dat")); //$NON-NLS-1$
    private static final String RESUME_DAT_MIME_TYPE = "application/x-bittorrent-resume-dat"; //$NON-NLS-1$
    private static final String[] header = new String[] { Messages.getString("BitTorrentResumeDatParser.TorrentFile"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.RootDir"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.Path"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.Downloaded"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.Uploaded"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.AddedDate"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.CompletedDate"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.Time"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.LastSeenCompleteDate"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.SeedTime"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.RunTime") //$NON-NLS-1$
    };

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        final DateFormat df = new SimpleDateFormat(Messages.getString("BitTorrentResumeDatParser.DateFormat")); //$NON-NLS-1$
        df.setTimeZone(TimeZone.getTimeZone("GMT+0")); //$NON-NLS-1$

        metadata.set(HttpHeaders.CONTENT_TYPE, RESUME_DAT_MIME_TYPE);
        metadata.remove(TikaMetadataKeys.RESOURCE_NAME_KEY);

        Map<String, Object> dict = new BencodeInputStream(stream).readDictionary();

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("style"); //$NON-NLS-1$
        xhtml.characters(".dt {display: table; border-collapse: collapse; font-family: Arial, sans-serif; } " //$NON-NLS-1$
                + ".rh { display: table-row; font-weight: bold; text-align: center; background-color:#AAAAEE; } " //$NON-NLS-1$
                + ".ra { display: table-row; vertical-align: middle; } " //$NON-NLS-1$
                + ".rb { display: table-row; background-color:#E7E7F0; vertical-align: middle; } " //$NON-NLS-1$
                + ".a { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; } " //$NON-NLS-1$
                + ".b { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; } "); //$NON-NLS-1$
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.newline();
        try {
            xhtml.startElement("div", "class", "dt"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
            xhtml.startElement("div", "class", "rh"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
            for (String h : header) {
                xhtml.startElement("div", "class", "b"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
                xhtml.characters(h);
                xhtml.endElement("div"); //$NON-NLS-1$
            }
            xhtml.endElement("div"); //$NON-NLS-1$

            boolean a = true;
            for (String torrent : dict.keySet()) {
                if (torrent.equals(".fileguard") || torrent.equals("rec")) { //$NON-NLS-1$ $NON-NLS-2$
                    continue;
                }
                xhtml.startElement("div", "class", a ? "ra" : "rb"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$
                String[] rowElememts = new String[] { torrent, getString(dict, torrent, "rootdir"), //$NON-NLS-1$
                        getString(dict, torrent, "path"), //$NON-NLS-1$
                        Long.toString(getLong(dict, torrent, "downloaded")), //$NON-NLS-1$
                        Long.toString(getLong(dict, torrent, "uploaded")), //$NON-NLS-1$
                        getDate(dict, torrent, "added_on", df), //$NON-NLS-1$
                        getDate(dict, torrent, "completed_on", df), //$NON-NLS-1$
                        getDate(dict, torrent, "time", df), //$NON-NLS-1$
                        getDate(dict, torrent, "last seen complete", df), //$NON-NLS-1$
                        Long.toString(getLong(dict, torrent, "seedtime")), //$NON-NLS-1$
                        Long.toString(getLong(dict, torrent, "runtime")) //$NON-NLS-1$
                };
                for (String c : rowElememts) {
                    xhtml.startElement("div", "class", "a"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
                    if (c.equals("")) { //$NON-NLS-1$
                        c = " "; //$NON-NLS-1$
                    }
                    xhtml.characters(c);
                    xhtml.endElement("div"); //$NON-NLS-1$
                }
                xhtml.endElement("div"); //$NON-NLS-1$
                a = !a;
            }

            xhtml.endElement("div"); //$NON-NLS-1$
        } finally {
            xhtml.endDocument();
        }

    }

    private static String getDate(Map<String, Object> dict, String torrent, String key, DateFormat df) {
        String resp = ""; //$NON-NLS-1$
        long num = getLong(dict, torrent, key);
        if (num != 0) {
            Date date = new Date(num * 1000);
            resp = df.format(date);
        }
        return resp;
    }

    private static Object getObject(Map<String, Object> dict, String torrent, String key) {
        if (dict.containsKey(torrent)) {
            if (!(dict.get(torrent) instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked") //$NON-NLS-1$
            Map<String, Object> torrentDict = (Map<String, Object>) dict.get(torrent);
            if (torrentDict.containsKey(key)) {
                return torrentDict.get(key);
            }
        }
        return null;
    }

    private static long getLong(Map<String, Object> dict, String torrent, String key) {
        Object obj = getObject(dict, torrent, key);
        if (obj == null || !(obj instanceof Long)) {
            return 0;
        }
        return (Long) obj;
    }

    private static String getString(Map<String, Object> dict, String torrent, String key) {
        Object obj = getObject(dict, torrent, key);
        if (obj == null || !(obj instanceof String)) {
            return ""; //$NON-NLS-1$
        }
        return (String) obj;
    }

}