package dpf.inc.sepinf.browsers.parsers;

public class SafariBookmark {
    private long id;
    private String title;
    private String url;
    private String uuid;

    public SafariBookmark(long id, String uuid, String title, String url) {
        super();
        this.id = id;
        this.uuid = uuid;
        this.title = title;
        this.url = url;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
