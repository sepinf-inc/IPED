package iped.parsers.shareaza;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
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
import iped.parsers.util.ExportFolder;
import iped.parsers.util.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

public class ShareazaDownloadParser extends AbstractParser {

    @SuppressWarnings("unused")
    private static Logger LOGGER = LoggerFactory.getLogger(ShareazaDownloadParser.class);

    private static final long serialVersionUID = 1L;
    public static final String SHAREAZA_DOWNLOAD_META = "application/x-shareaza-download";

    private static final String HASH_MD5 = "md5";
    private static final String HASH_EDONKEY = "edonkey";
    private static final String HASH_SHA1 = "sha1";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.parse(SHAREAZA_DOWNLOAD_META));

    private static final String INCOMPLETE_FILE_EX_MESSAGE = "Error during file parser, possible incomplete or corrupted file.";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try {
            metadata.set(HttpHeaders.CONTENT_TYPE, SHAREAZA_DOWNLOAD_META);
            metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

            IItemSearcher searcher = context.get(IItemSearcher.class);

            IItemReader item = context.get(IItemReader.class);

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();

            xhtml.startElement("body");
            xhtml.startElement("pre");

            processSDFile(stream, xhtml, searcher, metadata, item.getPath());

            xhtml.endElement("pre");
            xhtml.endElement("body");
            xhtml.endDocument();

            metadata.set(ExtraProperties.P2P_REGISTRY_COUNT, String.valueOf(1));
            metadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void processSDFile(InputStream inputStreamFile, XHTMLContentHandler xhtml, IItemSearcher searcher,
            Metadata metadata, String evidencePath) throws IOException, SAXException {

        DecimalFormat df = new DecimalFormat("#,##0");
        DecimalFormat df2 = new DecimalFormat("#,##0.0");

        try {
            long fileSize = inputStreamFile.available();
            byte[] data = new byte[(int) fileSize];

            inputStreamFile.read(data);

            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            byte[] magicBytes = new byte[3];
            buffer.get(magicBytes);

            String magic = new String(magicBytes);
            int version = buffer.getInt();

            addLine(xhtml, "MAGIC:                          " + magic);
            addLine(xhtml, "VERSION:                        " + version);

            if (!magic.equals("SDL")) {
                addLine(xhtml, "ERROR: magic value 'SDL' not found");
                return;
            }

            if (version < 11) {
                addLine(xhtml, "ERROR: actually only version greater than 11 is supported");
                return;
            }

            if (version > 42) {
                addLine(xhtml, "ERROR: actually only version less or equal to 42 is supported");
                return;
            }

            String fileName = readString(buffer);
            addLine(xhtml, "FILE NAME:                      " + fileName);

            String searchTerms = "";

            if (version >= 33) {
                searchTerms = readString(buffer);

                if (searchTerms.equals(""))
                    searchTerms = "<none present>";

            } else {
                searchTerms = "<not expected in this version>";
            }

            addLine(xhtml, "SEARCH TERMS:                   " + searchTerms);

            byte[] fileLenBytes;

            if (version >= 29) {
                fileLenBytes = new byte[8];
                buffer.get(fileLenBytes);

                int fileLenLow = 0;

                fileLenLow += (fileLenBytes[0] & 0xFF);
                fileLenLow += (fileLenBytes[1] & 0xFF) << 8;
                fileLenLow += (fileLenBytes[2] & 0xFF) << 16;
                fileLenLow += (fileLenBytes[3] & 0xFF) << 24;
                fileLenLow += (fileLenBytes[4] & 0xFF) << 32;
                fileLenLow += (fileLenBytes[5] & 0xFF) << 40;
                fileLenLow += (fileLenBytes[6] & 0xFF) << 48;
                fileLenLow += (fileLenBytes[7] & 0xFF) << 56;

                double dbFileLen = fileLenLow / 1024.0 / 1024.0;

                addLine(xhtml, "FILE LENGTH:                    " + df.format(fileLenLow) + " Bytes ("
                        + df2.format(dbFileLen) + " MB)");

            } else {
                fileLenBytes = new byte[4];
                int fileLen = ByteBuffer.wrap(fileLenBytes).getInt();
                addLine(xhtml, "FILE LENGTH:                    " + fileLen);
            }

            int sha1Valid = readControl4Bytes(buffer);
            addLine(xhtml, "SHA1_VALID:                     " + sha1Valid);

            IItemReader item = null;
            HashSet<String> hashSets = new HashSet<String>();

            if (sha1Valid != 0) {
                String hash = readHashString(buffer, 20);
                metadata.add(ExtraProperties.SHARED_HASHES, hash);
                addLine(xhtml, "SHA1:                           " + hash);
                hashSets.addAll(ChildPornHashLookup.lookupHash(HASH_SHA1, hash));
                if (item == null) {
                    item = searchItemInCase(searcher, HASH_SHA1, hash);
                }
            }

            int sha1Trust = readControl4Bytes(buffer);
            addLine(xhtml, "SHA1_TRUST:                     " + sha1Trust);

            int tigerValid = readControl4Bytes(buffer);
            addLine(xhtml, "TIGER_VALID:                    " + tigerValid);

            if (tigerValid != 0) {
                addLine(xhtml, "TIGER:                          " + readHashString(buffer, 24));
            }

            int tigerTrust = readControl4Bytes(buffer);
            addLine(xhtml, "TIGER_TRUST:                    " + tigerTrust);

            if (version >= 22) {
                int md5Valid = readControl4Bytes(buffer);
                addLine(xhtml, "MD5_VALID:                      " + md5Valid);

                if (md5Valid != 0) {
                    String hash = readHashString(buffer, 16);
                    metadata.add(ExtraProperties.SHARED_HASHES, hash);
                    addLine(xhtml, "MD5:                            " + hash);
                    hashSets.addAll(ChildPornHashLookup.lookupHash(HASH_MD5, hash));
                    if (item == null) {
                        item = searchItemInCase(searcher, HASH_MD5, hash);
                    }
                }

                int md5Trust = readControl4Bytes(buffer);
                addLine(xhtml, "MD5_TRUST:                      " + md5Trust);

            }

            if (version >= 13) {
                int edonkeyValid = readControl4Bytes(buffer);
                addLine(xhtml, "EDONKEY_VALID:                  " + edonkeyValid);

                if (edonkeyValid != 0) {
                    String hash = readHashString(buffer, 16);
                    metadata.add(ExtraProperties.SHARED_HASHES, hash);
                    addLine(xhtml, "EDONKEY:                        " + hash);
                    hashSets.addAll(ChildPornHashLookup.lookupHash(HASH_EDONKEY, hash));
                    if (item == null) {
                        item = searchItemInCase(searcher, HASH_EDONKEY, hash);
                    }
                }

                int edonkeyTrust = readControl4Bytes(buffer);
                addLine(xhtml, "EDONKEY_TRUST:                  " + edonkeyTrust);
            }

            if (version >= 37) {
                int bthValid = readControl4Bytes(buffer);
                addLine(xhtml, "BTH_VALID:                      " + bthValid);

                if (bthValid != 0) {
                    addLine(xhtml, "BTH HASH:                       " + readHashString(buffer, 20));
                }

                int bthTrust = readControl4Bytes(buffer);
                addLine(xhtml, "BTH_TRUST:                      " + bthTrust);

            }

            // Item found in the case?
            if (item != null) {
                hashSets.addAll(ChildPornHashLookup.lookupHash(item.getHash()));
                xhtml.newline();
                xhtml.startElement("b");
                xhtml.characters(Messages.getString("KnownMetParser.FoundInCase") + " -> ");
                xhtml.endElement("b");
                AttributesImpl attributes = new AttributesImpl();
                if (item.getHash() != null && !item.getHash().isEmpty())
                    attributes.addAttribute("", "name", "name", "CDATA", item.getHash().toUpperCase());
                xhtml.startElement("span", attributes);

                printNameWithLink(xhtml, item, item.getName());
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
            addLine(xhtml, "NUM_OF_SOURCES:                 " + numberOfSources);

            for (int i = 0; i < numberOfSources; i++) {
                addLine(xhtml, "     SOURCE " + (i + 1) + ": ");

                String sourceTerm = readString(buffer);
                addLine(xhtml, "          SOURCE ADDRESS:                 " + sourceTerm);

                int protocolId = read4Bytes(buffer);
                addLine(xhtml, "          PROTOCOL_ID:                    " + getProtocolName(protocolId));

                int guidValid = read4Bytes(buffer);
                addLine(xhtml, "          GUID_VALID:                     " + guidValid);

                if (guidValid != 0) {
                    addLine(xhtml, "          GUID_VALUE:                     " + readHashString(buffer, 16));
                }

                long port = readUnsignedInt2Bytes(buffer);
                addLine(xhtml, "          PORT:                           " + port);

                if (port != 0) {
                    String ip = read1Byte(buffer) + "." + read1Byte(buffer) + "." + read1Byte(buffer) + "."
                            + read1Byte(buffer);
                    addLine(xhtml, "          IP_ADDRESS:                     " + ip);
                }

                long serverPort = readUnsignedInt2Bytes(buffer);
                addLine(xhtml, "          SERVER_PORT:                    " + serverPort);

                if (serverPort != 0) {
                    String serverIP = read1Byte(buffer) + "." + read1Byte(buffer) + "." + read1Byte(buffer) + "."
                            + read1Byte(buffer);
                    addLine(xhtml, "          SERVER_IP:                      " + serverIP);
                }

                String serverName = readString(buffer);
                if (serverName.equals("")) {
                    serverName = "<none>";
                }
                addLine(xhtml, "          SERVER NAME:                    " + serverName);

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

                addLine(xhtml, "          INDEX:                          " + index);
                addLine(xhtml, "          HASH_AUTH:                      " + getBoolStr(hashAuth));
                addLine(xhtml, "          Use_SHA1:                       " + getBoolStr(useSha1));
                addLine(xhtml, "          Use_TIGER:                      " + getBoolStr(useTiger));
                addLine(xhtml, "          Use_EDONKEY:                    " + getBoolStr(useEdonkey));
                addLine(xhtml, "          Use_BTH:                        " + getBoolStr(useBith));
                addLine(xhtml, "          Use_MD5:                        " + getBoolStr(useMd5));
                addLine(xhtml, "          SERVER TYPE:                    " + serverType);
                addLine(xhtml,
                        "          NICK NAME:                      " + (nickName.isBlank() ? "<none>" : nickName));
                addLine(xhtml, "          COUNTRY CODE:                   " + countryCode);
                addLine(xhtml, "          COUNTRY NAME:                   " + countryName);
                addLine(xhtml, "          SPEED:                          " + speed);
                addLine(xhtml, "          Push Only:                      " + getBoolStr(pushOnly));
                addLine(xhtml, "          Close Connection:               " + getBoolStr(closeConn));
                addLine(xhtml, "          Read Content                    " + getBoolStr(readContent));
                addLine(xhtml, "          Last Seen UTC:                  " + d);
                addLine(xhtml, "          Number of past fragments:       " + numPastFraments);

                if (numPastFraments != 0) {
                    addLine(xhtml, "          Skipping fragments...");

                    for (int j = 0; j < numPastFraments; j++) {
                        read8Bytes(buffer); // begin
                        read8Bytes(buffer); // length
                    }
                }

                if (version >= 39) {
                    int clientExtended = read4Bytes(buffer);
                    addLine(xhtml, "          Client Extended:                " + getBoolStr(clientExtended));
                }

                if (version >= 42) {
                    int metaIgnore = read4Bytes(buffer);
                    addLine(xhtml, "          Meta Ignore:                    " + getBoolStr(metaIgnore));
                }
            }

            int hasXML = read2Bytes(buffer);

            String str1 = "";

            if (hasXML == 1) {
                addLine(xhtml, "XML FILE: ");
                readXMLNode(buffer, xhtml, 0);
            }

            int hasFile = read2Bytes(buffer);

            if (hasFile == 1) {
                addLine(xhtml, "FILE: ");

                long nTotal = read8Bytes(buffer);
                long nRemaning = read8Bytes(buffer);
                int nFragments = read4Bytes(buffer);
                long notStart = nTotal - nRemaning;

                addLine(xhtml, "    TOTAL SIZE:          " + nTotal);
                addLine(xhtml, "    TOTAL REMANING:      " + nRemaning);
                addLine(xhtml, "    TOTAL DOWNLOADED:    " + notStart);
                addLine(xhtml, "    NUMBER OF FRAGMENTS: " + nFragments);

                for (int i = 0; i < nFragments; i++) {
                    long nRangeBegin = read8Bytes(buffer);
                    long nRangeLength = read8Bytes(buffer);

                    addLine(xhtml, "        FRAGMENT " + (i + 1));
                    addLine(xhtml, "            RANGE BEGIN: " + nRangeBegin);
                    addLine(xhtml, "            RANGE END:   " + (nRangeBegin + nRangeLength));
                }

                int nFiles = read4Bytes(buffer);
                addLine(xhtml, "    NUMBER OF FILES:     " + nFiles);

                for (int i = 0; i < nFiles; i++) {
                    addLine(xhtml, "        FILE " + (i + 1));
                    str1 = readString(buffer);
                    addLine(xhtml, "            FILE PATH:   " + str1);

                    long offSet = read8Bytes(buffer);
                    long size = read8Bytes(buffer);
                    int write = read4Bytes(buffer);
                    str1 = readString(buffer);
                    int nPriority = read4Bytes(buffer);

                    addLine(xhtml, "            OFFSET:      " + offSet);
                    addLine(xhtml, "            SIZE:        " + size);
                    addLine(xhtml, "            WRITE:       " + write);
                    addLine(xhtml, "            FILE NAME:   " + str1);
                    addLine(xhtml, "            PRIORITY:    " + nPriority);

                }
            }

            // TORRENT
            if (version >= 22) {
                int torrentVersion = read4Bytes(buffer);
                int torrentValid = read4Bytes(buffer);

                    addLine(xhtml, "TORRENT VERSION:         " + torrentVersion);
                    addLine(xhtml, "    VALID:               " + torrentValid);

                if (torrentValid > 0) {

                    String btHash = readHashString(buffer, 20);
                    //int trusted = read4Bytes(buffer);
                    long size = read8Bytes(buffer);
                    int blockSize = read4Bytes(buffer);
                    int blockCount = read4Bytes(buffer);

                    addLine(xhtml, "    BT HASH:             " + btHash);
                    addLine(xhtml, "    Size:                " + size);
                    addLine(xhtml, "    BlockSize:           " + blockSize);
                    addLine(xhtml, "    BlockCount:          " + blockCount);

                    for (int i = 0; i < blockCount; ++i) {
                        btHash = readHashString(buffer, 20);
                        addLine(xhtml, "    		BT HASH [" + i + "]:         " + btHash);
                    }

                    long totalUpload = read8Bytes(buffer);
                    long totalDownload = read8Bytes(buffer);
                    String name = readString(buffer);
                    int encoding = read4Bytes(buffer);
                    String comment = readString(buffer);
                    int creationDate = read4Bytes(buffer);
                    String createdBy = readString(buffer);
                    int privateStr = read2Bytes(buffer);
                    int fileCount = read4Bytes(buffer);

                    addLine(xhtml, "    TotalUpload:         " + totalUpload);
                    addLine(xhtml, "    TotalDownload:       " + totalDownload);
                    addLine(xhtml, "    Name:                " + name);
                    addLine(xhtml, "    Encoding:            " + encoding);
                    addLine(xhtml, "    Comment:             " + comment);
                    addLine(xhtml, "    CreationDate:        " + creationDate);
                    addLine(xhtml, "    CreatedBy:           " + createdBy);
                    addLine(xhtml, "    Private:             " + getBoolStr(privateStr));
                    addLine(xhtml, "    FileCount:           " + fileCount);

                    for (int i = 0; i < fileCount; i++) {
                        addLine(xhtml, "    		BT FILE [" + i + "]:         " + btHash);

                        long btSize = read8Bytes(buffer);
                        String btPath = readString(buffer);
                        String btName = readString(buffer);
                        String btSHA1 = readHashString(buffer, 20);
                        String btEDONKEY = readHashString(buffer, 16);
                        String btTIGER = readHashString(buffer, 24);
                        String btMD5 = readHashString(buffer, 16);

                        addLine(xhtml, "    			Size:                           " + btSize);
                        addLine(xhtml, "    			Path:                           " + btPath);
                        addLine(xhtml, "    			Name:                           " + btName);
                        addLine(xhtml, "    			SHA1:                           " + btSHA1);
                        addLine(xhtml, "    			EDONKEY:                        " + btEDONKEY);
                        addLine(xhtml, "    			TIGER:                          " + btTIGER);
                        addLine(xhtml, "    			MD5:                            " + btMD5);
                    }

                    int trackerIndex = read4Bytes(buffer);
                    int trackerMode = read4Bytes(buffer);
                    int nTrackers = read4Bytes(buffer);
                    
                    addLine(xhtml, "    TrackerIndex:        " + trackerIndex);
                    addLine(xhtml, "    TrackerMode:         " + trackerMode);
                    addLine(xhtml, "    Trackers:            " + nTrackers);

                    for (int i = 0; i < nTrackers; i++) {
                        addLine(xhtml, "    nTrackers[" + i + "]:   " + fileCount);

                        String address = readString(buffer);
                        int lastAccess = read4Bytes(buffer);
                        int lastSuccess = read4Bytes(buffer);
                        int nextTry = read4Bytes(buffer);
                        int failures = read4Bytes(buffer);
                        int tier = read4Bytes(buffer);
                        int type = read4Bytes(buffer);

                        addLine(xhtml, "    			Address:          " + address);
                        addLine(xhtml, "    			LastAccess:       " + lastAccess);
                        addLine(xhtml, "    			LastSuccess:      " + lastSuccess);
                        addLine(xhtml, "    			NextTry:          " + nextTry);
                        addLine(xhtml, "    			Failures:         " + failures);
                        addLine(xhtml, "    			Tier:             " + tier);
                        addLine(xhtml, "    			Type:             " + type);
                    }
                    
                    int length = read4Bytes(buffer);
                    addLine(xhtml, "    Source Length:       " + length);

                    if (length > 0) {
                        String nSource = readHashString(buffer, length);
                        int infoStart = read4Bytes(buffer);
                        int infoSize = read4Bytes(buffer);

                        addLine(xhtml, "    Source:                " + nSource);
                        addLine(xhtml, "    InfoStart:             " + infoStart);
                        addLine(xhtml, "    InfoSize:              " + infoSize);
                    }

                    if (version >= 23) {
                        addLine(xhtml, "    TorrentSuccess:      " + read4Bytes(buffer));
                        addLine(xhtml, "    TorrentBlock:        " + readHashString(buffer, blockCount));
                        addLine(xhtml, "    SEEDING:             " + read2Bytes(buffer));
                    }
                }

            }

            // TIGER HASH DATABASE
            int pTreeSize = read4Bytes(buffer);
            if (pTreeSize > 0) {

                addLine(xhtml, "TIGER [TREE SIZE]:         " + pTreeSize);

                int hashTigerDatabaseSize = hashTigerDataBaseSize(pTreeSize);

                addLine(xhtml, "TIGER [HASH DATABASE SIZE]:         " + hashTigerDatabaseSize);

                padding(buffer, hashTigerDatabaseSize);

                int tigerBlock = read4Bytes(buffer);
                int tigerSize = read4Bytes(buffer);
                int tigerSuccess = read4Bytes(buffer);

                addLine(xhtml, "TIGER [TIGER BLOCK] :         " + tigerBlock);
                addLine(xhtml, "TIGER [TIGER SIZE]  :         " + tigerSize);
                addLine(xhtml, "TIGER [TIGER SUCESS]:         " + tigerSuccess);

                padding(buffer, tigerBlock);
            }

            // ED2K HASH DATABASE
            int ed2kListSize = read4Bytes(buffer);
            addLine(xhtml, "EDONKEY HASH DATABASE:");
            addLine(xhtml, "     EDONKEY [LIST SIZE]:                " + ed2kListSize);

            int ed2kListSerialSize = getEd2kListSerialSize(ed2kListSize);
            addLine(xhtml, "     EDONKEY [HASH DATABASE SIZE]:       " + ed2kListSerialSize);

            padding(buffer, ed2kListSerialSize);

            if (ed2kListSize > 0) {
                int hashsetBlock = read4Bytes(buffer);
                int hashsetSuccess = read4Bytes(buffer);

                addLine(xhtml, "     EDONKEY [HASHSET BLOCK LENGTH]:     " + hashsetBlock);
                addLine(xhtml, "     EDONKEY [HASHSET SUCCESS]:          " + hashsetSuccess);

                padding(buffer, hashsetBlock);
            }

            int numPreviews = read2Bytes(buffer);
            addLine(xhtml, "NUMBER OF PREVIEWS:      " + numPreviews);

            for (int i = 0; i < numPreviews; i++) {
                String preview = readString(buffer);
                addLine(xhtml, "    PREVIEW " + (i + 1) + ":           " + preview);

            }

            if (version >= 32) {
                int numReviews = read2Bytes(buffer);
                addLine(xhtml, "NUMBER OF REVIEWS:       " + numReviews);

                if (numReviews > 0) {
                    for (int i = 0; i < numReviews; i++) {
                        addLine(xhtml, "     REVIEW " + (i + 1) + ":");

                        long userIP = read8Bytes(buffer);
                        int userPicture = read4Bytes(buffer);
                        String userName = readString(buffer);
                        int fileRating = read4Bytes(buffer);
                        String fileComments = readString(buffer);

                        addLine(xhtml, "     	USER IP:       " + userIP);
                        addLine(xhtml, "     	USER PICTURE:  " + userPicture);
                        addLine(xhtml, "     	USER NAME:     " + userName);
                        addLine(xhtml, "     	FILE RATING:   " + fileRating);
                        addLine(xhtml, "     	FILE COMMENTS: " + fileComments);
                    }
                }
            }

            int expanded = read4Bytes(buffer);
            int paused = read4Bytes(buffer);
            int boosted = read4Bytes(buffer);
            int shared = read4Bytes(buffer);
            String serialID = readHashString(buffer, 4);

            addLine(xhtml, "EXPANDED:                " + getBoolStr(expanded));
            addLine(xhtml, "PAUSED:                  " + getBoolStr(paused));
            addLine(xhtml, "BOOSTED:                 " + getBoolStr(boosted));
            addLine(xhtml, "SHARED:                  " + getBoolStr(shared));
            addLine(xhtml, "SERIAL ID:               " + serialID);

        } catch (BufferUnderflowException ex) {
            addLine(xhtml, INCOMPLETE_FILE_EX_MESSAGE + " Evidence=" + evidencePath);
            LOGGER.warn(INCOMPLETE_FILE_EX_MESSAGE + " Evidence=" + evidencePath);
        } catch (Exception ex) {
            addLine(xhtml, INCOMPLETE_FILE_EX_MESSAGE + " Evidence=" + evidencePath);
            LOGGER.warn(INCOMPLETE_FILE_EX_MESSAGE + " Evidence=" + evidencePath);
            ex.printStackTrace();
        }
    }

    public int hashTigerDataBaseSize(int nHeight) throws IOException {
        long maxSize = 4294967295l / 2;
        int height = nHeight;
        int nodeCount = 1;
        for (int nStep = height; nStep > 0; nStep--) {
            nodeCount *= 2;

            if (nodeCount > maxSize) {
                height = 0;
                nodeCount = 0;
                break;
            }
        }
        nodeCount--;

        return ((nodeCount * (24 + 1)));

    }

    public int getEd2kListSerialSize(int nListSize) throws IOException {
        int nSize = 0;
        if (nListSize > 0)
            nSize += 16;
        if (nListSize > 1)
            nSize += 16 * nListSize;
        return nSize;

    }

    public String getBoolStr(int num) {
        if (num == 0) {
            return "FALSE";
        } else if (num == 1) {
            return "TRUE";
        } else {
            return "<none>";
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

    public String readString(ByteBuffer buffer) throws BufferUnderflowException {
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

    public int readControl4Bytes(ByteBuffer buffer) throws Exception {
        byte[] read = new byte[4];
        buffer.get(read);
        int number = ByteBuffer.wrap(read).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (number != 1 && number != 0) {
            throw new Exception();
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

    public int readMiniHeader(byte[] ldata) {
        int h1 = ldata[0] & 0xFF;
        int h2 = ldata[1] & 0xFF;
        int h3 = ldata[2] & 0xFF;
        int len = ldata[3] & 0xFF;
        if (h1 != 0xFF || h2 != 0xFE || h3 != 0xFF) {
            throw new RuntimeException("ERROR: not found 'header' FF-FE-FF:" + h1 + ":" + h2 + ":" + h3 + ":" + len);
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

    public void readXMLNode(ByteBuffer buffer, XHTMLContentHandler xhtml, int level) throws Exception {

        String node = "";
        String str1 = "";
        String str2 = "";

        char[] aux = new char[(level + 1) * 4];
        Arrays.fill(aux, ' ');
        String align = new String(aux);

        node = readString(buffer);
        readString(buffer);
        xhtml.characters(align + "<" + node);

        int numAttr = read2Bytes(buffer);
        for (int i = 0; i < numAttr; i++) {
            str1 = readString(buffer);
            str2 = readString(buffer);
            xhtml.characters(" " + str1 + "=\"" + str2 + "\" ");
        }
        addLine(xhtml, ">");

        int numElement = read2Bytes(buffer);

        for (int i = 0; i < numElement; i++) {
            readXMLNode(buffer, xhtml, level + 1);
        }

        addLine(xhtml, align + "</" + node + ">");
    }

    public String getHash(MessageDigest digest) throws Exception {
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

    public String getMD5(File file) throws Exception {
        MessageDigest dgMD5 = MessageDigest.getInstance("MD5");
        byte[] b = Files.readAllBytes(file.toPath());
        dgMD5.update(b);
        return getHash(dgMD5);
    }

    public static void printNameWithLink(XHTMLContentHandler xhtml, IItemReader item, String name) throws SAXException {
        String hashPath = getPathFromHash(new File("../../../../", ExportFolder.getExportPath()), //$NON-NLS-1$
                item.getHash(), item.getExt());

        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "onclick", "onclick", "CDATA", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "app.open(\"" + BasicProps.HASH + ":" + item.getHash() + "\")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        attributes.addAttribute("", "href", "href", "CDATA", hashPath); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        xhtml.startElement("a", attributes); //$NON-NLS-1$
        xhtml.characters(name);
        xhtml.endElement("a"); //$NON-NLS-1$
    }

    private static String getPathFromHash(File baseDir, String hash, String ext) {
        if (hash == null || hash.length() < 2)
            return ""; //$NON-NLS-1$
        StringBuilder path = new StringBuilder();
        hash = hash.toUpperCase();
        path.append(hash.charAt(0)).append('/');
        path.append(hash.charAt(1)).append('/');
        path.append(hash).append('.').append(ext);
        File result = new File(baseDir, path.toString());
        return result.getPath();
    }

    public static IItemReader searchItemInCase(IItemSearcher searcher, String hashAlgo, String hash) {
        if (searcher == null) {
            return null;
        }
        List<IItemReader> items = searcher.search(hashAlgo + ":" + hash); //$NON-NLS-1$
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    private void addLine(XHTMLContentHandler xhtml, String value) throws SAXException {
        xhtml.characters(value);
        xhtml.newline();
    }
}