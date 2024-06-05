package iped.parsers.bittorrent;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.codec.binary.Hex;
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

/**
 * Parser for BitTorrent Client Artifacts
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class BitTorrentResumeDatParser extends AbstractParser {

    private static final long serialVersionUID = -203319828888512196L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("x-bittorrent-resume-dat")); //$NON-NLS-1$
    public static final String RESUME_DAT_MIME_TYPE = "application/x-bittorrent-resume-dat"; //$NON-NLS-1$
    private static final String[] header = new String[] { "#",
            Messages.getString("BitTorrentResumeDatParser.TorrentFile"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.RootDir"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.Path"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.InfoHash"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.Downloaded"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.Uploaded"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.AddedDate"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.CompletedDate"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.Time"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.LastSeenCompleteDate"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.SeedTime"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.RunTime"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.TorrentFoundInCase"), //$NON-NLS-1$
            Messages.getString("BitTorrentResumeDatParser.FilesFoundInCase") //$NON-NLS-1$
    };
    private static final char[] colAlign = new char[] { 'b', 'a', 'a', 'a', 'h', 'c', 'c', 'b', 'b', 'b', 'b', 'c', 'c', 'b', 'b' };
    private static final String strYes = Messages.getString("BitTorrentResumeDatParser.Yes");

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
        metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

        BencodedDict dict = new BencodedDict(stream, df, (context.get(IgnoreCorruptedCarved.class) == null));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("style"); //$NON-NLS-1$
        xhtml.characters(".dt {border-collapse: collapse; font-family: Arial, sans-serif; } " //$NON-NLS-1$
                + ".rh { font-weight: bold; text-align: center; background-color:#AAAAEE; } " //$NON-NLS-1$
                + ".ra { vertical-align: middle; } " //$NON-NLS-1$
                + ".rb { background-color:#E7E7F0; vertical-align: middle; } " //$NON-NLS-1$
                + ".a { max-width: 400px; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; } " //$NON-NLS-1$
                + ".b { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; } " //$NON-NLS-1$
                + ".c { border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word; } " //$NON-NLS-1$
                + ".h { font-weight: bold; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; white-space: nowrap; font-family: monospace; }");
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.newline();
        try {
            if (dict.isIncomplete()) {
                xhtml.characters("* " + Messages.getString("TorrentFileDatParser.IncompleteWarning"));
                metadata.set("incompleteTorrent", "true");
            }
            xhtml.startElement("table", "class", "dt"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
            xhtml.startElement("tr", "class", "rh"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
            for (String h : header) {
                xhtml.startElement("td", "class", "b"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
                xhtml.characters(h);
                xhtml.endElement("td"); //$NON-NLS-1$
            }
            xhtml.endElement("tr"); //$NON-NLS-1$

            int numEntries = 0;
            IItemSearcher searcher = context.get(IItemSearcher.class);
            boolean a = true;
            for (String torrent : dict.keySet()) {
                if (torrent.equals(".fileguard") || torrent.equals("rec")) { //$NON-NLS-1$ $NON-NLS-2$
                    continue;
                }
                BencodedDict torrentDict = dict.getDict(torrent);
                if (torrentDict == null) {
                    continue;
                }
                byte[] infoBytes = torrentDict.getBytes("info");
                String infoHash = "";
                if (infoBytes != null) {
                    infoHash = Hex.encodeHexString(infoBytes, false);
                }
                int filesFoundInCase = 0;
                IItemReader item = P2PUtil.searchItemInCase(searcher, TorrentFileParser.TORRENT_INFO_HASH, infoHash);
                if (item != null) {
                    metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + item.getHash());
                    String[] values = item.getMetadata().getValues(ExtraProperties.LINKED_ITEMS);
                    if (values != null) {
                        Long uploaded = torrentDict.getLong("uploaded");
                        boolean isShared = uploaded != null && uploaded > 0;
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
                    String v = item.getMetadata().get(TorrentFileParser.TORRENT_FILES_FOUND_IN_CASE);
                    if (v != null && !v.isBlank()) {
                        filesFoundInCase = Integer.parseInt(v);
                    }
                }

                xhtml.startElement("tr", "class", a ? "ra" : "rb"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$
                String[] rowElements = new String[] {
                        String.valueOf(++numEntries),
                        torrent, 
                        torrentDict.getString("rootdir"), //$NON-NLS-1$
                        torrentDict.getString("path"), //$NON-NLS-1$
                        infoHash,
                        Long.toString(torrentDict.getLong("downloaded")), //$NON-NLS-1$
                        Long.toString(torrentDict.getLong("uploaded")), //$NON-NLS-1$
                        torrentDict.getDate("added_on"), //$NON-NLS-1$
                        torrentDict.getDate("completed_on"), //$NON-NLS-1$
                        torrentDict.getDate("time"), //$NON-NLS-1$
                        torrentDict.getDate("last seen complete"), //$NON-NLS-1$
                        Long.toString(torrentDict.getLong("seedtime")), //$NON-NLS-1$
                        Long.toString(torrentDict.getLong("runtime")), //$NON-NLS-1$
                        item != null ? strYes: "",
                        filesFoundInCase > 0 ? String.valueOf(filesFoundInCase): ""
                };
                for (int i = 0; i < rowElements.length; i++) {
                    String c = rowElements[i];
                    char align = colAlign[i];
                    xhtml.startElement("td", "class", String.valueOf(align)); //$NON-NLS-1$ $NON-NLS-2$
                    if (c.equals("")) { //$NON-NLS-1$
                        c = " "; //$NON-NLS-1$
                    } else if (align == 'c') {
                        try {
                            c = LocalizedFormat.format(Long.parseLong(c));
                        } catch (Exception e) {
                        }
                    }
                    xhtml.characters(c);
                    xhtml.endElement("td"); //$NON-NLS-1$
                }
                xhtml.endElement("tr"); //$NON-NLS-1$
                a = !a;
            }
            metadata.set(ExtraProperties.P2P_REGISTRY_COUNT, String.valueOf(numEntries));

            xhtml.endElement("table"); //$NON-NLS-1$
        } finally {
            xhtml.endDocument();
        }
    }
}
