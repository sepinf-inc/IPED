package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.entries.AttachmentEntry;
import iped.parsers.util.EsedbManager;

public class AttachmentTable extends AbstractTable {

    private static Map<Long, ArrayList<AttachmentEntry>> attachments = new HashMap<>();

    public static void addAttachment(AttachmentEntry attachment) {
        attachments.computeIfAbsent(attachment.getMessageId(), k -> new ArrayList<AttachmentEntry>()).add(attachment);
    }

    public AttachmentTable(String filePath, String tableName, PointerByReference tablePointer,
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
            AttachmentEntry attachment = getAttachment(esedbLibrary, i, errorPointer, tablePointer);
            addAttachment(attachment);
        }
    }

    public static ArrayList<AttachmentEntry> getMessageAttachments(long messageId) {
        return attachments.get(messageId);
    }

    private AttachmentEntry getAttachment(EsedbLibrary esedbLibrary, int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

        int result = 0;

        PointerByReference recordPointerReference = new PointerByReference();
        IntByReference recordNumberOfValues = new IntByReference();

        // get row (record)
        result = esedbLibrary.libesedb_table_get_record(tablePointerReference.getValue(), i, recordPointerReference,
                errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Get Record", result, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_get_number_of_values(recordPointerReference.getValue(),
                recordNumberOfValues, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get Number of Values", result, filePath, errorPointer);

        int rowId = EsedbManager.getInt32Value(esedbLibrary, 0, recordPointerReference, filePath, errorPointer);
        int messageId = EsedbManager.getInt32Value(esedbLibrary, 3, recordPointerReference, filePath, errorPointer);
        int attachSize = EsedbManager.getInt32Value(esedbLibrary, 7, recordPointerReference, filePath, errorPointer);
        String fileName = EsedbManager.getUnicodeValue(esedbLibrary, 13, recordPointerReference, filePath, errorPointer);
        boolean received = EsedbManager.getBooleanValue(esedbLibrary, 20, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        return new AttachmentEntry(rowId, messageId, attachSize, fileName, null, received);
    }
    
}
