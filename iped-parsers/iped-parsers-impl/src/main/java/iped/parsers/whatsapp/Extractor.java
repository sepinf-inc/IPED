package iped.parsers.whatsapp;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class Extractor {
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

    protected void setGroupMembers(Chat c, Connection conn, String SELECT_GROUP_MEMBERS) throws SQLException {
        // adds all contacts that sent at least one message
        for (Message m : c.getMessages()) {
            if (m.getRemoteResource() != null)
                c.getGroupMembers().add(contacts.getContact(m.getRemoteResource()));
        }
        if (SELECT_GROUP_MEMBERS == null) {
            return;
        }
        // adds all contacts which is a member of the group now
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_GROUP_MEMBERS)) {
            stmt.setString(1, c.getRemote().getFullId());
            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String memberId = rs.getString("member");
                    if (memberId != null && !memberId.trim().isEmpty()) {
                        c.getGroupMembers().add(contacts.getContact(memberId));
                    }
                }
            }
        }
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
