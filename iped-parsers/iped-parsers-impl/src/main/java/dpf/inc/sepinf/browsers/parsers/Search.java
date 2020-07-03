package dpf.inc.sepinf.browsers.parsers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.google.common.base.Strings;

public class Search {
    private long id;
    private String terms = "";
    private String title = "";
    private String url;
    private Date lastVisitDate;

    public Search(long id, long lastVisitDate, String terms, String title, String url) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.terms = terms;
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

    public String getTerms() {
        return terms;
    }

    public void setTerms(String terms) {
        this.terms = terms;
    }

    public Date getLastVisitDate() {
        return lastVisitDate;
    }

    public void setLastVisitDate(Date lastVisitDate) {
        this.lastVisitDate = lastVisitDate;
    }

    public String getLastVisitDateAsString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(lastVisitDate);
    }

}
