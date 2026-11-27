package iped.parsers.whatsapp;

import java.util.Date;

public class EventData {
    private Date date;
    private Date endDate;
    private String title;
    private String description;
    private String location;

    public EventData(Date date, String title) {
        this.date = date;
        this.title = title;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Date getDate() {
        return date;
    }

    public Date getEndDate() {
        return endDate;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }
}
