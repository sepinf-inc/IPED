package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.ColumnCodes;
import iped.parsers.mail.win10.entries.ContactEntry;
import iped.parsers.mail.win10.entries.FolderEntry;
import iped.parsers.util.EsedbManager;

public class ContactTable extends AbstractTable {

    private List<ContactEntry> contacts = new ArrayList<>();
    private Map<Integer, ArrayList<ContactEntry>> folderToContactsMap = new HashMap<>();

    int rowIdPos, storeIdPos, displayNamePos, displayNamePos2, firstNamePos, lastNamePos, emailPos, emailWorkPos,
        emailOtherPos, phonePos, workPhonePos, addressPos, hasNamePos, parentFolderIdPos;

    public ContactTable(EsedbLibrary esedbLibrary, String filePath, PointerByReference tablePointer,
        PointerByReference errorPointer, long numRecords) {
        super();
        this.esedbLibrary = esedbLibrary;
        this.tablePointer = tablePointer;
        this.errorPointer = errorPointer;
        this.numRecords = numRecords;
        this.filePath = filePath;

        rowIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ROW_ID, errorPointer, tablePointer, filePath);
        storeIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.STORE_ID, errorPointer, tablePointer, filePath);
        displayNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DISPLAY_NAME_1, errorPointer, tablePointer, filePath);
        displayNamePos2 = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DISPLAY_NAME_2, errorPointer, tablePointer, filePath);
        firstNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.FIRST_NAME, errorPointer, tablePointer, filePath);
        lastNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.LAST_NAME, errorPointer, tablePointer, filePath);
        emailPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.CONTACT_EMAIL, errorPointer, tablePointer, filePath);
        emailWorkPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.EMAIL_WORK_CONTACT, errorPointer, tablePointer, filePath);
        emailOtherPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.EMAIL_OTHER_CONTACT, errorPointer, tablePointer, filePath);
        phonePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.PHONE, errorPointer, tablePointer, filePath);
        workPhonePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.WORK_PHONE_CONTACT, errorPointer, tablePointer, filePath);
        addressPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ADDRESS_CONTACT, errorPointer, tablePointer, filePath);
        hasNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.HAS_NAME, errorPointer, tablePointer, filePath);
        parentFolderIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.PARENT_FOLDER_ID, errorPointer, tablePointer, filePath);
    }

    @Override
    public void populateTable() {
        for (int i = 0; i < numRecords; i++) {
            ContactEntry contact = extractContact(i, errorPointer, tablePointer);
            contacts.add(contact);
            addContactToParentFolder(contact, contact.getParentFolderId());
        }
    }

    public void addContactToParentFolder(ContactEntry contact, int parentId) {
        ArrayList<ContactEntry> folderContacts = folderToContactsMap
            .computeIfAbsent(parentId, k -> new ArrayList<ContactEntry>());
        folderContacts.add(contact);
    }

    private ContactEntry extractContact(int row, PointerByReference errorPointer, PointerByReference tablePointerReference) {
        int result = 0;

        PointerByReference recordPointerRef = new PointerByReference();
        IntByReference recordNumberOfValues = new IntByReference();

        // get row (record)
        result = esedbLibrary.libesedb_table_get_record(tablePointerReference.getValue(), row, recordPointerRef, errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Get Record", result, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_get_number_of_values(recordPointerRef.getValue(),
                recordNumberOfValues, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get Number of Values", result, filePath, errorPointer);

        int rowId = EsedbManager.getInt32Value(esedbLibrary, rowIdPos, recordPointerRef, filePath, errorPointer);
        int storeId = EsedbManager.getInt32Value(esedbLibrary, storeIdPos, recordPointerRef, filePath, errorPointer);
        String displayName = EsedbManager.getUnicodeValue(esedbLibrary, displayNamePos, recordPointerRef, filePath, errorPointer);
        if (displayName.isEmpty())
            displayName = EsedbManager.getUnicodeValue(esedbLibrary, displayNamePos2, recordPointerRef, filePath, errorPointer);
        String firstName = EsedbManager.getUnicodeValue(esedbLibrary, firstNamePos, recordPointerRef, filePath, errorPointer);
        String lastName = EsedbManager.getUnicodeValue(esedbLibrary, lastNamePos, recordPointerRef, filePath, errorPointer);
        String email = EsedbManager.getUnicodeValue(esedbLibrary, emailPos, recordPointerRef, filePath, errorPointer);
        String emailWork = EsedbManager.getUnicodeValue(esedbLibrary, emailWorkPos, recordPointerRef, filePath, errorPointer);
        String emailOther = EsedbManager.getUnicodeValue(esedbLibrary, emailOtherPos, recordPointerRef, filePath, errorPointer);
        String phone = EsedbManager.getUnicodeValue(esedbLibrary, phonePos, recordPointerRef, filePath, errorPointer);
        String workPhone = EsedbManager.getUnicodeValue(esedbLibrary, workPhonePos, recordPointerRef, filePath, errorPointer);
        String address = EsedbManager.getUnicodeValue(esedbLibrary, addressPos, recordPointerRef, filePath, errorPointer);
        boolean hasName = EsedbManager.getBooleanValue(esedbLibrary, hasNamePos, recordPointerRef, filePath, errorPointer);
        int parentFolderId = EsedbManager.getInt32Value(esedbLibrary, parentFolderIdPos, recordPointerRef, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerRef, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        ContactEntry contact = new ContactEntry(rowId);
        contact.setStoreId(storeId);
        contact.setDisplayName(displayName);
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setEmail(email);
        contact.setEmailWork(emailWork);
        contact.setEmailOther(emailOther);
        contact.setPhone(phone);
        contact.setWorkPhone(workPhone);
        contact.setAddress(address);
        contact.setHasName(hasName);
        contact.setParentFolderId(parentFolderId);

        return contact;
    }

    public ArrayList<ContactEntry> getFolderChildContacts(FolderEntry folder) {
        ArrayList<ContactEntry> childContacts = new ArrayList<>();
        for (int id : folder.getAllFolderIds()) {
            if (folderToContactsMap.get(id) != null)
                childContacts.addAll(folderToContactsMap.get(id));
        }
        return childContacts;
    }
    
}
