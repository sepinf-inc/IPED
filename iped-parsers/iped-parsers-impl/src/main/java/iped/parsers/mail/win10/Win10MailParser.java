package iped.parsers.mail.win10;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
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
import iped.parsers.mail.win10.entries.AppointmentEntry;
import iped.parsers.mail.win10.entries.AttachmentEntry;
import iped.parsers.mail.win10.entries.FolderEntry;
import iped.parsers.mail.win10.entries.MessageEntry;
import iped.parsers.mail.win10.entries.RecipientEntry;
import iped.parsers.mail.win10.tables.AbstractTable;
import iped.parsers.mail.win10.tables.AppointmentTable;
import iped.parsers.mail.win10.tables.AttachmentTable;
import iped.parsers.mail.win10.tables.FolderTable;
import iped.parsers.mail.win10.tables.MessageTable;
import iped.parsers.mail.win10.tables.RecipientTable;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.Util;
import iped.parsers.util.EsedbManager;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.Messages;
import iped.parsers.util.MetadataUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;
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
 * |x| extract properties from store.vol into lists of new objects (representing the important tables)
 * |x| search for the matching .dat files and add more properties to the objects
 * |x| extract each instance as subitems in their respective categories
 * |x| create folder hierarchy
 * |x| merge duplicate folders
 * */
 /**
   * Parses Windows Mail App store.vol edb file. Extracts folders, emails, attachments, and appointments from the database.
   * Searches for additional information (e.g. attachment files and body of the emails) in the case.
   * Generates a subitem in the case for each table entry.
   * 
   * @author Felipe Farias da Costa
 */
public class Win10MailParser extends AbstractParser {

    public static final MediaType WIN10_MAIL_DB = MediaType.application("x-win10-mail-db");
    public static final MediaType WIN10_MAIL_MSG = MediaType.parse("message/x-win10-mail-msg");
    public static final MediaType WIN10_MAIL_APPT = MediaType.parse("message/x-win10-mail-appt");
    public static final MediaType WIN10_MAIL_ATTACH = MediaType.parse("message/x-win10-mail-attach");
    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(WIN10_MAIL_DB);

    private static final char MESSAGE_CATEGORY = '3';
    private static final char APPOINTMENT_CATEGORY = '5';
    private static final char ATTACH_CATEGORY = '7';
    
    private static final String EMAIL_VIRTUAL_ID_PREFIX = "winAppMail-";
    private static final String FOLDER_VIRTUAL_ID_PREFIX = "winAppFolder-";
    private static final String ATTACH_VIRTUAL_ID_PREFIX = "winAppAttach-";
    private static final String APPT_VIRTUAL_ID_PREFIX = "winAppAppt-";

    private enum FileTag {
        UNICODE("001e"), CONTACT_JPEG_1("00ff"), CONTACT_JPEG_2("00ff"), CONTACT_JPEG_3("01b5"),
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

    private static Logger LOGGER = LoggerFactory.getLogger(Win10MailParser.class);

    private static Object lock = new Object();

    private static EsedbLibrary esedbLibrary;

    private EDBParser genericParser = new EDBParser();

    private EmbeddedDocumentExtractor extractor;
    private XHTMLContentHandler xhtml;

    private ItemInfo itemInfo;

    private static IItemSearcher searcher;
    private SimpleDateFormat df = new SimpleDateFormat(Messages.getString("OutlookPSTParser.DateFormat"));

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
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

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

