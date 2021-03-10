package dpf.inc.sepinf.browsers.parsers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
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

import dpf.sp.gpinf.indexer.parsers.EDBParser;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import dpf.sp.gpinf.indexer.util.TimeConverter;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class EdgeWebCacheParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final MediaType EDGE_WEB_CACHE = MediaType.application("x-edge-web-cache"); //$NON-NLS-1$

    public static final MediaType EDGE_HISTORY = MediaType.application("x-edge-history"); //$NON-NLS-1$

    public static final MediaType EDGE_HISTORY_REG = MediaType.application("x-edge-history-registry"); //$NON-NLS-1$

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(EDGE_WEB_CACHE);

    private static Logger LOGGER = LoggerFactory.getLogger(EdgeWebCacheParser.class);

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
                LOGGER.error("Libesedb dll not loaded properly. " + EdgeWebCacheParser.class.getSimpleName()
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
                        + " will be disabled.", e);
                SUPPORTED_TYPES = Collections.EMPTY_SET;
            }
    }

    private void printError(String function, int result, PointerByReference errorPointer) {
        LOGGER.warn("Error decoding " + itemInfo.getPath() + ": Function '" + function + "'. Function result number '"
                + result + "' Error value: " + errorPointer.getValue().getString(0)); //$NON-NLS-1$
        esedbLibrary.libesedb_error_free(errorPointer);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        itemInfo = context.get(ItemInfo.class);

        TemporaryResources tmp = new TemporaryResources();
        File webcacheFile = tmp.createTemporaryFile();
        File evidenceFile = null;
        TikaInputStream tis = TikaInputStream.get(stream, tmp);

        try {

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (extractor.shouldParseEmbedded(metadata)) {

                evidenceFile = tis.getFile();

                String filePath = evidenceFile.getAbsolutePath();

                PointerByReference filePointerReference = new PointerByReference();
                List<EdgeContainer> history = getHistory(filePath, filePointerReference);

                int virtualId = 1;

                int i = 0;

                for (EdgeContainer ec : history) {

                    try (FileOutputStream tmpHistoryFile = new FileOutputStream(webcacheFile)) {

                        ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-8"); //$NON-NLS-1$
                        Metadata historyMetadata = new Metadata();
                        historyMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, EDGE_HISTORY.toString());
                        historyMetadata.add(Metadata.RESOURCE_NAME_KEY, "Edge History " + ec.getTableName()); // $NON-NLS-1$
                        historyMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(virtualId));
                        historyMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$

                        parseEdgeHistory(historyHandler, historyMetadata, context, ec);

                        try (FileInputStream fis = new FileInputStream(webcacheFile)) {
                            extractor.parseEmbedded(fis, handler, historyMetadata, true);
                        }
                    }

                    for (EdgeVisit ev : ec) {

                        if (!extractEntries)
                            break;

                        i++;
                        Metadata metadataHistory = new Metadata();

                        metadataHistory.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, EDGE_HISTORY_REG.toString());
                        metadataHistory.add(Metadata.RESOURCE_NAME_KEY, "Edge History Entry " + i); //$NON-NLS-1$
                        metadataHistory.set(TikaCoreProperties.CREATED, ev.getCreationDate());
                        metadataHistory.set(TikaCoreProperties.MODIFIED, ev.getModifiedDate());
                        metadataHistory.set(ExtraProperties.ACCESSED, ev.getAccessedDate());
                        metadataHistory.set(ExtraProperties.VISIT_DATE, ev.getAccessedDate());
                        metadataHistory.add(ExtraProperties.URL, ev.getUrl());
                        metadataHistory.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(virtualId));
                        metadataHistory.add((BasicProps.HASH), "");

                        extractor.parseEmbedded(new EmptyInputStream(), handler, metadataHistory, true);
                    }

                    closeTablePointer(ec.getTablePointer());
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

    private void parseEdgeHistory(ContentHandler handler, Metadata metadata, ParseContext context,
            Iterable<EdgeVisit> edgeHistory) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xHandler = null;

        // edgeHistory.sort(Comparator.reverseOrder());

        try {

            xHandler = new XHTMLContentHandler(handler, metadata);
            xHandler.startDocument();

            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$

            xHandler.startElement("h2 align=center"); //$NON-NLS-1$
            xHandler.characters("Edge Visited Sites History"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$

            xHandler.startElement("table"); //$NON-NLS-1$

            xHandler.startElement("tr"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(""); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("VISIT COUNT"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("LAST VISIT DATE (UTC)"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$

            int i = 1;

            for (EdgeVisit e : edgeHistory) {

                xHandler.startElement("tr"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Long.toString(e.getAccessCount()));
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(e.getAccessedDateAsString());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(e.getUrl());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }

            xHandler.endElement("table"); //$NON-NLS-1$

            xHandler.endDocument();

        } finally {
            if (xHandler != null)
                xHandler.endDocument();
        }
    }

    protected List<EdgeContainer> getHistory(String filePath, PointerByReference filePointerReference)
            throws EdgeWebCacheException {
        List<EdgeContainer> history = new LinkedList<EdgeContainer>();

        try {
            /*
             * Variables used by file functions
             */

            // PointerByReference columnPointerReference = new PointerByReference();
            PointerByReference errorPointer = new PointerByReference();
            IntByReference numberOfTables = new IntByReference();
            // int numColumns;
            int numTables;

            /*
             * Table Container_n columns names 0 - EntryId 1 - ContainerId 2 - CacheId 3 -
             * UrlHash 4 - SecureDirectory 5 - FileSize 6 - Type 7 - Flags 8 - AccessCount 9
             * - SyncTime 10- CreationTime 11- ExpiryTime 12 - ModifiedTime 13 -
             * AccessedTime 14 - PostCheckTime 15 - SyncCount 16 - ExemptionDelta 17 - Url
             * 18 - Filename 19 - FileExtension 20 - RequestHeaders 21 - ResponseHeaders 22
             * - RedirectUrl 23 - Group 24 - ExtraData
             */

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

            for (int tables = 0; tables < numTables; tables++) {

                /*
                 * Variables used by table functions
                 */
                PointerByReference tablePointerReference = new PointerByReference();

                Memory tableName = new Memory(256);
                IntByReference tableNameSize = new IntByReference();

                IntByReference numberOfColumns = new IntByReference();
                LongByReference numberOfRecords = new LongByReference();

                IntByReference columnType = new IntByReference();
                // IntByReference columnName = new IntByReference();
                // IntByReference columnNameSize = new IntByReference();

                long numRecords;

                result = esedbLibrary.libesedb_file_get_table(filePointerReference.getValue(), tables,
                        tablePointerReference, errorPointer);
                if (result < 0)
                    printError("File Get Table", result, errorPointer);

                result = esedbLibrary.libesedb_table_get_utf8_name_size(tablePointerReference.getValue(), tableNameSize,
                        errorPointer);
                if (result < 0)
                    printError("Table Get UTF8 Name Size", result, errorPointer);

                result = esedbLibrary.libesedb_table_get_utf8_name(tablePointerReference.getValue(), tableName,
                        tableNameSize.getValue(), errorPointer);
                if (result < 0)
                    printError("Table Get UTF8 Name", result, errorPointer);
                String tableNameString = tableName.getString(0);

                result = esedbLibrary.libesedb_table_get_number_of_columns(tablePointerReference.getValue(),
                        numberOfColumns, columnFlags, errorPointer);
                if (result < 0)
                    printError("Table Get Number of Columns", result, errorPointer);

                result = esedbLibrary.libesedb_table_get_number_of_records(tablePointerReference.getValue(),
                        numberOfRecords, errorPointer);
                if (result < 0)
                    printError("Table Get Number of Records", result, errorPointer);

                numRecords = numberOfRecords.getValue();

                /* Get table that begins with 'Container_' */
                if (tableNameString.contains("Container_")) {

                    EdgeContainer ec = new EdgeContainer(tableNameString, tablePointerReference) {
                        @Override
                        public Iterator<EdgeVisit> iterator() {
                            return new Iterator<EdgeVisit>() {

                                int i = 0;

                                @Override
                                public boolean hasNext() {
                                    return i < numRecords;
                                }

                                @Override
                                public EdgeVisit next() {
                                    return getRecord(i++, errorPointer, tablePointerReference, columnType);
                                }

                            };
                        }
                    };

                    /* Save table entries */
                    history.add(ec);

                }

            }

        } finally {

        }
        return history;
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

    private EdgeVisit getRecord(int i, PointerByReference errorPointer, PointerByReference tablePointerReference,
            IntByReference columnType) {

        int result = 0;
        /*
         * Variables used by record functions
         */
        PointerByReference recordPointerReference = new PointerByReference();
        IntByReference recordNumberOfValues = new IntByReference();

        /*
         * Variables used by value functions
         */
        IntByReference recordValueDataSize = new IntByReference();
        IntByReference recordValueData32 = new IntByReference();
        IntByReference valueDataFlags = new IntByReference();
        LongByReference recordValueData = new LongByReference();
        Memory recordValueDataUrl = new Memory(3072);
        Memory recordValueDataFilename = new Memory(1024);

        /* Table values */
        long entryId = 0;
        long fileSize = 0;
        long accessCount = 0;
        long modified = 0;
        long accessed = 0;
        long creation = 0;
        String url = "";
        String file = "";

        result = esedbLibrary.libesedb_table_get_record(tablePointerReference.getValue(), i, recordPointerReference,
                errorPointer);
        if (result < 0)
            printError("Table Get Record", result, errorPointer);

        result = esedbLibrary.libesedb_record_get_number_of_values(recordPointerReference.getValue(),
                recordNumberOfValues, errorPointer);
        if (result < 0)
            printError("Record Get Number of Values", result, errorPointer);

        /*
         * The column types
         *
         * enum LIBESEDB_COLUMN_TYPES { LIBESEDB_COLUMN_TYPE_NULL = 0,
         * LIBESEDB_COLUMN_TYPE_BOOLEAN = 1, LIBESEDB_COLUMN_TYPE_INTEGER_8BIT_UNSIGNED
         * = 2, LIBESEDB_COLUMN_TYPE_INTEGER_16BIT_SIGNED = 3,
         * LIBESEDB_COLUMN_TYPE_INTEGER_32BIT_SIGNED = 4, LIBESEDB_COLUMN_TYPE_CURRENCY
         * = 5, LIBESEDB_COLUMN_TYPE_FLOAT_32BIT = 6, LIBESEDB_COLUMN_TYPE_DOUBLE_64BIT
         * = 7, LIBESEDB_COLUMN_TYPE_DATE_TIME = 8, LIBESEDB_COLUMN_TYPE_BINARY_DATA =
         * 9, LIBESEDB_COLUMN_TYPE_TEXT = 10, LIBESEDB_COLUMN_TYPE_LARGE_BINARY_DATA =
         * 11, === Url, Filename === LIBESEDB_COLUMN_TYPE_LARGE_TEXT = 12,
         * LIBESEDB_COLUMN_TYPE_SUPER_LARGE_VALUE = 13, === AccessCount ===
         * LIBESEDB_COLUMN_TYPE_INTEGER_32BIT_UNSIGNED = 14, === EntryId, FileSize,
         * CreationTime, ModifiedTime, AccessedTime ===
         * LIBESEDB_COLUMN_TYPE_INTEGER_64BIT_SIGNED = 15, LIBESEDB_COLUMN_TYPE_GUID =
         * 16, LIBESEDB_COLUMN_TYPE_INTEGER_16BIT_UNSIGNED = 17 };
         */

        /*
         * Get values of interest
         */

        /* Integer 64bit signed */
        result = esedbLibrary.libesedb_record_get_value_64bit(recordPointerReference.getValue(), 0, recordValueData,
                errorPointer);
        if (result < 0)
            printError("Record Get EntryId Data", result, errorPointer);
        entryId = recordValueData.getValue();
        /* Integer 64bit signed */
        result = esedbLibrary.libesedb_record_get_value_64bit(recordPointerReference.getValue(), 5, recordValueData,
                errorPointer);
        if (result < 0)
            printError("Record Get FileSize Data", result, errorPointer);
        fileSize = recordValueData.getValue();
        /* Integer 32bit unsigned */
        result = esedbLibrary.libesedb_record_get_value_32bit(recordPointerReference.getValue(), 8, recordValueData32,
                errorPointer);
        if (result < 0)
            printError("Record Get AccessCount Data", result, errorPointer);
        accessCount = recordValueData32.getValue();

        /*
         * LDAP Timestamp The 18-digit Active Directory timestamps, also named 'Windows
         * NT time format' and 'Win32 FILETIME or SYSTEMTIME'. These are used in
         * Microsoft Active Directory for pwdLastSet, accountExpires, LastLogon,
         * LastLogonTimestamp and LastPwdSet. The timestamp is the number of
         * 100-nanoseconds intervals (1 nanosecond = one billionth of a second) since
         * Jan 1, 1601 UTC.
         */
        /* Integer 64bit signed */
        result = esedbLibrary.libesedb_record_get_value_64bit(recordPointerReference.getValue(), 10, recordValueData,
                errorPointer);
        if (result < 0)
            printError("Record Get CreationTime Data", result, errorPointer);
        creation = TimeConverter.filetimeToMillis(recordValueData.getValue());
        /* Integer 64bit signed */
        result = esedbLibrary.libesedb_record_get_value_64bit(recordPointerReference.getValue(), 12, recordValueData,
                errorPointer);
        if (result < 0)
            printError("Record Get ModifiedTime Data", result, errorPointer);
        modified = TimeConverter.filetimeToMillis(recordValueData.getValue());
        /* Integer 64bit signed */
        result = esedbLibrary.libesedb_record_get_value_64bit(recordPointerReference.getValue(), 13, recordValueData,
                errorPointer);
        if (result < 0)
            printError("Record Get AccessedTime Data", result, errorPointer);
        accessed = TimeConverter.filetimeToMillis(recordValueData.getValue());

        /* Large Text */
        result = esedbLibrary.libesedb_record_get_column_type(recordPointerReference.getValue(), 17, columnType,
                errorPointer);
        if (result < 0)
            printError("Record Get Column Type", result, errorPointer);

        result = esedbLibrary.libesedb_record_get_value_data_flags(recordPointerReference.getValue(), 17,
                valueDataFlags, errorPointer);
        if (result < 0)
            printError("Record Get Value Data Flags", result, errorPointer);

        if (valueDataFlags.getValue() == 1) {
            result = esedbLibrary.libesedb_record_get_value_utf8_string_size(recordPointerReference.getValue(), 17,
                    recordValueDataSize, errorPointer);
            if (result < 0)
                printError("Record Get URL UTF8 String Size", result, errorPointer);

            if ((recordValueDataSize.getValue() > 0) && (result == 1)) {
                result = esedbLibrary.libesedb_record_get_value_utf8_string(recordPointerReference.getValue(), 17,
                        recordValueDataUrl, recordValueDataSize.getValue(), errorPointer);
                if (result < 0)
                    printError("Record Get URL UTF8 String", result, errorPointer);
                url = recordValueDataUrl.getString(0);
            }
        }
        /* Large Text */
        result = esedbLibrary.libesedb_record_get_value_utf8_string_size(recordPointerReference.getValue(), 18,
                recordValueDataSize, errorPointer);
        if (result < 0)
            printError("Record Get FileName UTF8 String Size", result, errorPointer);
        if ((recordValueDataSize.getValue() > 0) && (result == 1)) {
            result = esedbLibrary.libesedb_record_get_value_utf8_string(recordPointerReference.getValue(), 18,
                    recordValueDataFilename, recordValueDataSize.getValue(), errorPointer);
            if (result < 0)
                printError("Record Get FileName UTF8 String", result, errorPointer);
            file = recordValueDataFilename.getString(0);
        }

        result = esedbLibrary.libesedb_record_free(recordPointerReference, errorPointer);
        if (result < 0)
            printError("Record Free", result, errorPointer);

        return new EdgeVisit(entryId, fileSize, accessCount, creation, modified, accessed, file, url);
    }
}
