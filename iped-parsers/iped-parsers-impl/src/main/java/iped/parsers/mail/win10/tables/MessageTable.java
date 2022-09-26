package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;
import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.entries.MessageEntry;
import iped.parsers.util.EsedbManager;


public class MessageTable extends AbstractTable {

    private List<MessageEntry> messages = new ArrayList<>();

    public MessageTable(String filePath, String tableName, PointerByReference tablePointer,
            PointerByReference errorPointer, long numRecords) {
        super();
        this.tableName = tableName;
        this.tablePointer = tablePointer;
        this.errorPointer = errorPointer;
        this.numRecords = numRecords;
        this.filePath = filePath;
    }

    @Override
    public void populateTable(EsedbLibrary esedbLibrary) {
        for (int i = 0; i < numRecords; i++) {
            messages.add(getMessage(esedbLibrary, i, errorPointer, tablePointer));
        }
    }

    public List<MessageEntry> getMessages() {
        return messages;
    }


    private MessageEntry getMessage(EsedbLibrary esedbLibrary, int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

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

        rowId = EsedbManager.getInt32Value(esedbLibrary, 0, recordPointerReference, filePath, errorPointer);
        conversationId = EsedbManager.getInt32Value(esedbLibrary, 21, recordPointerReference, filePath, errorPointer);
        msgAbstract = EsedbManager.getUnicodeValue(esedbLibrary, 142, recordPointerReference, filePath, errorPointer);
        subject = EsedbManager.getUnicodeValue(esedbLibrary, 160, recordPointerReference, filePath, errorPointer);
        senderName = EsedbManager.getUnicodeValue(esedbLibrary, 152, recordPointerReference, filePath, errorPointer);
        senderEmail = EsedbManager.getUnicodeValue(esedbLibrary, 153, recordPointerReference, filePath, errorPointer);
        msgDeliveryTime = EsedbManager.getFileTime(esedbLibrary, 55, recordPointerReference, filePath, errorPointer);
        lastModifiedTime = EsedbManager.getFileTime(esedbLibrary, 65, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        return new MessageEntry(rowId, conversationId, msgAbstract, subject, senderName, senderEmail, msgDeliveryTime, lastModifiedTime);
    }

}
