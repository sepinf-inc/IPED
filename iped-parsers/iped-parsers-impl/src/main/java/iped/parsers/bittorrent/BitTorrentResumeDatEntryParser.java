package iped.parsers.bittorrent;

import java.io.ByteArrayInputStream;
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
import iped.parsers.util.Messages;
import iped.parsers.util.P2PUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.LocalizedFormat;

public class BitTorrentResumeDatEntryParser extends AbstractParser {
    private static final long serialVersionUID = 9008710913652882111L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("x-bittorrent-resume-dat-entry"));
    public static final String RESUME_DAT_ENTRY_MIME_TYPE = "application/x-bittorrent-resume-dat-entry";

    private static final String[] header = new String[] { 
            Messages.getString("BitTorrentResumeDatParser.TorrentFile"),
            Messages.getString("BitTorrentResumeDatParser.RootDir"),
            Messages.getString("BitTorrentResumeDatParser.Path"),
            Messages.getString("BitTorrentResumeDatParser.InfoHash"),
            Messages.getString("BitTorrentResumeDatParser.Downloaded"),
            Messages.getString("BitTorrentResumeDatParser.Uploaded"),
            Messages.getString("BitTorrentResumeDatParser.AddedDate"),
            Messages.getString("BitTorrentResumeDatParser.CompletedDate"),
            Messages.getString("BitTorrentResumeDatParser.Time"),
            Messages.getString("BitTorrentResumeDatParser.LastSeenCompleteDate"),
            Messages.getString("BitTorrentResumeDatParser.SeedTime"),
            Messages.getString("BitTorrentResumeDatParser.RunTime"),
            Messages.getString("BitTorrentResumeDatParser.TorrentFoundInCase"),
            Messages.getString("BitTorrentResumeDatParser.FilesFoundInCase") };
    
    private static final String strYes = Messages.getString("BitTorrentResumeDatParser.Yes");

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        final DateFormat df = new SimpleDateFormat(Messages.getString("BitTorrentResumeDatParser.DateFormat"));
        df.setTimeZone(TimeZone.getTimeZone("GMT+0"));

        metadata.set(HttpHeaders.CONTENT_TYPE, RESUME_DAT_ENTRY_MIME_TYPE);
        metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

        // Generally stream.readAllBytes() is not safe and should be avoided because it
        // may cause OOME with large misdetected files. But in this specific case, just
        // carved resume.dat entries, limited to 32KB by the carving configuration, are
        // handled by this parser.
        byte[] bytes = stream.readAllBytes();

        char[] prev = "d1:X1:0".toCharArray();
        byte[] in = new byte[prev.length + bytes.length];
        for (int i = 0; i < prev.length; i++) {
            in[i] = (byte) prev[i];
        }
        System.arraycopy(bytes, 0, in, prev.length, bytes.length);
        ByteArrayInputStream is = new ByteArrayInputStream(in);
        BencodedDict dict = new BencodedDict(is, df, true);

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
            for (String torrent : dict.keySet()) {
                if (torrent.equals("X")) {
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

                String[] rowElements = new String[] { 
                        torrent, 
                        torrentDict.getString("rootdir"),
                        torrentDict.getString("path"), 
                        infoHash,
                        LocalizedFormat.format(torrentDict.getLong("downloaded")),
                        LocalizedFormat.format(torrentDict.getLong("uploaded")), 
                        torrentDict.getDate("added_on"),
                        torrentDict.getDate("completed_on"), 
                        torrentDict.getDate("time"),
                        torrentDict.getDate("last seen complete"),
                        LocalizedFormat.format(torrentDict.getLong("seedtime")),
                        LocalizedFormat.format(torrentDict.getLong("runtime")), 
                        item != null ? strYes : "",
                        filesFoundInCase > 0 ? String.valueOf(filesFoundInCase) : "" };

                for (int i = 0; i < rowElements.length; i++) {
                    String c = rowElements[i];
                    if (!c.isBlank()) {
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
                break;
            }

            xhtml.endElement("table");
        } finally {
            xhtml.endDocument();
        }
    }
}
