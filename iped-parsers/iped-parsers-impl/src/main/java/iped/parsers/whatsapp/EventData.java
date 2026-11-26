package iped.parsers.whatsapp;

import java.util.Date;

public class EventData {
    private Date date;
    private String title;

    public EventData(Date date, String title) {
        this.date = date;
        this.title = title;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }
}
