package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.ColumnCodes;
import iped.parsers.mail.win10.entries.RecipientEntry;
import iped.parsers.mail.win10.entries.RecipientEntry.RecipientType;
import iped.parsers.util.EsedbManager;

public class RecipientTable extends AbstractTable {

    private Map<Long, ArrayList<RecipientEntry>> parentMsgToRecipientsMap = new HashMap<>();

    int rowIdPos, messageIdPos, displayNamePos, displayNamePos2, emailAddressPos, recipientTypePos;

    public RecipientTable(EsedbLibrary esedbLibrary, String filePath, PointerByReference tablePointer,
        PointerByReference errorPointer, long numRecords) {
        super();
        this.esedbLibrary = esedbLibrary;
        this.tablePointer = tablePointer;
        this.errorPointer = errorPointer;
        this.numRecords = numRecords;
        this.filePath = filePath;
    
        rowIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ROW_ID, errorPointer, tablePointer, filePath);
        messageIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.MESSAGE_ID, errorPointer, tablePointer, filePath);
        displayNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DISPLAY_NAME_1, errorPointer, tablePointer, filePath);
        displayNamePos2 = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DISPLAY_NAME_2, errorPointer, tablePointer, filePath);
        emailAddressPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.EMAIL_ADDRESS, errorPointer, tablePointer, filePath);
        recipientTypePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.RECIPIENT_TYPE, errorPointer, tablePointer, filePath);
    
    }

    public void addRecipient(Long messageId, RecipientEntry recipient) {
        parentMsgToRecipientsMap.computeIfAbsent(messageId, k -> new ArrayList<RecipientEntry>()).add(recipient);
    }

    @Override
    public void populateTable() {
        for (int i = 0; i < numRecords; i++) {
            RecipientEntry recipient = extractRecipient(i, errorPointer, tablePointer);
            addRecipient(recipient.getMessageId(), recipient);
        }
    }

    public ArrayList<RecipientEntry> getMessageRecipients(long messageId) {
        return parentMsgToRecipientsMap.get(messageId);
    }

    private RecipientEntry extractRecipient(int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

        int result = 0;

        PointerByReference recordPointerRef = new PointerByReference();
        IntByReference recordNumberOfValues = new IntByReference();

        // get row (record)
        result = esedbLibrary.libesedb_table_get_record(tablePointerReference.getValue(), i, recordPointerRef,
                errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Get Record", result, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_get_number_of_values(recordPointerRef.getValue(),
                recordNumberOfValues, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get Number of Values", result, filePath, errorPointer);

        int rowId = EsedbManager.getInt32Value(esedbLibrary, rowIdPos, recordPointerRef, filePath, errorPointer);
        int messageId = EsedbManager.getInt32Value(esedbLibrary, messageIdPos, recordPointerRef, filePath, errorPointer);
        String displayName = EsedbManager.getUnicodeValue(esedbLibrary, displayNamePos, recordPointerRef, filePath, errorPointer);
        if (displayName.isEmpty())
            displayName = EsedbManager.getUnicodeValue(esedbLibrary, displayNamePos2, recordPointerRef, filePath, errorPointer);
        String emailAddress = EsedbManager.getUnicodeValue(esedbLibrary, emailAddressPos, recordPointerRef, filePath, errorPointer);
        int recipientType = EsedbManager.getInt32Value(esedbLibrary, recipientTypePos, recordPointerRef, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerRef, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        return new RecipientEntry(rowId, messageId, displayName, emailAddress, RecipientType.values()[recipientType]);
    }
}
