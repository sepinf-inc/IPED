package iped.parsers.whatsapp;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Extractor {

    private static final Logger logger = LoggerFactory.getLogger(Extractor.class);

    /**
     * Rough estimate of the memory needed to recover deleted records, in relation
     * to the size of the database. Measured with databases from 15 MB to 2 GB.
     */
    private static final int RECOVERY_MEMORY_FACTOR = 8;

    protected final File databaseFile;
    protected List<Chat> chatList;
    protected final WAContactsDirectory contacts;
    protected WAAccount account;
    protected boolean recoverDeletedRecords;
    protected String itemPath;
    private static final int MESSAGE_LENGTH_TO_COMPARE_ALMOST_EQUAL = 16;

    protected Extractor(String itemPath,File databaseFile, WAContactsDirectory contacts, WAAccount account, boolean recoverDeletedRecords) {
        this.itemPath = itemPath;
        this.databaseFile = databaseFile;
        this.contacts = contacts;
        this.account = account;
        this.recoverDeletedRecords = recoverDeletedRecords;
    }

    /**
     * Warns, before recovering deleted records, if the memory available may not be
     * enough for it. The recovery is not skipped: it is an estimate, and the memory
     * may be freed by the time it is needed.
     */
    protected void warnIfLowMemoryToRecoverDeletedRecords() {
        Runtime runtime = Runtime.getRuntime();
        long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        long needed = databaseFile.length() * RECOVERY_MEMORY_FACTOR;
        if (needed > available) {
            // error level, so it is shown in the console while processing (warn is not)
            logger.error("Recovering deleted WhatsApp records from {} ({} MB) may need about {} MB of memory, but only {} MB are available. "
                    + "If the processing fails with OutOfMemoryError, give IPED more memory (e.g. add -Xmx32G to its command line, after iped.jar or iped.exe) "
                    + "or disable this recovery (set {} to false for WhatsAppParser in conf/ParserConfig.xml).",
                    itemPath, databaseFile.length() >> 20, needed >> 20, available >> 20,
                    // a backup is only carved if the option for backups is enabled
                    WhatsAppParser.isBackupDatabase(itemPath) ? "recoverDeletedRecordsFromBackups" : "recoverDeletedRecords");
        }
    }

    /**
     * @return the chatList
     */
    public List<Chat> getChatList() throws WAExtractorException {
        if (chatList == null) {
            chatList = extractChatList();
        }
        return chatList;
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    protected abstract List<Chat> extractChatList() throws WAExtractorException;

    protected abstract Connection getConnection() throws SQLException;

    /**
     * Android stores the account owner as a group member using special JIDs:
     * "status_me" or "lid_me" in group_participant_user (newer databases), or an
     * empty JID in group_participants (older databases).
     */
    protected boolean isOwnerMemberId(String memberId) {
        return false;
    }

    protected void setGroupMembers(Chat c, Connection conn, String selectGroupMembersQuery) throws SQLException {
        // adds all contacts that sent at least one message
        for (Message m : c.getMessages()) {
            if (m.getRemoteResource() != null) {
                if (c.isGroupOrChannelChat() && c.getRemote() != null && m.getRemoteResource().equals(c.getRemote().getFullId())) {
                    continue; // don't add the group itself as a member
                }
                c.getGroupMembers().add(contacts.getContact(m.getRemoteResource()));
            }
        }

        // the account owner is a member if sent a message (as other members above), or
        // if listed in the group participants table
        boolean isOwnerMember = c.getMessages().stream().anyMatch(Message::isFromMe);

        if (selectGroupMembersQuery == null) {
            // participants table not available, so assume the owner is a member
            addOwnerAsMember(c);
            return;
        }
        // adds all contacts which is a member of the group now
        try (PreparedStatement stmt = conn.prepareStatement(selectGroupMembersQuery)) {
            stmt.setString(1, c.getRemote().getFullId());
            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String memberId = rs.getString("member");
                    boolean isAdmin = rs.getBoolean("admin");
                    if (isOwnerMemberId(memberId)) {
                        isOwnerMember = true;
                        if (isAdmin) {
                            c.setOwnerAdmin(true);
                        }
                    } else if (memberId != null && !memberId.trim().isEmpty()) {
                        c.getGroupMembers().add(contacts.getContact(memberId));
                        if (isAdmin) {
                            c.getGroupAdmins().add(contacts.getContact(memberId));
                        }
                    }
                }
            }
        }

        if (isOwnerMember) {
            addOwnerAsMember(c);
        }
        if (c.isOwnerAdmin()) {
            c.getGroupAdmins().add(getOwnerContact());
        }
    }

    private void addOwnerAsMember(Chat c) {
        c.getGroupMembers().add(getOwnerContact());
    }

    private WAContact getOwnerContact() {
        // if unknown, the account itself is used, so its unknown id is not output as a
        // contact id
        return account.isUnknown() ? account : contacts.getContact(account.getFullId());
    }
    
    protected static boolean isSqliteCorruptException(SQLException exception) {
        if (exception.getMessage() != null && (
           exception.getMessage().contains("SQLITE_CORRUPT") || //$NON-NLS-1$
           exception.getMessage().contains("SQLITE_ERROR"))) { //$NON-NLS-1$
            return true;
        }
        
        var cause = exception.getCause();
        if (cause != null && (
                exception.getMessage().contains("SQLITE_CORRUPT") || //$NON-NLS-1$
                exception.getMessage().contains("SQLITE_ERROR"))) { //$NON-NLS-1$
            return true;
        }
        
        
        return false;
    }

    protected boolean compareMessagesAlmostTheSame(Message m1, Message m2) {
        if (m1.getId() == m2.getId()) {
            String tx1 = m1.getData();
            String tx2 = m2.getData();
            if (tx1 != null &&
                tx2 != null &&
                tx1.length() >= MESSAGE_LENGTH_TO_COMPARE_ALMOST_EQUAL &&
                tx2.length() >= MESSAGE_LENGTH_TO_COMPARE_ALMOST_EQUAL ) {
                return tx1.substring(0, MESSAGE_LENGTH_TO_COMPARE_ALMOST_EQUAL)
                        .equals(tx2.substring(0, MESSAGE_LENGTH_TO_COMPARE_ALMOST_EQUAL));
            }
        }
        return false;
    }

    protected List<Chat> cleanChatList(List<Chat> list) {
        List<Chat> cleanedList = new ArrayList<>();
        for (Chat c : list) {
            String remote = c.getRemote() != null ? c.getRemote().getId() : null;
            if (!c.getMessages().isEmpty() || !c.getGroupMembers().isEmpty()
                    || (c.getSubject() != null && !c.getSubject().isBlank())
                    || (remote != null && !(remote = remote.strip()).isEmpty() && !remote.equals("0"))) {
                cleanedList.add(c);
            }
        }
        return cleanedList;
    }

}
