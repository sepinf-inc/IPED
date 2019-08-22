package dpf.inc.sepinf.browsers.parsers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.google.common.base.Strings;

public class EdgeVisit {
    private long id;
    private long fileSize = 0;
    private long accessCount = 0;
    private String url;
    private String filename = "";
    private Date creationDate;
    private Date modifiedDate;
    private Date accessedDate;

    public EdgeVisit(long id, long fileSize, long accessCount, long creationDate, long modifiedDate, long accessedDate,
            String filename, String url) {
        this.id = id;
        if (!Strings.isNullOrEmpty(filename)) {
            this.filename = filename;
        }
        this.url = url;
        this.accessCount = accessCount;
        this.creationDate = new Date(creationDate);
        this.modifiedDate = new Date(modifiedDate);
        this.accessedDate = new Date(accessedDate);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(long accessCount) {
        this.accessCount = accessCount;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(long creationDate) {
        this.creationDate = new Date(creationDate);
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(long modifiedDate) {
        this.modifiedDate = new Date(modifiedDate);
    }

    public Date getAccessedDate() {
        return accessedDate;
    }

    public void setAccessedDate(long accessedDate) {
        this.accessedDate = new Date(accessedDate);
    }

    public String getCreationDateAsString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(creationDate);
    }

    public String getModifiedDateAsString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(modifiedDate);
    }

    public String getAccessedDateAsString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(accessedDate);
    }
}
