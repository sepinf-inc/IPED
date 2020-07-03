package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public abstract class Extractor {
    protected final File databaseFile;
    protected List<Chat> chatList;
    protected final WAContactsDirectory contacts;
    protected WAAccount account;

    protected Extractor(File databaseFile, WAContactsDirectory contacts, WAAccount account) {
        this.databaseFile = databaseFile;
        this.contacts = contacts;
        this.account = account;
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
}
