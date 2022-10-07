package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.entries.FolderEntry;
import iped.parsers.util.EsedbManager;

public class FolderTable extends AbstractTable {

    private static ArrayList<FolderEntry> folderList = new ArrayList<>();
    private static Map<Long, ArrayList<FolderEntry>> parentFolderToSubfoldersMap = new HashMap<>();

    public FolderTable(String filePath, String tableName, PointerByReference tablePointer,
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
            FolderEntry folderEntry = getFolder(esedbLibrary, i, errorPointer, tablePointer);
            folderList.add(folderEntry);
            addSubfolder(folderEntry);
        }
    }

    public ArrayList<FolderEntry> getFolders() {
        return folderList;
    }

    public static void addSubfolder(FolderEntry subfolder) {
        ArrayList<FolderEntry> parentFolderSubfolders = parentFolderToSubfoldersMap
            .computeIfAbsent(subfolder.getParentFolderId(), k -> new ArrayList<FolderEntry>());

        // add if not already present
        if (!parentFolderSubfolders.stream().map(f -> f.getRowId()).anyMatch(id -> id == subfolder.getRowId())) {
            parentFolderSubfolders.add(subfolder);
        }
    }

    public static ArrayList<FolderEntry> getSubfolders(long parentFolderId) {
        ArrayList<FolderEntry> subfolders = parentFolderToSubfoldersMap.get(parentFolderId);
        if (subfolders == null) {
            return new ArrayList<FolderEntry>();
        }
        return subfolders;
    }

    private FolderEntry getFolder(EsedbLibrary esedbLibrary, int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

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
        String displayName = EsedbManager.getUnicodeValue(esedbLibrary, 34, recordPointerReference, filePath, errorPointer);
        long parentFolderId = EsedbManager.getInt32Value(esedbLibrary, 2, recordPointerReference, filePath, errorPointer);
        Date createTime = EsedbManager.getFileTime(esedbLibrary, 37, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        FolderEntry folder = new FolderEntry(rowId);
        folder.setDisplayName(displayName);
        folder.setCreateTime(createTime);
        folder.setParentFolderID(parentFolderId);

        return folder;
    }
}
