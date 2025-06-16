package iped.parsers.threema;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.io.TemporaryResources;

/**
 *
 * @author André Rodrigues Costa <andre.arc@pf.gov.br>
 */
public abstract class Extractor {
    protected TemporaryResources tmp;
    protected final File databaseFile;
    protected List<Chat> chatList;
    protected ThreemaAccount account;
    protected boolean recoverDeletedRecords;
    protected String itemPath;

    protected Extractor(TemporaryResources tmp, String itemPath, File databaseFile, ThreemaAccount account, boolean recoverDeletedRecords) {
        this.tmp = tmp;
        this.itemPath = itemPath;
        this.databaseFile = databaseFile;
        this.account = account;
        this.recoverDeletedRecords = recoverDeletedRecords;
    }

    /**
     * @return the chatList
     */
    public List<Chat> getChatList() {
        if (chatList == null) {
            chatList = extractChatList();
        }
        return chatList;
    }

    protected abstract List<Chat> extractChatList();

    protected abstract Connection getConnection() throws SQLException;

    protected void setGroupMembers(Chat c, Connection conn, String SELECT_GROUP_MEMBERS) throws SQLException {
        // adds user as group participant
        c.getParticipants().add(account);

        if (SELECT_GROUP_MEMBERS == null) {
            return;
        }
        // adds all contacts which is a member of the group now
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_GROUP_MEMBERS)) {
            stmt.setLong(1, c.getId());
            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String memberId = rs.getString("CONTACT_IDENTITY");
                    if (!memberId.trim().isEmpty()) {
                        ThreemaContact contact = new ThreemaContact(rs.getString("CONTACT_FIRSTNAME"), rs.getString("CONTACT_LASTNAME"), rs.getString("CONTACT_NICKNAME"), rs.getString("CONTACT_IDENTITY"), null);
                        c.getParticipants().add(contact);
                    }
                }
            }
        }
    }

    protected List<Chat> cleanChatList(List<Chat> list) {
        List<Chat> cleanedList = new ArrayList<>();
        for (Chat c : list) {
            String remote = c.getContact() != null ? c.getContact().getId() : null;
            if (!c.getMessages().isEmpty() || !c.getParticipants().isEmpty() || (c.getSubject() != null && !c.getSubject().isBlank()) || (remote != null && !(remote = remote.strip()).isEmpty() && !remote.equals("0"))) {
                cleanedList.add(c);
            }
        }
        return cleanedList;
    }

}
