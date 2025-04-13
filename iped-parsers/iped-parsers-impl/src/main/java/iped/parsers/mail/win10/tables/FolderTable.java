package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.ColumnCodes;
import iped.parsers.mail.win10.entries.FolderEntry;
import iped.parsers.util.EsedbManager;

public class FolderTable extends AbstractTable {

    // multiple ids may point to the same folder
    private Map<Integer, FolderEntry> uniqueFoldersMap = new TreeMap<>();

    private Map<Integer, ArrayList<FolderEntry>> parentFolderToSubfoldersMap = new HashMap<>();
    // auxiliary hashmaps for handling duplicate folders
    private Map<String, FolderEntry> nameFolderMap = new HashMap<>();
    private Map<Integer, FolderEntry> idFolderMap = new HashMap<>();

    private int rowIdPos, storeIdPos, displayNamePos, displayNamePos2, parentFolderIdPos, createTimePos;

    public FolderTable(EsedbLibrary esedbLibrary, String filePath, PointerByReference tablePointer,
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
        parentFolderIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.PARENT_FOLDER_ID, errorPointer, tablePointer, filePath);
        createTimePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.CREATE_TIME, errorPointer, tablePointer, filePath);
    }

    @Override
    public void populateTable() {

        for (int i = 0; i < numRecords; i++) {
            FolderEntry folderEntry = extractFolder(i, errorPointer, tablePointer);
            idFolderMap.put(folderEntry.getRowId(), folderEntry);

            // Some folders are the same but have different ids, we don't add duplicates do uniqueFolders
            FolderEntry sameNameFolder = nameFolderMap.get(folderEntry.getDisplayName());
            boolean duplicate = false;
            if (sameNameFolder == null) {   // no duplicates
                nameFolderMap.put(folderEntry.getDisplayName(), folderEntry);
                uniqueFoldersMap.put(folderEntry.getRowId(), folderEntry);
            } else {
                duplicate = areFoldersTheSame(folderEntry, sameNameFolder);
                if (duplicate == true) {
                    uniqueFoldersMap.put(folderEntry.getRowId(), sameNameFolder);
                    sameNameFolder.addFolderId(folderEntry.getRowId());
                }
            }
            if (duplicate == false) {
                addToParentFolder(folderEntry);
            }
        }
    }

    public ArrayList<FolderEntry> getFolders() {
        return new ArrayList<FolderEntry>(uniqueFoldersMap.values());
    }


    private void addToParentFolder(FolderEntry subfolder) {
        ArrayList<FolderEntry> parentFolderSubfolders = parentFolderToSubfoldersMap
            .computeIfAbsent(subfolder.getParentFolderId(), k -> new ArrayList<FolderEntry>());

        // add if not already present
        if (!parentFolderSubfolders.stream().map(f -> f.getRowId()).anyMatch(id -> id == subfolder.getRowId())) {
            parentFolderSubfolders.add(subfolder);
        }
    }


    private FolderEntry extractFolder(int row, PointerByReference errorPointer, PointerByReference tablePointerReference) {
        int result = 0;

        PointerByReference recordPointerRef = new PointerByReference();
        IntByReference recordNumberOfValues = new IntByReference();
        IntByReference numberOfColumns = new IntByReference();
        int columnFlags = 1;

        result = esedbLibrary.libesedb_table_get_number_of_columns(tablePointerReference.getValue(),
            numberOfColumns, columnFlags, errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Get Number of Columns", result, filePath, errorPointer);

        // get row (record)
        result = esedbLibrary.libesedb_table_get_record(tablePointerReference.getValue(), row, recordPointerRef,
                errorPointer);
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
        int parentFolderId = EsedbManager.getInt32Value(esedbLibrary, parentFolderIdPos, recordPointerRef, filePath, errorPointer);
        Date createTime = EsedbManager.getFileTime(esedbLibrary, createTimePos, recordPointerRef, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerRef, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        FolderEntry folder = new FolderEntry(rowId);
        folder.setStoreId(storeId);
        folder.setDisplayName(displayName);
        folder.setCreateTime(createTime);
        folder.setParentFolderID(parentFolderId);

        return folder;
    }

    private boolean areFoldersTheSame(FolderEntry folder1, FolderEntry folder2) {
        boolean duplicate = true;
        FolderEntry parentFolder1 = idFolderMap.get(folder1.getParentFolderId());
        FolderEntry parentFolder2 = idFolderMap.get(folder2.getParentFolderId());
        while (true) {
            if (parentFolder1 == null && parentFolder2 == null) break;
            if (parentFolder1 == null || parentFolder2 == null) {
                duplicate = false;
                break;
            }
            if (parentFolder1.getDisplayName().equals(parentFolder2.getDisplayName())) {
                parentFolder1 = idFolderMap.get(parentFolder1.getParentFolderId()); // parent of parent1
                parentFolder2 = idFolderMap.get(parentFolder2.getParentFolderId());
            } else {
                duplicate = false;
                break;
            }
        }
        return duplicate;
    }

    public Map<Integer, FolderEntry> getUniqueFoldersMap() {
        return uniqueFoldersMap;
    }
}
