package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;
import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.ColumnCodes;
import iped.parsers.mail.win10.entries.FolderEntry;
import iped.parsers.mail.win10.entries.MessageEntry;
import iped.parsers.util.EsedbManager;


public class MessageTable extends AbstractTable {

    private List<MessageEntry> messages = new ArrayList<>();
    private Map<Integer, ArrayList<MessageEntry>> folderToMsgsMap = new HashMap<>();

    private int rowIdPos, storeIdPos, parentFolderIdPos, conversationIdPos, messageSizePos, noOfAttachmentsPos,
        msgAbstractPos, subjectPos, senderNamePos, senderEmailPos, msgDeliveryTimePos, lastModifiedTimePos;

    public MessageTable(EsedbLibrary esedbLibrary, String filePath, PointerByReference tablePointer,
            PointerByReference errorPointer, long numRecords) {
        super();
        this.esedbLibrary = esedbLibrary;
        this.tablePointer = tablePointer;
        this.errorPointer = errorPointer;
        this.numRecords = numRecords;
        this.filePath = filePath;

        rowIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ROW_ID, errorPointer, tablePointer, filePath);
        storeIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.STORE_ID, errorPointer, tablePointer, filePath);
        parentFolderIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.PARENT_FOLDER_ID, errorPointer, tablePointer, filePath);
        conversationIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.CONVERSATION_ID, errorPointer, tablePointer, filePath);
        messageSizePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.MESSAGE_SIZE, errorPointer, tablePointer, filePath);
        noOfAttachmentsPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.NO_OF_ATTACHMENTS, errorPointer, tablePointer, filePath);
        msgAbstractPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ABSTRACT, errorPointer, tablePointer, filePath);
        subjectPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.SUBJECT, errorPointer, tablePointer, filePath);
        senderNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.SENDER_NAME, errorPointer, tablePointer, filePath);
        senderEmailPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.SENDER_EMAIL, errorPointer, tablePointer, filePath);
        msgDeliveryTimePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.MESSAGE_DELIVERY_TIME, errorPointer, tablePointer, filePath);
        lastModifiedTimePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.LAST_MODIFIED_TIME, errorPointer, tablePointer, filePath);
    }


    @Override
    public void populateTable() {
        for (int i = 0; i < numRecords; i++) {
            MessageEntry message = extractMessage(i, errorPointer, tablePointer);
            messages.add(message);
            addMessageToParentFolder(message, message.getParentFolderId());
        }
    }

    public List<MessageEntry> getMessages() {
        return messages;
    }

    public void addMessageToParentFolder(MessageEntry message, int parentId) {
        ArrayList<MessageEntry> folderMsgs = folderToMsgsMap.computeIfAbsent(parentId, k -> new ArrayList<MessageEntry>());
        folderMsgs.add(message);
    }

    public ArrayList<MessageEntry> getFolderChildMessages(FolderEntry folder) {
        ArrayList<MessageEntry> childMessages = new ArrayList<>();
        for (int id : folder.getAllFolderIds()) {
            if (folderToMsgsMap.get(id) != null)
                childMessages.addAll(folderToMsgsMap.get(id));
        }
        return childMessages;
    }


    private MessageEntry extractMessage(int row, PointerByReference errorPointer, PointerByReference tablePointerReference) {

        int result = 0;

        PointerByReference recordPointerReference = new PointerByReference();
        IntByReference recordNumberOfValues = new IntByReference();

        // get row (record)
        result = esedbLibrary.libesedb_table_get_record(tablePointerReference.getValue(), row, recordPointerReference,
                errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Get Record", result, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_get_number_of_values(recordPointerReference.getValue(),
                recordNumberOfValues, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get Number of Values", result, filePath, errorPointer);

        int rowId = EsedbManager.getInt32Value(esedbLibrary, rowIdPos, recordPointerReference, filePath, errorPointer);
        int storeId = EsedbManager.getInt32Value(esedbLibrary, storeIdPos, recordPointerReference, filePath, errorPointer);
        int parentFolderId = EsedbManager.getInt32Value(esedbLibrary, parentFolderIdPos, recordPointerReference, filePath, errorPointer);
        long conversationId = EsedbManager.getInt32Value(esedbLibrary, conversationIdPos, recordPointerReference, filePath, errorPointer);
        long messageSize = EsedbManager.getInt32Value(esedbLibrary, messageSizePos, recordPointerReference, filePath, errorPointer);
        int noOfAttachments = EsedbManager.getInt16Value(esedbLibrary, noOfAttachmentsPos, recordPointerReference, filePath, errorPointer);
        String msgAbstract = EsedbManager.getUnicodeValue(esedbLibrary, msgAbstractPos, recordPointerReference, filePath, errorPointer);
        String subject = EsedbManager.getUnicodeValue(esedbLibrary, subjectPos, recordPointerReference, filePath, errorPointer);
        String senderName = EsedbManager.getUnicodeValue(esedbLibrary, senderNamePos, recordPointerReference, filePath, errorPointer);
        String senderEmail = EsedbManager.getUnicodeValue(esedbLibrary, senderEmailPos, recordPointerReference, filePath, errorPointer);
        Date msgDeliveryTime = EsedbManager.getFileTime(esedbLibrary, msgDeliveryTimePos, recordPointerReference, filePath, errorPointer);
        Date lastModifiedTime = EsedbManager.getFileTime(esedbLibrary, lastModifiedTimePos, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        MessageEntry message = new MessageEntry(rowId);

        message.setStoreId(storeId);
        message.setConversationId(conversationId);
        message.setParentFolderId(parentFolderId);
        message.setNoOfAttachments(noOfAttachments);
        message.setMessageSize(messageSize);
        message.setMsgAbstract(msgAbstract);
        message.setSubject(subject);
        message.setSenderName(senderName);
        message.setSenderEmailAddress(senderEmail);
        message.setMsgDeliveryTime(msgDeliveryTime);
        message.setLastModifiedTime(lastModifiedTime);

        return message;
    }

}
