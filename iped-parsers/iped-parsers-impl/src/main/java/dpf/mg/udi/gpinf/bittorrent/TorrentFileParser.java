package dpf.mg.udi.gpinf.bittorrent;

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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.Messages;

/**
 * Parser for Torrent Files
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class TorrentFileParser extends AbstractParser {

    private static final long serialVersionUID = 3238363426940179831L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-bittorrent")); //$NON-NLS-1$
    private static final String TORRENT_FILE_MIME_TYPE = "application/x-bittorrent"; //$NON-NLS-1$
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
        
        boolean [] include = { true, true, false, false, false };
        for (FileInTorrent file : files) {
            if (! file.md5.isEmpty() ) include[2] = true;
            if (! file.sha1.isEmpty() ) include[3] = true;
            if (! file.ed2k.isEmpty() ) include[4] = true;
        }

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

        xhtml.startElement("div", "class", "dt"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$

        xhtml.startElement("div", "class", "rh"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
        for (int i = 0; i < header.length; i++) {
            if (include[i]) {
                xhtml.startElement("div", "class", "b"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
                xhtml.characters(header[i]);
                xhtml.endElement("div"); //$NON-NLS-1$
            }
        }
        xhtml.endElement("div"); //$NON-NLS-1$

        boolean a = true;
        for (FileInTorrent file : files) {
            xhtml.startElement("div", "class", a ? "ra" : "rb"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$
            String[] rowElements = new String[] { file.fullPath, Long.toString(file.length), file.md5, file.sha1, file.ed2k };
            for (int i = 0; i < rowElements.length; i++) {
                if (include[i]) {
                    xhtml.startElement("div", "class", "a"); //$NON-NLS-1$ $NON-NLS-2$ $NON-NLS-3$
                    if (rowElements[i].equals("")) { //$NON-NLS-1$
                        rowElements[i] = " "; //$NON-NLS-1$
                    }
                    xhtml.characters(rowElements[i]);
                    xhtml.endElement("div"); //$NON-NLS-1$
                }
            }
            xhtml.endElement("div"); //$NON-NLS-1$
            a = !a;
        }

        xhtml.endElement("div"); //$NON-NLS-1$
        xhtml.endDocument();
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

}
