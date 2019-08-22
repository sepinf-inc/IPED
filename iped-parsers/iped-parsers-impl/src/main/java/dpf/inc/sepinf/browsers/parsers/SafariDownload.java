package dpf.inc.sepinf.browsers.parsers;

public class SafariDownload {
    private String uid;
    private String urlFromDownload;
    private String downloadedLocalPath;

    public SafariDownload(String uid, String urlFromDownload, String downloadedLocalPath) {
        this.uid = uid;
        this.urlFromDownload = urlFromDownload;
        this.downloadedLocalPath = downloadedLocalPath;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
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
}
