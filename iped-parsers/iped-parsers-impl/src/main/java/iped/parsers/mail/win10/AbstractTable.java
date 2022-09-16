package iped.parsers.mail.win10;

import com.sun.jna.ptr.PointerByReference;


public abstract class AbstractTable implements Iterable<MessageEntry> {
    protected String tableName = "";
    protected long numRecords = 0;
    protected PointerByReference tablePointer;

    public PointerByReference getTablePointer() {
        return this.tablePointer;
    }

    public String getTableName() {
        return this.tableName;
    }
}
