package iped.parsers.shareaza;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import iped.data.IItemReader;
import iped.parsers.util.ChildPornHashLookup;
import iped.parsers.util.Messages;
import iped.parsers.util.MetadataUtil;
import iped.parsers.util.P2PUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

public class ShareazaDownloadParser extends AbstractParser {

    private static Logger LOGGER = LoggerFactory.getLogger(ShareazaDownloadParser.class);

    private static final long serialVersionUID = 1L;
    public static final String SHAREAZA_DOWNLOAD_META = "application/x-shareaza-download";

    private static final String HASH_MD5 = "md5";
    private static final String HASH_EDONKEY = "edonkey";
    private static final String HASH_SHA1 = "sha1";

    private static final String META_PREFIX = ExtraProperties.P2P_META_PREFIX;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.parse(SHAREAZA_DOWNLOAD_META));

    private static final String SDL_MAGIC_NOT_FOUND = "ERROR: magic value 'SDL' not found";
    private static final String VERSION_BEFORE_11 = "ERROR: version less than 11 is not supported";
    private static final String VERSION_AFTER_42 = "ERROR: version greater than 42 is not supported";
    private static final String INCOMPLETE_FILE_EX_MESSAGE = "Error during file parsing, possible incomplete or corrupted file.";
    private static final String ERROR_READING_CONTROL_BYTES = "Error reading control bytes, data is possibly corrupted";

    private static final int MAX_BUF_SIZE = 1 << 23;

    static {
        MetadataUtil.setMetadataType(META_PREFIX + "fileSize", Long.class);
        MetadataUtil.setMetadataType(META_PREFIX + "totalDownloaded", Long.class);
        // Commented out because these values are always 0 in the samples tested
        // MetadataUtil.setMetadataType(META_PREFIX + "torrentTotalDownload", Long.class);
        // MetadataUtil.setMetadataType(META_PREFIX + "torrentTotalUpload", Long.class);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("pre");
        try {
            metadata.set(HttpHeaders.CONTENT_TYPE, SHAREAZA_DOWNLOAD_META);
            metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

            IItemSearcher searcher = context.get(IItemSearcher.class);

            IItemReader item = context.get(IItemReader.class);

            processSDFile(stream, handler, xhtml, searcher, metadata, context, item.getPath(), item.getName());

            metadata.set(ExtraProperties.P2P_REGISTRY_COUNT, String.valueOf(1));

        } catch (TikaException | SAXException | IOException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            xhtml.endElement("pre");
            xhtml.endDocument();
        }

    }

    public void processSDFile(InputStream inputStreamFile, ContentHandler handler, XHTMLContentHandler xhtml, IItemSearcher searcher, Metadata metadata, ParseContext context, String evidencePath, String evidenceName)
            throws IOException, SAXException, TikaException {

        DecimalFormat df = new DecimalFormat("#,##0");
        DecimalFormat df2 = new DecimalFormat("#,##0.0");
        StringBuilder sbSources = new StringBuilder();
        StringBuilder sbXML = new StringBuilder();
        StringBuilder sbFile = new StringBuilder();
        StringBuilder sbTorrent = new StringBuilder();
        StringBuilder sbTigerDB = new StringBuilder();
        StringBuilder sbEd2kDB = new StringBuilder();
        StringBuilder sbPreview = new StringBuilder();
        StringBuilder sbReview = new StringBuilder();

        try {
            byte[] data = inputStreamFile.readNBytes(MAX_BUF_SIZE);

            if (data.length == MAX_BUF_SIZE && inputStreamFile.read() != -1) {
                throw new TikaException("File size larger than max memory buffer size");
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            byte[] magicBytes = new byte[3];
            buffer.get(magicBytes);

            String magic = new String(magicBytes);
            int version = buffer.getInt();

            addLine(xhtml, "Magic:                   " + magic);
            addLine(xhtml, "Version:                 " + version);

            if (!magic.equals("SDL")) {
                addLine(xhtml, SDL_MAGIC_NOT_FOUND);
                throw new TikaException(SDL_MAGIC_NOT_FOUND);
            }

            if (version < 11) {
                addLine(xhtml, VERSION_BEFORE_11);
                throw new TikaException(VERSION_BEFORE_11);
            }

            if (version > 42) {
                addLine(xhtml, VERSION_AFTER_42);
                throw new TikaException(VERSION_AFTER_42);
            }

            String fileName = readString(buffer);
            addLine(xhtml, "File Name:               " + fileName);

            String searchTerms = "";

            if (version >= 33) {
                searchTerms = readString(buffer);

                if (searchTerms.equals(""))
                    searchTerms = "<none present>";

            } else {
                searchTerms = "<not expected in this version>";
            }

            addLine(xhtml, "Search Terms:            " + searchTerms);

            byte[] fileLenBytes;
            long fileLen;
            if (version >= 29) {
                fileLenBytes = new byte[8];
                buffer.get(fileLenBytes);

                long fileLenLow = 0;

                fileLenLow += (fileLenBytes[0] & 0xFF);
                fileLenLow += (fileLenBytes[1] & 0xFF) << 8;
                fileLenLow += (fileLenBytes[2] & 0xFF) << 16;
                fileLenLow += (long) (fileLenBytes[3] & 0xFF) << 24;
                fileLenLow += (long) (fileLenBytes[4] & 0xFF) << 32;
                fileLenLow += (long) (fileLenBytes[5] & 0xFF) << 40;
                fileLenLow += (long) (fileLenBytes[6] & 0xFF) << 48;
                fileLenLow += (long) (fileLenBytes[7] & 0xFF) << 56;
                fileLen = fileLenLow;
                double dbFileLen = fileLenLow / 1024.0 / 1024.0;

                addLine(xhtml, "File Length:             " + df.format(fileLenLow) + " Bytes (" + df2.format(dbFileLen) + " MB)");

            } else {
                fileLenBytes = new byte[4];
                fileLen = ByteBuffer.wrap(fileLenBytes).getInt();
                addLine(xhtml, "File Length:             " + fileLen);
            }

            metadata.set(META_PREFIX + "fileSize", Long.toString(fileLen));

            int sha1Valid = readControl4Bytes(buffer);

            IItemReader item = null;
            HashSet<String> hashSets = new HashSet<String>();
            String md5 = null, sha1 = null, edonkey = null;

            if (sha1Valid != 0) {
                sha1 = readHashString(buffer, 20);
                addLine(xhtml, "SHA1:                    " + sha1);
                hashSets.addAll(ChildPornHashLookup.lookupHash(HASH_SHA1, sha1));
                if (item == null) {
                    item = P2PUtil.searchItemInCase(searcher, HASH_SHA1, sha1);
                }
            }

            int sha1Trust = readControl4Bytes(buffer);

            int tigerValid = readControl4Bytes(buffer);

            if (tigerValid != 0) {
                addLine(xhtml, "TIGER:                   " + readHashString(buffer, 24));
            }

            int tigerTrust = readControl4Bytes(buffer);

            if (version >= 22) {
                int md5Valid = readControl4Bytes(buffer);

                if (md5Valid != 0) {
                    md5 = readHashString(buffer, 16);
                    addLine(xhtml, "MD5:                     " + md5);
                    hashSets.addAll(ChildPornHashLookup.lookupHash(HASH_MD5, md5));
                    if (item == null) {
                        item = P2PUtil.searchItemInCase(searcher, HASH_MD5, md5);
                    }
                }

                int md5Trust = readControl4Bytes(buffer);

            }

            if (version >= 13) {
                int edonkeyValid = readControl4Bytes(buffer);

                if (edonkeyValid != 0) {
                    edonkey = readHashString(buffer, 16);
                    addLine(xhtml, "EDONKEY:                 " + edonkey);
                    hashSets.addAll(ChildPornHashLookup.lookupHash(HASH_EDONKEY, edonkey));
                    if (item == null) {
                        item = P2PUtil.searchItemInCase(searcher, HASH_EDONKEY, edonkey);
                    }
                }

                int edonkeyTrust = readControl4Bytes(buffer);
            }

            if (version >= 37) {
                int bthValid = readControl4Bytes(buffer);

                if (bthValid != 0) {
                    addLine(xhtml, "BTH HASH:                " + readHashString(buffer, 20));
                }

                int bthTrust = readControl4Bytes(buffer);

            }

            // Item found in the case?
            if (item != null) {
                metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + item.getHash());
                hashSets.addAll(ChildPornHashLookup.lookupHash(item.getHash()));
                xhtml.newline();
                xhtml.startElement("b");
                xhtml.characters(Messages.getString("KnownMetParser.FoundInCase") + " -> ");
                xhtml.endElement("b");
                AttributesImpl attributes = new AttributesImpl();
                if (item.getHash() != null && !item.getHash().isEmpty())
                    attributes.addAttribute("", "name", "name", "CDATA", item.getHash().toUpperCase());
                xhtml.startElement("span", attributes);

                P2PUtil.printNameWithLink(xhtml, item, item.getName());
                xhtml.endElement("span");
                xhtml.newline();
                xhtml.newline();
            }

            // Any hash found in hashes database?
            if (!hashSets.isEmpty()) {
                metadata.set(ExtraProperties.CSAM_HASH_HITS, "1");
                if (item == null) {
                    xhtml.newline();
                }
                xhtml.startElement("font color=red");
                xhtml.startElement("b");
                addLine(xhtml, Messages.getString("KnownMetParser.FoundInPedoHashDB") + " -> " + hashSets);
                xhtml.endElement("b");
                xhtml.endElement("font");
                xhtml.newline();
            }

            int numberOfSources = read2Bytes(buffer);
            sbSources.append("Sources Count:           " + numberOfSources + "\n");

            for (int i = 0; i < numberOfSources; i++) {
                sbSources.append("     Source " + (i + 1) + ": " + "\n");

                String sourceTerm = readString(buffer);
                sbSources.append("          Source Address:                 " + sourceTerm + "\n");

                int protocolId = read4Bytes(buffer);
                sbSources.append("          Protocol ID:                    " + getProtocolName(protocolId) + "\n");

                int guidValid = read4Bytes(buffer);
                sbSources.append("          GUID Valid:                     " + guidValid + "\n");

                if (guidValid != 0) {
                    sbSources.append("          GUID:                           " + readHashString(buffer, 16) + "\n");
                }

                long port = readUnsignedInt2Bytes(buffer);
                sbSources.append("          Port:                           " + port + "\n");

                if (port != 0) {
                    String ip = read1Byte(buffer) + "." + read1Byte(buffer) + "." + read1Byte(buffer) + "." + read1Byte(buffer);
                    sbSources.append("          IP Adress:                      " + ip + "\n");
                }

                long serverPort = readUnsignedInt2Bytes(buffer);
                sbSources.append("          Server Port:                    " + serverPort + "\n");

                if (serverPort != 0) {
                    String serverIP = read1Byte(buffer) + "." + read1Byte(buffer) + "." + read1Byte(buffer) + "." + read1Byte(buffer);
                    sbSources.append("          Server IP:                      " + serverIP + "\n");
                }

                String serverName = readString(buffer);
                if (serverName.equals("")) {
                    serverName = "<none>";
                }
                sbSources.append("          Server Name:                    " + serverName + "\n");

                int index = read4Bytes(buffer);
                int hashAuth = read4Bytes(buffer);
                int useSha1 = read4Bytes(buffer);
                int useTiger = read4Bytes(buffer);
                int useEdonkey = read4Bytes(buffer);
                int useBith = read4Bytes(buffer);
                int useMd5 = read4Bytes(buffer);
                String serverType = readString(buffer);
                String nickName = readString(buffer);
                String countryCode = readString(buffer);
                String countryName = readString(buffer);
                int speed = read4Bytes(buffer);
                int pushOnly = read4Bytes(buffer);
                int closeConn = read4Bytes(buffer);
                int readContent = read4Bytes(buffer);
                int dateLow = read4Bytes(buffer);
                int dateHigh = read4Bytes(buffer);
                long epoch = getTime(dateLow, dateHigh);
                Date d = new Date(epoch * 1000); // convert epoch seconds to microseconds
                long numPastFraments = readUnsignedInt2Bytes(buffer);

                sbSources.append("          Index:                          " + index + "\n");
                sbSources.append("          Hash Auth:                      " + getBoolStr(hashAuth) + "\n");
                sbSources.append("          Use SHA1:                       " + getBoolStr(useSha1) + "\n");
                sbSources.append("          Use TIGER:                      " + getBoolStr(useTiger) + "\n");
                sbSources.append("          Use EDONKEY:                    " + getBoolStr(useEdonkey) + "\n");
                sbSources.append("          Use BTH:                        " + getBoolStr(useBith) + "\n");
                sbSources.append("          Use MD5:                        " + getBoolStr(useMd5) + "\n");
                sbSources.append("          Server Type:                    " + serverType + "\n");
                sbSources.append("          Nick Name:                      " + (nickName.isBlank() ? "<none>" : nickName) + "\n");
                sbSources.append("          Country Code:                   " + countryCode + "\n");
                sbSources.append("          Country Name:                   " + countryName + "\n");
                sbSources.append("          Speed:                          " + speed + "\n");
                sbSources.append("          Push Only:                      " + getBoolStr(pushOnly) + "\n");
                sbSources.append("          Close Connection:               " + getBoolStr(closeConn) + "\n");
                sbSources.append("          Read Content                    " + getBoolStr(readContent) + "\n");
                sbSources.append("          Last Seen UTC:                  " + d + "\n");
                sbSources.append("          Number of past fragments:       " + numPastFraments + "\n");

                if (numPastFraments != 0) {
                    sbSources.append("          Skipping fragments..." + "\n");

                    for (int j = 0; j < numPastFraments; j++) {
                        read8Bytes(buffer); // begin
                        read8Bytes(buffer); // length
                    }
                }

                if (version >= 39) {
                    int clientExtended = read4Bytes(buffer);
                    sbSources.append("          Client Extended:                " + getBoolStr(clientExtended) + "\n");
                }

                if (version >= 42) {
                    int metaIgnore = read4Bytes(buffer);
                    sbSources.append("          Meta Ignore:                    " + getBoolStr(metaIgnore) + "\n");
                }
            }

            int hasXML = read2Bytes(buffer);

            String str1 = "";

            if (hasXML == 1) {
                sbXML.append("XML File: " + "\n");
                readXMLNode(buffer, xhtml, 0, sbXML);
            }

            int hasFile = read2Bytes(buffer);

            long totalDownloaded = 0;

            if (hasFile == 1) {
                sbFile.append("File: " + "\n");

                long nTotal = read8Bytes(buffer);
                long nRemaining = read8Bytes(buffer);
                int nFragments = read4Bytes(buffer);
                totalDownloaded = nTotal - nRemaining;

                sbFile.append("    Total Size:          " + nTotal + "\n");
                sbFile.append("    Total Remaining:     " + nRemaining + "\n");
                sbFile.append("    Total Downloaded:    " + totalDownloaded + "\n");
                sbFile.append("    Number of Fragments: " + nFragments + "\n");

                metadata.set(META_PREFIX + "totalDownloaded", Long.toString(totalDownloaded));

                for (int i = 0; i < nFragments; i++) {
                    long nRangeBegin = read8Bytes(buffer);
                    long nRangeLength = read8Bytes(buffer);

                    sbFile.append("        Fragment " + (i + 1) + "\n");
                    sbFile.append("            Range Begin: " + nRangeBegin + "\n");
                    sbFile.append("            Range End:   " + (nRangeBegin + nRangeLength) + "\n");
                }

                int nFiles = read4Bytes(buffer);
                sbFile.append("    Number of Files:     " + nFiles + "\n");

                for (int i = 0; i < nFiles; i++) {
                    sbFile.append("        File " + (i + 1) + "\n");
                    str1 = readString(buffer);
                    sbFile.append("            File Path:   " + str1 + "\n");

                    long offSet = read8Bytes(buffer);
                    long size = read8Bytes(buffer);
                    int write = read4Bytes(buffer);
                    str1 = readString(buffer);
                    int nPriority = read4Bytes(buffer);

                    sbFile.append("            Offset:      " + offSet + "\n");
                    sbFile.append("            Size:        " + size + "\n");
                    sbFile.append("            Write:       " + write + "\n");
                    sbFile.append("            File Name:   " + str1 + "\n");
                    sbFile.append("            Priority:    " + nPriority + "\n");

                }
            }

            // TORRENT
            if (version >= 22) {
                int torrentVersion = read4Bytes(buffer);
                int torrentValid = read4Bytes(buffer);

                sbTorrent.append("Torrent Version:         " + torrentVersion + "\n");
                sbTorrent.append("    Valid:               " + torrentValid + "\n");

                if (torrentValid > 0) {

                    String btHash = readHashString(buffer, 20);
                    // int trusted = read4Bytes(buffer);
                    long size = read8Bytes(buffer);
                    int blockSize = read4Bytes(buffer);
                    int blockCount = read4Bytes(buffer);

                    sbTorrent.append("    BitTorrent Hash:     " + btHash + "\n");
                    sbTorrent.append("    Size:                " + size + "\n");
                    sbTorrent.append("    BlockSize:           " + blockSize + "\n");
                    sbTorrent.append("    BlockCount:          " + blockCount + "\n");

                    for (int i = 0; i < blockCount; ++i) {
                        btHash = readHashString(buffer, 20);
                        sbTorrent.append("    		BitTorrent Hash: [" + i + "]:         " + btHash + "\n");
                    }

                    long totalUpload = read8Bytes(buffer);
                    long totalDownload = read8Bytes(buffer);
                    String name = readString(buffer);
                    int encoding = read4Bytes(buffer);
                    String comment = readString(buffer);
                    int creationDate = read4Bytes(buffer);
                    String createdBy = readString(buffer);
                    int privateStr = read4Bytes(buffer);
                    int fileCount = read2Bytes(buffer);

                    sbTorrent.append("    TotalUpload:         " + totalUpload + "\n");
                    sbTorrent.append("    TotalDownload:       " + totalDownload + "\n");
                    sbTorrent.append("    Name:                " + name + "\n");
                    sbTorrent.append("    Encoding:            " + encoding + "\n");
                    sbTorrent.append("    Comment:             " + comment + "\n");
                    sbTorrent.append("    CreationDate:        " + creationDate + "\n");
                    sbTorrent.append("    CreatedBy:           " + createdBy + "\n");
                    sbTorrent.append("    Private:             " + getBoolStr(privateStr) + "\n");
                    sbTorrent.append("    File Count:          " + fileCount + "\n");

                    // Commented out because these values are always 0 in the samples tested
                    // metadata.set(META_PREFIX + "torrentTotalDownload", Long.toString(totalDownload));
                    // metadata.set(META_PREFIX + "torrentTotalUpload", Long.toString(totalUpload));

                    for (int i = 0; i < fileCount; i++) {
                        sbTorrent.append("          File [" + i + "]:" + "\n");

                        long btSize = read8Bytes(buffer);
                        String btPath = readString(buffer);
                        String btName = readString(buffer);
                        sbTorrent.append("              Size:                           " + btSize + "\n");
                        sbTorrent.append("              Path:                           " + btPath + "\n");
                        sbTorrent.append("              Name:                           " + btName + "\n");

                        // TODO: Check why some files don't have hashes
                        if (readControl4Bytes(buffer) != 0) {
                            String btSHA1 = readHashString(buffer, 20);
                            sbTorrent.append("              SHA1:                           " + btSHA1 + "\n");
                        }

                        if (readControl4Bytes(buffer) != 0) {
                            String btEDONKEY = readHashString(buffer, 16);
                            sbTorrent.append("              EDONKEY:                        " + btEDONKEY + "\n");
                        }

                        if (readControl4Bytes(buffer) != 0) {
                            String btTIGER = readHashString(buffer, 24);
                            sbTorrent.append("              TIGER:                          " + btTIGER + "\n");
                        }

                        if (readControl4Bytes(buffer) != 0) {
                            String btMD5 = readHashString(buffer, 16);
                            sbTorrent.append("              MD5:                            " + btMD5 + "\n");
                        }

                        // Torrent file header
                        padding(buffer, 10);

                        // Torrent file size
                        int torrentFileSize = read4Bytes(buffer);
                        sbTorrent.append("              Torrent File Size:              " + torrentFileSize + "\n");

                        // Torrent File
                        byte[] torrentFile = readString(buffer, torrentFileSize);
                        if (torrentFile != null && torrentFile.length > 0) {
                            Metadata torrentMeta = new Metadata();
                            torrentMeta.set(TikaCoreProperties.TITLE, evidenceName + ".torrent");
                            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
                            extractor.parseEmbedded(new ByteArrayInputStream(torrentFile), handler, torrentMeta, true);
                        }
                    }

                    int trackerIndex = read4Bytes(buffer);
                    int trackerMode = read4Bytes(buffer);
                    int nTrackers = read2Bytes(buffer);

                    sbTorrent.append("    TrackerIndex:        " + trackerIndex + "\n");
                    sbTorrent.append("    TrackerMode:         " + trackerMode + "\n");
                    sbTorrent.append("    Trackers Count       " + nTrackers + "\n");

                    for (int i = 0; i < nTrackers; i++) {
                        sbTorrent.append("      Tracker[" + i + "]: " + "\n");
                        String address = readString(buffer);
                        int lastAccess = read4Bytes(buffer);
                        int lastSuccess = read4Bytes(buffer);
                        int nextTry = read4Bytes(buffer);
                        int failures = read4Bytes(buffer);
                        int tier = read4Bytes(buffer);
                        int type = read4Bytes(buffer);

                        sbTorrent.append("    			Address:          " + address + "\n");
                        sbTorrent.append("    			LastAccess:       " + lastAccess + "\n");
                        sbTorrent.append("    			LastSuccess:      " + lastSuccess + "\n");
                        sbTorrent.append("    			NextTry:          " + nextTry + "\n");
                        sbTorrent.append("    			Failures:         " + failures + "\n");
                        sbTorrent.append("    			Tier:             " + tier + "\n");
                        sbTorrent.append("    			Type:             " + type + "\n");
                    }

                    int length = read2Bytes(buffer);
                    sbTorrent.append("    Source Length:       " + length + "\n");

                    if (length > 0) {
                        String nSource = readHashString(buffer, length);
                        int infoStart = read4Bytes(buffer);
                        int infoSize = read4Bytes(buffer);

                        sbTorrent.append("    Source:                " + nSource + "\n");
                        sbTorrent.append("    InfoStart:             " + infoStart + "\n");
                        sbTorrent.append("    InfoSize:              " + infoSize + "\n");
                    }

                    if (version >= 23) {
                        sbTorrent.append("    TorrentSuccess:      " + read2Bytes(buffer) + "\n");
                        String torrentBlock = readHashString(buffer, blockCount);
                        // sbTorrent.append(" TorrentBlock: " + torrentBlock +"\n");
                        sbTorrent.append("    Seeding:             " + read2Bytes(buffer) + "\n");
                    }
                }
            }

            // TIGER HASH DATABASE
            int pTreeSize = read4Bytes(buffer);
            if (pTreeSize > 0) {
                sbTigerDB.append("TIGER Hash Database:"+ "\n");
                sbTigerDB.append("     Tree Size:               " + pTreeSize + "\n");

                int hashTigerDatabaseSize = hashTigerDataBaseSize(pTreeSize);

                sbTigerDB.append("     Hash Database Size:      " + hashTigerDatabaseSize + "\n");

                padding(buffer, hashTigerDatabaseSize);

                int tigerBlock = read4Bytes(buffer);
                int tigerSize = read4Bytes(buffer);
                int tigerSuccess = read4Bytes(buffer);

                sbTigerDB.append("     Block:                   " + tigerBlock + "\n");
                sbTigerDB.append("     Size:                    " + tigerSize + "\n");
                sbTigerDB.append("     Success:                 " + tigerSuccess + "\n");

                padding(buffer, tigerBlock);
            }

            // ED2K HASH DATABASE
            int ed2kListSize = read4Bytes(buffer);
            sbEd2kDB.append("EDONKEY Hash Database:" + "\n");
            sbEd2kDB.append("     List Size:               " + ed2kListSize + "\n");

            int ed2kListSerialSize = getEd2kListSerialSize(ed2kListSize);
            sbEd2kDB.append("     Hash Database Size:      " + ed2kListSerialSize + "\n");

            padding(buffer, ed2kListSerialSize);

            if (ed2kListSize > 0) {
                int hashsetBlock = read4Bytes(buffer);
                int hashsetSuccess = read4Bytes(buffer);

                sbEd2kDB.append("     Hashset Block Length:    " + hashsetBlock + "\n");
                sbEd2kDB.append("     Hashset Success:         " + hashsetSuccess + "\n");

                padding(buffer, hashsetBlock);
            }

            int numPreviews = read2Bytes(buffer);
            sbPreview.append("Number of Previews:      " + numPreviews + "\n");

            for (int i = 0; i < numPreviews; i++) {
                String preview = readString(buffer);
                sbPreview.append("     Preview " + (i + 1) + ":           " + preview + "\n");

            }

            if (version >= 32) {
                int numReviews = read2Bytes(buffer);
                sbReview.append("Number of Reviews:       " + numReviews + "\n");

                if (numReviews > 0) {
                    for (int i = 0; i < numReviews; i++) {
                        sbReview.append("     Review " + (i + 1) + ":" + "\n");

                        long userIP = read8Bytes(buffer);
                        int userPicture = read4Bytes(buffer);
                        String userName = readString(buffer);
                        int fileRating = read4Bytes(buffer);
                        String fileComments = readString(buffer);

                        sbReview.append("     	User IP:       " + userIP + "\n");
                        sbReview.append("     	User Picture:  " + userPicture + "\n");
                        sbReview.append("     	User Name:     " + userName + "\n");
                        sbReview.append("     	File Rating:   " + fileRating + "\n");
                        sbReview.append("     	File Comments: " + fileComments + "\n");
                    }
                }
            }

            int expanded = read4Bytes(buffer);
            addLine(xhtml, "Expanded:                " + getBoolStr(expanded));

            int paused = read4Bytes(buffer);
            addLine(xhtml, "Paused:                  " + getBoolStr(paused));

            int boosted = read4Bytes(buffer);
            addLine(xhtml, "Boosted:                 " + getBoolStr(boosted));

            int shared = read4Bytes(buffer);
            String sharedStr = getBoolStr(shared);
            addLine(xhtml, "Shared:                  " + sharedStr);
            metadata.set(META_PREFIX + "sharedFlag", sharedStr);

            boolean sharedBool = Util.TRI_STATE_TRUE.equals(sharedStr) && totalDownloaded > 0;
            if (sharedBool) {
                metadata.set(META_PREFIX + "shared", Boolean.toString(true));
                if (md5 != null) {
                    metadata.add(ExtraProperties.SHARED_HASHES, md5);
                }
                if (sha1 != null) {
                    metadata.add(ExtraProperties.SHARED_HASHES, sha1);
                }
                if (edonkey != null) {
                    metadata.add(ExtraProperties.SHARED_HASHES, edonkey);
                }
            }

            String serialID = readHashString(buffer, 4);
            addLine(xhtml, "Serial ID:               " + serialID);

        } catch (BufferUnderflowException ex) {
            addLine(xhtml, INCOMPLETE_FILE_EX_MESSAGE);
            throw new TikaException(INCOMPLETE_FILE_EX_MESSAGE, ex);
        } finally {
            addLines(xhtml, sbFile);
            addLines(xhtml, sbPreview);
            addLines(xhtml, sbReview);
            addLines(xhtml, sbXML);
            addLines(xhtml, sbTorrent);
            addLines(xhtml, sbSources);
            addLines(xhtml, sbEd2kDB);
            addLines(xhtml, sbTigerDB);
        }
    }

    public int hashTigerDataBaseSize(int nHeight) {
        int height = nHeight;
        int nodeCount = 1;
        for (int nStep = height; nStep > 0; nStep--) {
            nodeCount *= 2;

            // overflow
            if (nodeCount < 0) {
                height = 0;
                nodeCount = 1;
                break;
            }
        }
        nodeCount--;

        return ((nodeCount * (24 + 1)));

    }

    public int getEd2kListSerialSize(int nListSize) {
        int nSize = 0;
        if (nListSize > 0)
            nSize += 16;
        if (nListSize > 1)
            nSize += 16 * nListSize;
        return nSize;

    }

    public String getBoolStr(int num) {
        if (num == 0) {
            return Util.TRI_STATE_FALSE;
        } else if (num == 1) {
            return Util.TRI_STATE_TRUE;
        } else {
            return Util.TRI_STATE_UNKNOWN;
        }
    }

    public String getProtocolName(int num) {
        switch (num) {
            case -1:
                return "PROTOCOL_ANY";
            case 0:
                return "PROTOCOL_NULL";
            case 1:
                return "PROTOCOL_G1";
            case 2:
                return "PROTOCOL_G2";
            case 3:
                return "PROTOCOL_ED2K";
            case 4:
                return "PROTOCOL_HTTP";
            case 5:
                return "PROTOCOL_FTP";
            case 6:
                return "PROTOCOL_BT";
            case 7:
                return "PROTOCOL_KAD";
            case 8:
                return "PROTOCOL_DC";
            case 9:
                return "PROTOCOL_LAST";
            default:
                return "<none>";
        }

    }

    public void padding(ByteBuffer buffer, int i) throws BufferUnderflowException {
        byte[] read = new byte[i];

        if (buffer.remaining() < i) {
            throw new BufferUnderflowException();
        }

        buffer.get(read);
    }

    public String readString(ByteBuffer buffer) throws BufferUnderflowException, TikaException {
        byte[] read = new byte[4];
        buffer.get(read);
        int length = readMiniHeader(read);
        if (length > 0) {
            int stringSize = length * 2;
            byte[] stringBytes = new byte[stringSize];
            if (buffer.remaining() < stringSize) {
                throw new BufferUnderflowException();
            }
            buffer.get(stringBytes);
            return new String(stringBytes, StandardCharsets.UTF_16LE);
        }
        return "";
    }

    public byte[] readString(ByteBuffer buffer, int length) throws BufferUnderflowException {
        if (length > 0) {
            byte[] stringBytes = new byte[length];
            if (buffer.remaining() < length) {
                throw new BufferUnderflowException();
            }
            buffer.get(stringBytes);
            return stringBytes;
        }
        return null;
    }

    public int readUnsignedInt2Bytes(ByteBuffer buffer) {

        byte[] b = new byte[2];
        buffer.get(b);

        int i = 0;
        i |= b[1] & 0xFF;
        i <<= 8;
        i |= b[0] & 0xFF;
        return i;

    }

    public int read1Byte(ByteBuffer buffer) {
        byte[] read = new byte[1];
        buffer.get(read);
        return Byte.toUnsignedInt(read[0]);

    }

    public int read2Bytes(ByteBuffer buffer) {
        byte[] read = new byte[2];
        buffer.get(read);
        return ByteBuffer.wrap(read).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    public int read4Bytes(ByteBuffer buffer) {
        byte[] read = new byte[4];
        buffer.get(read);
        return ByteBuffer.wrap(read).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public int readControl4Bytes(ByteBuffer buffer) throws TikaException {
        byte[] read = new byte[4];
        buffer.get(read);
        int number = ByteBuffer.wrap(read).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (number != 1 && number != 0) {
            throw new TikaException(ERROR_READING_CONTROL_BYTES);
        }
        return number;
    }

    public long read8Bytes(ByteBuffer buffer) throws BufferUnderflowException {
        byte[] read = new byte[8];
        if (buffer.remaining() < 8) {
            throw new BufferUnderflowException();
        }
        buffer.get(read);
        return ByteBuffer.wrap(read).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    public String readHashString(ByteBuffer buffer, int len) {
        byte[] read = new byte[len];
        buffer.get(read);

        StringBuilder sb = new StringBuilder();
        for (byte b : read) {
            sb.append(String.format("%02X", b));
        }

        return sb.toString();
    }

    public int readMiniHeader(byte[] ldata) throws TikaException {
        int h1 = ldata[0] & 0xFF;
        int h2 = ldata[1] & 0xFF;
        int h3 = ldata[2] & 0xFF;
        int len = ldata[3] & 0xFF;
        if (h1 != 0xFF || h2 != 0xFE || h3 != 0xFF) {
            throw new TikaException("Header FF-FE-FF not found: " + h1 + "-" + h2 + "-" + h3);
        }
        return len;
    }

    public int getTime(int lo, int hi) {
        int t;
        if (lo == 0 && hi == 0) {
            t = 0;
        } else {
            lo -= 0xd53e8000;
            hi -= 0x019db1de;
            t = (int) (hi * 429.4967296 + lo / 1e7);
        }
        if (t < 0) {
            t = 0;
        }
        return t;
    }

    public void readXMLNode(ByteBuffer buffer, XHTMLContentHandler xhtml, int level, StringBuilder sbXML) throws BufferUnderflowException, TikaException {

        String node = "";
        String str1 = "";
        String str2 = "";

        char[] aux = new char[(level + 1) * 4];
        Arrays.fill(aux, ' ');
        String align = new String(aux);

        node = readString(buffer);
        readString(buffer);
        sbXML.append(align + "<" + node);

        int numAttr = read2Bytes(buffer);
        for (int i = 0; i < numAttr; i++) {
            str1 = readString(buffer);
            str2 = readString(buffer);
            sbXML.append(" " + str1 + "=\"" + str2 + "\" ");
        }
        sbXML.append(">" + "\n");

        int numElement = read2Bytes(buffer);

        for (int i = 0; i < numElement; i++) {
            readXMLNode(buffer, xhtml, level + 1, sbXML);
        }

        sbXML.append(align + "</" + node + ">" + "\n");
    }

    public String getHash(MessageDigest digest) {
        byte[] sum = digest.digest();
        BigInteger bigInt = new BigInteger(1, sum);
        String output = bigInt.toString(16);
        int len = sum.length * 2;
        StringBuilder sb = new StringBuilder(len);
        int add = len - output.length();
        for (int i = 0; i < add; i++) {
            sb.append('0');
        }
        sb.append(output);
        return sb.toString();
    }

    private void addLine(XHTMLContentHandler xhtml, String value) throws SAXException {
        xhtml.characters(value);
        xhtml.newline();
    }

    private void addLines(XHTMLContentHandler xhtml, StringBuilder sb) throws SAXException {
        String[] lines = sb.toString().split("\\n");
        for (String s : lines) {
            xhtml.characters(s);
            xhtml.newline();
        }
    }

}