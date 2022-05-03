package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public abstract class Extractor {
    protected final File databaseFile;
    protected List<Chat> chatList;
    protected final WAContactsDirectory contacts;
    protected WAAccount account;
    protected boolean recoverDeletedRecords;
    protected String itemPath;

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

    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    protected void setGroupMembers(Chat c, Connection conn, String SELECT_GROUP_MEMBERS) throws WAExtractorException {
        // adds all contacts that sent at least one message
        for (Message m : c.getMessages()) {
            if (m.getRemoteResource() != null)
                c.getGroupmembers().add(contacts.getContact(m.getRemoteResource()));
        }
        // adds all contacts which is a member of the group now
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_GROUP_MEMBERS)) {
            stmt.setString(1, c.getRemote().getFullId());
            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String memberId = rs.getString("member");
                    if (!memberId.trim().isEmpty()) {
                        c.getGroupmembers().add(contacts.getContact(memberId));
                    }
                }

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new WAExtractorException(ex);
        }

    }

}
