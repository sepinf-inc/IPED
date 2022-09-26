package iped.parsers.mail.win10;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.database.EDBParser;
import iped.parsers.mail.win10.entries.MessageEntry;
import iped.parsers.mail.win10.entries.RecipientEntry;
import iped.parsers.mail.win10.entries.RecipientTable;
import iped.parsers.mail.win10.tables.AbstractTable;
import iped.parsers.mail.win10.tables.MessageTable;
import iped.parsers.util.EsedbManager;
import iped.parsers.util.ItemInfo;

/*
 * Important tables to parse from store.vol:
 *  Message, Contact, Appointment, Attachment, Recipient, Folders, Store
 * 
 * .dat files categories
 * Unistore\data\0; Windows phone data
 * Unistore\data\2; contact lists within the account (contacts)
 * Unistore\data\3; the contents/body of the email (messages)
 * Unistore\data\5; calendar invitations (appointments)
 * Unistore\data\7; email attachments
 * 
 * checkboxes:
 * | | extract properties from store.vol into lists of new objects (representing the important tables)
 * | | search for the matching .dat files and add more properties to the objects
 * | | extract each instance as subitems in their respective categories
 * 
 */

public class Win10MailParser extends AbstractParser {

    public static final MediaType WIN10_MAIL_DB = MediaType.application("x-win10-mail-db");
    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(WIN10_MAIL_DB);

    private static Logger LOGGER = LoggerFactory.getLogger(Win10MailParser.class);

    private static Object lock = new Object();

    private static EsedbLibrary esedbLibrary;

    private EDBParser genericParser = new EDBParser();

    protected boolean extractEntries = true;

    private ItemInfo itemInfo;

    @Field
    public void setExtractEntries(boolean extractEntries) {
        this.extractEntries = extractEntries;
    }

