package iped.parsers.zoomdpapi;

/**
 * Holds Zoom installation data found on a Windows user profile:
 * the path to Zoom.us.ini, the DPAPI-encrypted OSKEY blob, and the
 * decrypted OSKEY once recovered.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomData {

    private String userPath;
    private String iniFilePath;
    private String sid;
    private String masterKeyGuid;
    private String encryptedBlob;
    private String decryptedOskey;

    public ZoomData(String userPath, String iniFilePath) {
        this.userPath = userPath;
        this.iniFilePath = iniFilePath;
    }

    public String getUserPath() { return userPath; }
    public void setUserPath(String userPath) { this.userPath = userPath; }

    public String getIniFilePath() { return iniFilePath; }
    public void setIniFilePath(String iniFilePath) { this.iniFilePath = iniFilePath; }

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getMasterKeyGuid() { return masterKeyGuid; }
    public void setMasterKeyGuid(String masterKeyGuid) { this.masterKeyGuid = masterKeyGuid; }

    public String getEncryptedBlob() { return encryptedBlob; }
    public void setEncryptedBlob(String encryptedBlob) { this.encryptedBlob = encryptedBlob; }

    public String getOskeyBase64() { return encryptedBlob; }

    public String getDecryptedOskey() { return decryptedOskey; }
    public void setDecryptedOskey(String decryptedOskey) { this.decryptedOskey = decryptedOskey; }
}
