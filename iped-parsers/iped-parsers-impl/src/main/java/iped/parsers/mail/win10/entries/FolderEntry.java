package iped.parsers.mail.win10.entries;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class FolderEntry extends AbstractEntry {
    private int storeId;
    private int parentFolderID;
    private String displayName;
    private Date createTime;
    private List<Integer> allIds = new ArrayList<>();

    public FolderEntry(int rowId) {
        super(rowId);
        addFolderId(rowId);
    }

    public int getStoreId() {
        return this.storeId;
    }

    public void setStoreId(int storeId) {
        this.storeId = storeId;
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

    public void addFolderId(int id) {
        allIds.add(id);
    }

    public List<Integer> getAllFolderIds() {
        return allIds;
    }

}
