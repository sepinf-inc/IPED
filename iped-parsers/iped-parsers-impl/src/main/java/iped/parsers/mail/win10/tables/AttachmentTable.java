package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.ColumnCodes;
import iped.parsers.mail.win10.entries.AttachmentEntry;
import iped.parsers.util.EsedbManager;

public class AttachmentTable extends AbstractTable {

    private Map<Long, ArrayList<AttachmentEntry>> msgToAttachmentsMap = new HashMap<>();
    private ArrayList<AttachmentEntry> attachmentList = new ArrayList<>();

    private int rowIdPos, messageIdPos, attachSizePos, attachCIDPos, fileNamePos,
        mimeTagPos, receivedPos;


    public AttachmentTable(EsedbLibrary esedbLibrary, String filePath, PointerByReference tablePointer,
        PointerByReference errorPointer, long numRecords) {
        super();
        this.esedbLibrary = esedbLibrary;
        this.tablePointer = tablePointer;
        this.errorPointer = errorPointer;
        this.numRecords = numRecords;
        this.filePath = filePath;

        rowIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ROW_ID, errorPointer, tablePointer, filePath);
        messageIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.MESSAGE_ID, errorPointer, tablePointer, filePath);
        attachSizePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ATTACH_SIZE, errorPointer, tablePointer, filePath);
        attachCIDPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.IMAGE_CID, errorPointer, tablePointer, filePath);
        fileNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.FILENAME, errorPointer, tablePointer, filePath);
        mimeTagPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.MIME_TAG, errorPointer, tablePointer, filePath);
        receivedPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.RECEIVED, errorPointer, tablePointer, filePath);
    }

    @Override
    public void populateTable() {
        for (int i = 0; i < numRecords; i++) {
            AttachmentEntry attachment = extractAttachment(i, errorPointer, tablePointer);
            addAttachment(attachment);
            attachmentList.add(attachment);
        }
    }

    public void addAttachment(AttachmentEntry attachment) {
        ArrayList<AttachmentEntry> messageAttachments = msgToAttachmentsMap.computeIfAbsent(attachment.getMessageId(), k -> new ArrayList<AttachmentEntry>());
        if (!messageAttachments.stream().map(m -> m.getRowId()).anyMatch(id -> id == attachment.getRowId())) {
            messageAttachments.add(attachment);
        }
    }
    
    public ArrayList<AttachmentEntry> getAttachments() {
        return attachmentList;
    }

    public ArrayList<AttachmentEntry> getMessageAttachments(long messageId) {
        ArrayList<AttachmentEntry> messageAttachments = msgToAttachmentsMap.get(messageId);
        if (messageAttachments == null) {
            return new ArrayList<AttachmentEntry>();
        }
        return messageAttachments;
    }

    private AttachmentEntry extractAttachment(int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

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

        int rowId = EsedbManager.getInt32Value(esedbLibrary, rowIdPos, recordPointerReference, filePath, errorPointer);
        int messageId = EsedbManager.getInt32Value(esedbLibrary, messageIdPos, recordPointerReference, filePath, errorPointer);
        long attachSize = EsedbManager.getInt32Value(esedbLibrary, attachSizePos, recordPointerReference, filePath, errorPointer);
        String attachCID = EsedbManager.getUnicodeValue(esedbLibrary, attachCIDPos, recordPointerReference, filePath, errorPointer);
        String fileName = EsedbManager.getUnicodeValue(esedbLibrary, fileNamePos, recordPointerReference, filePath, errorPointer);
        String mimeTag = EsedbManager.getUnicodeValue(esedbLibrary, mimeTagPos, recordPointerReference, filePath, errorPointer);
        boolean received = EsedbManager.getBooleanValue(esedbLibrary, receivedPos, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        AttachmentEntry attachment = new AttachmentEntry(rowId);
        attachment.setMessageId(messageId);
        attachment.setAttachSize(attachSize);
        attachment.setAttachCID(attachCID);
        attachment.setFileName(fileName);
        attachment.setMimeTag(mimeTag);
        attachment.setReceived(received);

        return attachment;
    }
    
}
