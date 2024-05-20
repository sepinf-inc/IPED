package iped.parsers.mail.win10;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
import iped.parsers.mail.win10.entries.AppointmentEntry;
import iped.parsers.mail.win10.entries.AttachmentEntry;
import iped.parsers.mail.win10.entries.ContactEntry;
import iped.parsers.mail.win10.entries.FolderEntry;
import iped.parsers.mail.win10.entries.MessageEntry;
import iped.parsers.mail.win10.entries.RecipientEntry;
import iped.parsers.mail.win10.entries.StoreEntry;
import iped.parsers.mail.win10.tables.AbstractTable;
import iped.parsers.mail.win10.tables.AppointmentTable;
import iped.parsers.mail.win10.tables.AttachmentTable;
import iped.parsers.mail.win10.tables.ContactTable;
import iped.parsers.mail.win10.tables.FolderTable;
import iped.parsers.mail.win10.tables.MessageTable;
import iped.parsers.mail.win10.tables.RecipientTable;
import iped.parsers.mail.win10.tables.StoreTable;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.EsedbManager;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.Messages;
import iped.parsers.util.MetadataUtil;
import iped.parsers.util.ToXMLContentHandler;
import iped.parsers.util.Util;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;
import iped.utils.IOUtil;
import iped.utils.ImageUtil;
import iped.utils.SimpleHTMLEncoder;

/**
   * Parses Windows Mail App store.vol edb file. Extracts folders, emails, attachments, and appointments from the database.
   * Searches for additional information (e.g. attachment files and body of the emails) in the case.
   * Generates a subitem in the case for each table entry.
   *
   * More info: https://eprints.whiterose.ac.uk/133161/1/Navigating_the_Windows_Mail_database_accepted.pdf
   *
   * @author Felipe Farias da Costa
 */
public class Win10MailParser extends AbstractParser {

    public static final MediaType WIN10_MAIL_DB = MediaType.application("x-win10-mail-db");
    public static final MediaType WIN10_MAIL_MSG = MediaType.parse("message/x-win10-mail-msg");
    public static final MediaType WIN10_MAIL_APPT = MediaType.application("x-win10-mail-appointment");
    public static final MediaType WIN10_MAIL_ATTACH = MediaType.application("x-win10-mail-attach");
    public static final MediaType WIN10_MAIL_CONTACT = MediaType.application("x-win10-mail-contact");
    public static final MediaType WIN10_MAIL_STORE = MediaType.application("x-win10-mail-store");
    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(WIN10_MAIL_DB);

    private static final char MESSAGE_CATEGORY = '3';
    private static final char CONTACT_CATEGORY = '2';
    private static final char APPOINTMENT_CATEGORY = '5';
    private static final char ATTACH_CATEGORY = '7';

    private static final String EMAIL_VIRTUAL_ID_PREFIX = "winAppMail-";
    private static final String FOLDER_VIRTUAL_ID_PREFIX = "winAppFolder-";
    private static final String ATTACH_VIRTUAL_ID_PREFIX = "winAppAttach-";
    private static final String APPT_VIRTUAL_ID_PREFIX = "winAppAppt-";

    private enum FileTag {
        UNICODE("001e"), CONTACT_JPEG_1("00ff"), CONTACT_JPEG_2("01a8"), CONTACT_JPEG_3("01b5"),
        MESSAGE_UNICODE("1000"), ASCII("1013"), ASCII_PAIRS("10b0"), ANY("3701");

        private final String tag;

        FileTag(final String tag) {
            this.tag = tag;
        }

        @Override
        public String toString() {
            return tag;
        }
    }

    private class Parameters {
        FolderTable folderTable;
        MessageTable messageTable;
        RecipientTable recipientTable;
        AttachmentTable attachTable;
        AppointmentTable apptTable;
        ContactTable contactTable;
        StoreTable storeTable;
        EmbeddedDocumentExtractor extractor;
        ItemInfo itemInfo;
        int grandParentId = -1;
        XHTMLContentHandler xhtml;
        IItemSearcher searcher;
        SimpleDateFormat df = new SimpleDateFormat(Messages.getString("OutlookPSTParser.DateFormat"));
    }

    private static Logger LOGGER = LoggerFactory.getLogger(Win10MailParser.class);

    private static Object lock = new Object();

    private static EsedbLibrary esedbLibrary;

    // This is already thread safe
    private EDBParser genericParser = new EDBParser();

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

        Parameters params = new Parameters();

