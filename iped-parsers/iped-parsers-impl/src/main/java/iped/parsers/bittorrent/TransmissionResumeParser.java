package iped.parsers.bittorrent;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.util.IgnoreCorruptedCarved;
import iped.parsers.util.Messages;
import iped.parsers.util.P2PUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.LocalizedFormat;

public class TransmissionResumeParser extends AbstractParser {
    private static final long serialVersionUID = 2692632610374337656L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("x-transmission-resume"));
    public static final String TRANSMISSION_RESUME_MIME_TYPE = "application/x-transmission-resume";

    private static final String[] header = new String[] { 
            Messages.getString("TransmissionResumeParser.Name"),
            Messages.getString("TransmissionResumeParser.Destination"),
            Messages.getString("TransmissionResumeParser.IncompleteDir"),
            Messages.getString("TransmissionResumeParser.InfoHash"),
            Messages.getString("TransmissionResumeParser.Downloaded"),
            Messages.getString("TransmissionResumeParser.Uploaded"),
            Messages.getString("TransmissionResumeParser.AddedDate"),
            Messages.getString("TransmissionResumeParser.LastActivityDate"),
            Messages.getString("TransmissionResumeParser.DoneDate"),
            Messages.getString("TransmissionResumeParser.TorrentFoundInCase"),
            Messages.getString("TransmissionResumeParser.FilesFoundInCase") };

    private static final String strYes = Messages.getString("TransmissionResumeParser.Yes");

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        final DateFormat df = new SimpleDateFormat(Messages.getString("TransmissionResumeParser.DateFormat"));
        df.setTimeZone(TimeZone.getTimeZone("GMT+0"));

        metadata.set(HttpHeaders.CONTENT_TYPE, TRANSMISSION_RESUME_MIME_TYPE);
        metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

        BencodedDict dict = new BencodedDict(stream, df, (context.get(IgnoreCorruptedCarved.class) == null));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("style");
        xhtml.characters(".dt {border-collapse: collapse; font-family: Arial, sans-serif; } "
                + ".a { background-color:#AAAAEE; font-weight: bold; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: nowrap; } "
                + ".b { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: nowrap; } "
                + ".h { font-weight: bold; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; white-space: nowrap; font-family: monospace; }");
        xhtml.endElement("style");
        xhtml.newline();
        try {
            xhtml.startElement("table", "class", "dt");

            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemReader item = context.get(IItemReader.class);

            int filesFoundInCase = 0;
            IItemReader torrentItem = null;

            // First check id the resume file name is the InfoHash
            String infoHash = getInfoHashFromName(item.getName());
            if (infoHash != null) {
                torrentItem = P2PUtil.searchItemInCase(searcher, TorrentFileParser.TORRENT_INFO_HASH, infoHash);
            } else {
                // If the name is NOT a InfoHash, try to find a torrent
                // in the same folder structure and name, like below:
                // <folder>/resume/xyz.resume
                // <folder>/torrent/xyz.torrent
                String[] torrentPaths = getTorrentPathsFromResumePath(item.getPath());
                if (torrentPaths != null) {
                    StringBuilder query = new StringBuilder();
                    query.append(BasicProps.PATH).append(": (");
                    for (int i = 0; i < torrentPaths.length; i++) {
                        if (i > 0) {
                            query.append(' ');
                        }
                        query.append('"');
                        query.append(searcher.escapeQuery(torrentPaths[i]));
                        query.append('"');
                    }
                    query.append(')');
                    Iterable<IItemReader> items = searcher.searchIterable(query.toString());
                    for (IItemReader it : items) {
                        torrentItem = it;
                        if (!it.isDeleted() && !it.isCarved()) {
                            // Prioritize non-deleted items, so stop as one is found
                            break;
                        }
                    }
                }
            }

            if (torrentItem != null) {
                // Corresponding torrent was found
                metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + torrentItem.getHash());
                String[] values = torrentItem.getMetadata().getValues(ExtraProperties.LINKED_ITEMS);
                if (values != null) {
                    long uploaded = dict.getLong("uploaded");
                    boolean isShared = uploaded > 0;
                    for (String v : values) {
                        metadata.add(ExtraProperties.LINKED_ITEMS, v);
                        if (isShared) {
                            int p = v.lastIndexOf(':');
                            if (p >= 0) {
                                v = v.substring(p + 1).trim();
                            }
                            metadata.add(ExtraProperties.SHARED_HASHES, v);
                        }
                    }
                }
                String v = torrentItem.getMetadata().get(TorrentFileParser.TORRENT_FILES_FOUND_IN_CASE);
                if (v != null && !v.isBlank()) {
                    try {
                        filesFoundInCase = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }

            String[] rowElements = new String[] { 
                    dict.getString("name"), 
                    dict.getString("destination"),
                    dict.getString("incomplete-dir"),
                    infoHash,
                    LocalizedFormat.format(dict.getLong("downloaded")),
                    LocalizedFormat.format(dict.getLong("uploaded")), 
                    dict.getDate("added-date"),
                    dict.getDate("activity-date"), 
                    dict.getDate("done-date"), 
                    torrentItem != null ? strYes : "",
                    filesFoundInCase > 0 ? String.valueOf(filesFoundInCase) : ""};

            for (int i = 0; i < rowElements.length; i++) {
                String c = rowElements[i];
                if (c != null && !c.isBlank()) {
                    xhtml.startElement("tr");

                    xhtml.startElement("td", "class", "a");
                    xhtml.characters(header[i]);
                    xhtml.endElement("td");

                    xhtml.startElement("td", "class", i == 3 ? "h" : "b");
                    xhtml.characters(c);
                    xhtml.endElement("td");

                    xhtml.endElement("tr");
                }
            }

            xhtml.endElement("table");
        } finally {
            xhtml.endDocument();
        }
    }

    private static String[] getTorrentPathsFromResumePath(String path) {
        if (path.toLowerCase().endsWith(".resume")) {
            int p1 = path.lastIndexOf('/');
            if (p1 > 0) {
                int p0 = path.lastIndexOf('/', p1 - 1);
                if (p0 > 0) {
                    String parent = path.substring(p0 + 1, p1);
                    if (parent.equalsIgnoreCase("resume")) {
                        int p2 = path.lastIndexOf('.');
                        // IN:   <folder>/resume/xyz.resume
                        // OUT1: <folder>/torrents/xyz.torrent
                        // OUT2: <folder>/torrents/xy.torrent
                        // Sometimes the last character is suppressed in torrent's name
                        String base = path.substring(0, p0 + 1) + "torrents";
                        return new String[] { base + path.substring(p1, p2) + ".torrent",
                                base + path.substring(p1, p2 - 1) + ".torrent" };
                    }
                }
            }
        }
        return null;
    }

    private static String getInfoHashFromName(String name) {
        // Check if item name is <infohash - 40 hex characters>.resume
        if (name.toLowerCase().endsWith(".resume")) {
            int p = name.lastIndexOf('.');
            name = name.substring(0, p);
            if (name.length() == 40) {
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if ((c < 'A' || c > 'F') && (c < 'a' || c > 'f') && (c < '0' || c > '9')) {
                        return null;
                    }
                }
                return name;
            }
        }
        return null;
    }
}
