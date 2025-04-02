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
import java.util.HashSet;
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

    private static final String TORRENT_CREATION_DATE = ExtraProperties.P2P_META_PREFIX + "torrentCreationDate";
    public static final String TORRENT_INFO_HASH = ExtraProperties.P2P_META_PREFIX + "torrentInfoHash";
    public static final String TORRENT_FILES_FOUND_IN_CASE = ExtraProperties.P2P_META_PREFIX
            + "torrentFilesFoundInCase";

    private static final long serialVersionUID = 3238363426940179831L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-bittorrent")); //$NON-NLS-1$
    public static final String TORRENT_FILE_MIME_TYPE = "application/x-bittorrent"; //$NON-NLS-1$
    private static final String[] header = { Messages.getString("TorrentFileDatParser.File"),
            Messages.getString("TorrentFileDatParser.FullPath"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.FileSize"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.MD5"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.SHA1"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.ED2K"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.FileFoundInCase"),
            Messages.getString("TorrentFileDatParser.PathInCase") 
    };
    private static final String strConfirmedPieces = Messages.getString("TorrentFileDatParser.ConfirmedPieces");
    private static final String strAtOffset = Messages.getString("TorrentFileDatParser.AtOffset");

    private static final String strYes = Messages.getString("TorrentFileDatParser.Yes");
    
    private static final String padding = "_____padding_file";
    
    private static final int maxPieceLength = 1 << 26;
    private static final long minFileLength = 1 << 16;
    private static final long maxFileLength = 1L << 34;
    private static final int maxHitsCheck = 64;
    private static final int minPiecesMultiFile = 8;
    
    // Length of valid hex-encoded hashes
    private static final int md5Len = 32;
    private static final int sha1Len = 40;
    private static final int edonkeyLen = 32;

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

        char[] colClass = { 'c', 'a', 'c', 'h', 'h', 'h', 'b', 'a' };
        boolean[] include = { true, true, true, false, false, false, false, false };
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
                        + ".a { min-width: 200px; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; } "
                        + ".b { min-width: 100px; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; } "
                        + ".c { min-width: 80px; border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word; } "
                        + ".d { font-weight: bold; background-color:#AAAAEE; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; white-space: nowrap; } "
                        + ".h { min-width: 200px; font-weight: bold; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; white-space: nowrap; font-family: monospace; } "
                        + ".s { font-size: x-small; } ");
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.newline();

        if (dict.isIncomplete()) {
            xhtml.characters("* "
                    + Messages.getString("TorrentFileDatParser.IncompleteWarning"));
            metadata.set("incompleteTorrent", "true");
        }

        TorrentInfo info = extractTorrentInfo(dict);

        // Try to link the items
        if (searcher != null && !files.isEmpty() && info.pieces != null && info.pieceLength != null
                && info.pieceLength > 0 && info.pieceLength < maxPieceLength) {
            long tot = 0;
            for (int i = 0; i < files.size(); i++) {
                FileInTorrent file = files.get(i);
                if (file.item == null) {
                    ItemPiecesMatchInfo itemPiecesMatchInfo = searchAndMatchFileInCase(searcher, file.length,
                            info.pieceLength, info.pieces,
                            (int) (tot / info.pieceLength), (int) (tot % info.pieceLength), i == files.size() - 1);
                    if (itemPiecesMatchInfo != null) {
                        file.item = itemPiecesMatchInfo.item;
                        file.itemPiecesMatchInfo = itemPiecesMatchInfo;
                        metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + file.item.getHash());
                    }
                }
                tot += file.length;
            }
        }

        int foundInCase = 0;
        int paddingEntries = 0;
        for (FileInTorrent file : files) {
            if (file.fullPath == null || file.fullPath.toLowerCase().contains(padding)) {
                paddingEntries++;
            } else if (file.item != null) {
                include[6] = true;
                include[7] = true;
                foundInCase++;
            }
        }

        // Torrent General Info Table
        xhtml.startElement("table", "class", "dt");
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.Name"), info.name);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.InfoHash"), info.infoHash, true);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.PieceLength"), info.pieceLength);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.NumberOfPieces"), info.numPieces);
        outputInfo(xhtml, Messages.getString("TorrentFileDatParser.NumberOfFiles"), Long.valueOf(files.size() - paddingEntries));
        if (foundInCase > 0) {
            outputInfo(xhtml, Messages.getString("TorrentFileDatParser.FilesFoundInCase"), Long.valueOf(foundInCase));
        }
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
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        
        // Set infoHash metadata
        if (info.infoHash != null && !info.infoHash.isBlank()) {
            metadata.set(TORRENT_INFO_HASH, info.infoHash);
        }

        // Set the number of files found in case metadata
        if (foundInCase > 0) {
            metadata.set(TORRENT_FILES_FOUND_IN_CASE, String.valueOf(foundInCase));
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

        int row = 0;
        for (int i = 0; i < files.size(); i++) {
            FileInTorrent file = files.get(i);
            if (file.fullPath == null || file.fullPath.toLowerCase().contains(padding)) {
                // Ignore padding entries
                continue;
            }
            xhtml.startElement("tr", "class", row % 2 == 0 ? "ra" : "rb"); 
            String[] rowElements = new String[] { String.valueOf(++row), file.fullPath, Long.toString(file.length),
                    file.md5, file.sha1, file.ed2k, "", file.item == null ? "" : file.item.getPath() };
            for (int col = 0; col < rowElements.length; col++) {
                if (include[col]) {
                    String str = rowElements[col];
                    xhtml.startElement("td", "class", String.valueOf(colClass[col]));
                    if (str.length() == 0) {
                        str = " ";
                    } else if (col == 0 || col == 2) {
                        // Row number and File length columns
                        try {
                            str = LocalizedFormat.format(Long.parseLong(str));
                        } catch (Exception e) {
                        }
                    }
                    if (col == 1 && file.item != null) {
                        P2PUtil.printNameWithLink(xhtml, file.item, str);
                    } else if (col == 6) {
                        outputMatchInfo(xhtml, file.itemPiecesMatchInfo);
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

    private static void outputMatchInfo(XHTMLContentHandler xhtml, ItemPiecesMatchInfo itemPiecesMatchInfo)
            throws SAXException {
        if (itemPiecesMatchInfo != null) {
            xhtml.characters(strYes);
            xhtml.startElement("br");
            xhtml.startElement("span", "class", "s");
            xhtml.characters("(" + strConfirmedPieces + " ");
            xhtml.characters(LocalizedFormat.format(itemPiecesMatchInfo.startPiece + 1));
            xhtml.characters("-");
            xhtml.characters(LocalizedFormat.format(itemPiecesMatchInfo.finalPiece + 1));
            if (itemPiecesMatchInfo.startOffset != 0) {
                xhtml.characters(" " + strAtOffset + " ");
                xhtml.characters(LocalizedFormat.format(itemPiecesMatchInfo.startOffset));
            }
            xhtml.characters(")");
            xhtml.endElement("span");
        }
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

    private static List<FileInTorrent> extractFileList(BencodedDict dict, Metadata metadata, IItemSearcher searcher)
            throws TikaException {
        BencodedDict info = dict.getDict("info");
        List<FileInTorrent> files;

        if (info != null) {
            if (info.containsKey("files")) {
                // Multi-file torrent
                List<Object> filesList = info.getList("files");
                if (filesList == null) {
                    throw new TikaException("Error parsing torrent file");
                }
                String name = info.getString("name");
                files = new ArrayList<>(filesList.size());
                for (Object fileDictObj : filesList) {
                    BencodedDict fileDict = (BencodedDict) fileDictObj;
                    FileInTorrent file = new FileInTorrent();
                    StringBuilder fullPathBuilder = new StringBuilder();
                    if (name != null && 0 != name.length()) {
                        fullPathBuilder.append(name).append(File.separator);
                    }
                    for (String elem : fileDict.getListOfStrings("path")) {
                        fullPathBuilder.append(elem).append(File.separator);
                    }
                    fullPathBuilder.deleteCharAt(fullPathBuilder.length() - 1);
                    file.fullPath = fullPathBuilder.toString();
                    file.length = fileDict.getLong("length");
                    file.md5 = getStringOrBytes(fileDict, "md5sum", md5Len);
                    file.sha1 = getStringOrBytes(fileDict, "sha1", sha1Len);
                    file.ed2k = getStringOrBytes(fileDict, "ed2k", edonkeyLen);
                    files.add(file);
                }

            } else {
                // Single file torrent
                FileInTorrent file = new FileInTorrent();
                file.fullPath = info.getString("name");
                file.length = info.getLong("length");
                file.md5 = getStringOrBytes(info, "md5sum", md5Len);
                file.sha1 = getStringOrBytes(info, "sha1", sha1Len);
                file.ed2k = getStringOrBytes(info, "ed2k", edonkeyLen);
                files = Collections.singletonList(file);
            }

            if (files.isEmpty()) {
                throw new TikaException("Error parsing torrent file");
            }

            // Try to link files to case items by hash
            for (FileInTorrent file : files) {
                if (file.length > 0) {
                    linkTorrentToItem(searcher, metadata, file, "md5", file.md5, md5Len);
                    linkTorrentToItem(searcher, metadata, file, "sha-1", file.sha1, sha1Len);
                    linkTorrentToItem(searcher, metadata, file, "edonkey", file.ed2k, edonkeyLen);
                }
            }

        } else {
            throw new TikaException("Error parsing torrent file");
        }
        return files;
    }

    private static String getStringOrBytes(BencodedDict dict, String key, int len) {
        String s = dict.getString(key).trim();
        if (s.length() > 0 && s.length() != len) {
            s = dict.getHexEncodedBytes(key);
            if (s.length() != len) {
                // Discard if hex-encoded string length does not match the expected length
                s = "";
            }
        } else {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                    // Discard if string has any non hexadecimal character
                    s = "";
                    break;
                }
            }
        }
        return s;
    }

    private static void linkTorrentToItem(IItemSearcher searcher, Metadata metadata, FileInTorrent file, String key,
            String val, int len) {
        if (val.length() == len) {
            metadata.add(ExtraProperties.LINKED_ITEMS, key + ":" + val);
            if (file.item == null) {
                file.item = P2PUtil.searchItemInCase(searcher, key, val);
            }
        }
    }

    private static class FileInTorrent {
        ItemPiecesMatchInfo itemPiecesMatchInfo;
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

    private static class ItemPiecesMatchInfo {
        IItemReader item;
        int startPiece;
        int finalPiece;
        int startOffset;
    }

    private static ItemPiecesMatchInfo searchAndMatchFileInCase(IItemSearcher searcher, long fileLength,
            long pieceLength,
            String[] pieces, final int firstPiece, final int offset, final boolean isLast) {
        if (fileLength < minFileLength || fileLength > maxFileLength) {
            return null;
        }
        int totPieces = (int) ((fileLength + pieceLength - 1 + offset) / pieceLength);
        if (firstPiece + totPieces > pieces.length || ((!isLast || offset != 0) && totPieces < minPiecesMultiFile)) {
            return null;
        }
        List<IItemReader> items = searcher.search(BasicProps.LENGTH + ":" + fileLength);
        if (items == null || items.isEmpty()) {
            return null;
        }
        byte[] bytes = new byte[(int) pieceLength];
        int check = maxHitsCheck;
        Set<String> seen = new HashSet<String>();
        for (int step = 0; step <= 1; step++) {
            NEXT: for (int i = 0; i < items.size(); i++) {
                IItemReader item = items.get(i);
                if (step == 0 ^ (item.isCarved() || item.isDeleted())) {
                    // In the first step check only "active" items
                    // Carved and deleted items are checked later
                    continue;
                }
                if (item.getHash() == null || !seen.add(item.getHash())) {
                    continue;
                }
                // Check if the all pieces hashes match
                try (BufferedInputStream is = item.getBufferedInputStream()) {
                    ItemPiecesMatchInfo itemPiecesMatchInfo = new ItemPiecesMatchInfo();
                    itemPiecesMatchInfo.startOffset = offset;
                    if (offset > 0) {
                        int read = is.readNBytes(bytes, 0, bytes.length - offset);
                        if (read < bytes.length - offset) {
                            continue NEXT;
                        }
                    }
                    int n = offset == 0 ? 0 : 1;
                    itemPiecesMatchInfo.startPiece = n + firstPiece;
                    for (; n < totPieces; n++) {
                        int read = is.readNBytes(bytes, 0, bytes.length);
                        if (read < 0 || (read < bytes.length && n < totPieces - 1)) {
                            continue NEXT;
                        }
                        if (read == bytes.length || isLast) {
                            byte[] in = bytes;
                            if (read < bytes.length) {
                                in = Arrays.copyOf(bytes, read);
                            }
                            String calc = DigestUtils.sha1Hex(in);
                            if (calc == null || !calc.equalsIgnoreCase(pieces[n + firstPiece])) {
                                continue NEXT;
                            }
                        }
                    }
                    itemPiecesMatchInfo.finalPiece = n + firstPiece - 1;
                    itemPiecesMatchInfo.item = item;

                    // Found an item that matches all pieces hashes
                    return itemPiecesMatchInfo;

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
