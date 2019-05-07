package dpf.sp.gpinf.indexer.util;

import java.util.List;

import iped3.Item;
import iped3.datasource.DataSource;

public class ParentInfo {

    private DataSource dataSource;
    private int id;
    private List<Integer> parentIds;
    private String path;
    private boolean isDeleted;

    public ParentInfo(Item item) {
        this.dataSource = item.getDataSource();
        this.id = item.getId();
        this.parentIds = item.getParentIds();
        this.path = item.getPath();
        this.isDeleted = item.isDeleted();
    }

    public DataSource getDataSource() {
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

}
