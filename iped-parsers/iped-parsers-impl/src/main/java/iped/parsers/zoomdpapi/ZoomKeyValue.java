package iped.parsers.zoomdpapi;

/**
 * A key-value entry from the zoom_kv configuration table.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomKeyValue {

    private String section;
    private String key;
    private String value;
    private boolean decrypted;

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public boolean isDecrypted() { return decrypted; }
    public void setDecrypted(boolean decrypted) { this.decrypted = decrypted; }
}
