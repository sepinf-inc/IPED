package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.File;
import java.util.List;

public abstract class Extractor {
    protected final File databaseFile;
    protected List<Chat> chatList;
    protected final WAContactsDirectory contacts;

    protected Extractor(File databaseFile, WAContactsDirectory contacts) {
        this.databaseFile = databaseFile;
        this.contacts = contacts;
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
}
