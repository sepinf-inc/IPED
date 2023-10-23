package iped.parsers.mail.win10.tables;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.ColumnCodes;
import iped.parsers.mail.win10.entries.AppointmentEntry;
import iped.parsers.mail.win10.entries.AppointmentEntry.ResponseType;
import iped.parsers.mail.win10.entries.FolderEntry;
import iped.parsers.util.EsedbManager;

public class AppointmentTable extends AbstractTable {

    private ArrayList<AppointmentEntry> appointments = new ArrayList<>();
    private Map<Integer, ArrayList<AppointmentEntry>> folderToApptMap = new HashMap<>();

    private int rowIdPos, storeIdPos, eventNamePos, locationPos, repeatPos, allDayPos, statusPos, reminderTimeMinPos, organizerPos,
        accountPos, linkPos, durationMinPos, startTimePos, additionalPeoplePos, responsePos, updateCountPos, parentFolderIdPos;

    public AppointmentTable(EsedbLibrary esedbLibrary, String filePath, PointerByReference tablePointer,
        PointerByReference errorPointer, long numRecords) {
        super();
        this.esedbLibrary = esedbLibrary;
        this.tablePointer = tablePointer;
        this.errorPointer = errorPointer;
        this.numRecords = numRecords;
        this.filePath = filePath;

        rowIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ROW_ID, errorPointer, tablePointer, filePath);
        storeIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.STORE_ID, errorPointer, tablePointer, filePath);
        eventNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.EVENT, errorPointer, tablePointer, filePath);
        locationPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.LOCATION, errorPointer, tablePointer, filePath);
        repeatPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.REPEAT, errorPointer, tablePointer, filePath);
        allDayPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ALLDAY, errorPointer, tablePointer, filePath);
        statusPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.STATUS, errorPointer, tablePointer, filePath);
        reminderTimeMinPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.REMINDER_TIME_MIN, errorPointer, tablePointer, filePath);
        organizerPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ORGANISER, errorPointer, tablePointer, filePath);
        accountPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ACCOUNT, errorPointer, tablePointer, filePath);
        linkPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.LINK, errorPointer, tablePointer, filePath);
        durationMinPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DURATION, errorPointer, tablePointer, filePath);
        startTimePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.START_TIME, errorPointer, tablePointer, filePath);
        additionalPeoplePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ADDITIONAL_PEOPLE, errorPointer, tablePointer, filePath);
        responsePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.RESPONSE, errorPointer, tablePointer, filePath);
        updateCountPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.UPDATE, errorPointer, tablePointer, filePath);
        parentFolderIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.PARENT_FOLDER_ID, errorPointer, tablePointer, filePath);

    }

    @Override
    public void populateTable() {
        for (int i = 0; i < numRecords; i++) {
            AppointmentEntry appointment = extractAppointment(i, errorPointer, tablePointer);
            appointments.add(appointment);
            addAppointmentToParentFolder(appointment, appointment.getParentFolderId());
        }
    }

    private void addAppointmentToParentFolder(AppointmentEntry appointment, int parentId) {
        ArrayList<AppointmentEntry> folderAppts = folderToApptMap
            .computeIfAbsent(parentId, k -> new ArrayList<AppointmentEntry>());
        folderAppts.add(appointment);
    }
    
    public ArrayList<AppointmentEntry> getFolderAppointments(FolderEntry folder) {
        ArrayList<AppointmentEntry> childAppointments = new ArrayList<>();
        for (int folderId : folder.getAllFolderIds()) {
            if (folderToApptMap.get(folderId) != null)
                childAppointments.addAll(folderToApptMap.get(folderId));
        }
        return childAppointments;
    }

    private AppointmentEntry extractAppointment(int i, PointerByReference errorPointer, PointerByReference tablePointerReference) {

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
        int storeId = EsedbManager.getInt32Value(esedbLibrary, storeIdPos, recordPointerReference, filePath, errorPointer);
        String eventName = EsedbManager.getUnicodeValue(esedbLibrary, eventNamePos, recordPointerReference, filePath, errorPointer);
        String location = EsedbManager.getUnicodeValue(esedbLibrary, locationPos, recordPointerReference, filePath, errorPointer);
        boolean repeat = EsedbManager.getBooleanValue(esedbLibrary, repeatPos, recordPointerReference, filePath, errorPointer);
        boolean allDay = EsedbManager.getBooleanValue(esedbLibrary, allDayPos, recordPointerReference, filePath, errorPointer);
        long status = EsedbManager.getInt32Value(esedbLibrary, statusPos, recordPointerReference, filePath, errorPointer);
        long reminderTimeMin = EsedbManager.getInt32Value(esedbLibrary, reminderTimeMinPos, recordPointerReference, filePath, errorPointer);
        String organizer = EsedbManager.getUnicodeValue(esedbLibrary, organizerPos, recordPointerReference, filePath, errorPointer);
        String account = EsedbManager.getUnicodeValue(esedbLibrary, accountPos, recordPointerReference, filePath, errorPointer);
        String link = EsedbManager.getUnicodeValue(esedbLibrary, linkPos, recordPointerReference, filePath, errorPointer);
        long durationMin = EsedbManager.getInt32Value(esedbLibrary, durationMinPos, recordPointerReference, filePath, errorPointer);
        Date startTime = EsedbManager.getFileTime(esedbLibrary, startTimePos, recordPointerReference, filePath, errorPointer);
        byte[] additionalPeopleBytes = EsedbManager.getBinaryValue(esedbLibrary, additionalPeoplePos, recordPointerReference, filePath, errorPointer);
        String additionalPeople = additionalPeopleBytes != null ? new String(additionalPeopleBytes, StandardCharsets.UTF_8) : null;
        int response = EsedbManager.getInt32Value(esedbLibrary, responsePos, recordPointerReference, filePath, errorPointer);
        long updateCount = EsedbManager.getInt32Value(esedbLibrary, updateCountPos, recordPointerReference, filePath, errorPointer);
        int parentFolderId = EsedbManager.getInt32Value(esedbLibrary, parentFolderIdPos, recordPointerReference, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Free", result, filePath, errorPointer);

        AppointmentEntry appointment = new AppointmentEntry(rowId);
        appointment.setStoreId(storeId);
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
