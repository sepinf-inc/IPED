package iped.parsers.mail.win10.entries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.entries.RecipientEntry.RecipientType;
import iped.parsers.mail.win10.tables.AbstractTable;
import iped.parsers.util.EsedbManager;

public class RecipientTable extends AbstractTable {

    private static Map<Long, ArrayList<RecipientEntry>> recipients = new HashMap<>();

    public RecipientTable(String filePath, String tableName, PointerByReference tablePointer,
        PointerByReference errorPointer, long numRecords) {
        super();
        this.tableName = tableName;
        this.tablePointer = tablePointer;
        this.errorPointer = errorPointer;
        this.numRecords = numRecords;
        this.filePath = filePath;
    }

    public static void addRecipient(Long messageId, RecipientEntry recipient) {
        recipients.computeIfAbsent(messageId, k -> new ArrayList<RecipientEntry>()).add(recipient);
    }

    @Override
    public void populateTable(EsedbLibrary esedbLibrary) {
        for (int i = 0; i < numRecords; i++) {
            RecipientEntry recipient = getRecipient(esedbLibrary, i, errorPointer, tablePointer);
            addRecipient(recipient.getMessageId(), recipient);
        }
    }

    public static ArrayList<RecipientEntry> getMessageRecipients(long messageId) {
        return recipients.get(messageId);
    }

    private RecipientEntry getRecipient(EsedbLibrary esedbLibrary, int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

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
        String displayName = EsedbManager.getUnicodeValue(esedbLibrary, 12, recordPointerReference, filePath, errorPointer);
        String emailAddress = EsedbManager.getUnicodeValue(esedbLibrary, 13, recordPointerReference, filePath, errorPointer);
        int recipientType = EsedbManager.getInt32Value(esedbLibrary, 4, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        return new RecipientEntry(rowId, messageId, displayName, emailAddress, RecipientType.values()[recipientType]);
    }
}
