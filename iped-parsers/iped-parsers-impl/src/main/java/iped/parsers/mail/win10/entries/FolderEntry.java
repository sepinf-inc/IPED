package iped.parsers.mail.win10.entries;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import iped.parsers.mail.win10.tables.FolderTable;
import iped.parsers.mail.win10.tables.MessageTable;

public class FolderEntry extends AbstractEntry {
    private int parentFolderID;
    private String displayName;
    private Date createTime;

    public FolderEntry(int rowId) {
        super(rowId);
    }

    public int getParentFolderId() {
        return this.parentFolderID;
    }

    public void setParentFolderID(int parentFolderID) {
        this.parentFolderID = parentFolderID;
    }


    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Date getCreateTime() {
        return this.createTime;
    }
    
    public String getCreateTimeStr() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(createTime);
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public ArrayList<MessageEntry> getChildMessages() {
        return MessageTable.getFolderChildMessages(rowId);
    }

    
    public ArrayList<FolderEntry> getSubfolders() {
        return FolderTable.getSubfolders(rowId);
    }

}
