package dpf.inc.sepinf.browsers.parsers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Download {
    private long id;
    private String urlFromDownload;
    private String downloadedLocalPath;
    private Date downloadedDate;

    public Download(long id, long downloadedDate, String urlFromDownload, String downloadedLocalPath) {
        this.id = id;
        this.urlFromDownload = urlFromDownload;
        this.downloadedLocalPath = downloadedLocalPath;
        this.downloadedDate = new Date(downloadedDate);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
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

}