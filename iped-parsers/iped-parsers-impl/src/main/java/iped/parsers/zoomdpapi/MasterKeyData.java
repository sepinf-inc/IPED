package iped.parsers.zoomdpapi;

/**
 * Holds DPAPI master key metadata: GUID, file path, SID,
 * the generated hash string, recovered password, and the
 * decrypted master key material.
 *
 * @author Calil Khalil (Hakal)
 */
public class MasterKeyData {

    private String guid;
    private String filePath;
    private String sid;
    private String hash;
    private String recoveredPassword;
    private String decryptedMasterKey;

    public MasterKeyData(String guid, String filePath, String sid) {
        this.guid = guid;
        this.filePath = filePath;
        this.sid = sid;
    }

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public String getRecoveredPassword() { return recoveredPassword; }
    public void setRecoveredPassword(String recoveredPassword) { this.recoveredPassword = recoveredPassword; }

    public String getDecryptedMasterKey() { return decryptedMasterKey; }
    public void setDecryptedMasterKey(String decryptedMasterKey) { this.decryptedMasterKey = decryptedMasterKey; }
}
