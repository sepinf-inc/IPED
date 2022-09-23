package iped.parsers.mail.win10.tables;

import java.util.Date;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
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

        /* Message table values */
        int rowId = 0;
        int conversationId = 0;
        String msgAbstract = "";
        String subject = "";
        String senderName = "";
        String senderEmail = "";
        Date msgDeliveryTime;
        Date lastModifiedTime;

        // get row (record)
        result = esedbLibrary.libesedb_table_get_record(tablePointerReference.getValue(), i, recordPointerReference,
                errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Get Record", result, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_get_number_of_values(recordPointerReference.getValue(),
                recordNumberOfValues, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get Number of Values", result, filePath, errorPointer);

        rowId = getInt32Value(0, recordPointerReference);
        conversationId = getInt32Value(21, recordPointerReference);
        msgAbstract = getUnicodeValue(142, recordPointerReference);
        subject = getUnicodeValue(160, recordPointerReference);
        senderName = getUnicodeValue(152, recordPointerReference);
        senderEmail = getUnicodeValue(153, recordPointerReference);
        msgDeliveryTime = getFileTime(55, recordPointerReference);
        lastModifiedTime = getFileTime(65, recordPointerReference);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        return new MessageEntry(rowId, conversationId, msgAbstract, subject, senderName, senderEmail, msgDeliveryTime, lastModifiedTime);
    }


    int getInt32Value(int value_entry, PointerByReference recordPointerReference) {
        IntByReference recordValueDataInt = new IntByReference();

        int result = esedbLibrary.libesedb_record_get_value_32bit(recordPointerReference.getValue(), value_entry, recordValueDataInt,
        errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get 32-Bit Data", result, filePath, errorPointer);
        return  recordValueDataInt.getValue();
    }

    String getUnicodeValue(int value_entry, PointerByReference recordPointerReference) {
        IntByReference recordValueDataInt = new IntByReference();
        Memory recordValueData = new Memory(3072);

        int result = esedbLibrary.libesedb_record_get_value_utf16_string_size(recordPointerReference.getValue(), value_entry,
            recordValueDataInt, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get UTF16 String Size", result, filePath, errorPointer);
        if ((recordValueDataInt.getValue() > 0) && (result == 1)) {
            result = esedbLibrary.libesedb_record_get_value_utf8_string(recordPointerReference.getValue(), value_entry,
                    recordValueData, recordValueDataInt.getValue(), errorPointer);
            if (result < 0)
                EsedbManager.printError("Record Get UTF8 String at " + value_entry, result, filePath, errorPointer);
            return recordValueData.getString(0);
        }
        return null;
    }

    Date getFileTime(int value_entry, PointerByReference recordPointerReference) {
        LongByReference recordValueData = new LongByReference();

        int result = esedbLibrary.libesedb_record_get_value_64bit(recordPointerReference.getValue(), value_entry, recordValueData,
                errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get FileTime Data", result, filePath, errorPointer);
        
        Date date = new Date((recordValueData.getValue() - 116444736000000000L)/10000);
        
        return date;
    }
}
