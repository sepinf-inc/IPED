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
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
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
 * Follows the same pattern as WhatsAppParser: emits meetings as virtual
 * child items with HTML reports, and individual messages as sub-children.
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
    public static final MediaType ZOOM_ACCOUNT = MediaType.parse("application/x-zoom-account");

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

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            // Step 1: Read INI and extract OSKEY blob
            byte[] iniBytes = org.apache.commons.io.IOUtils.toByteArray(tis);
            String iniContent = new String(iniBytes, StandardCharsets.UTF_8);
            logger.info("Processing Zoom.us.ini ({} bytes) from: {}", iniBytes.length, itemInfo != null ? itemInfo.getPath() : "unknown");

            String encryptedBlob = extractEncryptedKey(iniContent);
            if (encryptedBlob == null) {
                logger.warn("No encrypted OSKEY found in Zoom.us.ini: {}", itemInfo != null ? itemInfo.getPath() : "unknown");
                return;
            }
            logger.info("Extracted encrypted OSKEY blob ({} chars)", encryptedBlob.length());

            // Step 2: Determine the OSKEY
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

            // Step 3: Determine the SID for LocalDataDecryptor
            String sid = extractSidFromPath(itemInfo != null ? itemInfo.getPath() : null, searcher);

            // Step 4: Find and extract data from Zoom databases
            String basePath = itemInfo != null ? itemInfo.getPath() : "";
            String zoomDataDir = basePath.contains("Zoom") ? basePath.substring(0, basePath.lastIndexOf("Zoom") + 4) + "/Data" : "";

            ZoomDataExtractor dataExtractor = new ZoomDataExtractor();
            if (sid != null) {
                try {
                    dataExtractor.setLocalDecryptor(new LocalDataDecryptor(sid));
                } catch (Exception e) {
                    logger.warn("Failed to create LocalDataDecryptor with SID: {}", sid);
                }
            }

            // Collect all extracted data
            ZoomUserAccount account = null;
            ZoomSystemInfo sysInfo = null;
            List<ZoomParticipant> participants = new ArrayList<>();
            List<ZoomMessage> messages = new ArrayList<>();
            List<ZoomMeeting> meetings = new ArrayList<>();
            List<ZoomSharedFile> files = new ArrayList<>();
            List<ZoomRecording> recordings = new ArrayList<>();
            List<ZoomKeyValue> keyValues = new ArrayList<>();
            List<ZoomTimelineEvent> timeline = new ArrayList<>();

            // Extract from zoomus.enc.db
            File zoomusDb = findDatabaseFile("zoomus.enc.db", zoomDataDir, searcher);
            if (zoomusDb != null) {
                try (Connection conn = new ZoomDatabaseReader(zoomusDb, oskey).createConnection()) {
                    account = dataExtractor.extractUserAccount(conn);
                    participants = dataExtractor.extractParticipants(conn);
                    sysInfo = dataExtractor.extractKeyValues(conn, account, keyValues);
                } catch (Exception e) {
                    logger.warn("Failed to extract from zoomus.enc.db", e);
                }
            }

            // Extract from zoommeeting.enc.db
            File meetingDb = findDatabaseFile("zoommeeting.enc.db", zoomDataDir, searcher);
            if (meetingDb != null) {
                try (Connection conn = new ZoomDatabaseReader(meetingDb, oskey).createConnection()) {
                    messages = dataExtractor.extractMessages(conn);
                    files.addAll(dataExtractor.extractFilesFromChat(conn, messages));
                } catch (Exception e) {
                    logger.warn("Failed to extract from zoommeeting.enc.db", e);
                }
            }

            // Extract from calendar-history-meeting.enc.db
            File calendarDb = findDatabaseFile("calendar-history-meeting.enc.db", zoomDataDir, searcher);
            if (calendarDb == null) {
                calendarDb = findDatabaseFile("calendar-history-meeting.enc.db", null, searcher);
            }
            if (calendarDb != null) {
                try (Connection conn = new ZoomDatabaseReader(calendarDb, oskey).createConnection()) {
                    meetings = dataExtractor.extractMeetings(conn);
                    files.addAll(dataExtractor.extractSharedFiles(conn));
                    recordings = dataExtractor.extractRecordings(conn);
                } catch (Exception e) {
                    logger.warn("Failed to extract from calendar-history-meeting.enc.db", e);
                }
            }

            // Extract timeline from zoomus.enc.db (after messages are available)
            if (zoomusDb != null) {
                try (Connection conn = new ZoomDatabaseReader(zoomusDb, oskey).createConnection()) {
                    timeline = dataExtractor.extractTimeline(conn, messages, files);
                } catch (Exception e) {
                    logger.warn("Failed to extract timeline", e);
                }
            }

            // Step 5: Assign messages/files/participants to meetings
            assignDataToMeetings(meetings, messages, files, participants);

            // Step 6: Emit virtual items
            int virtualId = 0;

            // Emit account info
            if (account != null) {
                ZoomReportGenerator reportGen = new ZoomReportGenerator();
                byte[] htmlBytes = reportGen.generateAccountHtml(account, sysInfo);

                Metadata accountMeta = new Metadata();
                accountMeta.set(TikaCoreProperties.TITLE, "Zoom Account - " + account.getName());
                accountMeta.set(StandardParser.INDEXER_CONTENT_TYPE, ZOOM_ACCOUNT.toString());
                accountMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(virtualId));
                accountMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                extractor.parseEmbedded(new ByteArrayInputStream(htmlBytes), handler, accountMeta, false);
                virtualId++;
            }

            // Emit each meeting as a virtual item with its messages as children
            for (ZoomMeeting meeting : meetings) {
                int meetingVirtualId = virtualId++;

                ZoomReportGenerator reportGen = new ZoomReportGenerator();
                byte[] htmlBytes = reportGen.generateMeetingHtml(meeting);

                Metadata meetingMeta = new Metadata();
                meetingMeta.set(TikaCoreProperties.TITLE, meeting.getTitle());
                meetingMeta.set(StandardParser.INDEXER_CONTENT_TYPE, ZOOM_MEETING.toString());
                meetingMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(meetingVirtualId));
                meetingMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                if (meeting.getStartTime() > 0) {
                    meetingMeta.set(TikaCoreProperties.CREATED, new Date(meeting.getStartTime() * 1000));
                }
                if (!meeting.getParticipants().isEmpty()) {
                    for (ZoomParticipant p : meeting.getParticipants()) {
                        meetingMeta.add(ExtraProperties.PARTICIPANTS, p.getName());
                    }
                }
                if (extractMessages && !meeting.getMessages().isEmpty()) {
                    meetingMeta.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
                }

                extractor.parseEmbedded(new ByteArrayInputStream(htmlBytes), handler, meetingMeta, false);

                // Emit individual messages as children
                if (extractMessages) {
                    int msgCount = 0;
                    for (ZoomMessage msg : meeting.getMessages()) {
                        Metadata msgMeta = new Metadata();
                        String title = meeting.getTitle() + "_message_" + msgCount++;
                        msgMeta.set(TikaCoreProperties.TITLE, title);
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

            // Emit recordings
            for (ZoomRecording rec : recordings) {
                Metadata recMeta = new Metadata();
                String title = "Zoom Recording - " + (rec.getTopic() != null ? rec.getTopic() : rec.getMeetingNo());
                recMeta.set(TikaCoreProperties.TITLE, title);
                recMeta.set(StandardParser.INDEXER_CONTENT_TYPE, ZOOM_MEETING.toString());
                recMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(virtualId++));
                recMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                StringBuilder body = new StringBuilder();
                body.append(rec.isLocal() ? "Local Recording" : "Cloud Recording");
                if (rec.getLocation() != null) body.append("\nLocation: ").append(rec.getLocation());
                if (rec.getShareLink() != null) body.append("\nShare Link: ").append(rec.getShareLink());
                if (rec.getPasscode() != null) body.append("\nPasscode: ").append(rec.getPasscode());

                recMeta.set(ExtraProperties.MESSAGE_BODY, body.toString());
                recMeta.set(BasicProps.LENGTH, "");
                extractor.parseEmbedded(new EmptyInputStream(), handler, recMeta, false);
            }

        } catch (Exception e) {
            logger.error("Error parsing Zoom data", e);
            throw new TikaException("Error parsing Zoom DPAPI data", e);
        }
    }

    private void assignDataToMeetings(List<ZoomMeeting> meetings, List<ZoomMessage> messages,
                                       List<ZoomSharedFile> files, List<ZoomParticipant> participants) {
        if (meetings.isEmpty() && !messages.isEmpty()) {
            // Create a default meeting to hold all messages
            ZoomMeeting defaultMeeting = new ZoomMeeting();
            defaultMeeting.setTopic("Zoom Chat Session");
            if (!messages.isEmpty()) {
                defaultMeeting.setMeetingId(messages.get(0).getMeetingId());
            }
            meetings.add(defaultMeeting);
        }

        for (ZoomMeeting meeting : meetings) {
            // Assign messages
            for (ZoomMessage msg : messages) {
                if (meeting.getMeetingId() != null && meeting.getMeetingId().equals(msg.getMeetingId())) {
                    meeting.getMessages().add(msg);
                } else if (meeting.getConfId() != null && meeting.getConfId().equals(msg.getMeetingId())) {
                    meeting.getMessages().add(msg);
                }
            }

            // If no messages were matched and there's only one meeting, assign all
            if (meeting.getMessages().isEmpty() && meetings.size() == 1) {
                meeting.getMessages().addAll(messages);
            }

            // Assign files
            for (ZoomSharedFile f : files) {
                if (meeting.getMeetingId() != null &&
                    (meeting.getMeetingId().equals(f.getMeetingId()) || meeting.getMeetingId().equalsIgnoreCase(f.getConfId()))) {
                    meeting.getSharedFiles().add(f);
                }
            }

            // Assign participants
            if (meeting.getParticipants().isEmpty()) {
                meeting.getParticipants().addAll(participants);
            }

            // Sort messages
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
        if (searcher == null) return null;

        // Try to find DPAPI master key files in the evidence
        String masterKeyGuid = parseMasterKeyGuid(encryptedBlob);
        if (masterKeyGuid == null) return null;

        String query = BasicProps.NAME + ":\"" + masterKeyGuid + "\"";
        List<IItemReader> items = searcher.search(query);
        if (items.isEmpty()) {
            logger.info("DPAPI master key file not found for GUID: {}", masterKeyGuid);
            return null;
        }

        // Find SID from path
        String sid = extractSidFromPath(itemInfo != null ? itemInfo.getPath() : null, searcher);
        if (sid == null) {
            logger.info("Could not determine Windows SID from evidence paths");
            return null;
        }

        logger.info("Found master key for GUID {} with SID {}, attempting password cracking...", masterKeyGuid, sid);

        // Load embedded wordlist
        List<String> wordlist = loadEmbeddedWordlist();

        for (IItemReader mkItem : items) {
            try (InputStream is = mkItem.getBufferedInputStream()) {
                byte[] mkData = is.readAllBytes();

                // Generate hash for cracking
                HashGenerator hashGen = new HashGenerator();
                String hash = hashGen.generateHash(mkData, sid, "local");
                if (hash == null) {
                    logger.warn("Failed to generate DPAPI hash from master key file");
                    continue;
                }

                // Crack password using wordlist
                PasswordCracker cracker = new PasswordCracker();
                String password = cracker.crack(hash, wordlist);
                if (password == null) {
                    logger.info("Password not found in wordlist ({} entries)", wordlist.size());
                    continue;
                }

                logger.info("DPAPI password cracked successfully");

                // Decrypt master key with recovered password
                DPAPIMasterKeyDecryptor mkDecryptor = new DPAPIMasterKeyDecryptor();
                String masterKeyHex = mkDecryptor.decryptMasterKey(mkData, sid, password);

                // Decrypt OSKEY blob
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
        try (InputStream is = getClass().getResourceAsStream("/iped/parsers/zoomdpapi/wordlist.txt");
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
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
            byte[] data = Base64.getDecoder().decode(base64Blob);
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
        if (path == null) return null;

        // Try to extract SID from the evidence path
        // Look for S-1-5-21-... pattern in the path or in Microsoft\Protect directories
        java.util.regex.Pattern sidPattern = java.util.regex.Pattern.compile("S-1-5-21-[\\d-]+");
        java.util.regex.Matcher matcher = sidPattern.matcher(path);
        if (matcher.find()) return matcher.group();

        // Search for Protect directory in evidence
        if (searcher != null) {
            String query = BasicProps.NAME + ":\"Protect\" AND " + BasicProps.PATH + ":\"*Microsoft*\"";
            List<IItemReader> items = searcher.search(query);
            for (IItemReader item : items) {
                String itemPath = item.getPath();
                if (itemPath != null) {
                    matcher = sidPattern.matcher(itemPath);
                    if (matcher.find()) return matcher.group();
                }
            }
        }

        return null;
    }

    private File findDatabaseFile(String dbName, String zoomDataDir, IItemSearcher searcher) {
        if (searcher == null) return null;

        String query = BasicProps.NAME + ":\"" + dbName + "\"";
        List<IItemReader> items = searcher.search(query);

        for (IItemReader item : items) {
            try {
                // Write to temp file for SQLCipher access
                File tempFile = File.createTempFile("zoom_", "_" + dbName);
                tempFile.deleteOnExit();
                try (InputStream is = item.getBufferedInputStream();
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        fos.write(buf, 0, n);
                    }
                }
                return tempFile;
            } catch (Exception e) {
                logger.debug("Failed to extract database file: {}", dbName, e);
            }
        }
        return null;
    }
}
