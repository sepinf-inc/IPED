package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.entries.ContactEntry;
import iped.parsers.util.EsedbManager;

public class ContactTable extends AbstractTable {

    private List<ContactEntry> contacts = new ArrayList<>();
    private static Map<Integer, ArrayList<ContactEntry>> folderToContactsMap = new HashMap<>();

    public ContactTable(String filePath, String tableName, PointerByReference tablePointer,
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
            ContactEntry contact = getContact(esedbLibrary, i, errorPointer, tablePointer);
            contacts.add(contact);
            int uniqueContactParentFolderId = FolderTable.uniqueFoldersMap.get(contact.getParentFolderId()).getRowId();
            addContactToParentFolder(contact, uniqueContactParentFolderId);
        }
    }
    

    public static void addContactToParentFolder(ContactEntry contact, int parentId) {
        ArrayList<ContactEntry> folderContacts = folderToContactsMap
            .computeIfAbsent(parentId, k -> new ArrayList<ContactEntry>());
        folderContacts.add(contact);
    }

    private ContactEntry getContact(EsedbLibrary esedbLibrary, int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

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
        String displayName = EsedbManager.getUnicodeValue(esedbLibrary, 68, recordPointerReference, filePath, errorPointer);
        String firstName = EsedbManager.getUnicodeValue(esedbLibrary, 69, recordPointerReference, filePath, errorPointer);
        String lastName = EsedbManager.getUnicodeValue(esedbLibrary, 86, recordPointerReference, filePath, errorPointer);
        String email = EsedbManager.getUnicodeValue(esedbLibrary, 65, recordPointerReference, filePath, errorPointer);
        String emailWork = EsedbManager.getUnicodeValue(esedbLibrary, 66, recordPointerReference, filePath, errorPointer);
        String emailOther = EsedbManager.getUnicodeValue(esedbLibrary, 67, recordPointerReference, filePath, errorPointer);
        String phone = EsedbManager.getUnicodeValue(esedbLibrary, 90, recordPointerReference, filePath, errorPointer);
        String workPhone = EsedbManager.getUnicodeValue(esedbLibrary, 80, recordPointerReference, filePath, errorPointer);
        String address = EsedbManager.getUnicodeValue(esedbLibrary, 78, recordPointerReference, filePath, errorPointer);
        boolean hasName = EsedbManager.getBooleanValue(esedbLibrary, 17, recordPointerReference, filePath, errorPointer);
        int parentFolderId = EsedbManager.getInt32Value(esedbLibrary, 2, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        ContactEntry contact = new ContactEntry(rowId);
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

    public static ArrayList<ContactEntry> getFolderChildContacts(int parentFolderId) {
        ArrayList<ContactEntry> childContacts = folderToContactsMap.get(parentFolderId);
        
        if (childContacts == null) {
            return new ArrayList<ContactEntry>();
        }
        return childContacts;
    }
    
}