                for (AbstractTable table : tables) {
                    File tableFile = tmp.createTemporaryFile();

                    try (FileOutputStream tmpTableFile = new FileOutputStream(tableFile)) {
                        ToXMLContentHandler tableHandler = new ToXMLContentHandler(tmpTableFile, "UTF-8");
                        Metadata tableMetadata = new Metadata();
                        tableMetadata.add(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_DB.toString());
                        tableMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Mail Table " + table.getTableName()); // $NON-NLS-1$
                        // tableMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
                        tableMetadata.set(BasicProps.HASCHILD, "true");
                        tableMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                        xhtml = new XHTMLContentHandler(tableHandler, metadata);
                        xhtml.startDocument();

                        if (table instanceof FolderTable) {
                            FolderTable folderTable = (FolderTable) table;

                            String path = "";
                            for (FolderEntry folder : folderTable.getFolders()) {
                                // process folder considering the unique parent folder, since it may be duplicate
                                FolderEntry uniqueParentFolder = FolderTable.uniqueFoldersMap.get(folder.getParentFolderId());
                                int parentFolderId = uniqueParentFolder != null ? uniqueParentFolder.getRowId() : folder.getParentFolderId();
                                processFolder(folder, parentFolderId);

                                ArrayList<MessageEntry> childEmails = MessageTable.getFolderChildMessages(folder.getRowId());
                                if (!childEmails.isEmpty()) {
                                    for (MessageEntry childEmail : childEmails) {
                                        childEmail.setContentHtml(getMessageBody(childEmail));
                                        processEmail(childEmail, folder.getRowId(), path);
                                    }
                                }
                                ArrayList<AppointmentEntry> childAppts = AppointmentTable.getFolderChildAppointments(folder.getRowId());
                                if (!childAppts.isEmpty()) {
                                    for (AppointmentEntry childAppt : childAppts) {
                                        childAppt.setBody(getAppointmentBody(childAppt));
                                        processAppointment(childAppt, folder.getRowId(), path);
                                    }
                                }
                            }
                        }

                        // try (FileInputStream fis = new FileInputStream(tableFile)) {
                        //     extractor.parseEmbedded(fis, handler, tableMetadata, true);
                        // }
                    }
                    closeTablePointer(table.getTablePointer());
                }
                closeFilePointer(filePointerReference);
            }
        } catch (Exception e) {
            LOGGER.error("Exception parsing Win10 Mail app email, using generic parser");
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

                AbstractTable table = null;
                if (tableNameStr.contains("Message")) {
                    table = new MessageTable(itemInfo.getPath(), tableNameStr, tablePointer, errorPointer, numRecords);
                } else if (tableNameStr.contains("Recipient")) {
                    table = new RecipientTable(itemInfo.getPath(), tableNameStr, tablePointer, errorPointer, numRecords);
                } else if (tableNameStr.contains("Attachment")) {
                    table = new AttachmentTable(itemInfo.getPath(), tableNameStr, tablePointer, errorPointer, numRecords);
                } else if (tableNameStr.contains("Folder")) {
                    table = new FolderTable(itemInfo.getPath(), tableNameStr, tablePointer, errorPointer, numRecords);
                } else if (tableNameStr.contains("Appointment")) {
                    table = new AppointmentTable(itemInfo.getPath(), tableNameStr, tablePointer, errorPointer, numRecords);
                }

                if (table != null) {
                    table.populateTable(esedbLibrary);
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
    private void processFolder(FolderEntry folder, long parentId) throws SAXException, IOException {
        Metadata entrydata = new Metadata();
        entrydata.set(TikaCoreProperties.TITLE, folder.getDisplayName());
        entrydata.set(TikaCoreProperties.CREATED, folder.getCreateTime());
        entrydata.set(ExtraProperties.EMBEDDED_FOLDER, "true");
        entrydata.set(ExtraProperties.ITEM_VIRTUAL_ID, FOLDER_VIRTUAL_ID_PREFIX + folder.getRowId());
        if (parentId != -1)
            entrydata.set(ExtraProperties.PARENT_VIRTUAL_ID, FOLDER_VIRTUAL_ID_PREFIX + parentId);
        extractor.parseEmbedded(new EmptyInputStream(), xhtml, entrydata, true);
    }

    
    /** Extract an appointment as a subitem with all information of the event
     * @param appointment to be processed
     * @param parentId used to link to parent folder
     * @param path
     * @throws SAXException
     * @throws IOException
     */
    private void processAppointment(AppointmentEntry appointment, long parentId, String path) throws SAXException, IOException {
        Metadata appointMetadata = new Metadata();

        String body = appointment.getBody();

        appointMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        appointMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, APPT_VIRTUAL_ID_PREFIX + appointment.getRowId());
        appointMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, appointment.getEventName());
        appointMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_APPT.toString());
        appointMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, FOLDER_VIRTUAL_ID_PREFIX + parentId);
        appointMetadata.set(ExtraProperties.MESSAGE_BODY, Util.getContentPreview(body, MediaType.TEXT_HTML.toString()));

        Charset charset = Charset.forName("UTF-8");
        StringBuilder preview = new StringBuilder();

        preview.append("All day: " + appointment.getAllDay() + "<br>");
        preview.append("Event: " + appointment.getEventName() + "<br>");
        preview.append("Location: " + appointment.getLocation() + "<br>");
        preview.append("Organizer: " + appointment.getOrganizer() + "<br>");
        preview.append("Account: " + appointment.getAccount() + "<br>");
        preview.append("Link: " + "<a href=\"" + appointment.getLink() + "\">" + appointment.getLink() + "</a>" + "<br>");
        preview.append("Duration: " + appointment.getDurationMin() + " minutes" + "<br>");
        preview.append("Start Time: " + df.format(appointment.getStartTime()) + "<br>");
        preview.append("Reminder Time: " + appointment.getReminderTimeMin() + " minutes" + "<br>");
        preview.append("Repeat: " + appointment.getRepeat() + "<br>");
        preview.append("Response: " + appointment.getResponse() + "<br>");
        preview.append("Additional People: " + appointment.getAdditionalPeople() + "<br>");

        preview.append(body);
        try (ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset))) {
            if (extractor.shouldParseEmbedded(appointMetadata))
                extractor.parseEmbedded(stream, xhtml, appointMetadata, true);
        }
    }


    /** Search through the case for a .dat file containing the appointment body 
     * @param appointment from which the body is requested
     * @return appointment body
     * @throws IOException
     */
    private String getAppointmentBody(AppointmentEntry appointment) throws IOException {
        FileTag[] messageTags = new FileTag[] { FileTag.UNICODE, FileTag.ASCII_PAIRS };
        IItemReader item = null;
        String apptBody = "";
        String contentPath = "";

        for (FileTag messageTag : messageTags) {
            contentPath = getEntryLocation(appointment, APPOINTMENT_CATEGORY, messageTag);
            Pair<IItemReader, String> itemQueryPair = searchItemInCase(contentPath, 0);
            if (itemQueryPair.getLeft() != null) {
                item = itemQueryPair.getLeft();
                break;
            }
        }

        if (item != null) {
            InputStream is = item.getBufferedInputStream();
            InputStreamReader utf16Reader = new InputStreamReader(is, StandardCharsets.UTF_16BE);
            // convert text from utf-16 to utf-8
            is.mark(0);
            ReaderInputStream utf8IS = new ReaderInputStream(utf16Reader, StandardCharsets.UTF_8);
            apptBody = IOUtils.toString(utf8IS, StandardCharsets.UTF_8);
            // the BOM of the file seems to be reversed, we do this workaroud for now
            if (contentPath.contains(FileTag.UNICODE + ".dat") && !apptBody.toLowerCase().contains("html")) {
                is.reset();
                utf16Reader = new InputStreamReader(is, StandardCharsets.UTF_16LE);
                utf8IS = new ReaderInputStream(utf16Reader, StandardCharsets.UTF_8);
                apptBody = IOUtils.toString(utf8IS, StandardCharsets.UTF_8);
            }

            utf8IS.close();
            utf16Reader.close();
            is.close();
        }
        return apptBody;
    }

    
    /** Search through the case for a .dat file containing the email body, probably in html format
     * @param email that we want to search for the body 
     * @return message body
     * @throws IOException
     */
    private String getMessageBody(MessageEntry email) throws IOException {
        FileTag[] messageTags = new FileTag[] { FileTag.ASCII, FileTag.MESSAGE_UNICODE };
        IItemReader item = null;
        for (FileTag messageTag : messageTags) {
            String contentPath = getEntryLocation(email, MESSAGE_CATEGORY, messageTag);
            Pair<IItemReader, String> itemQueryPair = searchItemInCase(contentPath, email.getMessageSize());
            if (itemQueryPair != null) {
                item = itemQueryPair.getLeft();
                break;
            }
        }

        if (item != null) {
            InputStream is = item.getBufferedInputStream();
            InputStreamReader utf16Reader = new InputStreamReader(is, StandardCharsets.UTF_16LE);

            // convert text from utf-16 to utf-8
            byte[] byteOrderMark = new byte[2];
            is.mark(2);
            if ((is.read(byteOrderMark)) != -1) {
                is.reset();
                if (byteOrderMark[0] == (byte) 0xFE && byteOrderMark[1] == (byte) 0xFF) {
                    utf16Reader = new InputStreamReader(is, StandardCharsets.UTF_16BE);
                }
            }
            ReaderInputStream utf8IS = new ReaderInputStream(utf16Reader, StandardCharsets.UTF_8);

            String messageBody = IOUtils.toString(utf8IS, StandardCharsets.UTF_8);

            utf8IS.close();
            utf16Reader.close();
            is.close();

            return messageBody;
        }
        return "";
    }

    
    /** 
     * Extracts the email with recipients, attachments and html body. Ready to be previewed
     * @param email to be processed
     * @param parentId used to link to parent folder
     * @param path
     */
    private void processEmail(MessageEntry email, long parentId, String path) {
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
                List<RecipientEntry> recipients = email.getRecipients();
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
                preview.append("<b>" + Messages.getString("OutlookPSTParser.Sent") + ":</b> " + df.format(sent) + " (UTC) <br>");
            }

            // Attachments:
            if (email.getNoOfAttachments() > 0) {
                List<String> attachNames = email.getAttachments().stream().map(attach -> attach.getFileName()).collect(Collectors.toList());
                if (!attachNames.isEmpty()) {
                    preview.append("<b>" + Messages.getString("OutlookPSTParser.Attachments") + " (" + attachNames.size()
                            + "):</b><br>");
                    for (String attach : attachNames) {
                        preview.append(SimpleHTMLEncoder.htmlEncode(attach) + "<br>");
                    }
                }
                emailMetadata.set(ExtraProperties.MESSAGE_ATTACHMENT_COUNT, email.getNoOfAttachments());
            }

            // Body:
            preview.append("</div>\n");
            String bodyHtml = email.getContentHtml();
            if (bodyHtml != null && !bodyHtml.trim().isEmpty()) {
                preview.append(bodyHtml);
                emailMetadata.set(ExtraProperties.MESSAGE_BODY,
                        Util.getContentPreview(bodyHtml, MediaType.TEXT_HTML.toString()));
            }
            preview.append("</body>");

            preview.append("</html>");

            try (ByteArrayInputStream stream = new ByteArrayInputStream(preview.toString().getBytes(charset))) {
                if (extractor.shouldParseEmbedded(emailMetadata))
                    extractor.parseEmbedded(stream, xhtml, emailMetadata, true);
            }

        } catch (Exception e) {
            LOGGER.error("Exception extracting email: {}>>{}\t{}", path, email.getSubject(), e.toString());
            // e.printStackTrace();
        }

        String successfulQuery = null;
        for (AttachmentEntry attach : email.getAttachments()) {
            String contentPath = getEntryLocation(attach, ATTACH_CATEGORY, FileTag.ANY);
            Pair<IItemReader, String> itemQueryPair = searchItemInCase(contentPath, attach.getAttachSize());
            if (itemQueryPair != null) {
                successfulQuery = itemQueryPair.getRight();
                emailMetadata.add(ExtraProperties.LINKED_ITEMS, successfulQuery);
                processAttachment(attach, successfulQuery, path);
            }
        }

    }

    /** Extract attachment with the correct file name, linked to the original .dat attachment file
     * @param attachment entry to be processed
     * @param query to link to the original .dat file
     * @param path
     */
    private void processAttachment(AttachmentEntry attachment, String query, String path) {
        String parentId = EMAIL_VIRTUAL_ID_PREFIX + attachment.getMessageId();
        String filename = attachment.getFileName();
        long rowId = attachment.getRowId();
        try {
            Metadata attachMetadata = new Metadata();

            attachMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, attachment.getFileName());
            attachMetadata.add(StandardParser.INDEXER_CONTENT_TYPE, WIN10_MAIL_ATTACH.toString());
            attachMetadata.set(Metadata.CONTENT_TYPE, attachment.getMimeTag());
            attachMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, ATTACH_VIRTUAL_ID_PREFIX + rowId);
            attachMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, parentId);
            attachMetadata.set(ExtraProperties.MESSAGE_IS_ATTACHMENT, Boolean.TRUE.toString());
            attachMetadata.set(ExtraProperties.LINKED_ITEMS, query);

            if (extractor.shouldParseEmbedded(attachMetadata))
                extractor.parseEmbedded(new EmptyInputStream(), xhtml, attachMetadata, true);

        } catch (Exception e) {
            LOGGER.error("Exception extracting attachment {}>>{}\t{}", path, filename, e.toString());
            // e.printStackTrace();
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
        entryPath.append("/" + "abcdefghijklmnopqrstuvwxyz".charAt(Integer.parseInt("" + hexRowId.charAt(hexRowId.length()-1), 16)) + "/");
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
    public static ImmutablePair<IItemReader, String> searchItemInCase(String path, long size) {
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
                return new ImmutablePair<>(null, null);;
        }

        return new ImmutablePair<>(items.get(0), query);
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
