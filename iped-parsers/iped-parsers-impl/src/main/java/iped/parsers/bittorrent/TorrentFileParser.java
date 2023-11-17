package iped.parsers.bittorrent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
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

import iped.parsers.util.Messages;
import iped.utils.LocalizedFormat;

/**
 * Parser for Torrent Files
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class TorrentFileParser extends AbstractParser {

    private static final long serialVersionUID = 3238363426940179831L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-bittorrent")); //$NON-NLS-1$
    public static final String TORRENT_FILE_MIME_TYPE = "application/x-bittorrent"; //$NON-NLS-1$
    private static final String[] header = { Messages.getString("TorrentFileDatParser.FullPath"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.FileSize"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.MD5"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.SHA1"), //$NON-NLS-1$
            Messages.getString("TorrentFileDatParser.ED2K") //$NON-NLS-1$
    };

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
            dict = new BencodedDict(stream, df);
        } catch (IOException e) {
            throw new TikaException("Error parsing torrent file", e); //$NON-NLS-1$
        }

        List<FileInTorrent> files = extractFileList(dict);

        char[] colClass = { 'a', 'c', 'h', 'h', 'h' };
        boolean[] include = { true, true, false, false, false };
        for (FileInTorrent file : files) {
            if (!file.md5.isEmpty())
                include[2] = true;
            if (!file.sha1.isEmpty())
                include[3] = true;
            if (!file.ed2k.isEmpty())
                include[4] = true;
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

        boolean a = true;
        for (FileInTorrent file : files) {
            xhtml.startElement("tr", "class", a ? "ra" : "rb"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$
            String[] rowElements = new String[] { file.fullPath, Long.toString(file.length), file.md5, file.sha1,
                    file.ed2k };
            for (int i = 0; i < rowElements.length; i++) {
                if (include[i]) {
                    xhtml.startElement("td", "class", String.valueOf(colClass[i])); //$NON-NLS-1$ $NON-NLS-2$
                    if (rowElements[i].equals("")) { //$NON-NLS-1$
                        rowElements[i] = " "; //$NON-NLS-1$
                    } else if (i == 1) {
                        // File length column
                        try {
                            rowElements[i] = LocalizedFormat.format(Long.parseLong(rowElements[i]));
                        } catch (Exception e) {
                        }
                    }
                    xhtml.characters(rowElements[i]);
                    xhtml.endElement("td"); //$NON-NLS-1$
                }
            }
            xhtml.endElement("tr"); //$NON-NLS-1$
            a = !a;
        }
        xhtml.endElement("table"); //$NON-NLS-1$

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

            a = true;
            for (int i = 0; i < info.pieces.length; i++) {
                xhtml.startElement("tr", "class", a ? "ra" : "rb");
                xhtml.startElement("td", "class", "c");
                xhtml.characters(LocalizedFormat.format(i + 1));
                xhtml.endElement("td");
                xhtml.startElement("td", "class", "h");
                xhtml.characters(info.pieces[i]);
                xhtml.endElement("td");
                xhtml.endElement("tr");
                a = !a;
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

    private static List<FileInTorrent> extractFileList(BencodedDict dict) throws TikaException {
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
                    file.sha1 = fileDict.getHexEncodedBytes("sha1"); //$NON-NLS-1$
                    file.ed2k = fileDict.getHexEncodedBytes("ed2k"); //$NON-NLS-1$
                    files.add(file);
                }

            } else {
                // Single file torrent
                FileInTorrent file = new FileInTorrent();
                file.fullPath = info.getString("name"); //$NON-NLS-1$
                file.length = info.getLong("length"); //$NON-NLS-1$
                file.md5 = info.getString("md5sum"); //$NON-NLS-1$
                file.sha1 = info.getHexEncodedBytes("sha1"); //$NON-NLS-1$
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
                        torrentInfo.pieces[i] = Hex.encodeHexString(sha1).toUpperCase();
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
}
