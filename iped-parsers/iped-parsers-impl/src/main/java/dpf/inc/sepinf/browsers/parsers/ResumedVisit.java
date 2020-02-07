package dpf.inc.sepinf.browsers.parsers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.google.common.base.Strings;

public class ResumedVisit {
    private long id;
    private String title = "";
    private String url;
    private long visitCount = 0;
    private Date lastVisitDate;

    public ResumedVisit(long id, String title, String url, long visitCount, long lastVisitDate) {
        this.id = id;
        if (!Strings.isNullOrEmpty(title)) {
            this.title = title;
        }
        this.url = url;
        this.visitCount = visitCount;
        this.lastVisitDate = new Date(lastVisitDate);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public long getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(long visitCount) {
        this.visitCount = visitCount;
    }

    public Date getLastVisitDate() {
        return lastVisitDate;
    }

    public void setLastVisitDate(long visitDate) {
        this.lastVisitDate = new Date(visitDate);
    }

    public String getLastVisitDateAsString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(lastVisitDate);
    }
}
