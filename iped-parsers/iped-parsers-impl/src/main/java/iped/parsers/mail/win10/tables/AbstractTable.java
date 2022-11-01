package iped.parsers.mail.win10.tables;

import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;

public abstract class AbstractTable {
    protected String tableName = "";
    protected long numRecords = 0;
    protected PointerByReference tablePointer;
    protected PointerByReference errorPointer;
    protected String filePath;
    protected EsedbLibrary esedbLibrary;

    public PointerByReference getTablePointer() {
        return this.tablePointer;
    }

    public String getTableName() {
        return this.tableName;
    }

    abstract public void populateTable();
}
