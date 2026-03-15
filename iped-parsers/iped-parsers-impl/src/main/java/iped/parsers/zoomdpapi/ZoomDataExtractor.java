package iped.parsers.zoomdpapi;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts forensic artifacts from decrypted Zoom SQLite databases:
 * user accounts, messages, meetings, participants, shared files,
 * recordings, system info, and timeline events.
 *
 * Each extract method accepts a JDBC Connection directly so the
 * parser can handle file discovery via IPED's IItemSearcher.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomDataExtractor {

    private LocalDataDecryptor localDecryptor;

    public void setLocalDecryptor(LocalDataDecryptor localDecryptor) {
        this.localDecryptor = localDecryptor;
    }

    // --- zoomus.enc.db extraction ---

    public ZoomUserAccount extractUserAccount(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT uid, uname, zoomEmail, firstName, lastName, zoomJID, userType, accountType " +
                 "FROM zoom_user_account_enc LIMIT 1")) {
            if (rs.next()) {
                ZoomUserAccount acc = new ZoomUserAccount();
                acc.setEmail(rs.getString("uid"));
                acc.setZoomEmail(decrypt(rs.getString("zoomEmail")));
                acc.setFirstName(decrypt(rs.getString("firstName")));
                acc.setLastName(decrypt(rs.getString("lastName")));
                acc.setZoomJid(decrypt(rs.getString("zoomJID")));
                acc.setUserType(rs.getInt("userType"));
                acc.setAccountType(rs.getInt("accountType"));
                return acc;
            }
        } catch (Exception e) { /* table may not exist */ }
        return null;
    }

    public List<ZoomParticipant> extractParticipants(Connection conn) {
        List<ZoomParticipant> participants = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT itemID, name, avatar, snsID, roleType FROM zoom_meet_participants")) {
            while (rs.next()) {
                ZoomParticipant p = new ZoomParticipant();
                p.setOdId(rs.getString("itemID"));
                p.setName(rs.getString("name"));
                p.setOdAvatar(rs.getString("avatar"));
                p.setOdEmail(rs.getString("snsID"));
                p.setRoleType(rs.getInt("roleType"));
                participants.add(p);
            }
        } catch (Exception e) { /* table may not exist */ }
        return participants;
    }

    public ZoomSystemInfo extractKeyValues(Connection conn, ZoomUserAccount account, List<ZoomKeyValue> keyValues) {
        ZoomSystemInfo sysInfo = new ZoomSystemInfo();
        ZoomUserAccount acc = account != null ? account : new ZoomUserAccount();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT key, value, section FROM zoom_kv")) {
            while (rs.next()) {
                String k = rs.getString("key");
                String v = rs.getString("value");
                String s = rs.getString("section");

                if (k.endsWith(".enc") && localDecryptor != null && localDecryptor.canDecrypt(v)) {
                    String decrypted = decrypt(v);
                    if (!decrypted.equals(v)) {
                        ZoomKeyValue kv = new ZoomKeyValue();
                        kv.setSection(s);
                        kv.setKey(k);
                        kv.setValue(decrypted);
                        kv.setDecrypted(true);
                        keyValues.add(kv);
                    }
                }

                if ("com.zoom.us.account.user.id".equals(k)) acc.setZoomUserId(v);
                if ("com.zoom.client.version".equals(k)) acc.setClientVersion(v);
                if ("com.zoom.client.lastLoginTime".equals(k)) {
                    try { acc.setLastLoginTime(Long.parseLong(v)); } catch (Exception e) { /* ignore */ }
                }
                if ("Win32_Processor".equals(k)) sysInfo.setProcessor(v.replace("##x##", " | ").replace("##y##", ""));
                if ("Win32_VideoController".equals(k)) sysInfo.setVideoController(v.replace("##x##", " | "));
                if ("Win32_ComputerSystem".equals(k)) sysInfo.setComputerSystem(v.replace("##x##", " | "));
                if ("com.zoom.client.GUID".equals(k)) sysInfo.setClientGuid(v);
                if ("com.zoom.client.did".equals(k)) sysInfo.setFingerprint(v);
            }
        } catch (Exception e) { /* table may not exist */ }

        if (sysInfo.getProcessor() != null || sysInfo.getClientGuid() != null) {
            return sysInfo;
        }
        return null;
    }

    // --- zoommeeting.enc.db extraction ---

    public List<ZoomMessage> extractMessages(Connection conn) {
        List<ZoomMessage> messages = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                     "SELECT guid, confID, time, content, senderName, senderGuid, msgType " +
                     "FROM zoom_conf_chat_gen2_enc ORDER BY time ASC")) {
                while (rs.next()) {
                    ZoomMessage msg = new ZoomMessage();
                    msg.setId(rs.getString("guid"));
                    msg.setMeetingId(rs.getString("confID"));
                    msg.setBody(rs.getString("content"));
                    msg.setTimestamp(rs.getLong("time"));
                    msg.setSenderName(rs.getString("senderName"));
                    msg.setSenderGuid(rs.getString("senderGuid"));
                    msg.setMsgType(rs.getInt("msgType"));
                    messages.add(msg);
                }
            }
        } catch (Exception e) { /* table may not exist */ }
        return messages;
    }

    public List<ZoomSharedFile> extractFilesFromChat(Connection conn, List<ZoomMessage> messages) {
        List<ZoomSharedFile> files = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT msgData, localTime FROM zoom_conf_new_chat WHERE typeOfMsg = 1")) {
            while (rs.next()) {
                String xml = rs.getString("msgData");
                long localTime = rs.getLong("localTime");
                if (xml != null && xml.contains("msg_type>10<")) {
                    ZoomSharedFile f = parseFileFromXml(xml, localTime);
                    if (f != null) {
                        files.add(f);
                        ZoomMessage fileMsg = new ZoomMessage();
                        fileMsg.setId(f.getMsgId());
                        fileMsg.setMeetingId(f.getConfId() != null ? f.getConfId() : f.getMeetingId());
                        fileMsg.setBody("File Uploaded: " + f.getFileName());
                        fileMsg.setTimestamp(f.getTimestamp());
                        fileMsg.setSenderName(f.getOwnerJid());
                        fileMsg.setSenderGuid(f.getSenderGuid());
                        fileMsg.setMsgType(10);
                        fileMsg.setFileTransfer(true);
                        messages.add(fileMsg);
                    }
                }
            }
        } catch (Exception e) { /* table may not exist */ }
        return files;
    }

    // --- calendar-history-meeting.enc.db extraction ---

    public List<ZoomMeeting> extractMeetings(Connection conn) {
        List<ZoomMeeting> meetings = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT meeting_id, meeting_no, topic, description, host_id, host_name, " +
                 "start_time, end_time, schedule_time, duration, meeting_type, " +
                 "participants_size, cloud_recording_status " +
                 "FROM zoom_calendar_history_meeting_base ORDER BY start_time DESC")) {
            while (rs.next()) {
                ZoomMeeting m = new ZoomMeeting();
                m.setMeetingId(rs.getString("meeting_id"));
                m.setMeetingNo(rs.getString("meeting_no"));
                m.setTopic(rs.getString("topic"));
                m.setDescription(rs.getString("description"));
                m.setHostId(rs.getString("host_id"));
                m.setHostName(rs.getString("host_name"));
                m.setStartTime(rs.getLong("start_time"));
                m.setEndTime(rs.getLong("end_time"));
                m.setScheduleTime(rs.getLong("schedule_time"));
                m.setDuration(rs.getInt("duration"));
                m.setMeetingType(rs.getInt("meeting_type"));
                m.setParticipantsSize(rs.getInt("participants_size"));
                m.setCloudRecordingStatus(rs.getString("cloud_recording_status"));
                meetings.add(m);
            }
        } catch (Exception e) { /* table may not exist */ }
        return meetings;
    }

    public List<ZoomSharedFile> extractSharedFiles(Connection conn) {
        List<ZoomSharedFile> files = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT file_id, meeting_id, file_name, ext_name, file_size, file_link, " +
                 "owner_id, owner_jid, file_serv_type " +
                 "FROM zoom_calendar_history_files")) {
            while (rs.next()) {
                ZoomSharedFile f = new ZoomSharedFile();
                f.setFileId(rs.getString("file_id"));
                f.setMeetingId(rs.getString("meeting_id"));
                f.setFileName(rs.getString("file_name"));
                f.setExtName(rs.getString("ext_name"));
                f.setFileSize(rs.getLong("file_size"));
                f.setFileLink(rs.getString("file_link"));
                f.setOwnerId(rs.getString("owner_id"));
                f.setOwnerJid(rs.getString("owner_jid"));
                f.setFileType(rs.getInt("file_serv_type"));
                files.add(f);
            }
        } catch (Exception e) { /* table may not exist */ }
        return files;
    }

    public List<ZoomRecording> extractRecordings(Connection conn) {
        List<ZoomRecording> recordings = new ArrayList<>();
        // Cloud recordings
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT meeting_id, meeting_no, topic, share_link, recording_url, " +
                 "preview_url, start_time, duration, passcode " +
                 "FROM zoom_calendar_cloud_recording")) {
            while (rs.next()) {
                ZoomRecording r = new ZoomRecording();
                r.setMeetingId(rs.getString("meeting_id"));
                r.setMeetingNo(rs.getString("meeting_no"));
                r.setTopic(rs.getString("topic"));
                r.setShareLink(rs.getString("share_link"));
                r.setRecordingUrl(rs.getString("recording_url"));
                r.setPreviewUrl(rs.getString("preview_url"));
                r.setStartTime(rs.getLong("start_time"));
                r.setDuration(rs.getInt("duration"));
                r.setPasscode(rs.getString("passcode"));
                r.setLocal(false);
                recordings.add(r);
            }
        } catch (Exception e) { /* table may not exist */ }

        // Local recordings
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT meeting_id, meeting_no, topic, location, start_time " +
                 "FROM zoom_calendar_local_recording")) {
            while (rs.next()) {
                ZoomRecording r = new ZoomRecording();
                r.setMeetingId(rs.getString("meeting_id"));
                r.setMeetingNo(rs.getString("meeting_no"));
                r.setTopic(rs.getString("topic"));
                r.setLocation(rs.getString("location"));
                r.setStartTime(rs.getLong("start_time"));
                r.setLocal(true);
                recordings.add(r);
            }
        } catch (Exception e) { /* table may not exist */ }
        return recordings;
    }

    // --- Timeline extraction from zoomus.enc.db ---

    public List<ZoomTimelineEvent> extractTimeline(Connection conn, List<ZoomMessage> messages, List<ZoomSharedFile> files) {
        List<ZoomTimelineEvent> timeline = new ArrayList<>();

        // Avatar cache events
        List<ZoomTimelineEvent> avatarEvents = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT timestamp, url, path, filesize FROM zoom_conf_avatar_image_cache ORDER BY timestamp")) {
            while (rs.next()) {
                ZoomTimelineEvent ev = new ZoomTimelineEvent();
                ev.setTimestamp(rs.getLong("timestamp"));
                ev.setType("avatar");
                ev.setResourceUrl(rs.getString("url"));
                ev.setLocalPath(rs.getString("path"));
                ev.setFileSize(rs.getLong("filesize"));
                avatarEvents.add(ev);
            }
        } catch (Exception e) { /* table may not exist */ }

        // Waiting room events
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT timestamp, url, path, filesize FROM zoom_conf_waitingroom_cache")) {
            while (rs.next()) {
                ZoomTimelineEvent ev = new ZoomTimelineEvent();
                ev.setTimestamp(rs.getLong("timestamp"));
                ev.setType("waiting_room");
                ev.setResourceUrl(rs.getString("url"));
                ev.setLocalPath(rs.getString("path"));
                ev.setFileSize(rs.getLong("filesize"));

                String path = ev.getLocalPath();
                if (path != null) {
                    int idx = path.indexOf("WaitingRoom");
                    if (idx >= 0) {
                        String afterWR = path.substring(idx + 12);
                        if (afterWR.contains("_")) {
                            ev.setMeetingId(afterWR.substring(0, afterWR.indexOf("_")));
                        }
                    }
                }

                ev.setDescription(ev.getMeetingId() != null
                    ? "Entered waiting room for meeting " + ev.getMeetingId()
                    : "Entered waiting room");
                timeline.add(ev);
            }
        } catch (Exception e) { /* table may not exist */ }

        // Correlate avatar events with participants
        List<String[]> participants = getParticipantsByFirstMessage(messages);
        for (int i = 0; i < avatarEvents.size(); i++) {
            ZoomTimelineEvent ev = avatarEvents.get(i);

            if (i < participants.size()) {
                ev.setActor(participants.get(i)[0]);
                ev.setActorGuid(participants.get(i)[1]);
            }

            extractAvatarMetadata(ev, i);
            ev.setDescription(ev.getActor() != null
                ? ev.getActor() + " joined"
                : "Participant #" + (i + 1) + " joined");
            ev.setAvatarColor(getAvatarColor(i));
            timeline.add(ev);
        }

        // Message events
        addMessageEvents(timeline, messages);

        // File transfer events
        addFileEvents(timeline, files, messages);

        Collections.sort(timeline);
        return timeline;
    }

    // --- Private helpers ---

    private void extractAvatarMetadata(ZoomTimelineEvent ev, int index) {
        String path = ev.getLocalPath();
        if (path != null && path.contains("conf_avatar_")) {
            int idx = path.indexOf("conf_avatar_") + 12;
            if (idx < path.length()) {
                String hash = path.substring(idx);
                if (hash.contains("\\")) hash = hash.substring(0, hash.indexOf("\\"));
                if (hash.contains("/")) hash = hash.substring(0, hash.indexOf("/"));
                if (hash.contains("_")) hash = hash.substring(0, hash.indexOf("_"));
                ev.setAvatarHash(hash);
            }
        }

        if (ev.getResourceUrl() != null) {
            String url = ev.getResourceUrl();
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash > 0 && lastSlash < url.length() - 1) {
                String uuidPart = url.substring(lastSlash + 1);
                if (uuidPart.contains("?")) uuidPart = uuidPart.substring(0, uuidPart.indexOf("?"));
                if (uuidPart.matches(".*-\\d+$")) uuidPart = uuidPart.substring(0, uuidPart.lastIndexOf("-"));
                ev.setAvatarUuid(uuidPart);
            }
        }
    }

    private void addMessageEvents(List<ZoomTimelineEvent> timeline, List<ZoomMessage> messages) {
        Map<String, Integer> participantIndex = buildParticipantIndex(messages);
        for (ZoomMessage m : messages) {
            ZoomTimelineEvent ev = new ZoomTimelineEvent();
            ev.setTimestamp(m.getTimestamp());
            ev.setType("message");
            ev.setActor(m.getSenderName());
            ev.setActorGuid(m.getSenderGuid());
            ev.setContent(m.getBody());
            ev.setDescription("sent a message");
            Integer pIdx = participantIndex.get(m.getSenderGuid());
            ev.setAvatarColor(getAvatarColor(pIdx != null ? pIdx : 0));
            timeline.add(ev);
        }
    }

    private void addFileEvents(List<ZoomTimelineEvent> timeline, List<ZoomSharedFile> files, List<ZoomMessage> messages) {
        Map<String, Integer> participantIndex = buildParticipantIndex(messages);
        for (ZoomSharedFile f : files) {
            ZoomTimelineEvent ev = new ZoomTimelineEvent();
            ev.setTimestamp(f.getTimestamp());
            ev.setType("file_transfer");
            ev.setActor(f.getOwnerJid());
            ev.setActorGuid(f.getSenderGuid());
            ev.setContent(f.getFileName());
            ev.setFileSize(f.getFileSize());
            ev.setDescription("shared a file: " + f.getFileName() + " (" + formatSize(f.getFileSize()) + ")");
            Integer pIdx = participantIndex.get(f.getSenderGuid());
            ev.setAvatarColor(getAvatarColor(pIdx != null ? pIdx : 0));
            timeline.add(ev);
        }
    }

    private Map<String, Integer> buildParticipantIndex(List<ZoomMessage> messages) {
        Map<String, Integer> index = new LinkedHashMap<>();
        int idx = 0;
        for (String[] p : getParticipantsByFirstMessage(messages)) {
            index.put(p[1], idx++);
        }
        return index;
    }

    private List<String[]> getParticipantsByFirstMessage(List<ZoomMessage> messages) {
        Map<String, Long> firstMsgTime = new LinkedHashMap<>();
        Map<String, String> guidToName = new LinkedHashMap<>();

        for (ZoomMessage m : messages) {
            if (m.getSenderGuid() != null && !firstMsgTime.containsKey(m.getSenderGuid())) {
                firstMsgTime.put(m.getSenderGuid(), m.getTimestamp());
                guidToName.put(m.getSenderGuid(), m.getSenderName());
            }
        }

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(firstMsgTime.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        List<String[]> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : sorted) {
            result.add(new String[]{guidToName.get(e.getKey()), e.getKey()});
        }
        return result;
    }

    private String decrypt(String data) {
        if (localDecryptor == null) return data;
        return localDecryptor.decrypt(data);
    }

    static String getAvatarColor(int index) {
        String[] colors = {"#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#06B6D4", "#84CC16"};
        return colors[index % colors.length];
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    ZoomSharedFile parseFileFromXml(String xml, long timestamp) {
        try {
            ZoomSharedFile f = new ZoomSharedFile();
            f.setTimestamp(timestamp > 9999999999L ? timestamp / 1000 : timestamp);
            f.setMsgId(extractAttr(xml, " id"));

            int objStart = xml.indexOf("<obj ");
            int objEnd = xml.indexOf("/>", objStart);
            if (objStart < 0 || objEnd < 0) return null;
            String objTag = xml.substring(objStart, objEnd);

            f.setFileName(extractAttr(objTag, "nm"));
            String size = extractAttr(objTag, " s");
            if (size != null) f.setFileSize(Long.parseLong(size));
            f.setFileId(extractAttr(objTag, " id"));

            String fileType = extractAttr(objTag, " f");
            if (fileType != null) {
                try { f.setFileType(Integer.parseInt(fileType)); } catch (Exception e) { /* ignore */ }
            }

            String k = extractAttr(objTag, " k");
            if (k != null) {
                f.setKAttribute(k);
                if (k.contains(".")) {
                    f.setEncryptionKey(k.substring(0, k.indexOf(".")));
                    f.setFileHash(k.substring(k.indexOf(".") + 1));
                } else {
                    f.setEncryptionKey(k);
                }
            }

            int dbStart = xml.indexOf("<db ");
            int dbEnd = xml.indexOf("/>", dbStart);
            if (dbStart > 0 && dbEnd > 0) {
                String dbTag = xml.substring(dbStart, dbEnd);
                f.setDbKey(extractAttr(dbTag, "key"));
                String dbConfId = extractAttr(dbTag, "confId");
                if (dbConfId != null) f.setConfId(dbConfId);
            }

            String encAlg = extractAttr(xml, "enc_alg");
            if (encAlg != null) {
                try { f.setEncryptionAlg(Integer.parseInt(encAlg)); } catch (Exception e) { /* ignore */ }
            }

            String kg = extractAttr(xml, " kg");
            if (kg != null) {
                try { f.setKeyGeneration(Integer.parseInt(kg)); } catch (Exception e) { /* ignore */ }
            }

            String fileSync = extractAttr(xml, "pmc_file_sync");
            if (fileSync != null) {
                try { f.setFileSyncFlag(Integer.parseInt(fileSync)); } catch (Exception e) { /* ignore */ }
            }

            String transType = extractAttr(xml, "meet_trans_type");
            if (transType != null) {
                try { f.setTransType(Integer.parseInt(transType)); } catch (Exception e) { /* ignore */ }
            }

            int fromStart = xml.indexOf("<from n=\"");
            if (fromStart > 0) {
                fromStart += 9;
                int fromEnd = xml.indexOf("\"", fromStart);
                f.setOwnerJid(xml.substring(fromStart, fromEnd));
            }

            f.setSenderGuid(extractAttr(xml, "meet_sender_user_guid"));
            f.setSenderId(extractAttr(xml, "meet_sender_conf_user_id"));
            f.setSenderNodeId(extractAttr(xml, "meet_sender_node_id"));
            f.setMeetingId(extractAttr(xml, "meetid"));

            return f.getFileName() != null ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractAttr(String xml, String attr) {
        int start = xml.indexOf(attr + "=\"");
        if (start < 0) return null;
        start += attr.length() + 2;
        int end = xml.indexOf("\"", start);
        return end > start ? xml.substring(start, end) : null;
    }
}