    static {
        esedbLibrary = EsedbManager.getEsedbLibrary();
        if (EsedbManager.loadFailed()) {
            SUPPORTED_TYPES = Collections.emptySet();
        }
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        itemInfo = context.get(ItemInfo.class);

        TemporaryResources tmp = new TemporaryResources();
        // File tableFile = tmp.createTemporaryFile();
        File storeVolFile = null;
        TikaInputStream storeVolTis = TikaInputStream.get(stream, tmp);
        
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        try {
            if (extractor.shouldParseEmbedded(metadata)) {
                storeVolFile = storeVolTis.getFile();

                String storeVolPath = storeVolFile.getAbsolutePath();

                PointerByReference filePointerReference = new PointerByReference();

                List<AbstractTable> tables = getMailTables(storeVolPath, filePointerReference);

                for (AbstractTable table : tables) {

                    if (table instanceof MessageTable) {
                        MessageTable msgTable = (MessageTable) table;
                        for (MessageEntry message : msgTable.getMessages()) {
                            String str = "";
                            if (message.getRecipients() != null) {
                                str = message.getRecipients().get(0).getDisplayName();
                            }
                            System.out.println(message.getRowId() + ": " + str);
                        }
                    }
                    closeTablePointer(table.getTablePointer());
                }
                closeFilePointer(filePointerReference);
            }
        } catch (Exception e) {
            genericParser.parse(storeVolTis, handler, metadata, context);
            throw new TikaException(this.getClass().getSimpleName() + " exception", e); //$NON-NLS-1$

        } finally {
            tmp.close();
        }
    }

    
    /** 
     * Retrieves all important mail tables from the specified database
     * @param filePath
     * @param filePointerReference
     * @return List of tables extracted from database
     * @throws Win10MailException
     */
    protected List<AbstractTable> getMailTables(String filePath, PointerByReference filePointerReference)
            throws Win10MailException {
        List<AbstractTable> tables = new LinkedList<AbstractTable>();

        String dbPath = itemInfo.getPath();

        try {
            PointerByReference errorPointer = new PointerByReference();
            IntByReference numTablesRef = new IntByReference();
            int numTables;

            int accessFlags = 1;    // 1 - read, 2 - write
            int result = 0;

            // initialize file and get general info from database
            synchronized (lock) {
                result = esedbLibrary.libesedb_file_initialize(filePointerReference, errorPointer);
                if (result < 0)
                    EsedbManager.printError("File Initialize", result, dbPath, errorPointer);

                result = esedbLibrary.libesedb_check_file_signature(filePath, errorPointer);
                if (result < 0)
                    EsedbManager.printError("Check File Signature", result, dbPath, errorPointer);
                if (result == 0)
                    throw new Win10MailException("File does not contain an ESEDB");

                result = esedbLibrary.libesedb_file_open(filePointerReference.getValue(), filePath, accessFlags,
                        errorPointer);
                if (result < 0)
                    EsedbManager.printError("File Open", result, dbPath, errorPointer);

                result = esedbLibrary.libesedb_file_get_number_of_tables(filePointerReference.getValue(),
                        numTablesRef, errorPointer);
                if (result < 0)
                    EsedbManager.printError("File Get Number of Tables", result, dbPath, errorPointer);

                numTables = numTablesRef.getValue();

                LOGGER.info(numTables + " tables found in " + itemInfo.getPath());
            }

            // extract info from selected tables
            for (int tableIdx = 0; tableIdx < numTables; tableIdx++) {

                PointerByReference tablePointer = new PointerByReference();
                IntByReference tableNameSize = new IntByReference();
                Memory tableName = new Memory(256);

                LongByReference numberOfRecords = new LongByReference();

                long numRecords;

                result = esedbLibrary.libesedb_file_get_table(filePointerReference.getValue(), tableIdx,
                        tablePointer, errorPointer);
                if (result < 0)
                    EsedbManager.printError("File Get Table", result, dbPath, errorPointer);

                result = esedbLibrary.libesedb_table_get_utf8_name_size(tablePointer.getValue(), tableNameSize,
                        errorPointer);
                if (result < 0)
                    EsedbManager.printError("Table Get UTF8 Name Size", result, dbPath, errorPointer);

                result = esedbLibrary.libesedb_table_get_utf8_name(tablePointer.getValue(), tableName,
                        tableNameSize.getValue(), errorPointer);
                if (result < 0)
                    EsedbManager.printError("Table Get UTF8 Name", result, dbPath, errorPointer);
                
                String tableNameStr = tableName.getString(0);

                result = esedbLibrary.libesedb_table_get_number_of_records(tablePointer.getValue(),
                        numberOfRecords, errorPointer);
                if (result < 0)
                    EsedbManager.printError("Table Get Number of Records", result, dbPath, errorPointer);

                numRecords = numberOfRecords.getValue();

                if (tableNameStr.contains("Message")) {
                    MessageTable msgTable = new MessageTable(itemInfo.getPath(), tableNameStr, tablePointer, errorPointer, numRecords);
                    msgTable.populateTable(esedbLibrary);
                    tables.add(msgTable);
                }

                if (tableNameStr.contains("Recipient")) {
                    RecipientTable recipientTable = new RecipientTable(itemInfo.getPath(), tableNameStr, tablePointer, errorPointer, numRecords);
                    recipientTable.populateTable(esedbLibrary);
                    tables.add(recipientTable);
                }
            }

        } finally {

        }
        return tables;
    }

    private void closeTablePointer(PointerByReference tablePointer) {
        PointerByReference errorPointer = new PointerByReference();
        int result = esedbLibrary.libesedb_table_free(tablePointer, errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Free", result, itemInfo.getPath(), errorPointer);
    }

    private void closeFilePointer(PointerByReference filePointerReference) {
        PointerByReference errorPointer = new PointerByReference();
        int result = esedbLibrary.libesedb_file_close(filePointerReference.getValue(), errorPointer);
        if (result < 0)
            EsedbManager.printError("File Close", result, itemInfo.getPath(), errorPointer);

        result = esedbLibrary.libesedb_file_free(filePointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("File Free", result, itemInfo.getPath(), errorPointer);
    }
}
