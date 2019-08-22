package dpf.inc.sepinf.browsers.parsers;

import java.util.LinkedList;
import java.util.List;

public class EdgeContainer {
    private String tableName = "";
    private List<EdgeVisit> entries = new LinkedList<EdgeVisit>();

    public EdgeContainer(String tableName) {
        super();
        this.tableName = tableName;
    }

    public String getTableName() {
        return this.tableName;
    }

    public List<EdgeVisit> getEntries() {
        return entries;
    }

    public void addEntry(EdgeVisit edgeEntry) {
        this.entries.add(edgeEntry);
    }

}