        params.itemInfo = context.get(ItemInfo.class);
        params.searcher = context.get(IItemSearcher.class);
        params.df.setTimeZone(TimeZone.getTimeZone("UTC"));

        // get grandParent of current item to search related items below same tree
        Integer parentId = context.get(IItemReader.class).getParentId();
        List<IItemReader> parent = params.searcher.search(BasicProps.ID + ":" + parentId);
        if (parent != null && parent.size() == 1) {
            params.grandParentId = parent.get(0).getParentId();
        }

        params.xhtml = new XHTMLContentHandler(handler, metadata);
        params.xhtml.startDocument();

        TemporaryResources tmp = new TemporaryResources();
        File storeVolFile = null;
        TikaInputStream storeVolTis = TikaInputStream.get(stream, tmp);

        params.extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        try {
            if (params.extractor.shouldParseEmbedded(metadata)) {
                storeVolFile = storeVolTis.getFile();

                String storeVolPath = storeVolFile.getAbsolutePath();
                PointerByReference filePointerReference = new PointerByReference();
                List<AbstractTable> tables = setMailTables(storeVolPath, filePointerReference, params);

                for (FolderEntry folder : params.folderTable.getFolders()) {
                    // process folder considering the unique parent folder, since it may be duplicate
                    FolderEntry uniqueParentFolder = params.folderTable.getUniqueFoldersMap().get(folder.getParentFolderId());
                    int parentFolderId = uniqueParentFolder != null ? uniqueParentFolder.getRowId() : folder.getParentFolderId();
                    processFolder(folder, parentFolderId, params);

                    ArrayList<MessageEntry> childEmails = params.messageTable.getFolderChildMessages(folder);
                    if (!childEmails.isEmpty()) {
                        for (MessageEntry childEmail : childEmails) {
                            childEmail.setBody(getMessageBody(childEmail, params));
                            processEmail(childEmail, folder.getRowId(), params);
                        }
                    }
                    ArrayList<AppointmentEntry> childAppts = params.apptTable.getFolderAppointments(folder);
                    if (!childAppts.isEmpty()) {
                        for (AppointmentEntry childAppt : childAppts) {
                            childAppt.setBody(getAppointmentBody(childAppt, params));
                            processAppointment(childAppt, folder.getRowId(), params);
                        }
                    }

                    ArrayList<ContactEntry> childContacts = params.contactTable.getFolderChildContacts(folder);
                    if (!childContacts.isEmpty()) {
                        for (ContactEntry childContact : childContacts) {
                            processContact(childContact, folder.getRowId(), params);
                        }
                    }
                }

                // Store table
                File storeTableFile = tmp.createTemporaryFile();
                try (FileOutputStream tmpStoreFile = new FileOutputStream(storeTableFile)) {
                    Metadata metadataStore = new Metadata();
                    metadataStore.add(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_STORE.toString());
                    metadataStore.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Win10 Mail App Store Table");

                    ToXMLContentHandler storeTableHandler = new ToXMLContentHandler(tmpStoreFile, "UTF-8");
                    XHTMLContentHandler xhtmlStore = StoreTable.emitStoreHeader(storeTableHandler, metadataStore);

                    for (StoreEntry storeEntry : params.storeTable.getStoreEntries()) {
                        StoreTable.emitStoreEntry(xhtmlStore, storeEntry);
                    }
                    StoreTable.endStoreEntries(xhtmlStore);

                    try (FileInputStream fis = new FileInputStream(storeTableFile)) {
                        params.extractor.parseEmbedded(fis, params.xhtml, metadataStore, true);
                    }
                }

                for (AbstractTable table : tables)
                    closeTablePointer(table.getTablePointer(), params);
                closeFilePointer(filePointerReference, params);
            }
        } catch (Exception e) {
            LOGGER.warn("Exception parsing Win10 Mail DB, using generic parser for " + params.itemInfo.getPath(), e);
            genericParser.parse(storeVolTis, handler, metadata, context);
            throw new TikaException(this.getClass().getSimpleName() + " exception", e);

        } finally {
            params.xhtml.endDocument();
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
    protected List<AbstractTable> setMailTables(String filePath, PointerByReference filePointerReference, Parameters params)
            throws Win10MailException {
        List<AbstractTable> tables = new ArrayList<>();

        String dbPath = params.itemInfo.getPath();

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

                LOGGER.info(numTables + " tables found in " + params.itemInfo.getPath());
            }

            // extract info from selected tables
            for (int tableIdx = 0; tableIdx < numTables; tableIdx++) {

                PointerByReference tablePointer = new PointerByReference();
                IntByReference tableNameSize = new IntByReference();
                Memory tableNameRef = new Memory(256);

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

                result = esedbLibrary.libesedb_table_get_utf8_name(tablePointer.getValue(), tableNameRef,
                        tableNameSize.getValue(), errorPointer);
                if (result < 0)
                    EsedbManager.printError("Table Get UTF8 Name", result, dbPath, errorPointer);

                String tableName = tableNameRef.getString(0);

                result = esedbLibrary.libesedb_table_get_number_of_records(tablePointer.getValue(),
                        numberOfRecords, errorPointer);
                if (result < 0)
                    EsedbManager.printError("Table Get Number of Records", result, dbPath, errorPointer);

                numRecords = numberOfRecords.getValue();

                AbstractTable table = null;
                if (tableName.equals("Message")) {
                    params.messageTable = new MessageTable(esedbLibrary, dbPath, tablePointer, errorPointer, numRecords);
                    table = params.messageTable;
                } else if (tableName.equals("Recipient")) {
                    params.recipientTable = new RecipientTable(esedbLibrary, dbPath, tablePointer, errorPointer, numRecords);
                    table = params.recipientTable;
                } else if (tableName.equals("Attachment")) {
                    params.attachTable = new AttachmentTable(esedbLibrary, dbPath, tablePointer, errorPointer, numRecords);
                    table = params.attachTable;
                } else if (tableName.equals("Folders")) {
                    params.folderTable = new FolderTable(esedbLibrary, dbPath, tablePointer, errorPointer, numRecords);
                    table = params.folderTable;
                } else if (tableName.equals("Appointment")) {
                    params.apptTable = new AppointmentTable(esedbLibrary, dbPath, tablePointer, errorPointer, numRecords);
                    table = params.apptTable;
                } else if (tableName.equals("Contact")) {
                    params.contactTable = new ContactTable(esedbLibrary, dbPath, tablePointer, errorPointer, numRecords);
                    table = params.contactTable;
                } else if (tableName.equals("Store")) {
                    params.storeTable = new StoreTable(esedbLibrary, dbPath, tablePointer, errorPointer, numRecords);
                    table = params.storeTable;
                }

                if (table != null) {
                    table.populateTable();
                    tables.add(table);
                }
            }

        } finally {
        }

