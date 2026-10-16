package iped.parsers.zoomdpapi;

/**
 * A file shared during a Zoom meeting, with encryption metadata.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomSharedFile {

    private String fileId;
    private String meetingId;
    private String confId;
    private String msgId;
    private String fileName;
    private String extName;
    private long fileSize;
    private String fileLink;
    private String ownerId;
    private String ownerJid;
    private String senderId;
    private String senderGuid;
    private String senderNodeId;
    private int fileType;
    private long timestamp;
    private String fileHash;
    private String encryptionKey;
    private String kAttribute;
    private int encryptionAlg;
    private int keyGeneration;
    private String dbKey;
    private int fileSyncFlag;
    private int transType;

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }

    public String getConfId() { return confId; }
    public void setConfId(String confId) { this.confId = confId; }

    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getExtName() { return extName; }
    public void setExtName(String extName) { this.extName = extName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFileLink() { return fileLink; }
    public void setFileLink(String fileLink) { this.fileLink = fileLink; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getOwnerJid() { return ownerJid; }
    public void setOwnerJid(String ownerJid) { this.ownerJid = ownerJid; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderGuid() { return senderGuid; }
    public void setSenderGuid(String senderGuid) { this.senderGuid = senderGuid; }

    public String getSenderNodeId() { return senderNodeId; }
    public void setSenderNodeId(String senderNodeId) { this.senderNodeId = senderNodeId; }

    public int getFileType() { return fileType; }
    public void setFileType(int fileType) { this.fileType = fileType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }

    public String getKAttribute() { return kAttribute; }
    public void setKAttribute(String kAttribute) { this.kAttribute = kAttribute; }

    public int getEncryptionAlg() { return encryptionAlg; }
    public void setEncryptionAlg(int encryptionAlg) { this.encryptionAlg = encryptionAlg; }

    public int getKeyGeneration() { return keyGeneration; }
    public void setKeyGeneration(int keyGeneration) { this.keyGeneration = keyGeneration; }

    public String getDbKey() { return dbKey; }
    public void setDbKey(String dbKey) { this.dbKey = dbKey; }

    public int getFileSyncFlag() { return fileSyncFlag; }
    public void setFileSyncFlag(int fileSyncFlag) { this.fileSyncFlag = fileSyncFlag; }

    public int getTransType() { return transType; }
    public void setTransType(int transType) { this.transType = transType; }
}
