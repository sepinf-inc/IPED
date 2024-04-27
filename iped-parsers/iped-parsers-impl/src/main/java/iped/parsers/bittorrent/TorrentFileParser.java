package iped.parsers.bittorrent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.util.IgnoreCorruptedCarved;
import iped.parsers.util.Messages;
import iped.parsers.util.MetadataUtil;
import iped.parsers.util.P2PUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;
import iped.utils.LocalizedFormat;

/**
 * Parser for Torrent Files
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class TorrentFileParser extends AbstractParser {

    private static final String TORRENT_CREATION_DATE = "torrentCreationDate";
    public static final String TORRENT_INFO_HASH = "torrentInfoHash";
    private static final long serialVersionUID = 3238363426940179831L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-bittorrent")); //$NON-NLS-1$
    public static final String TORRENT_FILE_MIME_TYPE = "application/x-bittorrent"; //$NON-NLS-1$
    private static final String[] header = { Messages.getString("TorrentFileDatParser.File"),
            Messages.getString("TorrentFileDatParser.FullPath"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.FileSize"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.MD5"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.SHA1"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.ED2K"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.FileFoundInCase")
    };
    private static final String strYes = Messages.getString("TorrentFileDatParser.Yes");
    
    private static final int maxPieceLength = 1 << 26;
    private static final long minFileLength = 1 << 16;
    private static final long maxFileLength = 1L << 34;
    private static final int maxHitsCheck = 16;
    private static final int minPiecesMultiFile = 8;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        final DateFormat df = new SimpleDateFormat(Messages.getString("TorrentFileDatParser.DateFormat")); //$NON-NLS-1$
        df.setTimeZone(TimeZone.getTimeZone("GMT+0")); //$NON-NLS-1$

        metadata.set(HttpHeaders.CONTENT_TYPE, TORRENT_FILE_MIME_TYPE);

        BencodedDict dict;
        try {
            dict = new BencodedDict(stream, df, (context.get(IgnoreCorruptedCarved.class) == null));
        } catch (IOException e) {
            throw new TikaException("Error parsing torrent file", e); //$NON-NLS-1$
        }

        IItemSearcher searcher = context.get(IItemSearcher.class);
        List<FileInTorrent> files = extractFileList(dict, metadata, searcher);

        char[] colClass = { 'c', 'a', 'c', 'h', 'h', 'h', 'b' };
        boolean[] include = { true, true, true, false, false, false, false };
        for (FileInTorrent file : files) {
            if (!file.md5.isEmpty())
                include[3] = true;
            if (!file.sha1.isEmpty())
                include[4] = true;
            if (!file.ed2k.isEmpty())
                include[5] = true;
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("style"); //$NON-NLS-1$
        xhtml.characters(
                ".dt {border-collapse: collapse; font-family: Arial, sans-serif; margin-right: 32px; margin-bottom: 32px; } "
                        + ".rh { font-weight: bold; text-align: center; background-color:#AAAAEE; } "
                        + ".ra { vertical-align: middle; } "
                        + ".rb { background-color:#E7E7F0; vertical-align: middle; } "
                        + ".a { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; } "
                        + ".b { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; } "
                        + ".c { border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word; } "
                        + ".d { font-weight: bold; background-color:#AAAAEE; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; white-space: nowrap; } "
                        + ".h { font-weight: bold; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; white-space: nowrap; font-family: monospace; } ");
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.newline();

        if (dict.isIncomplete()) {
            xhtml.characters("* "
                    + Messages.getString("TorrentFileDatParser.IncompleteWarning"));
            metadata.set("incompleteTorrent", "true");
        }

        // Torrent General Info Table
        xhtml.startElement("table", "class", "dt");
        TorrentInfo info = extractTorrentInfo(dict);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.Name"), info.name);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.InfoHash"), info.infoHash, true);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.PieceLength"), info.pieceLength);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.NumberOfPieces"), info.numPieces);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.NumberOfFiles"), files.size());
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.Announce"), info.announce);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.Comment"), info.comment);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.CreatedBy"), info.createdBy);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.CreationDate"), info.creationDate);
        xhtml.endElement("table");

        // Set creation date metadata
        MetadataUtil.setMetadataType(TORRENT_CREATION_DATE, Date.class);
        if (!info.creationDate.isEmpty()) {
            try {
                metadata.set(TORRENT_CREATION_DATE, DateUtil.dateToString(df.parse(info.creationDate)));
            } catch (ParseException e1) {
                e1.printStackTrace();
            }
        }
        
        // Set infoHash metadata
        if (info.infoHash != null && !info.infoHash.isBlank()) {
            metadata.set(TORRENT_INFO_HASH, info.infoHash);
        }

        // Try to link the first item
        if (searcher != null && !files.isEmpty() && info.pieces != null && info.pieceLength != null) {
            FileInTorrent file = files.get(0);
            if (file.item == null) {
                IItemReader item = searchAndMatchFileInCase(searcher, file.length, info.pieceLength, info.pieces,
                        files.size() > 1);
                if (item != null) {
                    file.item = item;
                    include[6] = true;
                    metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + file.item.getHash());
                }
            }
        }

        // Files Table
        xhtml.startElement("table", "class", "dt"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
        xhtml.startElement("tr", "class", "rh"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
        for (int i = 0; i < header.length; i++) {
            if (include[i]) {
                xhtml.startElement("td", "class", "b"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
                xhtml.characters(header[i]);
                xhtml.endElement("td"); //$NON-NLS-1$
            }
        }
        xhtml.endElement("tr"); //$NON-NLS-1$

        for (int i = 0; i < files.size(); i++) {
            FileInTorrent file = files.get(i);
            xhtml.startElement("tr", "class", i % 2 == 0 ? "ra" : "rb"); 
            String[] rowElements = new String[] { String.valueOf(i + 1), file.fullPath, Long.toString(file.length),
                    file.md5, file.sha1, file.ed2k, file.item != null ? strYes : "" };
            for (int j = 0; j < rowElements.length; j++) {
                if (include[j]) {
                    String str = rowElements[j];
                    xhtml.startElement("td", "class", String.valueOf(colClass[j]));
                    if (str.length() == 0) {
                        str = " ";
                    } else if (j == 2) {
                        // File length column
                        try {
                            str = LocalizedFormat.format(Long.parseLong(str));
                        } catch (Exception e) {
                        }
                    }
                    if (j == 1 && file.item != null) {
                        P2PUtil.printNameWithLink(xhtml, file.item, str);
                    } else {
                        xhtml.characters(str);
                    }
                    xhtml.endElement("td");
                }
            }
            xhtml.endElement("tr");
        }
        xhtml.endElement("table");

        // Pieces Hashes Table
        if (info.pieces != null) {
            xhtml.startElement("table", "class", "dt");
            xhtml.startElement("tr", "class", "rh");
            xhtml.startElement("td", "class", "b");
            xhtml.characters(Messages.getString("TorrentFileDatParser.Piece"));
            xhtml.endElement("td");
            xhtml.startElement("td", "class", "b");
            xhtml.characters(Messages.getString("TorrentFileDatParser.SHA1"));
            xhtml.endElement("td");
            xhtml.endElement("tr");

            for (int i = 0; i < info.pieces.length; i++) {
                xhtml.startElement("tr", "class", i % 2 == 0 ? "ra" : "rb");
                xhtml.startElement("td", "class", "c");
                xhtml.characters(LocalizedFormat.format(i + 1));
                xhtml.endElement("td");
                xhtml.startElement("td", "class", "h");
                xhtml.characters(info.pieces[i]);
                xhtml.endElement("td");
                xhtml.endElement("tr");
            }
            xhtml.endElement("table");
        }

        xhtml.endDocument();
    }

    private static void outputInfo(XHTMLContentHandler xhtml, String key, Object value) throws SAXException {
        outputInfo(xhtml, key, value, false);
    }

    private static void outputInfo(XHTMLContentHandler xhtml, String key, Object value, boolean isMono)
            throws SAXException {
        if (value != null && !value.toString().isBlank()) {
            xhtml.startElement("tr", "class", "ra");
            xhtml.startElement("td", "class", "d");
            xhtml.characters(key);
            xhtml.endElement("td");
            xhtml.startElement("td", "class", isMono ? "h" : "a");
            String s = value instanceof Long ? LocalizedFormat.format((Long) value) : value.toString();
            xhtml.characters(s);
            xhtml.endElement("td");
            xhtml.endElement("tr");
        }
    }

    private static List<FileInTorrent> extractFileList(BencodedDict dict, Metadata metadata, IItemSearcher searcher) throws TikaException {
        BencodedDict info = dict.getDict("info"); //$NON-NLS-1$
        List<FileInTorrent> files;

        if (info != null) {
            if (info.containsKey("files")) { //$NON-NLS-1$
                // Multi file torrent
                List<Object> filesList = info.getList("files"); //$NON-NLS-1$
                if (filesList == null) {
                    throw new TikaException("Error parsing torrent file"); //$NON-NLS-1$
                }
                String name = info.getString("name"); //$NON-NLS-1$
                files = new ArrayList<>(filesList.size());
                for (Object fileDictObj : filesList) {
                    BencodedDict fileDict = (BencodedDict) fileDictObj;
                    FileInTorrent file = new FileInTorrent();
                    StringBuilder fullPathBuilder = new StringBuilder();
                    if (name != null && 0 != name.length()) {
                        fullPathBuilder.append(name).append(File.separator);
                    }
                    for (String elem : fileDict.getListOfStrings("path")) { //$NON-NLS-1$
                        fullPathBuilder.append(elem).append(File.separator);
                    }
                    fullPathBuilder.deleteCharAt(fullPathBuilder.length() - 1);
                    file.fullPath = fullPathBuilder.toString();
                    file.length = fileDict.getLong("length"); //$NON-NLS-1$
                    file.md5 = fileDict.getString("md5sum"); //$NON-NLS-1$
                    if (file.md5.length() > 0) {
                        metadata.add(ExtraProperties.LINKED_ITEMS, "md5:" + file.md5);
                        if (file.item == null) {
                            file.item = P2PUtil.searchItemInCase(searcher, "md5", file.md5);
                        }
                    }
                    file.sha1 = fileDict.getString("sha1").trim(); //$NON-NLS-1$
                    if (file.sha1.length() > 0) {
                        if (file.sha1.length() != 40) {
                            file.sha1 = fileDict.getHexEncodedBytes("sha1"); //$NON-NLS-1$
                        }
                        metadata.add(ExtraProperties.LINKED_ITEMS, "sha-1:" + file.sha1);
                        if (file.item == null) {
                            file.item = P2PUtil.searchItemInCase(searcher, "sha-1", file.sha1);
                        }
                    }
                    file.ed2k = fileDict.getHexEncodedBytes("ed2k");
                    if (file.ed2k.length() > 0) {
                        metadata.add(ExtraProperties.LINKED_ITEMS, "edonkey" + file.ed2k);
                        if (file.item == null) {
                            file.item = P2PUtil.searchItemInCase(searcher, "edonkey", file.ed2k);
                        }
                    }
                    files.add(file);
                }

            } else {
                // Single file torrent
                FileInTorrent file = new FileInTorrent();
                file.fullPath = info.getString("name"); //$NON-NLS-1$
                file.length = info.getLong("length"); //$NON-NLS-1$
                file.md5 = info.getString("md5sum"); //$NON-NLS-1$
                if (file.md5.length() > 0) {
                    metadata.add(ExtraProperties.LINKED_ITEMS, "md5:" + file.md5);
                }
                file.sha1 = info.getString("sha1").trim(); //$NON-NLS-1$
                if (file.sha1.length() > 0) {
                    if (file.sha1.length() != 40) {
                        file.sha1 = info.getHexEncodedBytes("sha1"); //$NON-NLS-1$
                    }
                    metadata.add(ExtraProperties.LINKED_ITEMS, "sha-1:" + file.sha1);
                }
                file.ed2k = info.getHexEncodedBytes("ed2k"); //$NON-NLS-1$
                files = Collections.singletonList(file);
            }

            if (files.isEmpty()) {
                throw new TikaException("Error parsing torrent file"); //$NON-NLS-1$
            }
        } else {
            throw new TikaException("Error parsing torrent file"); //$NON-NLS-1$
        }
        return files;
    }

    private static class FileInTorrent {
        String fullPath;
        long length;
        String md5;
        String sha1;
        String ed2k;
        IItemReader item;
    }

    private static TorrentInfo extractTorrentInfo(BencodedDict dict) throws TikaException {
        TorrentInfo torrentInfo = new TorrentInfo();
        BencodedDict info = dict.getDict("info");
        if (info != null) {
            torrentInfo.name = info.getString("name");
            torrentInfo.pieceLength = info.getLongNull("piece length");
            byte[] piecesBytes = info.getBytes("pieces");
            if (piecesBytes != null) {
                int len = 20;
                int n = piecesBytes.length / len;
                torrentInfo.numPieces = Long.valueOf(n);
                torrentInfo.pieces = new String[n];
                byte[] sha1 = new byte[len];
                for (int i = 0; i < n; i++) {
                    if (i * len + len <= piecesBytes.length) {
                        System.arraycopy(piecesBytes, i * len, sha1, 0, len);
                        torrentInfo.pieces[i] = Hex.encodeHexString(sha1, false);
                    } else {
                        torrentInfo.pieces[i] = "-";
                    }
                }
            }
            byte[] infoBytes = info.getDictBytes();
            if (infoBytes != null) {
                torrentInfo.infoHash = DigestUtils.sha1Hex(infoBytes).toUpperCase();
            }
        }
        torrentInfo.announce = dict.getString("announce");
        torrentInfo.comment = dict.getString("comment");
        torrentInfo.createdBy = dict.getString("created by");
        torrentInfo.creationDate = dict.getDate("creation date");
        return torrentInfo;
    }

    private static class TorrentInfo {
        String name;
        String infoHash;
        String announce;
        String comment;
        String createdBy;
        String creationDate;
        Long pieceLength;
        Long numPieces;
        String[] pieces;
    }

    private static IItemReader searchAndMatchFileInCase(IItemSearcher searcher, long fileLength, long pieceLength,
            String[] pieces, boolean isMultiFile) {
        if (pieceLength <= 0 || pieceLength > maxPieceLength || fileLength < minFileLength
                || fileLength > maxFileLength) {
            return null;
        }
        int totPieces = (int) ((fileLength + pieceLength - 1) / pieceLength);
        if (totPieces > pieces.length || (isMultiFile && totPieces < minPiecesMultiFile)) {
            return null;
        }
        List<IItemReader> items = searcher.search(BasicProps.LENGTH + ":" + fileLength);
        if (items == null) {
            return null;
        }
        byte[] bytes = new byte[(int) pieceLength];
        int check = maxHitsCheck;
        for (int step = 0; step <= 1; step++) {
            NEXT: for (int i = 0; i < items.size(); i++) {
                IItemReader item = items.get(i);
                if (item.getHash() == null) {
                    continue;
                }
                if (step == 0 ^ (item.isCarved() || item.isDeleted())) {
                    // In the first step check only "active" items
                    // Carved and deleted items are checked later
                    continue;
                }

                // Check if the all pieces hashes match
                try (BufferedInputStream is = item.getBufferedInputStream()) {
                    for (int n = 0; n < totPieces; n++) {
                        int read = is.readNBytes(bytes, 0, bytes.length);
                        if (read < 0 || (read < bytes.length && n < totPieces - 1)) {
                            continue NEXT;
                        }
                        if (read == bytes.length || !isMultiFile) {
                            byte[] in = bytes;
                            if (read < bytes.length) {
                                in = Arrays.copyOf(bytes, read);
                            }
                            String calc = DigestUtils.sha1Hex(in);
                            if (calc == null || !calc.equalsIgnoreCase(pieces[n])) {
                                continue NEXT;
                            }
                        }
                    }

                    // Found an item that matches all piece hashes
                    return item;

                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (--check == 0) {
                    break;
                }
            }
        }
        return null;
    }
}
