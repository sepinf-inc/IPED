package iped.parsers.mail.win10;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
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
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EdgeWebCacheException;
import iped.parsers.browsers.edge.EdgeWebCacheParser;
import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.database.EDBParser;
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
        if (Platform.isWindows()) {
            String osArch = System.getProperty("os.arch");
            String arch = osArch.equals("amd64") || osArch.equals("x86_64") ? "x64" : "x86";

            try (InputStream is = EdgeWebCacheParser.class
                    .getResourceAsStream("/nativelibs/libesedb/" + arch + "/libesedb.dll")) {
                File file = new File(System.getProperty("java.io.tmpdir") + "/libesedb.dll");
                if (!file.exists())
                    Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.load(file.getAbsolutePath());

            } catch (Throwable e) {
                LOGGER.error("Libesedb dll not loaded properly. " + Win10MailParser.class.getSimpleName()
                        + " will be disabled.", e);
                SUPPORTED_TYPES = Collections.EMPTY_SET;
            }
        }
        if (!SUPPORTED_TYPES.isEmpty())
            try {
                esedbLibrary = (EsedbLibrary) Native.load("esedb", EsedbLibrary.class);
                LOGGER.info("Libesedb library version: " + esedbLibrary.libesedb_get_version());

            } catch (Throwable e) {
                LOGGER.error("Libesedb JNA not loaded properly. " + EdgeWebCacheParser.class.getSimpleName()
                        + " will be disabled.");
                e.printStackTrace();
                SUPPORTED_TYPES = Collections.EMPTY_SET;
            }
    }

    private void printError(String function, int result, PointerByReference errorPointer) {
        LOGGER.warn("Error decoding " + itemInfo.getPath() + ": Function '" + function + "'. Function result number '"
                + result + "' Error value: " + errorPointer.getValue().getString(0)); //$NON-NLS-1$
        esedbLibrary.libesedb_error_free(errorPointer);
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
        // File messagesFile = tmp.createTemporaryFile();
        File evidenceFile = null;
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        try {

            if (extractor.shouldParseEmbedded(metadata)) {

                evidenceFile = tis.getFile();

                String filePath = evidenceFile.getAbsolutePath();

                PointerByReference filePointerReference = new PointerByReference();
                List<AbstractTable> tables = getTables(filePath, filePointerReference);

                for (AbstractTable table : tables) {

                    if (table instanceof MessageTable) {
                        for (MessageEntry message : table) {
                            System.out.println(message.getRowId());
                        }
                    }

                    closeTablePointer(table.getTablePointer());
                }

                closeFilePointer(filePointerReference);
            }
        } catch (Exception e) {
            genericParser.parse(tis, handler, metadata, context);
            throw new TikaException(this.getClass().getSimpleName() + " exception", e); //$NON-NLS-1$

        } finally {
            tmp.close();
        }
    }

    protected List<AbstractTable> getTables(String filePath, PointerByReference filePointerReference)
            throws EdgeWebCacheException {
        List<AbstractTable> tables = new LinkedList<AbstractTable>();

        try {
            /*
             * Variables used by file functions
             */

            // PointerByReference columnPointerReference = new PointerByReference();
            PointerByReference errorPointer = new PointerByReference();
            IntByReference numberOfTables = new IntByReference();
            // int numColumns;
            int numTables;

            int accessFlags = 1;
            int columnFlags = 1;
            int result = 0;

            synchronized (lock) {

                result = esedbLibrary.libesedb_file_initialize(filePointerReference, errorPointer);
                if (result < 0)
                    printError("File Initialize", result, errorPointer);

                result = esedbLibrary.libesedb_check_file_signature(filePath, errorPointer);
                if (result < 0)
                    printError("Check File Signature", result, errorPointer);

                if (result == 0) {
                    throw new EdgeWebCacheException("File does not contains an ESEDB");
                }

                result = esedbLibrary.libesedb_file_open(filePointerReference.getValue(), filePath, accessFlags,
                        errorPointer);
                if (result < 0)
                    printError("File Open", result, errorPointer);

                result = esedbLibrary.libesedb_file_get_number_of_tables(filePointerReference.getValue(),
                        numberOfTables, errorPointer);
                if (result < 0)
                    printError("File Get Number of Tables", result, errorPointer);

                numTables = numberOfTables.getValue();

                LOGGER.info(numTables + " tables found in " + itemInfo.getPath());
            }

            for (int tableIdx = 0; tableIdx < numTables; tableIdx++) {

                /*
                 * Variables used by table functions
                 */
                PointerByReference tablePointer = new PointerByReference();

                Memory tableName = new Memory(256);
                IntByReference tableNameSize = new IntByReference();

                IntByReference numberOfColumns = new IntByReference();
                LongByReference numberOfRecords = new LongByReference();

                long numRecords;

                result = esedbLibrary.libesedb_file_get_table(filePointerReference.getValue(), tableIdx,
                        tablePointer, errorPointer);
                if (result < 0)
                    printError("File Get Table", result, errorPointer);

                result = esedbLibrary.libesedb_table_get_utf8_name_size(tablePointer.getValue(), tableNameSize,
                        errorPointer);
                if (result < 0)
                    printError("Table Get UTF8 Name Size", result, errorPointer);

                result = esedbLibrary.libesedb_table_get_utf8_name(tablePointer.getValue(), tableName,
                        tableNameSize.getValue(), errorPointer);
                if (result < 0)
                    printError("Table Get UTF8 Name", result, errorPointer);
                String tableNameString = tableName.getString(0);

                result = esedbLibrary.libesedb_table_get_number_of_columns(tablePointer.getValue(),
                        numberOfColumns, columnFlags, errorPointer);
                if (result < 0)
                    printError("Table Get Number of Columns", result, errorPointer);

                result = esedbLibrary.libesedb_table_get_number_of_records(tablePointer.getValue(),
                        numberOfRecords, errorPointer);
                if (result < 0)
                    printError("Table Get Number of Records", result, errorPointer);

                numRecords = numberOfRecords.getValue();

                if (tableNameString.contains("Message")) {
                    tables.add(new MessageTable(esedbLibrary, tableNameString, tablePointer, errorPointer, numRecords));
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
            printError("Table Free", result, errorPointer);
    }

    private void closeFilePointer(PointerByReference filePointerReference) {
        PointerByReference errorPointer = new PointerByReference();
        int result = esedbLibrary.libesedb_file_close(filePointerReference.getValue(), errorPointer);
        if (result < 0)
            printError("File Close", result, errorPointer);

        result = esedbLibrary.libesedb_file_free(filePointerReference, errorPointer);
        if (result < 0)
            printError("File Free", result, errorPointer);
    }
}
