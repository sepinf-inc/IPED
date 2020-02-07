package dpf.inc.sepinf.browsers.parsers;

import com.sun.jna.ptr.PointerByReference;

public abstract class EdgeContainer implements Iterable<EdgeVisit>{
    
    private String tableName = "";
    PointerByReference tablePointer;

    public EdgeContainer(String tableName, PointerByReference tablePointer) {
        super();
        this.tableName = tableName;
        this.tablePointer = tablePointer;
    }

    public String getTableName() {
        return this.tableName;
    }
    
    public PointerByReference getTablePointer() {
        return this.tablePointer;
    }
    
}