        return tables;
    }



    /** Extract folder preserving hierarchy
     * @param folder to be processed
     * @param parentId parent folder id
     * @throws SAXException
     * @throws IOException
     */
    private void processFolder(FolderEntry folder, long parentId, Parameters params) throws SAXException, IOException {
        Metadata folderMetadata = new Metadata();
        folderMetadata.set(TikaCoreProperties.TITLE, folder.getDisplayName());
        folderMetadata.set(TikaCoreProperties.CREATED, folder.getCreateTime());
        folderMetadata.set(ExtraProperties.EMBEDDED_FOLDER, "true");
        folderMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, FOLDER_VIRTUAL_ID_PREFIX + folder.getRowId());
        folderMetadata.add("StoreId", "" + folder.getStoreId());
        if (parentId != -1)
            folderMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, FOLDER_VIRTUAL_ID_PREFIX + parentId);
        params.extractor.parseEmbedded(new EmptyInputStream(), params.xhtml, folderMetadata, true);
    }


    /** Extract an appointment as a subitem with all information of the event
     * @param appointment to be processed
     * @param parentId used to link to parent folder
     * @throws SAXException
     * @throws IOException
     */
    private void processAppointment(AppointmentEntry appointment, long parentId, Parameters params) throws SAXException, IOException {
        Metadata appointMetadata = new Metadata();
        String body = appointment.getBody();

        appointMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        appointMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, APPT_VIRTUAL_ID_PREFIX + appointment.getRowId());
        appointMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, appointment.getEventName());
        appointMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_APPT.toString());
        appointMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, FOLDER_VIRTUAL_ID_PREFIX + parentId);
        appointMetadata.set(ExtraProperties.MESSAGE_BODY, Util.getContentPreview(body, MediaType.TEXT_HTML.toString()));
        if (appointment.getBodyFound());
            appointMetadata.add("originalBodyPath", appointment.getBodyOriginalPath());
        appointMetadata.add("StoreId", "" + appointment.getStoreId());

        Charset charset = Charset.forName("UTF-8");
        StringBuilder preview = new StringBuilder();

        preview.append("<html>");
        preview.append("<head>");
        preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />");
        preview.append("</head>");
        preview.append("<body>");
        preview.append(htmlPairLine("All day", "" + appointment.getAllDay()));
        preview.append(htmlPairLine("Event", appointment.getEventName()));
        preview.append(htmlPairLine("Location", appointment.getLocation()));
        preview.append(htmlPairLine("Organizer", appointment.getOrganizer()));
        preview.append(htmlPairLine("Account", appointment.getAccount()));
        if (!appointment.getLink().isEmpty())
            preview.append("Link:" + " <a href=\"" + appointment.getLink() + "\">" + appointment.getLink() + "</a><br>");
        preview.append(htmlPairLine("Duration", appointment.getDurationMin() + " minutes"));
        preview.append(htmlPairLine("Start Time", params.df.format(appointment.getStartTime())));
        preview.append(htmlPairLine("Reminder Time", appointment.getReminderTimeMin() + " minutes"));
        preview.append(htmlPairLine("Repeat", "" + appointment.getRepeat()));
        preview.append(htmlPairLine("Response", "" + appointment.getResponse()));
        preview.append(htmlPairLine("Additional People (raw)", appointment.getAdditionalPeople()));
        preview.append("<br>");
        preview.append(body.replace("\r\n", "<br>"));

        preview.append("</body>");
        preview.append("</html>");

        try (ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset))) {
            if (params.extractor.shouldParseEmbedded(appointMetadata)) {
                params.xhtml.characters("\t");
                params.extractor.parseEmbedded(stream, params.xhtml, appointMetadata, true);
            }
        }
    }

    /** Extract a contact as a subitem
     * @param contact to be processed
     * @param parentId used to link to parent folder
     * @throws SAXException
     * @throws IOException
     */
    private void processContact(ContactEntry contact, long parentId, Parameters params) throws SAXException, IOException {
        Metadata contactMetadata = new Metadata();

        contactMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        contactMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, APPT_VIRTUAL_ID_PREFIX + contact.getRowId());
        contactMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, contact.getDisplayName());
        contactMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_CONTACT.toString());
        contactMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, FOLDER_VIRTUAL_ID_PREFIX + parentId);
        contactMetadata.add("StoreId", "" + contact.getStoreId());

        Charset charset = Charset.forName("UTF-8");
        StringBuilder preview = new StringBuilder();

        preview.append("<html>");
        preview.append("<head>");
        preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />");
        preview.append("<head>");
        preview.append(htmlPairLine("First Name", contact.getFirstName()));
        preview.append(htmlPairLine("Last Name", contact.getLastName()));
        preview.append(htmlPairLine("Email", contact.getEmail()));
        preview.append(htmlPairLine("Work Email", contact.getEmailWork()));
        preview.append(htmlPairLine("Phone", contact.getPhone()));
        preview.append(htmlPairLine("Work Phone", contact.getWorkPhone()));
        preview.append(htmlPairLine("Address", contact.getAddress()));
        preview.append("<body>");

        FileTag[] contactTags = new FileTag[] { FileTag.ASCII, FileTag.ASCII_PAIRS, FileTag.CONTACT_JPEG_1,
            FileTag.CONTACT_JPEG_2, FileTag.CONTACT_JPEG_3 };
        for (FileTag contactTag : contactTags) {
            String contentPath = getEntryLocation(contact, CONTACT_CATEGORY, contactTag);
            Pair<IItemReader, String> itemQueryPair = searchItemInCase(contentPath, -1L, params);
            IItemReader item = itemQueryPair.getLeft();
            if (item != null) {
                if (contactTag == FileTag.CONTACT_JPEG_1 || contactTag == FileTag.CONTACT_JPEG_2 || contactTag == FileTag.CONTACT_JPEG_3) {
                    String imgSrc = item.getTempFile().toURI().toString();
                    preview.append("<img src=\"" + imgSrc + "\"><br>");
                } else {
                    InputStream is = item.getBufferedInputStream();
                    String contactInfo = Util.decodeMixedCharset(is.readAllBytes());
                    preview.append("<br>" + contactInfo.replace("\n", "<br>") + "<br>");
                }
            }
        }
        preview.append("</body>");
        preview.append("</html>");

        try (ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset))) {
            if (params.extractor.shouldParseEmbedded(contactMetadata)) {
                params.xhtml.characters("\t");
                params.extractor.parseEmbedded(stream, params.xhtml, contactMetadata, true);
            }
        }
    }

    private String htmlPairLine(String key, String value) {
        if (value != null && !value.isEmpty())
            return key + ": " + value + "<br>";
        return "";
    }


    /** Search through the case for a .dat file containing the appointment body
     * @param appointment from which the body is requested
     * @return appointment body
     * @throws IOException
     */
    private String getAppointmentBody(AppointmentEntry appointment, Parameters params) throws IOException {
        FileTag[] messageTags = new FileTag[] { FileTag.UNICODE, FileTag.ASCII_PAIRS };
        IItemReader item = null;
        String apptBody = "";
        String contentPath = "";

        for (FileTag messageTag : messageTags) {
            contentPath = getEntryLocation(appointment, APPOINTMENT_CATEGORY, messageTag);
            Pair<IItemReader, String> itemQueryPair = searchItemInCase(contentPath, 0, params);
            if (itemQueryPair.getLeft() != null) {
                item = itemQueryPair.getLeft();
                appointment.setBodyOriginalPath(item.getPath());
                break;
            }
        }

        if (item != null) {
            InputStream is = item.getBufferedInputStream();
            apptBody = Util.decodeMixedCharset(is.readAllBytes());
            is.close();
        }
        return apptBody;
    }


    /** Search through the case for a .dat file containing the email body, probably in html format
     * @param email that we want to search for the body
     * @return message body
     * @throws IOException
     */
    private String getMessageBody(MessageEntry email, Parameters params) throws IOException {
        FileTag[] messageTags = new FileTag[] { FileTag.ASCII, FileTag.MESSAGE_UNICODE };
        IItemReader item = null;
        for (FileTag messageTag : messageTags) {
            String contentPath = Win10MailParser.getEntryLocation(email, MESSAGE_CATEGORY, messageTag);
            Pair<IItemReader, String> itemQueryPair = searchItemInCase(contentPath, email.getMessageSize(), params);
            if (itemQueryPair.getLeft() != null) {
                item = itemQueryPair.getLeft();
                email.setBodyOriginalPath(item.getPath());
                break;
            }
        }

        if (item != null) {
            InputStream is = null;
            InputStreamReader utf16Reader = null;
            ReaderInputStream utf8IS = null;
            try {
                is = item.getBufferedInputStream();
                utf16Reader = new InputStreamReader(new CloseShieldInputStream(is), StandardCharsets.UTF_16LE);

                // convert text from utf-16 to utf-8
                byte[] byteOrderMark = new byte[2];
                is.mark(2);
                if ((is.readNBytes(byteOrderMark, 0, 2)) == 2) {
                    is.reset();
                    if (byteOrderMark[0] == (byte) 0xFE && byteOrderMark[1] == (byte) 0xFF) {
                        IOUtil.closeQuietly(utf16Reader);
                        utf16Reader = new InputStreamReader(is, StandardCharsets.UTF_16BE);
                    }
                }
                utf8IS = new ReaderInputStream(utf16Reader, StandardCharsets.UTF_8);

                String messageBody = IOUtils.toString(utf8IS, StandardCharsets.UTF_8);
                return messageBody;

            } finally {
                IOUtil.closeQuietly(utf8IS);
                IOUtil.closeQuietly(utf16Reader);
                IOUtil.closeQuietly(is);
            }
        }
        return "";
    }


    /**
     * Extracts the email with recipients, attachments and html body. Ready to be previewed
     * @param email to be processed
     * @param parentId used to link to parent folder
     * @param path
     */
    private void processEmail(MessageEntry email, long parentId, Parameters params) {
        Metadata emailMetadata = new Metadata();

        String subject = email.getSubject();
        if (subject == null || subject.trim().isEmpty())
            subject = Messages.getString("OutlookPSTParser.NoSubject");

        String virtualId = EMAIL_VIRTUAL_ID_PREFIX + email.getRowId();

        emailMetadata.set(ExtraProperties.MESSAGE_SUBJECT, subject);
        emailMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_MSG.toString());
        emailMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        emailMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, virtualId);
        emailMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, FOLDER_VIRTUAL_ID_PREFIX + parentId);
        emailMetadata.add(Message.MESSAGE_PREFIX + "StoreId", Long.toString(email.getStoreId()));
        emailMetadata.add(Message.MESSAGE_PREFIX + "ConversationId", Long.toString(email.getConversationId()));
        if (email.getBodyFound())
            emailMetadata.add(Message.MESSAGE_PREFIX + "OriginalBodyPath", email.getBodyOriginalPath());

        try {
            Charset charset = Charset.forName("UTF-8");
            StringBuilder preview = new StringBuilder();
            preview.append("<html>");
            preview.append("<head>");
            preview.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />");
            preview.append("</head>");
            preview.append(
                    "<body style=\"background-color:white;text-align:left;font-family:arial;color:black;font-size:14px;margin:0px;\">");

            preview.append("<div class=\"ipedtheme\">");

            // Subject:
            preview.append("<b>" + Messages.getString("OutlookPSTParser.Subject") + ": "
                    + SimpleHTMLEncoder.htmlEncode(subject) + "</b><br>");

            // From:
            String from = OutlookPSTParser.formatNameAndAddress(email.getSenderName(), email.getSenderEmailAddress());
            if (!from.isEmpty()) {
                emailMetadata.set(Message.MESSAGE_FROM, from);
                preview.append("<b>" + Messages.getString("OutlookPSTParser.From") + ":</b> "
                        + SimpleHTMLEncoder.htmlEncode(from) + "<br>");
            }

            // To, BCC, CC:
            Object[][] recipTypes = { { RecipientEntry.RecipientType.TO, Messages.getString("OutlookPSTParser.To") },
                { RecipientEntry.RecipientType.CC, "CC:" }, { RecipientEntry.RecipientType.BCC, "BCC:" } };
            String[] recipMeta = { Message.MESSAGE_TO, Message.MESSAGE_CC, Message.MESSAGE_BCC };

            for (int k = 0; k < recipTypes.length; k++) {
                List<String> recipientNames = new ArrayList<>();
                List<RecipientEntry> recipients = params.recipientTable.getMessageRecipients(email.getRowId());
                if (recipients != null) {
                    for (int i = 0; i < recipients.size(); i++) {
                        RecipientEntry recipient = recipients.get(i);
                        if (recipTypes[k][0] == recipient.getType()) {
                            String recipName = OutlookPSTParser.formatNameAndAddress(recipient.getDisplayName(), recipient.getEmailAddress());
                            if (!recipName.isEmpty()) {
                                recipientNames.add(recipName);
                            }
                            MetadataUtil.fillRecipientAddress(emailMetadata, recipient.getEmailAddress());
                        }
                    }
                }
                if (!recipientNames.isEmpty()) {
                    String key = recipMeta[k];
                    recipientNames.stream().forEach(r -> emailMetadata.add(key, r));
                    preview.append("<b>" + recipTypes[k][1] + "</b> "
                            + SimpleHTMLEncoder.htmlEncode(recipientNames.stream().collect(Collectors.joining("; ")))
                            + "<br>");
                }
            }

            // Sent time:
            Date sent = email.getMsgDeliveryTime();
            if (sent != null) {
                emailMetadata.set(ExtraProperties.MESSAGE_DATE, sent);
                preview.append("<b>" + Messages.getString("OutlookPSTParser.Sent") + ":</b> " + params.df.format(sent) + " (UTC) <br>");
            }

            // Attachments:
            ArrayList<AttachmentEntry> emailAttachments = params.attachTable.getMessageAttachments(email.getRowId());
            int noOfAttachments = emailAttachments.size();
            if (noOfAttachments > 0) {
                preview.append("<b>" + Messages.getString("OutlookPSTParser.Attachments") + " (" + noOfAttachments
                    + "):</b><br>");
                for (AttachmentEntry attach : emailAttachments) {
                    String contentPath = Win10MailParser.getEntryLocation(attach, ATTACH_CATEGORY, FileTag.ANY);
                    attach.setOriginalFileName(StringUtils.substringAfterLast(contentPath, "/"));
                    Pair<IItemReader, String> itemQueryPair = searchItemInCase(contentPath, attach.getAttachSize(), params);
                    attach.setCaseQuery(itemQueryPair.getRight());
                    emailMetadata.add(ExtraProperties.LINKED_ITEMS, attach.getCaseQuery());
                    IItemReader item = itemQueryPair.getLeft();
                    String desc = new DecimalFormat().format(attach.getAttachSize()) + " bytes";
                    if (attach.getMimeTag() != null && !attach.getMimeTag().isBlank()) {
                        desc += ", mime: " + SimpleHTMLEncoder.htmlEncode(attach.getMimeTag().strip());
                    }
                    if (item != null) {
                        attach.setCaseItem(item);
                        String queryHTML = SimpleHTMLEncoder.htmlEncode(attach.getCaseQuery());
                        preview.append("<a href=\"" + Util.getExportPath(item) + "\" onclick=\"app.open('" + queryHTML + "');\">" + SimpleHTMLEncoder.htmlEncode(attach.getFileName()) + "</a> <i>(" + desc + ")</i><br>");
                    } else {
                        preview.append(SimpleHTMLEncoder.htmlEncode(attach.getFileName()) + " <i>(" + Messages.getString("Win10Mail.NotFound") + ", " + desc + ")</i><br>");
                    }
                }
                emailMetadata.set(ExtraProperties.MESSAGE_ATTACHMENT_COUNT, noOfAttachments);
            }

            // Body:
            preview.append("<hr>");
            preview.append("</div>\n");
            String bodyHtml = email.getBody();

            // get body preview if full body is not found
            if ((bodyHtml == null || bodyHtml.isBlank()) && !email.getMsgAbstract().isBlank()) {
                bodyHtml = "<div>";
                bodyHtml += SimpleHTMLEncoder.htmlEncode(email.getMsgAbstract());
                bodyHtml += "</div>";
            }
            if (bodyHtml != null && !bodyHtml.trim().isEmpty()) {
                bodyHtml = handleInlineImages(bodyHtml, emailAttachments, email, params);
                preview.append(bodyHtml);
                emailMetadata.set(ExtraProperties.MESSAGE_BODY,
                        Util.getContentPreview(bodyHtml, MediaType.TEXT_HTML.toString()));
            }
            preview.append("</body>");

            preview.append("</html>");

            try (ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset))) {
                if (params.extractor.shouldParseEmbedded(emailMetadata))
                    params.xhtml.characters("\t");
                params.extractor.parseEmbedded(stream, params.xhtml, emailMetadata, true);
            }

            // See https://github.com/sepinf-inc/IPED/issues/1570 why this was turned off
            // for (AttachmentEntry attach : emailAttachments) {
            // processAttachment(attach, email, params);
            // }
        } catch (Exception e) {
            LOGGER.warn("Exception extracting email: subject='" + email.getSubject() + "' rowid='" + email.getRowId() + "' DB='" + params.itemInfo.getPath() + "'", e);
        }

    }

    /** Extract attachment with the correct file name, linked to the original .dat attachment file
     * @param attachment entry to be processed
     * @param path
     */
    private void processAttachment(AttachmentEntry attachment, MessageEntry email, Parameters params) {
        String parentId = EMAIL_VIRTUAL_ID_PREFIX + attachment.getMessageId();
        String filename = attachment.getFileName();
        long rowId = attachment.getRowId();
        try {
            Metadata attachMetadata = new Metadata();

            attachMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, attachment.getFileName());
            attachMetadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, attachment.getOriginalFileName());
            attachMetadata.add(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_ATTACH.toString());
            attachMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
            attachMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, ATTACH_VIRTUAL_ID_PREFIX + rowId);
            attachMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, parentId);
            attachMetadata.set(ExtraProperties.MESSAGE_IS_ATTACHMENT, Boolean.TRUE.toString());
            attachMetadata.set(ExtraProperties.LINKED_ITEMS, attachment.getCaseQuery());
            attachMetadata.set(Message.MESSAGE_PREFIX + "AttachmentMime", attachment.getMimeTag());
            attachMetadata.set(Message.MESSAGE_PREFIX + "AttachmentCid", attachment.getAttachCID());
            attachMetadata.set(Message.MESSAGE_PREFIX + "AttachmentSize", Long.toString(attachment.getAttachSize()));

            if (params.extractor.shouldParseEmbedded(attachMetadata)) {
                params.xhtml.characters("\t");
                params.extractor.parseEmbedded(new EmptyInputStream(), params.xhtml, attachMetadata, true);
            }

        } catch (Exception e) {
            LOGGER.warn("Exception extracting attachment: name='" + filename + "' email='" + email.getSubject() + "' rowid='" + email.getRowId() + "' DB='" + params.itemInfo.getPath() + "'", e);
        }
    }


    /**
     * Encodes the file path in the same way microsoft does
     * in order to generate the file location of the .dat file in the case
     * @param entry (row) of table
     * @param category number of entry (message, attachment, etc)
     * @param fileTag that describes the type of file (unicode, ascii, etc)
     * @return path of .dat file
     */
    private static String getEntryLocation(AbstractEntry entry, char category, FileTag fileTag) {
        StringBuilder entryPath = new StringBuilder();

        String hexRowId = Integer.toHexString(entry.getRowId());
        while (hexRowId.length() < 8) hexRowId = "0" + hexRowId;

        entryPath.append("/" + category);
        entryPath.append("/" + "abcdefghijklmnopqrstuvwxyz".charAt(
            Integer.parseInt("" + hexRowId.charAt(hexRowId.length()-1), 16)) + "/");
        entryPath.append(hexRowId.charAt(hexRowId.length()-2));
        entryPath.append(hexRowId.substring(1, 6) + "0");
        entryPath.append(hexRowId.charAt(hexRowId.length()-1));
        entryPath.append("0000000");
        entryPath.append(category);
        entryPath.append(fileTag.toString());
        entryPath.append(".dat");

        return entryPath.toString();
    }


    /**
     * Tries to find file from path and size, if doesn't succeed tries without the size parameters
     * @param path
     * @param size
     * @return ImmutablePair<IItemReader, String> of (Item, QueryUsed)
     */
    public ImmutablePair<IItemReader, String> searchItemInCase(String path, long size, Parameters params) {
        if (params.searcher == null) {
            return new ImmutablePair<>(null, null);
        }

        List<IItemReader> items = null;
        String pathQuery = BasicProps.PATH + ":\"" + params.searcher.escapeQuery(path) + "\"";
        String pathParentQuery = pathQuery + " && " + BasicProps.PARENTIDs + ":" + params.grandParentId;
        String pathParentSizeQuery = pathParentQuery + " && " + BasicProps.LENGTH + ":" + size;
        if (size > 0) {
            items = params.searcher.search(pathParentSizeQuery);
        }

        if (items == null || items.isEmpty()) {
            // search without size restriction
            items = params.searcher.search(pathParentQuery);

            if (items == null || items.isEmpty())
                // return the more restrictive query to avoid false positives
                return new ImmutablePair<>(null, pathParentSizeQuery);
        }
        
        IItemReader item = null;
        if (items.size() == 1) {
            item = items.get(0);
        } else {
            // needs to check path, searching for "a/b/file" can return "a/b/file-slack" or
            // "a/b/file>>subitem"
            for (IItemReader ir : items) {
                if (ir.getPath().endsWith(path)) {
                    item = ir;
                    break;
                }
            }
            if (item == null) {
                // return the more restrictive query to avoid false positives
                return new ImmutablePair<>(null, pathParentSizeQuery);
            }
        }

        // return query based on item hash, very specific and execution independent
        // also add path to avoid including unrelated items with same hash in report
        String hashQuery = pathQuery + " && " + BasicProps.HASH + ":" + item.getHash();

        return new ImmutablePair<>(item, hashQuery);
    }


    /**
     * Handle cid images, changing the src value to the attachment base64 image
     * 
     * @param body
     *            email body to replace inline images
     * @param attachments
     *            list of email attachments
     * 
     * @return new body that handles cid images with associated attachments
     */
    private String handleInlineImages(String body, ArrayList<AttachmentEntry> attachments, MessageEntry email, Parameters params) {
        if (body != null && body.contains("cid:")) {
            for (AttachmentEntry attachment : attachments) {
                if (attachment.getAttachCID() != null && attachment.getCaseItem() != null) {
                    String attachCid = attachment.getAttachCID().replaceAll("^<|>$", "");
                    // always convert to a jpeg with limited resolution
                    try {
                        BufferedImage img = ImageUtil.getSubSampledImage(attachment.getCaseItem(), 1024);
                        if (img != null) {
                            img = ImageUtil.getOpaqueImage(img);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(img, "jpg", baos);
                            String base64Img = Base64.getEncoder().encodeToString(baos.toByteArray());
                            body = body.replace("cid:" + attachCid, "data:image/jpeg;base64," + base64Img);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Exception inlining attachment: name='" + attachment.getFileName() + "' email='" + email.getSubject() + "' rowid='" + email.getRowId() + "' DB='" + params.itemInfo.getPath() + "'", e);
                    }
                }
            }
        }
        return body;
    }


    private void closeTablePointer(PointerByReference tablePointer, Parameters params) {
        PointerByReference errorPointer = new PointerByReference();
        int result = esedbLibrary.libesedb_table_free(tablePointer, errorPointer);
        if (result < 0)
            EsedbManager.printError("Table Free", result, params.itemInfo.getPath(), errorPointer);
    }

    private void closeFilePointer(PointerByReference filePointerReference, Parameters params) {
        PointerByReference errorPointer = new PointerByReference();
        int result = esedbLibrary.libesedb_file_close(filePointerReference.getValue(), errorPointer);
        if (result < 0)
            EsedbManager.printError("File Close", result, params.itemInfo.getPath(), errorPointer);

        result = esedbLibrary.libesedb_file_free(filePointerReference, errorPointer);
        if (result < 0)
            EsedbManager.printError("File Free", result, params.itemInfo.getPath(), errorPointer);
    }

}
