package iped.parsers.mail.win10.tables;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.ColumnCodes;
import iped.parsers.mail.win10.entries.StoreEntry;
import iped.parsers.util.EsedbManager;

public class StoreTable extends AbstractTable {

    List<StoreEntry> storeEntries = new ArrayList<>();

    private int rowIdPos, addressPos, displayNamePos, displayNamePos2, protocolPos, downloadNewEmailMinsPos, downloadEmailFromDaysPos,
        incomingEmailServerPos, outgoingEmailServerPos, outgoingEmailServerUsernamePos, contactsServerPos, calendarServerPos;

    public StoreTable(EsedbLibrary esedbLibrary, String filePath, PointerByReference tablePtr,
            PointerByReference errorPtr, long numRecords) {
        super();
        this.esedbLibrary = esedbLibrary;
        this.tablePointer = tablePtr;
        this.errorPointer = errorPtr;
        this.numRecords = numRecords;
        this.filePath = filePath;

        rowIdPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.ROW_ID, errorPtr, tablePtr, filePath);
        addressPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.EMAIL_ADDRESS, errorPtr, tablePtr, filePath);
        displayNamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DISPLAY_NAME_1, errorPtr, tablePtr, filePath);
        displayNamePos2 = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DISPLAY_NAME_2, errorPtr, tablePtr, filePath);
        protocolPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.PROTOCOL, errorPtr, tablePtr, filePath);
        downloadNewEmailMinsPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DOWNLOAD_EMAIL_FROM, errorPtr, tablePtr, filePath);
        downloadEmailFromDaysPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.DOWNLOAD_NEW_EMAIL, errorPtr, tablePtr, filePath);
        incomingEmailServerPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.INCOMING_EMAIL_SERVER, errorPtr, tablePtr, filePath);
        outgoingEmailServerPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.OUTGOING_EMAIL_SERVER, errorPtr, tablePtr, filePath);
        outgoingEmailServerUsernamePos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.OUTGOING_EMAIL_SERVER_USERNAME, errorPtr, tablePtr, filePath);
        contactsServerPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.CONTACTS_SERVER, errorPtr, tablePtr, filePath);
        calendarServerPos = EsedbManager.getColumnPosition(esedbLibrary, ColumnCodes.CALENDAR_SERVER, errorPtr, tablePtr, filePath);
    }

    @Override
    public void populateTable() {
        for (int i = 0; i < numRecords; i++) {
            StoreEntry store = extractStoreEntry(i, errorPointer, tablePointer);
            storeEntries.add(store);
        }
    }

    public List<StoreEntry> getStoreEntries() {
        return storeEntries;
    }

    private StoreEntry extractStoreEntry(int row, PointerByReference errorPtr, PointerByReference tablePointerRef) {
        int result = 0;

        PointerByReference recordPointerRef = new PointerByReference();
        IntByReference recordNumberOfValues = new IntByReference();

        // get row (record)
        result = esedbLibrary.libesedb_table_get_record(tablePointerRef.getValue(), row, recordPointerRef,
                errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Get Record", result, filePath, errorPointer);

        result = esedbLibrary.libesedb_record_get_number_of_values(recordPointerRef.getValue(),
                recordNumberOfValues, errorPointer);
        if (result < 0)
            EsedbManager.printError("Record Get Number of Values", result, filePath, errorPointer);

        int rowId = EsedbManager.getInt32Value(esedbLibrary, rowIdPos, recordPointerRef, filePath, errorPtr);
        String protocol = EsedbManager.getUnicodeValue(esedbLibrary, protocolPos, recordPointerRef, filePath, errorPtr);
        String address = EsedbManager.getUnicodeValue(esedbLibrary, addressPos, recordPointerRef, filePath, errorPtr);
        String displayName = EsedbManager.getUnicodeValue(esedbLibrary, displayNamePos, recordPointerRef, filePath, errorPtr);
        if (displayName.isEmpty())
            displayName = EsedbManager.getUnicodeValue(esedbLibrary, displayNamePos2, recordPointerRef, filePath, errorPtr);
        long downloadNewEmailMins = EsedbManager.getInt32Value(esedbLibrary, downloadNewEmailMinsPos, recordPointerRef, filePath, errorPtr);
        long downloadEmailFromDays = EsedbManager.getInt32Value(esedbLibrary, downloadEmailFromDaysPos, recordPointerRef, filePath, errorPtr);
        String incomingEmailServer = EsedbManager.getUnicodeValue(esedbLibrary, incomingEmailServerPos, recordPointerRef, filePath, errorPtr);
        String outgoingEmailServer = EsedbManager.getUnicodeValue(esedbLibrary, outgoingEmailServerPos, recordPointerRef, filePath, errorPtr);
        String outgoingEmailServerUsername = EsedbManager.getUnicodeValue(esedbLibrary, outgoingEmailServerUsernamePos, recordPointerRef, filePath, errorPtr);
        String contactsServer = EsedbManager.getUnicodeValue(esedbLibrary, contactsServerPos, recordPointerRef, filePath, errorPtr);
        String calendarServer = EsedbManager.getUnicodeValue(esedbLibrary, calendarServerPos, recordPointerRef, filePath, errorPtr);

        StoreEntry storeEntry = new StoreEntry(rowId);
        storeEntry.setRowId(rowId);
        storeEntry.setAddress(address);
        storeEntry.setDisplayName(displayName);
        storeEntry.setProtocol(protocol);
        storeEntry.setDownloadEmailFromDays(downloadEmailFromDays);
        storeEntry.setDownloadNewEmailMins(downloadNewEmailMins);
        storeEntry.setOutgoingEmailServer(outgoingEmailServer);
        storeEntry.setIncomingEmailServer(incomingEmailServer);
        storeEntry.setOutgoingEmailServerUsername(outgoingEmailServerUsername);
        storeEntry.setContactsServer(contactsServer);
        storeEntry.setCalendarServer(calendarServer);

        return storeEntry;
    }

    public static XHTMLContentHandler emitStoreHeader(ContentHandler handler, Metadata metadata) throws SAXException {
        String[] colNames = {"Id", "DisplayName", "Address", "Protocol", "DownloadNewEmail (Mins)", "DownloadEmailFrom (Days)",
            "IncomingEmailServer", "OutgoingEmailServer", "OutgoingEmailServerUsername", "ContactsServer", "CalendarServer" };

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.startElement("head");
        xhtml.startElement("style");
        xhtml.characters("table {border-collapse: collapse;}"
            + " table, td, th {border: 1px solid black; max-width:500px; word-wrap: break-word;}"
            + " th {white-space: nowrap; padding: 5px;}");
        xhtml.endElement("style");
        xhtml.endElement("head");

        xhtml.startElement("h2 align=center");
        xhtml.characters("Store Table");
        xhtml.endElement("h2");
        xhtml.startElement("br");
        xhtml.startElement("br");

        xhtml.startElement("table");
        xhtml.startElement("tr");

        for (String colname : colNames) {
            xhtml.startElement("th");
            xhtml.characters(colname);
            xhtml.endElement("th");
        }
        xhtml.endElement("tr");

        return xhtml;
    }

    public static void emitStoreEntry(XHTMLContentHandler xhtmlStore, StoreEntry storeEntry)
        throws SQLException, TikaException, SAXException {

        xhtmlStore.startElement("td");
        xhtmlStore.characters("" + storeEntry.getRowId());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters(storeEntry.getDisplayName());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters(storeEntry.getAddress());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters(storeEntry.getProtocol());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters("" + storeEntry.getDownloadNewEmailMins());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters("" + storeEntry.getDownloadEmailFromDays());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters(storeEntry.getIncomingEmailServer());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters(storeEntry.getOutgoingEmailServer());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters(storeEntry.getOutgoingEmailServerUsername());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters(storeEntry.getContactsServer());
        xhtmlStore.endElement("td");
        xhtmlStore.startElement("td");
        xhtmlStore.characters(storeEntry.getCalendarServer());
        xhtmlStore.endElement("td");

        xhtmlStore.endElement("tr");   
    }

    public static void endStoreEntries(XHTMLContentHandler xhtmlStore) throws SAXException {
        xhtmlStore.endElement("table");
        xhtmlStore.endDocument();
    }
}
