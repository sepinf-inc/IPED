package iped.parsers.mail.win10.tables;

import java.util.Iterator;

import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;
import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.entries.MessageEntry;
import iped.parsers.util.EsedbManager;


public class MessageTable extends AbstractTable {

    private PointerByReference errorPointer;
    private EsedbLibrary esedbLibrary;
    private String filePath;

    public MessageTable(EsedbLibrary esedbLibrary, String filePath, String tableName, PointerByReference tablePointer,
            PointerByReference errorPointer, long numRecords) {
        super();
        this.tableName = tableName;
        this.tablePointer = tablePointer;
        this.errorPointer = errorPointer;
        this.numRecords = numRecords;
        this.esedbLibrary = esedbLibrary;
        this.filePath = filePath;
    }


    @Override
    public Iterator<MessageEntry> iterator() {
        return new Iterator<MessageEntry>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < numRecords;
            }

            @Override
            public MessageEntry next() {
                return getMessage(i++, errorPointer, tablePointer);
            }
        };
    }

    private MessageEntry getMessage(int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

        int result = 0;

        PointerByReference recordPointerReference = new PointerByReference();
        IntByReference recordNumberOfValues = new IntByReference();

        IntByReference recordValueDataSize = new IntByReference();
        IntByReference recordValueDataInt = new IntByReference();
        Memory recordValueDataAbstract = new Memory(3072);

        /* Table values */
        long rowId = 0;
        String msgAbstract = "";

        // get row (record)
        result = esedbLibrary.libesedb_table_get_record(tablePointerReference.getValue(), i, recordPointerReference,
                errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Get Record", result, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_get_number_of_values(recordPointerReference.getValue(),
                recordNumberOfValues, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get Number of Values", result, filePath, errorPointer);

        /* get rowId (Integer 64bit signed) */
        result = esedbLibrary.libesedb_record_get_value_32bit(recordPointerReference.getValue(), 0, recordValueDataInt,
                errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get RowId Data", result, filePath, errorPointer);
        rowId = recordValueDataInt.getValue();

        // get msgAbstract (Unicode)
        result = esedbLibrary.libesedb_record_get_value_utf16_string_size(recordPointerReference.getValue(), 142,
                recordValueDataSize, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get FileName UTF16 String Size", result, filePath, errorPointer);
        if ((recordValueDataSize.getValue() > 0) && (result == 1)) {
            result = esedbLibrary.libesedb_record_get_value_utf8_string(recordPointerReference.getValue(), 142,
                    recordValueDataAbstract, recordValueDataSize.getValue(), errorPointer);
            if (result < 0)
                EsedbManager.printError("Record Get FileName UTF8 String", result, filePath, errorPointer);
            msgAbstract = recordValueDataAbstract.getString(0);
        }

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        return new MessageEntry(rowId, msgAbstract != null ? msgAbstract : "");
    }
}
