package dpf.sp.gpinf.indexer.util;

import java.util.List;

import iped3.IItem;
import iped3.datasource.IDataSource;

public class ParentInfo {

    private IDataSource dataSource;
    private int id;
    private String persistentId;
    private List<Integer> parentIds;
    private String path;
    private boolean isDeleted;

    public ParentInfo(IItem item) {
        this.dataSource = item.getDataSource();
        this.id = item.getId();
        this.parentIds = item.getParentIds();
        this.path = item.getPath();
        this.isDeleted = item.isDeleted();
        this.persistentId = Util.getPersistentId(item);
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

    public String getPersistentId() {
        return persistentId;
    }

}
