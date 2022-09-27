package iped.parsers.mail.win10.entries;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

public class MessageEntry {
    private int rowId;
    private int conversationId;
    private int noOfAttachments;
    private String msgAbstract;
    private String subject;
    private String senderName;
    private String senderEmail;
    private Date msgDeliveryTime;
    private Date lastModifiedTime;

    public MessageEntry(int rowId, int conversationId, int noOfAttachments, String msgAbstract, String subject,
            String senderName, String senderAddress, Date msgDeliveryTime, Date lastModifiedTime) {
        this.rowId = rowId;
        this.conversationId = conversationId;
        this.noOfAttachments = noOfAttachments;
        this.msgAbstract = msgAbstract != null ? msgAbstract : "";
        this.subject = subject;
        this.senderName = senderName;
        this.senderEmail = senderAddress;
        this.msgDeliveryTime = msgDeliveryTime;
        this.lastModifiedTime = lastModifiedTime;
    }

    public int getRowId() {
        return this.rowId;
    }

    public void setRowId(int rowId) {
        this.rowId = rowId;
    }

    public int getConversationId() {
        return this.conversationId;
    }

    public void setConversationId(int conversationId) {
        this.conversationId = conversationId;
    }


    public int getNoOfAttachments() {
        return this.noOfAttachments;
    }

    public void setNoOfAttachments(int noOfAttachments) {
        this.noOfAttachments = noOfAttachments;
    }

    public String getMsgAbstract() {
        return this.msgAbstract;
    }

    public void setMsgAbstract(String msgAbstract) {
        this.msgAbstract = msgAbstract != null ? msgAbstract : "";
    }

    public String getSubject() {
        return this.subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getSenderName() {
        return this.senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderEmail() {
        return this.senderEmail;
    }

    public void setSenderEmail(String senderAddress) {
        this.senderEmail = senderAddress;
    }
    
    public Date getMsgDeliveryTime() {
        return this.msgDeliveryTime;
    }
    
    public String getMsgDeliveryTimeStr() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(msgDeliveryTime);
    }

    public void setMsgDeliveryTime(Date lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public Date getLastModifiedTime() {
        return this.lastModifiedTime;
    }
    
    public String getLastModifiedTimeStr() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(lastModifiedTime);
    }

    public void setLastModifiedTime(Date lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public ArrayList<RecipientEntry> getRecipients() {
        return RecipientTable.getMessageRecipients(rowId);
    }
}
