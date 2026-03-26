/*
 * Copyright 2025, Calil Khalil (Hakal)
 *
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.parsers.zoomdpapi;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.Util;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

/**
 * Parser for Zoom client forensic data on Windows systems.
 *
 * Triggered by Zoom.us.ini files (MIME type application/x-zoom-dpapi-ini).
 * Reads the DPAPI-encrypted OSKEY from the INI, optionally decrypts it
 * using master keys found in the evidence, then opens the encrypted
 * Zoom SQLite databases and extracts meetings, messages, participants,
 * files, recordings, and timeline events.
 *
 * Follows the same pattern as WhatsAppParser: emits a full report as
 * virtual child item with HTML, and individual messages as sub-children.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomDpapiParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(ZoomDpapiParser.class);

    public static final String ZOOM = "Zoom";

    public static final MediaType ZOOM_INI = MediaType.application("x-zoom-dpapi-ini");
    public static final MediaType ZOOM_MEETING = MediaType.parse("application/x-zoom-meeting");
    public static final MediaType ZOOM_MESSAGE = MediaType.parse("message/x-zoom-message");

    private static final String ZOOM_SECTION = "[ZoomChat]";
    private static final String KEY_NAME = "win_osencrypt_key";
    private static final String KEY_PREFIX = "ZWOSKEY";

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(ZOOM_INI);

    private boolean extractMessages = true;
    private String decryptedOskey = null;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    @Field
    public void setDecryptedOskey(String decryptedOskey) {
        this.decryptedOskey = decryptedOskey;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        IItemSearcher searcher = context.get(IItemSearcher.class);
        ItemInfo itemInfo = context.get(ItemInfo.class);
        IItemReader currentItem = context.get(IItemReader.class);

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        try {
            TikaInputStream tis = TikaInputStream.get(stream);

            byte[] iniBytes = org.apache.commons.io.IOUtils.toByteArray(tis);
            String iniContent = new String(iniBytes, StandardCharsets.UTF_8);
            String iniPath = itemInfo != null ? itemInfo.getPath() : "";
            String evidenceUUID = currentItem != null ? currentItem.getDataSource().getUUID() : null;
            logger.info("Processing Zoom.us.ini ({} bytes) from: {}", iniBytes.length, iniPath);

            String encryptedBlob = extractEncryptedKey(iniContent);
            if (encryptedBlob == null) {
                logger.warn("No encrypted OSKEY found in Zoom.us.ini: {}", iniPath);
                return;
            }
            logger.info("Extracted encrypted OSKEY blob ({} chars)", encryptedBlob.length());

            String oskey = decryptedOskey;
            if (oskey != null) {
                logger.info("Using pre-configured decryptedOskey");
            } else {
                logger.info("No pre-configured OSKEY, attempting DPAPI decryption...");
                oskey = tryDecryptOskey(encryptedBlob, itemInfo, searcher);
            }
            if (oskey == null) {
                logger.warn("Could not decrypt Zoom OSKEY. Set decryptedOskey parameter or provide DPAPI master keys.");
                return;
            }
            logger.info("OSKEY available, proceeding with database extraction");

            String sid = extractSidFromPath(iniPath, searcher);
            String userProfileName = ZoomReportGenerator.extractUsername(iniPath);

            ZoomDataExtractor dataExtractor = new ZoomDataExtractor();
            if (sid != null) {
                try {
                    dataExtractor.setLocalDecryptor(new LocalDataDecryptor(sid));
                } catch (Exception e) {
                    logger.warn("Failed to create LocalDataDecryptor with SID: {}", sid);
                }
            }

            ZoomUserAccount account = null;
            ZoomSystemInfo sysInfo = null;
            List<ZoomParticipant> participants = new ArrayList<>();
            List<ZoomMessage> messages = new ArrayList<>();
            List<ZoomMeeting> meetings = new ArrayList<>();
            List<ZoomMeeting> savedMeetings = new ArrayList<>();
            List<ZoomSharedFile> files = new ArrayList<>();
            List<ZoomTimelineEvent> timeline = new ArrayList<>();

            File zoomusDb = findDatabaseFile("zoomus.enc.db", oskey, iniPath, searcher, evidenceUUID);
            if (zoomusDb != null) {
                try (Connection conn = new ZoomDatabaseReader(zoomusDb, oskey).createConnection()) {
                    account = dataExtractor.extractUserAccount(conn);
                    participants = dataExtractor.extractParticipants(conn);
                    sysInfo = dataExtractor.extractKeyValues(conn, account, savedMeetings);

                } catch (Exception e) {
                    logger.warn("Failed to extract from zoomus.enc.db", e);
                }
            }

            File meetingDb = findDatabaseFile("zoommeeting.enc.db", oskey,  iniPath, searcher, evidenceUUID);
            String[] meetingIds = null;
            if (meetingDb != null) {
                try (Connection conn = new ZoomDatabaseReader(meetingDb, oskey).createConnection()) {
                    meetingIds = dataExtractor.extractMeetingIds(conn);
                    messages = dataExtractor.extractMessages(conn);
                    files.addAll(dataExtractor.extractFilesFromChat(conn, messages));
                } catch (Exception e) {
                    logger.warn("Failed to extract from zoommeeting.enc.db", e);
                }
            }

            if (meetingIds != null) {
                String confId = meetingIds[0];
                String sdkUid = meetingIds[1];
                for (ZoomMeeting m : savedMeetings) {
                    if (m.getConfId() == null && m.getTopic() != null) {
                        m.setConfId(confId);
                        m.setMeetingId(sdkUid);
                    }
                }
            }

            File calendarDb = findDatabaseFile("calendar-history-meeting.enc.db", oskey, iniPath, searcher, evidenceUUID);
            if (calendarDb != null) {
                try (Connection conn = new ZoomDatabaseReader(calendarDb, oskey).createConnection()) {
                    meetings = dataExtractor.extractMeetings(conn);
                    files.addAll(dataExtractor.extractSharedFiles(conn));
                } catch (Exception e) {
                    logger.warn("Failed to extract from calendar-history-meeting.enc.db", e);
                }
            }

            for (ZoomMeeting saved : savedMeetings) {
                meetings.add(0, saved);
            }

            if (zoomusDb != null) {
                try (Connection conn = new ZoomDatabaseReader(zoomusDb, oskey).createConnection()) {
                    timeline = dataExtractor.extractTimeline(conn, messages, files);
                } catch (Exception e) {
                    logger.warn("Failed to extract timeline", e);
                }
            }

            assignDataToMeetings(meetings, messages, files, participants);

            int virtualId = 0;
            ZoomReportGenerator reportGen = new ZoomReportGenerator();

            for (ZoomMeeting meeting : meetings) {
                int meetingVirtualId = virtualId++;
                String confLabel = meeting.getMeetingNo() != null ? meeting.getMeetingNo()
                        : (meeting.getConfId() != null ? meeting.getConfId()
                        : (meeting.getTopic() != null ? meeting.getTopic() : "Unknown"));

                byte[] reportHtml = reportGen.generateMeetingReport(
                        sid, oskey, userProfileName, account, sysInfo, timeline, meeting);

                Metadata meetingMeta = new Metadata();
                meetingMeta.set(TikaCoreProperties.TITLE,
                        "Meeting: " + confLabel + " Report");
                meetingMeta.set(StandardParser.INDEXER_CONTENT_TYPE, ZOOM_MEETING.toString());
                meetingMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(meetingVirtualId));
                meetingMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                if (meeting.getStartTime() > 0) {
                    meetingMeta.set(TikaCoreProperties.CREATED, new Date(meeting.getStartTime() * 1000));
                }
                for (ZoomParticipant p : meeting.getParticipants()) {
                    meetingMeta.add(ExtraProperties.PARTICIPANTS, p.getName());
                }
                String meetingId = meeting.getMeetingId() != null ? meeting.getMeetingId()
                        : (meeting.getConfId() != null ? meeting.getConfId() : confLabel);
                meetingMeta.set(ExtraProperties.CONVERSATION_ID, meetingId);
                meetingMeta.set(ExtraProperties.CONVERSATION_NAME, confLabel);
                meetingMeta.set(ExtraProperties.CONVERSATION_MESSAGES_COUNT,
                        meeting.getMessages().size());
                if (!meeting.getParticipants().isEmpty()) {
                    for (ZoomParticipant p : meeting.getParticipants()) {
                        meetingMeta.add(ExtraProperties.CONVERSATION_PARTICIPANTS, p.getName());
                    }
                } else {
                    java.util.LinkedHashSet<String> senders = new java.util.LinkedHashSet<>();
                    for (ZoomMessage msg : meeting.getMessages()) {
                        if (msg.getSenderName() != null) {
                            senders.add(msg.getSenderName());
                        }
                    }
                    for (String sender : senders) {
                        meetingMeta.add(ExtraProperties.CONVERSATION_PARTICIPANTS, sender);
                    }
                }
                if (extractMessages && !meeting.getMessages().isEmpty()) {
                    meetingMeta.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
                }

                extractor.parseEmbedded(new ByteArrayInputStream(reportHtml), handler, meetingMeta, false);

                if (extractMessages) {
                    int msgCount = 0;
                    for (ZoomMessage msg : meeting.getMessages()) {
                        Metadata msgMeta = new Metadata();
                        msgMeta.set(TikaCoreProperties.TITLE, "Zoom Message " + msgCount++);
                        msgMeta.set(StandardParser.INDEXER_CONTENT_TYPE, ZOOM_MESSAGE.toString());
                        msgMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(meetingVirtualId));
                        msgMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                        if (msg.getDate() != null) {
                            msgMeta.set(ExtraProperties.MESSAGE_DATE, msg.getDate());
                            msgMeta.set(TikaCoreProperties.CREATED, msg.getDate());
                        }
                        if (msg.getBody() != null) {
                            msgMeta.set(ExtraProperties.MESSAGE_BODY, msg.getBody());
                        }
                        if (msg.getSenderName() != null) {
                            msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, msg.getSenderName());
                        }
                        msgMeta.set(ExtraProperties.USER_ACCOUNT_TYPE, ZOOM);
                        msgMeta.set(BasicProps.LENGTH, "");
                        extractor.parseEmbedded(new EmptyInputStream(), handler, msgMeta, false);
                        virtualId++;
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error parsing Zoom data", e);
            throw new TikaException("Error parsing Zoom DPAPI data", e);
        }
    }

    private void assignDataToMeetings(List<ZoomMeeting> meetings, List<ZoomMessage> messages,
                                       List<ZoomSharedFile> files, List<ZoomParticipant> participants) {
        if (meetings.isEmpty() && !messages.isEmpty()) {
            ZoomMeeting defaultMeeting = new ZoomMeeting();
            defaultMeeting.setTopic("Zoom Chat Session");
            defaultMeeting.setMeetingId(messages.get(0).getMeetingId());
            meetings.add(defaultMeeting);
        }

        for (ZoomMeeting meeting : meetings) {
            for (ZoomMessage msg : messages) {
                if (meeting.getMeetingId() != null && meeting.getMeetingId().equals(msg.getMeetingId())) {
                    meeting.getMessages().add(msg);
                } else if (meeting.getConfId() != null
                        && meeting.getConfId().equalsIgnoreCase(msg.getMeetingId())) {
                    meeting.getMessages().add(msg);
                }
            }

            if (meeting.getMessages().isEmpty() && meetings.size() == 1) {
                meeting.getMessages().addAll(messages);
            }

            for (ZoomSharedFile f : files) {
                boolean match = false;
                if (meeting.getMeetingId() != null) {
                    match = meeting.getMeetingId().equals(f.getMeetingId())
                            || meeting.getMeetingId().equalsIgnoreCase(f.getConfId());
                }
                if (!match && meeting.getConfId() != null) {
                    match = meeting.getConfId().equalsIgnoreCase(f.getConfId())
                            || meeting.getConfId().equalsIgnoreCase(f.getMeetingId());
                }
                if (match) {
                    meeting.getSharedFiles().add(f);
                }
            }

            if (meeting.getSharedFiles().isEmpty() && meetings.size() == 1) {
                meeting.getSharedFiles().addAll(files);
            }

            if (meeting.getParticipants().isEmpty()) {
                meeting.getParticipants().addAll(participants);
            }

            Collections.sort(meeting.getMessages());
        }
    }

    public String extractEncryptedKey(String iniContent) {
        boolean inSection = false;
        for (String line : iniContent.split("\\r?\\n")) {
            line = line.trim();
            if (line.equalsIgnoreCase(ZOOM_SECTION)) { inSection = true; continue; }
            if (line.startsWith("[")) { inSection = false; continue; }

            if (inSection && line.toLowerCase().startsWith(KEY_NAME.toLowerCase())) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2 && parts[1].trim().startsWith(KEY_PREFIX)) {
                    return parts[1].trim().substring(KEY_PREFIX.length());
                }
            }
        }
        return null;
    }

    private String tryDecryptOskey(String encryptedBlob, ItemInfo itemInfo, IItemSearcher searcher) {
        if (searcher == null) {
            logger.warn("IItemSearcher is null, cannot search for DPAPI master keys");
            return null;
        }

        String masterKeyGuid = parseMasterKeyGuid(encryptedBlob);
        if (masterKeyGuid == null) {
            logger.warn("Could not parse master key GUID from DPAPI blob");
            return null;
        }
        logger.info("Parsed master key GUID from blob: {}", masterKeyGuid);

        String escapedGuid = searcher.escapeQuery(masterKeyGuid);
        String query = BasicProps.NAME + ":\"" + escapedGuid + "\"";
        List<IItemReader> items = searcher.search(query);
        if (items.isEmpty()) {
            logger.warn("DPAPI master key file not found for GUID: {} (query: {})", masterKeyGuid, query);
            return null;
        }
        logger.info("Found {} master key file(s) for GUID: {}", items.size(), masterKeyGuid);

        String sid = extractSidFromPath(itemInfo != null ? itemInfo.getPath() : null, searcher);
        if (sid == null) {
            logger.warn("Could not determine Windows SID from evidence paths");
            return null;
        }

        logger.info("Found master key for GUID {} with SID {}, attempting password cracking...", masterKeyGuid, sid);

        List<String> wordlist = loadEmbeddedWordlist();

        for (IItemReader mkItem : items) {
            try (InputStream is = mkItem.getBufferedInputStream()) {
                byte[] mkData = is.readAllBytes();

                HashGenerator hashGen = new HashGenerator();
                String hash = hashGen.generateHash(mkData, sid, "local");
                if (hash == null) {
                    logger.warn("Failed to generate DPAPI hash from master key file");
                    continue;
                }

                PasswordCracker cracker = new PasswordCracker();
                String password = cracker.crack(hash, wordlist);
                if (password == null) {
                    logger.info("Password not found in wordlist ({} entries)", wordlist.size());
                    continue;
                }

                logger.info("DPAPI password cracked successfully");

                DPAPIMasterKeyDecryptor mkDecryptor = new DPAPIMasterKeyDecryptor();
                String masterKeyHex = mkDecryptor.decryptMasterKey(mkData, sid, password);

                DPAPIBlobDecryptor blobDecryptor = new DPAPIBlobDecryptor();
                byte[] decrypted = blobDecryptor.decryptBlobFromBase64(encryptedBlob, masterKeyHex);
                String oskey = new String(decrypted, StandardCharsets.UTF_8).trim();
                logger.info("Zoom OSKEY decrypted successfully");
                return oskey;

            } catch (Exception e) {
                logger.debug("Failed to process master key file", e);
            }
        }

        return null;
    }

    private List<String> loadEmbeddedWordlist() {
        List<String> words = new ArrayList<>();
        InputStream is = getClass().getResourceAsStream("/iped/parsers/zoomdpapi/wordlist.txt");
        if (is == null) {
            logger.warn("Embedded wordlist resource not found");
            return words;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    words.add(line);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load embedded wordlist: {}", e.getMessage());
        }
        return words;
    }

    private String parseMasterKeyGuid(String base64Blob) {
        try {
            byte[] data = java.util.Base64.getDecoder().decode(base64Blob);
            if (data.length < 40) return null;
            byte[] guidBytes = new byte[16];
            System.arraycopy(data, 24, guidBytes, 0, 16);
            return String.format("%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                guidBytes[3], guidBytes[2], guidBytes[1], guidBytes[0],
                guidBytes[5], guidBytes[4], guidBytes[7], guidBytes[6],
                guidBytes[8], guidBytes[9], guidBytes[10], guidBytes[11],
                guidBytes[12], guidBytes[13], guidBytes[14], guidBytes[15]);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSidFromPath(String path, IItemSearcher searcher) {
        java.util.regex.Pattern sidPattern = java.util.regex.Pattern.compile("S-1-5-21-[\\d-]+");

        if (path != null) {
            java.util.regex.Matcher matcher = sidPattern.matcher(path);
            if (matcher.find()) return matcher.group();
        }

        if (searcher != null) {
            String query = BasicProps.PATH + ":\"Microsoft\" AND " + BasicProps.PATH + ":\"Protect\"";
            List<IItemReader> items = searcher.search(query);
            for (IItemReader item : items) {
                String itemPath = item.getPath();
                if (itemPath != null) {
                    java.util.regex.Matcher matcher = sidPattern.matcher(itemPath);
                    if (matcher.find()) {
                        logger.info("Extracted SID from evidence: {}", matcher.group());
                        return matcher.group();
                    }
                }
            }
        }

        if (path != null && searcher != null) {
            String userBase = extractUserBasePath(path);
            if (userBase != null) {
                String protectQuery = BasicProps.PATH + ":\"" + searcher.escapeQuery(userBase) + "\" AND "
                        + BasicProps.NAME + ":\"Preferred\"";
                List<IItemReader> items = searcher.search(protectQuery);
                for (IItemReader item : items) {
                    String itemPath = item.getPath();
                    if (itemPath != null) {
                        java.util.regex.Matcher matcher = sidPattern.matcher(itemPath);
                        if (matcher.find()) {
                            logger.info("Extracted SID from Protect folder: {}", matcher.group());
                            return matcher.group();
                        }
                    }
                }
            }
        }

        logger.warn("Could not determine Windows SID");
        return null;
    }

    private String extractUserBasePath(String path) {
        String normalized = path.replace('\\', '/');
        int usersIdx = normalized.toLowerCase().indexOf("/users/");
        if (usersIdx < 0) return null;
        int userStart = usersIdx + "/users/".length();
        int userEnd = normalized.indexOf('/', userStart);
        if (userEnd < 0) return null;
        return normalized.substring(0, userEnd);
    }

    private File findDatabaseFile(String dbName, String oskey, String iniPath, IItemSearcher searcher, String evidenceUUID) {
        if (searcher == null) return null;

        List<IItemReader> items = searcher.search(BasicProps.NAME + ":\"" + searcher.escapeQuery(dbName) + "\"");

        String parentPath = Util.getUpToLastSeparator(iniPath, true);

        // Sort items by priority (matching conditions come first):
        // 1. Exact file name match (dbName)
        // 2. Exact full path match (parentPath + "/" + dbName)
        // 3. Path starts with the parent directory
        // 4. Match evidenceUUID
        // 5. Not deleted (false comes first)
        items.sort(Comparator
                .comparing((IItemReader o) -> !o.getName().equals(dbName))
                .thenComparing(o -> !o.getPath().equals(parentPath + "/" + dbName))
                .thenComparing(o -> !o.getPath().startsWith(parentPath))
                .thenComparing((IItemReader o) -> !o.getDataSource().getUUID().equals(evidenceUUID))
                .thenComparing(IItemReader::isDeleted));

        // Returns the first database file that can be opened with oskey
        for (IItemReader item : items) {
            try (Connection conn = new ZoomDatabaseReader(item.getTempFile(), oskey).createConnection()) {
                return item.getTempFile();
            } catch (Exception e) {
                logger.info("{} is not a valid database for oskey {}: {}", item, oskey, e.getMessage());
            }
        }

        logger.info("Database file not found: {}", dbName);
        return null;
    }
}
