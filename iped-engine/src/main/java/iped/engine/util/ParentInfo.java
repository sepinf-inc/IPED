package iped.engine.util;

import java.util.List;

import iped.data.IItem;
import iped.datasource.IDataSource;

public class ParentInfo {

    private IDataSource dataSource;
    private int id;
    private String trackID;
    private List<Integer> parentIds;
    private String path;
    private boolean isDeleted;

    public ParentInfo(IItem item) {
        this.dataSource = item.getDataSource();
        this.id = item.getId();
        this.parentIds = item.getParentIds();
        this.path = item.getPath();
        this.isDeleted = item.isDeleted();
        this.trackID = Util.getTrackID(item);
    }

    public IDataSource getDataSource() {
        return dataSource;
    }

    public int getId() {
        return id;
    }

    public List<Integer> getParentIds() {
        return parentIds;
    }

    public String getPath() {
        return path;
    }

    public boolean isDeleted() {
        return this.isDeleted;
    }

    public String getTrackId() {
        return trackID;
    }

}
