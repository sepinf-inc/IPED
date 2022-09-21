package iped.parsers.mail.win10.tables;

import com.sun.jna.ptr.PointerByReference;

import iped.parsers.mail.win10.entries.MessageEntry;


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
