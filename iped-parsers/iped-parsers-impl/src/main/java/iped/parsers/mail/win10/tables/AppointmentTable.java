package iped.parsers.mail.win10.tables;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.entries.AppointmentEntry;
import iped.parsers.mail.win10.entries.AppointmentEntry.ResponseType;
import iped.parsers.util.EsedbManager;

public class AppointmentTable extends AbstractTable {

    private static ArrayList<AppointmentEntry> appointments = new ArrayList<>();
    private static Map<Integer, ArrayList<AppointmentEntry>> folderToApptMap = new HashMap<>();

    public AppointmentTable(String filePath, String tableName, PointerByReference tablePointer,
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
            AppointmentEntry appointment = getAppointment(esedbLibrary, i, errorPointer, tablePointer);
            appointments.add(appointment);
            int uniqueApptParentFolderId = FolderTable.uniqueFoldersMap.get(appointment.getParentFolderId()).getRowId();
            addAppointmentToParentFolder(appointment, uniqueApptParentFolderId);
        }
    }

    public static void addAppointmentToParentFolder(AppointmentEntry appointment, int parentId) {
        ArrayList<AppointmentEntry> folderAppoints = folderToApptMap
            .computeIfAbsent(parentId, k -> new ArrayList<AppointmentEntry>());
        folderAppoints.add(appointment);
    }
    
    public static ArrayList<AppointmentEntry> getFolderChildAppointments(int parentFolderId) {
        ArrayList<AppointmentEntry> childAppointments = folderToApptMap.get(parentFolderId);

        if (childAppointments == null) {
            return new ArrayList<AppointmentEntry>();
        }
        return childAppointments;
    }

    private AppointmentEntry getAppointment(EsedbLibrary esedbLibrary, int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

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
        String eventName = EsedbManager.getUnicodeValue(esedbLibrary, 34, recordPointerReference, filePath, errorPointer);
        String location = EsedbManager.getUnicodeValue(esedbLibrary, 37, recordPointerReference, filePath, errorPointer);
        boolean repeat = EsedbManager.getBooleanValue(esedbLibrary, 4, recordPointerReference, filePath, errorPointer);
        boolean allDay = EsedbManager.getBooleanValue(esedbLibrary, 6, recordPointerReference, filePath, errorPointer);
        long status = EsedbManager.getInt32Value(esedbLibrary, 7, recordPointerReference, filePath, errorPointer);
        long reminderTimeMin = EsedbManager.getInt32Value(esedbLibrary, 8, recordPointerReference, filePath, errorPointer);
        String organizer = EsedbManager.getUnicodeValue(esedbLibrary, 41, recordPointerReference, filePath, errorPointer);
        String account = EsedbManager.getUnicodeValue(esedbLibrary, 42, recordPointerReference, filePath, errorPointer);
        String link = EsedbManager.getUnicodeValue(esedbLibrary, 46, recordPointerReference, filePath, errorPointer);
        long durationMin = EsedbManager.getInt32Value(esedbLibrary, 18, recordPointerReference, filePath, errorPointer);
        Date startTime = EsedbManager.getFileTime(esedbLibrary, 20, recordPointerReference, filePath, errorPointer);
        String additionalPeople = EsedbManager.getBinaryValue(esedbLibrary, 44, recordPointerReference, filePath, errorPointer);
        int response = EsedbManager.getInt32Value(esedbLibrary, 22, recordPointerReference, filePath, errorPointer);
        long updateCount = EsedbManager.getInt32Value(esedbLibrary, 2, recordPointerReference, filePath, errorPointer);
        int parentFolderId = EsedbManager.getInt32Value(esedbLibrary, 16, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        AppointmentEntry appointment = new AppointmentEntry(rowId);
        appointment.setEventName(eventName);
        appointment.setLocation(location);
        appointment.setRepeat(repeat);
        appointment.setAllDay(allDay);
        appointment.setStatus(status);
        appointment.setReminderTimeMin(reminderTimeMin);
        appointment.setOrganizer(organizer);
        appointment.setAccount(account);
        appointment.setLink(link);
        appointment.setDurationMin(durationMin);
        appointment.setStartTime(startTime);
        appointment.setAdditionalPeople(additionalPeople);
        appointment.setResponse((response > 0 && response <= 4) ? ResponseType.values()[response-1] : null);
        appointment.setUpdateCount(updateCount);
        appointment.setParentFolderId(parentFolderId);

        return appointment;
    }
}
