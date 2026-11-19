package iped.parsers.signal;

import java.util.Base64;

import iped.data.IItemReader;

/** A row of Signal's attachment table (part, before Signal 7.x). */
public class SignalAttachment {

    /** What was found in the case for the attachment file. */
    public enum State {
        // Not looked up: the parser runs without access to the rest of the case
        UNKNOWN,
        // Decrypted with the key from the extraction and extracted as a sub-item
        DECRYPTED,
        // The file is in the case, still encrypted: the attachment key was not found
        ENCRYPTED,
        // The file is in the case and the key was found, but it did not decrypt the file
        // (no data_random, a read error, or a result that does not match the stored hash)
        NOT_DECRYPTED,
        // The database references a file that is not in the case
        MISSING,
        // The database row has no file, e.g. media that was never downloaded
        NO_FILE
    }

    private long id;
    private long messageId;
    private String contentType;
    private String fileName;
    private String dataFile;
    private long dataSize;
    private byte[] dataRandom;
    // SHA-256 of the plain content, base64: at write time and after upload
    private String dataHashStart;
    private String dataHashEnd;
    private String caption;
    private boolean voiceNote;
    private boolean sticker;
    // Thumbnail of the media in a quoted message, not something sent with this one
    private boolean quote;

    private State state = State.UNKNOWN;
    private IItemReader item;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getMessageId() { return messageId; }
    public void setMessageId(long messageId) { this.messageId = messageId; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getDataFile() { return dataFile; }
    public void setDataFile(String dataFile) { this.dataFile = dataFile; }

    public long getDataSize() { return dataSize; }
    public void setDataSize(long dataSize) { this.dataSize = dataSize; }

    public byte[] getDataRandom() { return dataRandom; }
    public void setDataRandom(byte[] dataRandom) { this.dataRandom = dataRandom; }

    public String getDataHashStart() { return dataHashStart; }
    public void setDataHashStart(String dataHashStart) { this.dataHashStart = dataHashStart; }

    public String getDataHashEnd() { return dataHashEnd; }
    public void setDataHashEnd(String dataHashEnd) { this.dataHashEnd = dataHashEnd; }

    /** Whether the database holds a hash to check the decrypted content against. */
    public boolean hasDataHash() {
        return (dataHashStart != null && !dataHashStart.isBlank()) || (dataHashEnd != null && !dataHashEnd.isBlank());
    }

    /** Whether the SHA-256 of decrypted content is one of the hashes Signal stored. */
    public boolean matchesDataHash(byte[] sha256) {
        String hash = Base64.getEncoder().encodeToString(sha256);
        return hash.equals(dataHashStart) || hash.equals(dataHashEnd);
    }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public boolean isVoiceNote() { return voiceNote; }
    public void setVoiceNote(boolean voiceNote) { this.voiceNote = voiceNote; }

    public boolean isSticker() { return sticker; }
    public void setSticker(boolean sticker) { this.sticker = sticker; }

    public boolean isQuote() { return quote; }
    public void setQuote(boolean quote) { this.quote = quote; }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    /** The decrypted sub-item, or the encrypted file itself when there was no key. */
    public IItemReader getItem() { return item; }
    public void setItem(IItemReader item) { this.item = item; }

    /** Base name of the stored file, e.g. part1234.mms; null when the row has none. */
    public String getStoredFileName() {
        if (dataFile == null || dataFile.isBlank())
            return null;
        String name = dataFile.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1);
    }

    /** Short description for message bodies, e.g. "Voice message" or "image/jpeg". */
    public String getLabel() {
        if (sticker)
            return "Sticker";
        if (voiceNote)
            return "Voice message";
        if (fileName != null && !fileName.isBlank())
            return fileName.strip();
        return contentType != null ? contentType : "Attachment";
    }
}
