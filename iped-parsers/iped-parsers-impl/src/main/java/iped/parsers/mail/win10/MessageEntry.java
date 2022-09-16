package iped.parsers.mail.win10;

public class MessageEntry {
    private long rowId;
    private String msgAbstract;

    public MessageEntry(long rowId, String msgAbstract) {
        this.rowId = rowId;
        this.msgAbstract = msgAbstract;
    }

    public long getRowId() {
        return this.rowId;
    }

    public void setRowId(long rowId) {
        this.rowId = rowId;
    }

    public String getMsgAbstract() {
        return this.msgAbstract;
    }

    public void setMsgAbstract(String msgAbstract) {
        this.msgAbstract = msgAbstract;
    }
}
