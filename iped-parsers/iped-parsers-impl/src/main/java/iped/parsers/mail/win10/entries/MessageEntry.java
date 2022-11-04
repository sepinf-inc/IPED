package iped.parsers.mail.win10.entries;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


public class MessageEntry extends AbstractEntry {
    private int storeId;
    private long conversationId;
    private int parentFolderId;
    private long messageSize;
    private int noOfAttachments;
    private String msgAbstract;
    private String subject;
    private String senderName;
    private String senderEmailAddress;
    private Date msgDeliveryTime;
    private Date lastModifiedTime;
    private String body;
    private boolean bodyFound;
    private String bodyOriginalPath;

    public final char categoryNumber = '3';

    public MessageEntry(int rowId) {
        super(rowId);
    }

    public long getStoreId() {
        return this.storeId;
    }

    public void setStoreId(int storeId) {
        this.storeId = storeId;
    }

    public long getConversationId() {
        return this.conversationId;
    }

    public void setConversationId(long conversationId) {
        this.conversationId = conversationId;
    }

    public int getParentFolderId() {
        return this.parentFolderId;
    }

    public void setParentFolderId(int parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public int getNoOfAttachments() {
        return this.noOfAttachments;
    }

    public void setNoOfAttachments(int noOfAttachments) {
        this.noOfAttachments = noOfAttachments;
    }

    public long getMessageSize() {
        return this.messageSize;
    }

    public void setMessageSize(long messageSize) {
        this.messageSize = messageSize;
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

    public String getSenderEmailAddress() {
        return this.senderEmailAddress;
    }

    public void setSenderEmailAddress(String senderAddress) {
        this.senderEmailAddress = senderAddress;
    }
    
    public Date getMsgDeliveryTime() {
        return this.msgDeliveryTime;
    }
    
    public String getMsgDeliveryTimeStr() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(msgDeliveryTime);
    }

    public void setMsgDeliveryTime(Date msgDeliveryTime) {
        this.msgDeliveryTime = msgDeliveryTime;
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

    public String getBody() {
        return this.body;
    }

    public void setBody(String body) {
        this.body = body;
        if (!body.isEmpty()) {
            this.bodyFound = true;
        }
    }

    public boolean getBodyFound() {
        return this.bodyFound;
    }

    public String getBodyOriginalPath() {
        return this.bodyOriginalPath;
    }

    public void setBodyOriginalPath(String bodyOriginalPath) {
        this.bodyOriginalPath = bodyOriginalPath;
    }

}
