package dpf.inc.sepinf.browsers.parsers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.google.common.base.Strings;

public class Visit {
    private long id;
    private String title = "";
    private String url;
    private Date visitDate;

    public Visit(long id, String title, long visitDate, String url) {
        this.id = id;
        if (!Strings.isNullOrEmpty(title)) {
            this.title = title;
        }
        this.url = url;
        this.visitDate = new Date(visitDate);
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

    public Date getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(long visitDate) {
        this.visitDate = new Date(visitDate);
    }

    public String getVisitDateAsString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(visitDate);
    }
}