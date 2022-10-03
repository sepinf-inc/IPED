package iped.parsers.mail.win10;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
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
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import iped.data.IItemReader;
import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.database.EDBParser;
import iped.parsers.mail.OutlookPSTParser;
import iped.parsers.mail.win10.entries.AbstractEntry;
import iped.parsers.mail.win10.entries.AttachmentEntry;
import iped.parsers.mail.win10.entries.MessageEntry;
import iped.parsers.mail.win10.entries.RecipientTable;
import iped.parsers.mail.win10.tables.AbstractTable;
import iped.parsers.mail.win10.tables.AttachmentTable;
import iped.parsers.mail.win10.tables.MessageTable;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.Util;
import iped.parsers.util.EsedbManager;
import iped.parsers.util.IgnoreContentHandler;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.SimpleHTMLEncoder;

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
    public static final MediaType WIN10_MAIL_MSG_REG = MediaType.application("x-win10-mail-msg-registry");
    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(WIN10_MAIL_DB);

    private static final char MESSAGE_CATEGORY = '3';

    private static Logger LOGGER = LoggerFactory.getLogger(Win10MailParser.class);

    private static Object lock = new Object();

    private static EsedbLibrary esedbLibrary;

    private EDBParser genericParser = new EDBParser();

    private EmbeddedDocumentExtractor extractor;
    private XHTMLContentHandler xhtml;

    protected boolean extractEntries = true;

    private ItemInfo itemInfo;

    private static IItemSearcher searcher;

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
        searcher = context.get(IItemSearcher.class);

        TemporaryResources tmp = new TemporaryResources();
        // File tableFile = tmp.createTemporaryFile();
        File storeVolFile = null;
        TikaInputStream storeVolTis = TikaInputStream.get(stream, tmp);
        
        extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        try {
            if (extractor.shouldParseEmbedded(metadata)) {
                storeVolFile = storeVolTis.getFile();

                String storeVolPath = storeVolFile.getAbsolutePath();

                PointerByReference filePointerReference = new PointerByReference();

                List<AbstractTable> tables = getMailTables(storeVolPath, filePointerReference);
                
                File tableFile = tmp.createTemporaryFile();

                for (AbstractTable table : tables) {

                    try (FileOutputStream tmpTableFile = new FileOutputStream(tableFile)) {

                        ToXMLContentHandler tableHandler = new ToXMLContentHandler(tmpTableFile, "UTF-8");
                        Metadata tableMetadata = new Metadata();
                        tableMetadata.add(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_DB.toString());
                        tableMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Mail Table " + table.getTableName()); // $NON-NLS-1$
                        tableMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
                        tableMetadata.set(BasicProps.HASCHILD, "true");
                        tableMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                        xhtml = new XHTMLContentHandler(tableHandler, metadata);

                        try (FileInputStream fis = new FileInputStream(tableFile)) {
                            extractor.parseEmbedded(fis, handler, tableMetadata, true);
                        }
                    }

                    if (table instanceof MessageTable) {
                        MessageTable msgTable = (MessageTable) table;
                        for (MessageEntry message : msgTable.getMessages()) {
                            String str = "";
                            if (message.getAttachments() != null && message.getAttachments().size() > 1) {
                                str = message.getAttachments().get(1).getFileName();
                            }
                            System.out.println(message.getRowId() + ": " + str);

                            
                            String contentPath = Win10MailParser.getEntryLocation(message, MESSAGE_CATEGORY);
                            IItemReader item = Win10MailParser.searchItemInCase(contentPath, message.getMessageSize());


                            if (item != null) {
                                InputStream is = item.getBufferedInputStream();
                                InputStreamReader utf16Reader = new InputStreamReader(is, StandardCharsets.UTF_16LE);

                                // convert text from utf-16 to utf-8
                                byte[] bom = new byte[2];   // byte-order mark
                                is.mark(2);
                                if ((is.read(bom)) != -1) {
                                    is.reset();
                                    if (bom[0] == (byte) 0xFE && bom[1] == (byte) 0xFF) {
                                        utf16Reader = new InputStreamReader(is, StandardCharsets.UTF_16BE);
                                    }
                                }
                                ReaderInputStream utf8IS = new ReaderInputStream(utf16Reader, StandardCharsets.UTF_8);

                                if (utf8IS != null) {
                                    message.setContentHtml(IOUtils.toString(utf8IS, StandardCharsets.UTF_8));
                                    processMail(message, storeVolPath);
                                }

                                utf8IS.close();
                                utf16Reader.close();
                            }
                        }
                    }
                    closeTablePointer(table.getTablePointer());
                }
                closeFilePointer(filePointerReference);
            }
        } catch (Exception e) {
            genericParser.parse(storeVolTis, handler, metadata, context);
            throw new TikaException(this.getClass().getSimpleName() + " exception", e);

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
                
                if (tableNameStr.contains("Attachment")) {
                    AttachmentTable attachmentTable = new AttachmentTable(itemInfo.getPath(), tableNameStr, tablePointer, errorPointer, numRecords);
                    attachmentTable.populateTable(esedbLibrary);
                    tables.add(attachmentTable);
                }
            }

        } finally {

        }
        return tables;
    }

    private void processMail(MessageEntry email, String path) {
        Metadata metadata = new Metadata();

        try {
            String subject = email.getSubject();
            if (subject == null || subject.trim().isEmpty())
                subject = Messages.getString("OutlookPSTParser.NoSubject");

            metadata.set(ExtraProperties.MESSAGE_SUBJECT, subject);
            metadata.set(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_MSG_REG.toString());
            metadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
            // metadata.set(ExtraProperties.ITEM_VIRTUAL_ID, "0");
            // metadata.set(ExtraProperties.PARENT_VIRTUAL_ID, "0");

            Charset charset = Charset.forName("UTF-8"); 
            StringBuilder preview = new StringBuilder();
            preview.append("<html>");
            preview.append("<head>");
            preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />");
            preview.append("</head>");
            preview.append(
                    "<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:0px;\">");

            preview.append("<div class=\"ipedtheme\">");
            preview.append("<b>" + Messages.getString("OutlookPSTParser.Subject") + ": "
                    + SimpleHTMLEncoder.htmlEncode(subject) + "</b><br>");

            String from = OutlookPSTParser.formatNameAndAddress(email.getSenderName(), email.getSenderEmailAddress());
            if (!from.isEmpty()) {
                metadata.set(Message.MESSAGE_FROM, from);
                preview.append("<b>" + Messages.getString("OutlookPSTParser.From") + ":</b> "
                        + SimpleHTMLEncoder.htmlEncode(from) + "<br>");
            }

            preview.append("</div>\n");
            String bodyHtml = email.getContentHtml();
            if (bodyHtml != null && !bodyHtml.trim().isEmpty()) {
                preview.append(bodyHtml);
                metadata.set(ExtraProperties.MESSAGE_BODY,
                        Util.getContentPreview(bodyHtml, MediaType.TEXT_HTML.toString()));
            }

            preview.append("</body>");
            preview.append("</html>");

            ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset));
            preview = null;

            if (extractor.shouldParseEmbedded(metadata))
                extractor.parseEmbedded(stream, xhtml, metadata, true);

            stream.close();

        } catch (Exception e) {
            LOGGER.warn("Exception extracting email: {}>>{}\t{}", path, email.getSubject(), e.toString()); //$NON-NLS-1$
            // e.printStackTrace();
        }

    }

    public static String getEntryLocation(AbstractEntry entry, char category) {
        String entryPath = "";

        String contentType = "1013";

        String hexRowId = Integer.toHexString(entry.getRowId());
        while (hexRowId.length() < 8) hexRowId = "0" + hexRowId;

        entryPath += "/" + "abcdefghijklmnopqrstuvwxyz".charAt(Integer.parseInt("" + hexRowId.charAt(hexRowId.length()-1), 16)) + "/";
        entryPath += hexRowId.charAt(hexRowId.length()-2);
        entryPath += hexRowId.substring(1, 6) + "0";
        entryPath += hexRowId.charAt(hexRowId.length()-1);
        entryPath += "0000000";
        entryPath += category;
        entryPath += contentType;
        entryPath += ".dat";

        return entryPath;
    }

    public static IItemReader searchItemInCase(String path, long size) {
        if (searcher == null) {
            return null;
        }

        String query = BasicProps.PATH + ":\"" + searcher.escapeQuery(path) + "\"" + " && " + BasicProps.LENGTH + ":" + size;

        List<IItemReader> items = searcher.search(query);
        if (items == null || items.isEmpty()) {
            // search without size restriction
            query = BasicProps.PATH + ":\"" + searcher.escapeQuery(path) + "\"";
            items = searcher.search(query);

            if (items == null || items.isEmpty())
                return null;
        }

        return items.get(0);
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
