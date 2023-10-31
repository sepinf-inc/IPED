package iped.parsers.whatsapp;

import java.util.Date;

public class MessageAddOn implements Comparable<MessageAddOn> {

    private int type;
    private Date timeStamp;
    private int status;
    private String remoteResource;
    private boolean FromMe;
    private String reaction;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getRemoteResource() {
        return remoteResource;
    }

    public void setRemoteResource(String remoteResource) {
        this.remoteResource = remoteResource;
    }

    public boolean isFromMe() {
        return FromMe;
    }

    public void setFromMe(boolean fromMe) {
        FromMe = fromMe;
    }

    public String getReaction() {
        return reaction;
    }

    public void setReaction(String reaction) {
        this.reaction = reaction;
    }

    public int compareTo(MessageAddOn o) {
        int cmp = Boolean.compare(timeStamp == null, o.timeStamp == null);
        if (cmp == 0 && timeStamp != null) {
            cmp = timeStamp.compareTo(o.timeStamp);
        }
        return cmp;
    }
}
