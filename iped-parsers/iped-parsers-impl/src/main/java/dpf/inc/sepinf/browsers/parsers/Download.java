package dpf.inc.sepinf.browsers.parsers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Download {
    private String id;
    private String urlFromDownload;
    private String downloadedLocalPath;
    private Date downloadedDate;
    private Long totalBytes;
    private Long receivedBytes;

    public Download(String id, Long downloadedTime, String urlFromDownload, String downloadedLocalPath) {
        this.id = id;
        this.urlFromDownload = urlFromDownload;
        this.downloadedLocalPath = downloadedLocalPath;
        if (downloadedTime != null)
            this.downloadedDate = new Date(downloadedTime);
    }

    public Download(String id, Long downloadedTime, String urlFromDownload, String downloadedLocalPath,
            Long totalBytes) {
        this(id, downloadedTime, urlFromDownload, downloadedLocalPath);
        this.totalBytes = totalBytes;
    }

    public Download(String id, Long downloadedTime, String urlFromDownload, String downloadedLocalPath, Long totalBytes,
            Long receivedBytes) {
        this(id, downloadedTime, urlFromDownload, downloadedLocalPath, totalBytes);
        this.receivedBytes = receivedBytes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrlFromDownload() {
        return urlFromDownload;
    }

    public void setUrlFromDownload(String urlFromDownload) {
        this.urlFromDownload = urlFromDownload;
    }

    public String getDownloadedLocalPath() {
        return downloadedLocalPath;
    }

    public void setDownloadedLocalPath(String downloadedLocalPath) {
        this.downloadedLocalPath = downloadedLocalPath;
    }

    public Date getDownloadedDate() {
        return downloadedDate;
    }

    public void setDownloadedDate(Date downloadedDate) {
        this.downloadedDate = downloadedDate;
    }

    public String getDownloadedDateAsString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(downloadedDate);
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public Long getReceivedBytes() {
        return receivedBytes;
    }

}