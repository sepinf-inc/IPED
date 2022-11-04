package iped.parsers.mail.win10.entries;

import java.util.Date;

public class AppointmentEntry extends AbstractEntry {
    private int storeId;
    private int parentFolderId;
    private String body;
    private boolean bodyFound;
    private String eventName;
    private String location;
    private boolean repeat;
    private boolean allDay;
    private long status;
    private long reminderTimeMin;
    private String organizer;
    private String account;
    private String link;
    private long durationMin;
    private Date startTime;
    private String additionalPeople;
    private ResponseType response;
    private long updateCount;
    private String bodyOriginalPath;

    public AppointmentEntry(int rowId) {
        super(rowId);
    }

    public int getStoreId() {
        return this.storeId;
    }

    public void setStoreId(int storeId) {
        this.storeId = storeId;
    }

    public void setBody(String body) {
        this.body = body;
        if (!body.isEmpty()) {
            this.bodyFound = true;
        }
    }
    
    public String getBody() {
        return this.body;
    }

    public boolean getBodyFound() {
        return this.bodyFound;
    }

    public int getParentFolderId() {
        return this.parentFolderId;
    }

    public void setParentFolderId(int parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public String getEventName() {
        return this.eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getLocation() {
        return this.location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isRepeat() {
        return this.repeat;
    }

    public boolean getRepeat() {
        return this.repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public boolean isAllDay() {
        return this.allDay;
    }

    public boolean getAllDay() {
        return this.allDay;
    }

    public void setAllDay(boolean allDay) {
        this.allDay = allDay;
    }

    public long getStatus() {
        return this.status;
    }

    public void setStatus(long status) {
        this.status = status;
    }

    public long getReminderTimeMin() {
        return this.reminderTimeMin;
    }

    public void setReminderTimeMin(long reminderTimeMin) {
        this.reminderTimeMin = reminderTimeMin;
    }

    public String getOrganizer() {
        return this.organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public String getAccount() {
        return this.account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getLink() {
        return this.link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public long getDurationMin() {
        return this.durationMin;
    }

    public void setDurationMin(long durationMin) {
        this.durationMin = durationMin;
    }

    public Date getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public String getNamelist() {
        return this.additionalPeople;
    }

    public void setAdditionalPeople(String additionalPeople) {
        this.additionalPeople = additionalPeople;
    }
    
    public String getAdditionalPeople() {
        return additionalPeople;
    }

    public ResponseType getResponse() {
        return this.response;
    }

    public void setResponse(ResponseType response) {
        this.response = response;
    }

    public long getUpdateCount() {
        return this.updateCount;
    }

    public void setUpdateCount(long updateCount) {
        this.updateCount = updateCount;
    }

    public String getBodyOriginalPath() {
        return this.bodyOriginalPath;
    }

    public void setBodyOriginalPath(String originalBodyPath) {
        this.bodyOriginalPath = originalBodyPath;
    }

    public enum ResponseType {
        ACCEPTED, DECLINED, TENTATIVE, AWAITING;
    }
}
