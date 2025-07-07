/*
 * Copyright 2015-2015, Fabio Melo Pfeifer
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.parsers.whatsapp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xerces.impl.io.MalformedByteSequenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.io.SeekableInputStream;
import iped.parsers.plist.detector.PListDetector;
import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLite3Parser;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.PhoneParsingConfig;
import iped.parsers.vcard.VCardParser;
import iped.parsers.whatsapp.LinkDownloader.URLnotFound;
import iped.parsers.whatsapp.Message.MessageType;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;
import iped.utils.SimpleHTMLEncoder;

/**
 * Parser para banco de dados do WhatsApp
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class WhatsAppParser extends SQLite3DBParser {

    private static Logger logger = LoggerFactory.getLogger(WhatsAppParser.class);

    private static final long serialVersionUID = 1L;

    public static final String WHATSAPP = "WhatsApp";

    public static final MediaType WA_USER_XML = MediaType.application("x-whatsapp-user-xml"); //$NON-NLS-1$

    public static final MediaType WA_USER_PLIST = PListDetector.WA_USER_PLIST;

    public static final MediaType WHATSAPP_ACCOUNT = MediaType.application("x-whatsapp-account"); //$NON-NLS-1$

    public static final MediaType MSG_STORE = MediaType.application("x-whatsapp-db"); //$NON-NLS-1$

    public static final MediaType MSG_STORE_2 = MediaType.application("x-whatsapp-db-f"); //$NON-NLS-1$

    public static final MediaType WA_DB = MediaType.application("x-whatsapp-wadb"); //$NON-NLS-1$

    public static final MediaType CHAT_STORAGE = MediaType.application("x-whatsapp-chatstorage"); //$NON-NLS-1$

    public static final MediaType CHAT_STORAGE_2 = MediaType.application("x-whatsapp-chatstorage-f"); //$NON-NLS-1$

    public static final MediaType CONTACTS_V2 = MediaType.application("x-whatsapp-contactsv2"); //$NON-NLS-1$

    public static final MediaType WHATSAPP_CHAT = MediaType.parse("application/x-whatsapp-chat"); //$NON-NLS-1$

    public static final MediaType WHATSAPP_CONTACT = MediaType.parse("contact/x-whatsapp-contact"); //$NON-NLS-1$

    public static final MediaType WHATSAPP_MESSAGE = MediaType.parse("message/x-whatsapp-message"); //$NON-NLS-1$

    public static final MediaType WHATSAPP_ATTACHMENT = MediaType.parse("message/x-whatsapp-attachment"); //$NON-NLS-1$

    public static final MediaType WHATSAPP_CALL = MediaType.parse("call/x-whatsapp-call"); //$NON-NLS-1$

    public static final String SHA256_ENABLED_SYSPROP = "IsSha256Enabled"; //$NON-NLS-1$

    public static final String DOWNLOAD_MEDIA_FILES_PROP = "downloadWhatsAppMediaProp";

    // TODO: Once #2286 is merged, use some property to identify WhatsApp Status chats/messages 
    // private static final String STATUS_PROP = ExtraProperties.COMMUNICATION_PREFIX + "isStatus";

    private static final AtomicBoolean sha256Checked = new AtomicBoolean();

    // workaround to show message type before caption (values are shown in sort
    // order)
    private static final String MESSAGE_TYPE_PREFIX = "! "; //$NON-NLS-1$

    private static final int MESSAGE_SEARCH_BATCH_SIZE = 512;

    // a global set to prevent redownload files;
    private static final Set<String> hashesDownloaded = Collections.synchronizedSet(new HashSet<>());

    private static final List<ContentHandler> handlerToUpdate = new ArrayList<>();

    private static final Pattern MSGSTORE_BKP = Pattern.compile("msgstore-\\d{4}-\\d{2}-\\d{2}"); //$NON-NLS-1$
    private static final String MSGSTORE_CRYPTO = "msgstore.db.crypt"; //$NON-NLS-1$
    private static final String IS_BACKUP_FROM = "isBackupFrom";

    private static final Map<Integer, WhatsAppContext> dbsFound = new ConcurrentHashMap<>();

    private static final int POOL_SIZE = 20;

    private static ExecutorService executor;

    private static final AtomicInteger backupsMerged = new AtomicInteger();

    private static boolean dbsSearchedFor = false;
    private static int dbsSearchedForAndAdded = 0;

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(MSG_STORE, WA_DB, CHAT_STORAGE, CONTACTS_V2,
            WA_USER_PLIST, MSG_STORE_2, CHAT_STORAGE_2);

    private static final Map<String, WAContactsDirectory> contactsDirectoriesMap = new ConcurrentHashMap<>();

    private SQLite3Parser sqliteParser = new SQLite3Parser();

    private boolean extractMessages = true;
    private boolean linkMediasByNameAndApproxSizeFallback = true;
    private boolean mergeBackups = false;
    private int linkMediasByLongPathFallback = 0;
    private int downloadConnectionTimeout = 500;
    private int downloadReadTimeout = 500;
    private boolean recoverDeletedRecords = true;
    private int minChatSplitSize = 6000000;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        if (!sha256Checked.getAndSet(true)) {
            if (!Boolean.valueOf(System.getProperty(SHA256_ENABLED_SYSPROP, "false"))) { //$NON-NLS-1$
                logger.error("SHA-256 is disabled. WhatsAppParser needs it to link attachments to chats!"); //$NON-NLS-1$
            }
        }
        return SUPPORTED_TYPES;
    }

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    @Field
    public void setMergeBackups(boolean mergeBackups) {
        this.mergeBackups = mergeBackups;
    }

    @Field
    public void setLinkMediasByNameAndApproxSizeFallback(boolean linkMediasByNameAndApproxSizeFallback) {
        this.linkMediasByNameAndApproxSizeFallback = linkMediasByNameAndApproxSizeFallback;
    }

    @Field
    public void setLinkMediasByLongPathFallback(int linkMediasByLongPathFallback) {
        this.linkMediasByLongPathFallback = linkMediasByLongPathFallback;
    }

    @Field
    public void setDownloadConnectionTimeout(int downloadConnectionTimeout) {
        this.downloadConnectionTimeout = downloadConnectionTimeout;
    }

    @Field
    public void setDownloadReadTimeout(int downloadReadTimeout) {
        this.downloadReadTimeout = downloadReadTimeout;
    }

    @Field
    public void setRecoverDeletedRecords(boolean recoverDeletedRecords) {
        this.recoverDeletedRecords = recoverDeletedRecords;
    }

    @Field
    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    private boolean isDownloadMediaFilesEnabled() {
        return Boolean.valueOf(System.getProperty(DOWNLOAD_MEDIA_FILES_PROP));
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        IItemReader item = context.get(IItemReader.class);
        if (PhoneParsingConfig.isExternalPhoneParsersOnly() && PhoneParsingConfig.isFromUfdrDatasourceReader(item)) {
            return;
        }

        String mimetype = metadata.get(StandardParser.INDEXER_CONTENT_TYPE);
        if (mimetype == null) {
            mimetype = metadata.get(Metadata.CONTENT_TYPE);
        }
        try (TemporaryResources tmp = new TemporaryResources()) {
            stream = TikaInputStream.get(stream, tmp);

            if (mimetype.equals(WA_USER_PLIST.toString())) {
                parseWhatsAppAccount(stream, context, handler, false);
            } else if (mimetype.equals(MSG_STORE.toString())) {
                if (mergeBackups || isDownloadMediaFilesEnabled())
                    parseAndCheckIfIsMainDb(stream, handler, metadata, context, new ExtractorAndroidFactory());
                else
                    parseWhatsAppMessages(stream, handler, metadata, context, new ExtractorAndroidFactory());
            } else if (mimetype.equals(WA_DB.toString())) {
                parseWhatsAppContacts(stream, handler, metadata, context, new ExtractorAndroidFactory());
            } else if (mimetype.equals(CHAT_STORAGE.toString())) {
                if (isDownloadMediaFilesEnabled()) {
                    parseAndCheckIfIsMainDb(stream, handler, metadata, context, new ExtractorIOSFactory());
                } else {
                    parseWhatsAppMessages(stream, handler, metadata, context, new ExtractorIOSFactory());
                }
            } else if (mimetype.equals(CONTACTS_V2.toString())) {
                parseWhatsAppContacts(stream, handler, metadata, context, new ExtractorIOSFactory());
            } else if (mimetype.equals(MSG_STORE_2.toString())) {
                mergeParsedDBsAndOutputResults(stream, handler, metadata, context, new ExtractorAndroidFactory());
            } else if (mimetype.equals(CHAT_STORAGE_2.toString())) {
                parseWhatsAppMessages(stream, handler, metadata, context, new ExtractorIOSFactory());
            }

        } catch (Exception e) {
            // log all whatsapp exceptions
            if (e.getCause() != null && (e.getCause() instanceof MalformedByteSequenceException)) {
                logger.warn("Possibly corrupted file: {} > {}", item, e.getMessage());
            } else {
                logger.error("Error parsing WhatsApp: " + item, e);
            }

            throw e;
        }
    }

    private void createReport(List<Chat> chatList, IItemSearcher searcher, WAContactsDirectory contacts,
            ContentHandler handler, EmbeddedDocumentExtractor extractor, WAAccount account, File dbPath,
            ParseContext context) throws Exception {

        // Expand broadcast chat, from a single chat to one per contact
        expandBroadcastChat(chatList, contacts);        

        int chatVirtualId = 0;
        HashMap<String, String> cache = new HashMap<>();
        for (Chat c : chatList) {
            // sort messages before generating the report
            Message.sort(c.getMessages());

            getAvatar(searcher, c.getRemote());
            searchMediaFilesForMessagesInBatches(c.getMessages(), searcher, handler, extractor, dbPath, context, null);
            int frag = 0;
            int firstMsg = 0;
            ReportGenerator reportGenerator = new ReportGenerator();
            reportGenerator.setMinChatSplitSize(this.minChatSplitSize);
            StringBuilder histFrag = new StringBuilder();
            int histFragCount = 0;
            byte[] bytes = reportGenerator.generateNextChatHtml(c, contacts, account, histFragCount, histFrag);
            while (bytes != null) {
                histFragCount++;
                Metadata chatMetadata = new Metadata();
                int nextMsg = reportGenerator.getNextMsgNum();

                List<Message> msgSubset = c.getMessages().subList(firstMsg, nextMsg);
                storeLinkedHashes(msgSubset, chatMetadata, searcher);

                // condition to avoid duplicate locations being saved in chat & messages
                if (!extractMessages) {
                    storeLocations(msgSubset, chatMetadata);
                }
                firstMsg = nextMsg;
                byte[] nextBytes = reportGenerator.generateNextChatHtml(c, contacts, account, histFragCount, histFrag);

                String chatName = c.getTitle();
                if (frag > 0 || nextBytes != null)
                    chatName += "_" + frag++; //$NON-NLS-1$

                chatMetadata.set("chatId", Long.toString(c.getId()));
                chatMetadata.set(TikaCoreProperties.TITLE, chatName);
                chatMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, WHATSAPP_CHAT.toString());
                chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(chatVirtualId));
                chatMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                if (c.isDeleted()) {
                    chatMetadata.set(ExtraProperties.DELETED, Boolean.TRUE.toString());
                }

                if (extractMessages && msgSubset.size() > 0) {
                    chatMetadata.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
                }
                if (account != null) {
                    String local = formatContact(account, cache);
                    chatMetadata.add(ExtraProperties.PARTICIPANTS, local);
                }
                if (c.isGroupChat()) {
                    for (WAContact member : c.getGroupMembers()) {
                        chatMetadata.add(ExtraProperties.PARTICIPANTS, formatContact(member, cache));
                    }
                    // string formatted as {creator's phone number}-{creation time}@g.us
                    chatMetadata.add(ExtraProperties.GROUP_ID, c.getRemote().getFullId());
                } else {
                    if (c.getRemote() != null) {
                        chatMetadata.add(ExtraProperties.PARTICIPANTS, formatContact(c.getRemote(), cache));
                    }
                }
                if (c.isBroadcast()) {
                    // TODO: chatMetadata.set(STATUS_PROP, Boolean.TRUE.toString());
                }

                // Set created and modified dates based on the first and last messages dates
                if (!msgSubset.isEmpty()) {
                    Message first = msgSubset.get(0);
                    chatMetadata.set(TikaCoreProperties.CREATED, first.getTimeStamp());
                    Message last = msgSubset.get(msgSubset.size() - 1);
                    chatMetadata.set(TikaCoreProperties.MODIFIED, last.getTimeStamp());
                }

                // "isEmpty" = the chat is empty or contains only system messages
                boolean isEmpty = true;
                for (Message m : msgSubset) {
                    if (!m.isSystemMessage()) {
                        isEmpty = false;
                        break;
                    }
                }
                chatMetadata.set(ExtraProperties.COMMUNICATION_PREFIX + "isEmpty", Boolean.valueOf(isEmpty).toString());

                ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                bytes = nextBytes;

                if (extractMessages) {
                    extractMessages(chatName, c, msgSubset, account, contacts, chatVirtualId++, handler, extractor,
                            cache);
                }
            }
            // clear heavy items references (possibly with thumbs loaded)
            c.getMessages().stream().forEach(m -> m.setMediaItem(null));
        }

    }

    private void parseWhatsAppMessages(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, ExtractorFactory extFactory) throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        IItemSearcher searcher = context.get(IItemSearcher.class);
        TemporaryResources tmp = new TemporaryResources();

        if (extractor.shouldParseEmbedded(metadata)) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            try {
                ItemInfo itemInfo = context.get(ItemInfo.class);
                String filePath = null;
                if (itemInfo != null) {
                    filePath = itemInfo.getPath();
                }
                WAContactsDirectory contacts = getWAContactsDirectoryForPath(filePath, searcher, extFactory.getClass());

                String dbPath = ((ItemInfo) context.get(ItemInfo.class)).getPath();
                WAAccount account = getUserAccount(searcher, dbPath, extFactory instanceof ExtractorAndroidFactory);

                File tempDbFile = tis.getFile();
                extFactory.setConnectionParams(tis, metadata, context, this);
                Extractor waExtractor = extFactory.createMessageExtractor(filePath, tempDbFile, contacts, account,
                        recoverDeletedRecords);
                List<Chat> chatList = waExtractor.getChatList();
                createReport(chatList, searcher, contacts, handler, extractor, account, tis.getFile(), context);

            } catch (Exception e) {
                sqliteParser.parse(tis, handler, metadata, context);
                if (e instanceof TikaException)
                    throw (TikaException) e;
                else
                    throw new TikaException("WAExtractorException Exception", e); //$NON-NLS-1$

            } finally {
                tmp.dispose();
            }
        }
    }

    private static void expandBroadcastChat(List<Chat> chatList, WAContactsDirectory contacts) {
        Map<String, Chat> statusChats = new HashMap<String, Chat>();
        for (int i = 0; i < chatList.size(); i++) {
            Chat c = chatList.get(i);
            if (c.getRemote().getFullId().equals(WAContact.waStatusBroadcast)) {
                chatList.remove(i--);
                List<Message> msgs = new ArrayList<Message>(c.getMessages());
                for (Message m : msgs) {
                    String remote = m.getRemoteResource();
                    Chat newChat = statusChats.get(remote);
                    if (newChat == null) {
                        WAContact contact = contacts.getContact(remote);
                        newChat = new Chat(contact);
                        newChat.setBroadcast(true);
                        newChat.setDeleted(c.isDeleted());
                        newChat.setId(c.getId() + 1_000_000_000L);
                        statusChats.put(remote, newChat);
                        chatList.add(newChat);
                    }
                    newChat.add(m);
                }
            }
        }
    }

    private void parseDB(WhatsAppContext wcontext, Metadata metadata, ParseContext context, ExtractorFactory extFactory)
            throws IOException, SAXException, TikaException {
        if (wcontext.getItem().getLength() == 0) {
            wcontext.setParsingError(true);
            throw new TikaException("Empty database");
        }
        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            WAContactsDirectory contacts = getWAContactsDirectoryForPath(wcontext.getItem().getPath(), searcher,
                    extFactory.getClass());

            WAAccount account = getUserAccount(searcher, wcontext.getItem().getPath(),
                    extFactory instanceof ExtractorAndroidFactory);

            wcontext.setChalist(extractChatList(wcontext, extFactory, metadata, context, contacts, account));

        } catch (Exception e) {
            wcontext.setParsingError(true);
            if (e instanceof TikaException)
                throw (TikaException) e;
            else
                throw new TikaException("WAExtractorException Exception", e); //$NON-NLS-1$
        }

    }

    // update all handlers as it could only download files from one handler
    private static void updateHandlers() throws SAXException {
        synchronized (handlerToUpdate) {
            for (ContentHandler handler : handlerToUpdate) {
                if (handler != null) {
                    handler.characters(" ".toCharArray(), 0, 1);
                }
            }
        }
    }

    private static final void waitDownloads(List<Future<?>> futures) throws SAXException {
        try {
            for (Future<?> f : futures) {
                f.get();
                updateHandlers();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

    }

    private ExecutorService getExecutor() {
        if (executor == null) {
            synchronized (this.getClass()) {
                if (executor == null) {
                    executor = Executors.newFixedThreadPool(POOL_SIZE);
                }
            }
        }
        return executor;
    }

    public static final void clearStaticResources() {
        try {
            Message.closeStaticResources();
        } catch (IOException e) {
            logger.warn("Fail to clear resources from WhatsAppParser", e);
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    private void parseAndCheckIfIsMainDb(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, ExtractorFactory extFactory) throws IOException, SAXException, TikaException {

        WhatsAppContext wcontext = new WhatsAppContext(false, context.get(IItemReader.class));
        try {
            parseDB(wcontext, metadata, context, extFactory);
        } catch (Exception e) {
            checkIfIsMainDBAndStore(wcontext);
            wcontext.setParsingError(true);
            throw e;
        }
        if (isDownloadMediaFilesEnabled()) {
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));
            IItemSearcher searcher = context.get(IItemSearcher.class);
            ArrayList<Future<?>> futures = new ArrayList<>();
            AtomicInteger downloadedFiles = new AtomicInteger(0);
            for (Chat c : wcontext.getChalist()) {
                futures.addAll(searchMediaFilesForMessagesInBatches(c.getMessages(), searcher, handler, extractor,
                        wcontext.getItem().getTempFile(), context, downloadedFiles));
            }
            if (futures.size() > 0) {
                handler.startDocument();
                synchronized (handlerToUpdate) {
                    handlerToUpdate.add(handler);
                }
                waitDownloads(futures);

                synchronized (handlerToUpdate) {
                    handlerToUpdate.remove(handler);
                }
                handler.endDocument();
            }
            if (downloadedFiles.get() > 0) {
                logger.info("Downloaded {} files from {}", downloadedFiles.get(), wcontext.getItem().getName());
            }
        }

        checkIfIsMainDBAndStore(wcontext);

        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, extFactory.getType2().toString());
    }

    private static boolean checkIfIsMainDBAndStore(WhatsAppContext wcontext) {
        String type = wcontext.getItem().getMediaType().toString();
        // this is not used for IOS
        if (type.equals(CHAT_STORAGE.toString()) || type.equals(CHAT_STORAGE_2.toString())) {
            return false;
        }
        IItemReader item = wcontext.getItem();
        if (!MSGSTORE_BKP.matcher(item.getName()).find() && !item.getPath().contains(MSGSTORE_CRYPTO)
                && wcontext.getChalist() != null) {
            wcontext.setMainDB(true);
            wcontext.setBackup(false);
        }
        return dbsFound.putIfAbsent(item.getId(), wcontext) == null;
    }

    private List<Chat> extractChatList(WhatsAppContext wcontext, ExtractorFactory extFactory, Metadata metadata,
            ParseContext context, WAContactsDirectory contacts, WAAccount account)
            throws WAExtractorException, IOException, SQLException {
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(wcontext.getItem().getSeekableInputStream(), tmp);
            File tempFile = tis.getFile();

            String filePath = null;
            filePath = wcontext.getItem().getPath();

            extFactory.setConnectionParams(tis, metadata, context, this);
            Extractor waExtractor = extFactory.createMessageExtractor(filePath, tempFile, contacts, account,
                    recoverDeletedRecords);
            return waExtractor.getChatList();
        }
    }

    private static synchronized void findOtherDBS(IItemSearcher searcher) {
        if (dbsSearchedFor) {
            return;
        }
        String query = "(" + BasicProps.CONTENTTYPE + ":\"" + MSG_STORE + "\" OR " + BasicProps.CONTENTTYPE + ":\"" //$NON-NLS-1$ //$NON-NLS-2$
                + MSG_STORE_2 + "\") AND NOT " + BasicProps.LENGTH + ":0";
        List<IItemReader> result = iped.parsers.util.Util.getItems(query, searcher);
        for (IItemReader it : result) {
            WhatsAppContext wcontext = new WhatsAppContext(false, it);
            if (checkIfIsMainDBAndStore(wcontext)) {
                dbsSearchedForAndAdded++;
            }
        }
        dbsSearchedFor = true;
    }

    private void addBackupMessage(WhatsAppContext item, IItemReader main, XHTMLContentHandler xhtml)
            throws SAXException {
        IItem i = (IItem) item.getItem();
        i.getMetadata().set(IS_BACKUP_FROM, main.getExtraAttribute(ExtraProperties.GLOBAL_ID).toString());
        xhtml.startDocument();
        xhtml.characters("Backup from " + main.getPath());
        xhtml.endDocument();
    }

    private void mergeParsedDBsAndOutputResults(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, ExtractorFactory extFactory) throws IOException, SAXException, TikaException {

        IItemReader DB = context.get(IItemReader.class);
        IItemSearcher searcher = context.get(IItemSearcher.class);

        // this call is needed when processing was stopped and is being resumed, so DBs
        // list and parsing results won't be on memory yet
        findOtherDBS(searcher);

        WhatsAppContext wcontext = dbsFound.get(DB.getId());
        if (wcontext != null && wcontext.getChalist() == null && !wcontext.getParsingError()) {
            // if not parsed yet, parse the DB here
            // If a parsing occurred do not try to parse again
            synchronized (wcontext) {
                if (wcontext.getChalist() == null) {
                    try {
                        parseDB(wcontext, metadata, context, extFactory);
                    } catch (Exception e) {
                        wcontext.setParsingError(true);
                        throw e;
                    }
                }
            }
        }

        // parse DBs found above
        for (WhatsAppContext other : dbsFound.values().toArray(new WhatsAppContext[0])) {
            if (other == wcontext || other.getParsingError())
                continue;
            synchronized (other) {
                if (other.getChalist() == null && !other.getParsingError()) {
                    // if not parsed yet, parse the DB here.
                    // If a parsing occurred do not try to parse again
                    try {
                        parseDB(other, metadata, context, extFactory);
                    } catch (Exception e) {
                        other.setParsingError(true);
                        other.setMainDB(false);
                        other.setBackup(false);
                        logger.warn("Could not parse DB {} ({} bytes): {}", other.getItem().getPath(),
                                other.getItem().getLength(), e.toString());
                        logger.debug("", e);
                    }
                }

            }
        }

        List<WhatsAppContext> dbsFoundList = new ArrayList<>(dbsFound.values());

        // this is used to sort backups by decreasing modifiedDate (from name) order, so
        // the most recent backup will be used when merging if a missing chat/message is
        // found, giving a better idea about when the user deleted the chat/message
        Collections.sort(dbsFoundList, new Comparator<WhatsAppContext>() {
            @Override
            public int compare(WhatsAppContext o1, WhatsAppContext o2) {
                return -o1.getItem().getName().compareTo(o2.getItem().getName());
            }
        });

        if (wcontext == null) {
            // MakePreviewTask enters here for main dbs and backups without main db
            return;
        }

        try (TemporaryResources tmp = new TemporaryResources()) {

            WAContactsDirectory contacts = getWAContactsDirectoryForPath(DB.getPath(), searcher, extFactory.getClass());

            boolean isAndroid = extFactory instanceof ExtractorAndroidFactory;
            WAAccount account = getUserAccount(searcher, DB.getPath(), isAndroid);

            File tmpDB = TikaInputStream.get(stream, tmp).getFile();

            stream.skip(wcontext.getItem().getLength());

            List<Chat> dbChatList = wcontext.getChalist();
            // if merge is not enable create a report for every db
            if (!mergeBackups) {
                EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                        new ParsingEmbeddedDocumentExtractor(context));
                createReport(dbChatList, searcher, contacts, handler, extractor, account, tmpDB, context);

                dbsFound.remove(DB.getId());
                return;
            }

            if (wcontext.isBackup() && wcontext.getMainDBItem() != null) {
                // MakePreviewTask enters here later for backups, output preview message
                addBackupMessage(wcontext, wcontext.getMainDBItem(), new XHTMLContentHandler(handler, metadata));
                return;
            }

            for (WhatsAppContext db : dbsFoundList) {
                // skip current DB
                if (db.getItem().getId() == wcontext.getItem().getId()) {
                    continue;
                }
                WhatsAppContext mainDb;
                WhatsAppContext other;

                if (wcontext.isMainDB() && !db.isMainDB()) {
                    mainDb = wcontext;
                    other = db;
                } else if (!wcontext.isMainDB() && db.isMainDB()) {
                    mainDb = db;
                    other = wcontext;
                } else if (wcontext.getItem().getHash().equals(db.getItem().getHash())) {

                    if (wcontext.getItem().getId() > db.getItem().getId()) {
                        wcontext.setBackup(true);
                        wcontext.setMainDBItem(db.getItem());
                        addBackupMessage(wcontext, wcontext.getMainDBItem(),
                                new XHTMLContentHandler(handler, metadata));
                        backupsMerged.incrementAndGet();
                        return;
                    }
                    continue;

                } else {
                    // skip if both are mainDB or if both are backups
                    continue;
                }

                // new instance to avoid threading issues
                List<Chat> mainDBChatList = new ArrayList<Chat>();
                if (mainDb.getChalist() != null) {
                    mainDBChatList.addAll(mainDb.getChalist());
                }
                ChatMerge cm = new ChatMerge(mainDBChatList, other.getItem().getName());

                if (cm.isBackup(other.getChalist())) {
                    if (wcontext == mainDb) {
                        // merge backup in the main chat list
                        int numMsgRecovered = cm.mergeChatList(other.getChalist());
                        logger.info("Recovered {} messages from {}", numMsgRecovered, other.getItem().getPath()); //$NON-NLS-1$
                        mainDb.setChalist(mainDBChatList);
                        dbChatList = mainDBChatList;
                    }
                    if (wcontext == other) {
                        other.setBackup(true);
                        other.setMainDBItem(mainDb.getItem());
                        // output from which main DB is this backup from
                        addBackupMessage(wcontext, wcontext.getMainDBItem(),
                                new XHTMLContentHandler(handler, metadata));
                        backupsMerged.incrementAndGet();
                        return;
                    }
                }
            }
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));
            if (!extractor.shouldParseEmbedded(metadata)) {
                return;
            }

            if (!wcontext.isMainDB() && !wcontext.isBackup()) {
                // if this is a "backup" but its main db was not found
                logger.info("Creating separate report for {}", DB.getPath()); //$NON-NLS-1$
            }

            // create report for main dbs and backups which main db was not found
            createReport(dbChatList, searcher, contacts, handler, extractor, account, tmpDB, context);

            // and free memory used by main dbs and backups which main db was not found
            dbsFound.remove(DB.getId());

        } catch (Exception e) {
            if (e instanceof TikaException)
                throw (TikaException) e;
            else
                throw new TikaException("WAExtractorException Exception", e); //$NON-NLS-1$
        } finally {
            if (dbsFound.size() == backupsMerged.get() + dbsSearchedForAndAdded) {
                // just merged backups left in map, clear all remaining heavy data
                logger.info("Clearing remaining whatsapp decoded data from cache.");
                dbsFound.values().stream().filter(wacontext -> wacontext.getChalist() != null)
                        .forEach(wacontext -> wacontext.getChalist().clear());
            }
        }

    }

    private void parseWhatsAppAccount(InputStream is, ParseContext context, ContentHandler handler, boolean isAndroid)
            throws SAXException, IOException, TikaException {
        WAAccount account = null;
        if (isAndroid) {
            account = WAAccount.getFromAndroidXml(is);
        } else {
            account = WAAccount.getFromIOSPlist(is);
        }

        if (account == null) {
            // This may happen if the file does not contain WA account info, e.g. parsing a
            // "com.whatsapp_preferences.xml" but the account data is in
            // "com.whatsapp_preferences_light.xml" (or vice-versa).
            return;
        }

        parseEmbeddedWhatsAppAccount(account, context, handler);
    }

    private void parseEmbeddedWhatsAppAccount(WAAccount account, ParseContext context, ContentHandler handler)
            throws SAXException, IOException, TikaException {

        Metadata meta = new Metadata();
        meta.set(StandardParser.INDEXER_CONTENT_TYPE, WHATSAPP_ACCOUNT.toString());
        meta.set(TikaCoreProperties.TITLE, account.getTitle());
        meta.set(ExtraProperties.USER_NAME, account.getName());
        meta.set(ExtraProperties.USER_PHONE, getInternationalPhone(account.getId()));
        meta.set(ExtraProperties.USER_ACCOUNT, account.getFullId());
        meta.set(ExtraProperties.USER_ACCOUNT_TYPE, WHATSAPP);
        meta.set(ExtraProperties.USER_NOTES, account.getStatus());
        meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

        if (account.getAvatar() != null) {
            meta.set(ExtraProperties.THUMBNAIL_BASE64, Base64.getEncoder().encodeToString(account.getAvatar()));
        }

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        ReportGenerator reportGenerator = new ReportGenerator();

        if (extractor.shouldParseEmbedded(meta)) {
            ByteArrayInputStream bais = new ByteArrayInputStream(reportGenerator.generateAccountHtml(account));
            extractor.parseEmbedded(bais, handler, meta, false);
        }
    }

    private void fillAccountWithContactData(WAAccount account, IItemSearcher searcher, String dbPath) {
        if (account == null || account.getWaName() != null) {
            return;
        }
        try {
            WAContactsDirectory contacts = getWAContactsDirectoryForPath(dbPath, searcher,
                    ExtractorAndroidFactory.class);
            WAContact c = contacts.getContact(account.getFullId());
            if (c != null) {
                account.setWaName(c.getDisplayName());
                account.setStatus(c.getStatus());
            }
        } catch (Exception e) {
            logger.warn("Error filling WhatsApp account with contact data: " + account.getFullId(), e);
        }
    }

    private void fillAccountAvatar(WAAccount account, IItemSearcher searcher, String dbPath) {
        if (searcher == null || account == null || account.getAvatar() != null || account.getAvatarPath() != null || dbPath == null) {
            return;
        }

        // Goes up 2 levels (if possible)
        String filePath = dbPath;
        for (int i = 0; i < 2; i++) {
            int p = filePath.lastIndexOf('/');
            if (p > 0) {
                filePath = filePath.substring(0, p);
            }
        }
        // Append sub folder and expected filename
        filePath += "/files/me.jpg";
        logger.debug("AccountAvatar path: " + filePath);

        String query = BasicProps.PATH + ":\"" + searcher.escapeQuery(filePath) + "\""; //$NON-NLS-1$ //$NON-NLS-2$
        List<IItemReader> result = searcher.search(query);
        if (!result.isEmpty()) {
            try (InputStream is = result.get(0).getBufferedInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copy(is, bos);
                account.setAvatar(bos.toByteArray());
            } catch (IOException e) {
                logger.warn("Error getting WhatsApp account avatar: " + account.getFullId(), e);
            }
        }
    }

    private static final Pattern numbers = Pattern.compile("[0-9]+"); //$NON-NLS-1$

    private String getInternationalPhone(String id) {
        Matcher m = numbers.matcher(id);
        if (m.matches())
            return "+" + id; //$NON-NLS-1$
        else
            return null;
    }

    private WAAccount getUserAccount(IItemSearcher searcher, String dbPath, boolean isAndroid) {
        WAAccount account = new WAAccount("unknownAccount");
        account.setUnknown(true);
        if (searcher != null) {
            StringBuilder query = new StringBuilder();
            // Array with possible WA account file names, order by priority
            String[] names;
            if (isAndroid) {
                names = new String[] { "com.whatsapp.w4b_preferences_light.xml", "com.whatsapp_preferences_light.xml",
                        "com.whatsapp.w4b_preferences.xml", "com.whatsapp_preferences.xml",
                        "registration.RegisterPhone.xml", "startup_prefs.xml" };
                query.append(BasicProps.NAME).append(":(");
                for (int i = 0; i < names.length; i++) {
                    query.append(" \"").append(names[i]).append('"');
                }
                query.append(')');
            } else {
                names = new String[] { null };
                query.append(BasicProps.CONTENTTYPE).append(":\"");
                query.append(WA_USER_PLIST.toString()).append("\"");
            }

            List<IItemReader> result = searcher.search(query.toString());
            List<IItemReader> items = getBestItems(result, dbPath, names);
            for (IItemReader item : items) {
                try (InputStream is = item.getBufferedInputStream()) {
                    WAAccount a = isAndroid ? WAAccount.getFromAndroidXml(is) : WAAccount.getFromIOSPlist(is);
                    if (a != null) {
                        if (account.isUnknown() && !a.getId().isEmpty()) {
                            account.updateId(a.getFullId());
                            account.setUnknown(false);
                        }
                        if (account.getWaName() == null && a.getWaName() != null) {
                            account.setWaName(a.getWaName());
                        }
                        if (account.getStatus() == null && a.getStatus() != null) {
                            account.setStatus(a.getStatus());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error trying to get user account from {}: {}", item, e);
                }
            }
        }

        if (isAndroid) {
            fillAccountAvatar(account, searcher, dbPath);
            fillAccountWithContactData(account, searcher, dbPath);
        }

        return account;
    }

    /**
     * Return a list of possible matches ordered by "priority" (first longer path
     * matches, then the index of names array).
     */
    private List<IItemReader> getBestItems(List<IItemReader> result, String path, String[] names) {
        boolean isWABusiness = isWABusiness(path);
        List<IItemReader> bests = new ArrayList<IItemReader>();
        while (!result.isEmpty()) {
            int pos = path.lastIndexOf('/');
            if (pos < 0)
                break;
            path = path.substring(0, pos);
            for (int i = 0; i < names.length; i++) {
                for (int j = 0; j < result.size(); j++) {
                    IItemReader item = result.get(j);
                    // Check if WA type (business or not), path and name match
                    if (isWABusiness(item.getPath()) == isWABusiness && item.getPath().startsWith(path)
                            && (names[i] == null || item.getName().equalsIgnoreCase(names[i]))) {
                        bests.add(item);
                        result.remove(j--);
                    }
                }
            }
        }
        return bests;
    }

    private static boolean isWABusiness(String path) {
        return path.contains(".w4b") || path.contains("WhatsApp Business");
    }

    private String formatContact(WAContact contact, Map<String, String> cache) {
        String result = cache.get(contact.getId());
        if (result == null) {
            if (contact.getName() == null || contact.getName().isBlank()) {
                result = contact.getFullId();
            } else if (contact.getName().strip().equals(contact.getId())) {
                result = contact.getFullId();
            } else if (contact.getFullId().isBlank()) {
                result = contact.getName().strip();
            } else {
                result = contact.getName().strip() + " (" + contact.getFullId().strip() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
            }
            cache.put(contact.getId(), result);
        }
        return result;
    }

    private void fillGroupRecipients(Metadata meta, Chat c) {
        String to = c.isChannelChat() ? "Channel " : "Group ";
        if (c.getSubject() != null) {
            to += c.getSubject().strip();
        }
        to += " (id:" + c.getId() + ")";
        meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, to);
        meta.set(ExtraProperties.IS_GROUP_MESSAGE, "true");
    }

    private void extractMessages(String chatName, Chat c, List<Message> messages, WAAccount account,
            WAContactsDirectory contacts, int parentVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor, Map<String, String> cache) throws SAXException, IOException {
        int msgCount = 0;
        for (iped.parsers.whatsapp.Message m : messages) {

            Metadata meta = new Metadata();
            meta.set(TikaCoreProperties.TITLE, chatName + "_message_" + msgCount++); //$NON-NLS-1$
            meta.set(StandardParser.INDEXER_CONTENT_TYPE, WHATSAPP_MESSAGE.toString());
            meta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentVirtualId));
            meta.set(ExtraProperties.PARENT_VIEW_POSITION, String.valueOf(m.getUniqueId()));
            meta.set(ExtraProperties.USER_ACCOUNT_TYPE, WHATSAPP);
            meta.set(ExtraProperties.MESSAGE_DATE, m.getTimeStamp());
            meta.set(TikaCoreProperties.CREATED, m.getTimeStamp());
            meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
            if (m.isDeleted()) {
                meta.set(ExtraProperties.DELETED, Boolean.toString(true));
            }

            if (!m.isSystemMessage()) {
                String local = formatContact(account, cache);
                String remote = m.getRemoteResource();
                if (remote != null) {
                    WAContact contact = contacts.getContact(remote);
                    remote = contact == null ? remote : formatContact(contact, cache);
                }
                if (m.isFromMe()) {
                    meta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, local);
                    if (c.isGroupOrChannelChat()) {
                        fillGroupRecipients(meta, c);
                    } else {
                        meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, remote);
                    }
                } else {
                    meta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, remote);
                    if (c.isGroupOrChannelChat()) {
                        fillGroupRecipients(meta, c);
                    } else {
                        meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, local);
                    }
                }
                if (c.isBroadcast()) {
                    // TODO: meta.set(STATUS_PROP, Boolean.TRUE.toString());
                }
            }
            meta.set(ExtraProperties.MESSAGE_BODY, m.getData());
            meta.set(ExtraProperties.URL, m.getUrl());

            meta.set("mediaName", m.getMediaName()); //$NON-NLS-1$
            meta.set("mediaMime", m.getMediaMime()); //$NON-NLS-1$
            if (m.getMediaSize() != 0) {
                meta.set("mediaSize", Long.toString(m.getMediaSize()));
            }
            if (m.getMediaQuery() != null && m.getMediaSize() > 2) {
                meta.set(StandardParser.INDEXER_CONTENT_TYPE, WHATSAPP_ATTACHMENT.toString());
                meta.set(ExtraProperties.LINKED_ITEMS, revertEscapeQuery(m.getMediaQuery())); // $NON-NLS-1$
            }
            if (!m.getChildPornSets().isEmpty()) {
                meta.set("hash:status", "pedo"); //$NON-NLS-1$ //$NON-NLS-2$
                for (String set : m.getChildPornSets()) {
                    meta.add("hash:set", set); //$NON-NLS-1$
                }
            }

            // TODO store thumb in metadata?

            if (m.getMessageType() == MessageType.LOCATION_MESSAGE
                    || m.getMessageType() == MessageType.SHARE_LOCATION_MESSAGE) {
                meta.set(ExtraProperties.LOCATIONS, m.getLatitude() + ";" + m.getLongitude()); //$NON-NLS-1$
            }

            if (m.getMessageStatus() != null) {
                meta.set("messageStatus", m.getMessageStatus().toString()); //$NON-NLS-1$
            }

            if (m.isCall()) {
                meta.set(StandardParser.INDEXER_CONTENT_TYPE, WHATSAPP_CALL.toString());
                meta.set("duration", ReportGenerator.formatMMSS(m.getDuration())); //$NON-NLS-1$
            }

            if (meta.get(ExtraProperties.MESSAGE_BODY) == null) {
                meta.set(ExtraProperties.MESSAGE_BODY, MESSAGE_TYPE_PREFIX + m.getMessageType().toString());
            }
            if (m.getMediaCaption() != null) {
                meta.add(ExtraProperties.MESSAGE_BODY, m.getMediaCaption());
            }
            if (m.getVcards() != null && !m.getVcards().isEmpty()) {
                meta.set(StandardParser.INDEXER_CONTENT_TYPE, VCardParser.VCARD_MIME.toString());
                for (String vcard : m.getVcards()) {
                    if (vcard != null) {
                        extractor.parseEmbedded(new ByteArrayInputStream(vcard.getBytes(StandardCharsets.UTF_8)),
                                handler, meta, false);
                    }

                }
            } else {
                meta.set(BasicProps.LENGTH, ""); //$NON-NLS-1$
                extractor.parseEmbedded(new EmptyInputStream(), handler, meta, false);
            }
        }
    }

    private void storeLinkedHashes(List<Message> messages, Metadata metadata, IItemSearcher searcher) {
        for (Message m : messages) {
            if (m.getMediaQuery() != null && m.getMediaSize() > 2) {
                String query = revertEscapeQuery(m.getMediaQuery());
                metadata.add(ExtraProperties.LINKED_ITEMS, query);
                if (m.isFromMe()) {
                    metadata.add(ExtraProperties.SHARED_HASHES, query);
                }
            }
        }
    }

    private String revertEscapeQuery(String query) {
        if (query.startsWith("'") && query.endsWith("'")) {
            query = query.substring(1, query.length() - 1);
        } else if (query.startsWith("&quot;") && query.endsWith("&quot;")) {
            query = SimpleHTMLEncoder.htmlDecode(query).replace("\\\"", "\"");
            query = query.substring(1, query.length() - 1);
        }
        return query;
    }

    private String escapeQuery(String query, boolean isHashQuery) {
        if (isHashQuery) {
            return "'" + query + "'";
        } else {
            return SimpleHTMLEncoder.htmlEncode("\"" + query.replace("\"", "\\\"") + "\"");
        }
    }

    private void storeLocations(List<Message> messages, Metadata metadata) {
        for (Message m : messages) {
            if (m.getMessageType() == MessageType.LOCATION_MESSAGE
                    || m.getMessageType() == MessageType.SHARE_LOCATION_MESSAGE) {
                if (m.getLatitude() != 0.0 && m.getLongitude() != 0.0) {
                    metadata.add(ExtraProperties.LOCATIONS, m.getLatitude() + ";" + m.getLongitude()); //$NON-NLS-1$
                }
            }
        }
    }

    private void getAvatar(IItemSearcher searcher, WAContact contact) {
        if (searcher != null && contact.getAvatar() == null) {
            List<IItemReader> result = searcher
                    .search(BasicProps.NAME + ":\"" + escape(searcher, contact.getFullId()) + ".j\""); //$NON-NLS-1$ //$NON-NLS-2$
            if (result.isEmpty()) {
                if (contact.getAvatarPath() != null) {
                    String avatarFileBase = contact.getAvatarPath();
                    if (avatarFileBase.contains("/")) { //$NON-NLS-1$
                        avatarFileBase = avatarFileBase.substring(avatarFileBase.lastIndexOf('/') + 1); // $NON-NLS-1$
                    }
                    avatarFileBase = escape(searcher, avatarFileBase);
                    // Try file .jpg
                    result = searcher.search(BasicProps.NAME + ":\"" + avatarFileBase + ".jpg\""); //$NON-NLS-1$ //$NON-NLS-2$
                    if (result.isEmpty()) {
                        // Try file .thumb
                        result = searcher.search(BasicProps.NAME + ":\"" + avatarFileBase + ".thumb\""); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
            }
            if (result.isEmpty()) {
                if (contact.getId() != null && !contact.getId().isEmpty()) {
                    Iterable<IItemReader> resultIterable = searcher.searchIterable(
                            BasicProps.NAME + ":(\"" + escape(searcher, contact.getId()) + "\" AND (jpg thumb))"); //$NON-NLS-1$ //$NON-NLS-2$
                    IItemReader avatar = getNewerAvatar(resultIterable, contact.getId());
                    if (avatar != null) {
                        result.add(avatar);
                    }
                }
            }

            if (!result.isEmpty()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];

                try (InputStream is = result.get(0).getBufferedInputStream()) {
                    int len = 0;
                    while ((len = is.read(buf)) != -1)
                        bos.write(buf, 0, len);
                    contact.setAvatar(bos.toByteArray());

                } catch (IOException e) {
                    logger.warn("Error setting avatar for contact {}: {}", contact.getFullId(), e);
                }
            }
        }
    }

    private String escape(IItemSearcher searcher, String string) {
        if (searcher != null)
            return searcher.escapeQuery(string);
        else
            return string;
    }

    private IItemReader getNewerAvatar(Iterable<IItemReader> avatars, String id) {
        // WhatsApp initial release 2009-01-01
        long startTime = 1230768000;
        long endTime = System.currentTimeMillis() / 1000;
        IItemReader newerAvatar = null;
        AvatarComparator comparator = new AvatarComparator();
        for (IItemReader item : avatars) {
            // filter group avatars and unrelated images
            if (item.getName().startsWith(id) && item.getName().split("-").length < 3) { //$NON-NLS-1$
                String str = item.getName().substring(id.length());
                int idx = str.indexOf("."); //$NON-NLS-1$
                if (str.startsWith("-") && idx > 0) { //$NON-NLS-1$
                    String t = str.substring(1, idx); // $NON-NLS-1$
                    try {
                        Long time = Long.valueOf(t);
                        if (time > startTime && time < endTime) {
                            if (newerAvatar == null || comparator.compare(item, newerAvatar) > 0) {
                                newerAvatar = item;
                            }
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                } else if (str.equals(".thumb") || str.equals(".jpg")) { //$NON-NLS-1$ //$NON-NLS-2$
                    if (newerAvatar == null || comparator.compare(item, newerAvatar) > 0) {
                        newerAvatar = item;
                    }
                }
            }
        }
        return newerAvatar;
    }

    // sort newer avatar to be first
    private class AvatarComparator implements Comparator<IItemReader> {
        @Override
        public int compare(IItemReader o1, IItemReader o2) {
            return o2.getName().compareTo(o1.getName());
        }
    }

    private void parseWhatsAppContacts(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, ExtractorFactory extFactory) throws IOException, SAXException, TikaException {

        extFactory.setConnectionParams(stream, metadata, context, this);
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        IItemSearcher searcher = context.get(IItemSearcher.class);
        TemporaryResources tmp = new TemporaryResources();

        if (extractor.shouldParseEmbedded(metadata)) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            File contactDbFile = tis.getFile();
            try {
                WAContactsExtractor waExtractor = extFactory.createContactsExtractor(contactDbFile,
                        recoverDeletedRecords);
                waExtractor.extractContactList();

                ItemInfo itemInfo = context.get(ItemInfo.class);
                String path = null;
                if (itemInfo != null) {
                    path = itemInfo.getPath();
                }
                WAContactsDirectory contacts = getWAContactsDirectoryForPath(path, null, null);
                contacts.putAll(waExtractor.getContactsDirectory());

                String dbPath = ((ItemInfo) context.get(ItemInfo.class)).getPath();
                boolean isAndroid = extFactory instanceof ExtractorAndroidFactory;
                WAAccount account = getUserAccount(searcher, dbPath, isAndroid);

                ReportGenerator reportGenerator = new ReportGenerator();
                for (WAContact c : waExtractor.getContactsDirectory().contacts()) {
                    if (c.getFullId().equals(WAContact.waStatusBroadcast)) {
                        // Skip status@broadcast 
                        continue;
                    }
                    Metadata cMetadata = new Metadata();
                    cMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, WHATSAPP_CONTACT.toString());
                    cMetadata.set(TikaCoreProperties.TITLE, c.getTitle());
                    cMetadata.set(ExtraProperties.USER_NAME, c.getName());
                    cMetadata.set(ExtraProperties.USER_PHONE, getInternationalPhone(c.getId()));
                    cMetadata.set(ExtraProperties.USER_ACCOUNT, c.getFullId());
                    cMetadata.set(ExtraProperties.USER_ACCOUNT_TYPE, WHATSAPP);
                    cMetadata.set(ExtraProperties.CONTACT_OF_ACCOUNT, account.getFullId());
                    cMetadata.set(ExtraProperties.USER_NOTES, c.getStatus());
                    cMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                    if (c.isDeleted()) {
                        cMetadata.set(ExtraProperties.DELETED, Boolean.toString(c.isDeleted()));
                    }

                    getAvatar(searcher, c);
                    if (c.getAvatar() != null) {
                        cMetadata.set(ExtraProperties.THUMBNAIL_BASE64,
                                Base64.getEncoder().encodeToString(c.getAvatar()));
                    }

                    if (extractor.shouldParseEmbedded(cMetadata)) {
                        ByteArrayInputStream chatStream = new ByteArrayInputStream(
                                reportGenerator.generateContactHtml(c));
                        extractor.parseEmbedded(chatStream, handler, cMetadata, false);
                    }
                }

                if (isAndroid) {
                    fillAccountAvatar(account, searcher, dbPath);
                    fillAccountWithContactData(account, searcher, dbPath);
                    parseEmbeddedWhatsAppAccount(account, context, handler);
                }

            } catch (Exception e) {
                sqliteParser.parse(tis, handler, metadata, context);
                throw new TikaException("WAExtractorException Exception", e); //$NON-NLS-1$

            } finally {
                tmp.dispose();
            }
        }
    }

    private WAContactsDirectory getWAContactsDirectoryForPath(String path, IItemSearcher searcher,
            Class<?> extFactoryClass)
            throws IOException, WAExtractorException, InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        if (path == null) {
            path = ""; //$NON-NLS-1$
        } else if (path.contains("/")) { //$NON-NLS-1$
            path = path.substring(0, path.lastIndexOf('/')); // $NON-NLS-1$
        } else if (path.contains("\\")) { //$NON-NLS-1$
            path = path.substring(0, path.lastIndexOf('\\')); // $NON-NLS-1$
        }

        WAContactsDirectory cd = contactsDirectoriesMap.get(path);
        if (cd == null) {
            cd = getContacts(path, searcher, extFactoryClass);
            contactsDirectoriesMap.put(path, cd);
        }
        return cd;
    }

    private WAContactsDirectory getContacts(String path, IItemSearcher searcher, Class<?> extFactoryClass)
            throws IOException, WAExtractorException, InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        if (searcher == null) {
            return new WAContactsDirectory();
        }
        String query = BasicProps.PATH + ":\"" + searcher.escapeQuery(path) + "\""; //$NON-NLS-1$ //$NON-NLS-2$
        query += " && " + BasicProps.CONTENTTYPE + ":(\"" + WA_DB.toString() + "\" || \"" + CONTACTS_V2.toString() //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "\")"; //$NON-NLS-1$
        List<IItemReader> items = searcher.search(query);
        if (items.size() == 0) {
            return new WAContactsDirectory();
        }
        IItemReader item = items.get(0);
        ParseContext context = new ParseContext();
        context.set(IItemSearcher.class, searcher);
        context.set(IItemReader.class, item);
        ExtractorFactory extFactory = (ExtractorFactory) extFactoryClass.getDeclaredConstructor().newInstance();

        try (InputStream is = item.getBufferedInputStream()) {
            extFactory.setConnectionParams(is, null, context, this);
            WAContactsExtractor waExtractor = extFactory.createContactsExtractor(item.getTempFile(),
                    recoverDeletedRecords);
            waExtractor.extractContactList();
            return waExtractor.getContactsDirectory();
        }
    }

    private static abstract class ExtractorFactory {

        InputStream is;
        Metadata metadata;
        ParseContext context;
        WhatsAppParser connFactory;

        abstract Extractor createMessageExtractor(String itemPath, File file, WAContactsDirectory directory,
                WAAccount account, boolean recoverDeletedRecords);

        abstract WAContactsExtractor createContactsExtractor(File file, boolean recoverDeletedRecords);

        private void setConnectionParams(InputStream is, Metadata metadata, ParseContext context,
                WhatsAppParser connFactory) {
            this.is = is;
            this.metadata = metadata;
            this.context = context;
            this.connFactory = connFactory;
        }

        protected Connection getConnection() throws SQLException {

            try {
                return connFactory.getConnection(is, metadata, context);
            } catch (IOException e) {
                throw new SQLException(e);
            }

        }

        public abstract MediaType getType2();

    }

    // must be static and non be private because of newInstance in getContacts()
    // method
    protected static class ExtractorAndroidFactory extends ExtractorFactory {

        private boolean new_db = false;

        @Override
        public Extractor createMessageExtractor(String itemPath, File file, WAContactsDirectory directory,
                WAAccount account, boolean recoverDeletedRecords) {
            try (Connection conn = getConnection()) {
                new_db = !SQLite3DBParser.containsTable("messages", conn);
            } catch (Exception e) {
                new_db = false;
            }
            if (new_db) {
                return new ExtractorAndroidNew(itemPath, file, directory, account) {
                    @Override
                    protected Connection getConnection() throws SQLException {
                        return ExtractorAndroidFactory.this.getConnection();
                    }
                };
            }

            return new ExtractorAndroid(itemPath, file, directory, account, recoverDeletedRecords) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorAndroidFactory.this.getConnection();
                }
            };

        }

        @Override
        public WAContactsExtractor createContactsExtractor(File file, boolean recoverDeletedRecords) {
            return new WAContactsExtractorAndroid(file, new WAContactsDirectory(), recoverDeletedRecords) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorAndroidFactory.this.getConnection();
                }
            };
        }

        @Override
        public MediaType getType2() {
            return MSG_STORE_2;
        }
    }

    // must be static and non be private because of newInstance in getContacts()
    // method
    protected static class ExtractorIOSFactory extends ExtractorFactory {

        @Override
        public Extractor createMessageExtractor(String itemPath, File file, WAContactsDirectory directory,
                WAAccount account, boolean recoverDeletedRecords) {
            return new ExtractorIOS(itemPath, file, directory, account, recoverDeletedRecords) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorIOSFactory.this.getConnection();
                }
            };
        }

        @Override
        public WAContactsExtractor createContactsExtractor(File file, boolean recoverDeletedRecords) {
            return new WAContactsExtractorIOS(file, new WAContactsDirectory(), recoverDeletedRecords) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorIOSFactory.this.getConnection();
                }
            };
        }

        @Override
        public MediaType getType2() {
            return CHAT_STORAGE_2;
        }
    }

    private List<Future<?>> searchMediaFilesForMessagesInBatches(List<Message> messages, IItemSearcher searcher,
            ContentHandler handler, EmbeddedDocumentExtractor extractor, File dbPath, ParseContext context,
            AtomicInteger downloadedFiles) {

        if (searcher == null) {
            return Collections.emptyList();
        }
        List<List<Message>> listsToProcess = new ArrayList<>();
        List<Message> messagesToProcess = new ArrayList<>();
        int count = 0;
        for (Message m : messages) {
            if ((m.getMediaHash() != null && !m.getMediaHash().isEmpty())
                    || (m.getMediaName() != null && !m.getMediaName().isEmpty())) {
                messagesToProcess.add(m);
                count++;
                if (count == MESSAGE_SEARCH_BATCH_SIZE) {
                    count = 0;
                    listsToProcess.add(messagesToProcess);
                    messagesToProcess = new ArrayList<>();
                }
            }
        }
        if (count > 0) {
            listsToProcess.add(messagesToProcess);
        }

        ArrayList<Future<?>> futures = new ArrayList<>();
        for (List<Message> listToProcess : listsToProcess) {
            futures.addAll(searchMediaFilesForMessages(listToProcess, searcher, handler, extractor, dbPath, context,
                    downloadedFiles));
        }
        return futures;
    }

    private void setItemToMessage(IItemReader item, List<Message> messageList, String query, boolean isHashQuery,
            boolean saveItemRef) {
        if (messageList != null && saveItemRef) {
            for (Message m : messageList) {
                m.setMediaItem(item);
                m.setMediaQuery(escapeQuery(query, isHashQuery));
            }
        }
    }

    private List<Future<?>> searchMediaFilesForMessages(List<Message> messages, IItemSearcher searcher,
            ContentHandler handler, EmbeddedDocumentExtractor extractor, File dbPath, ParseContext context,
            AtomicInteger downloadedFiles) {

        // just save heavy item refs if creating report, per chat, not per database
        boolean saveItemRef = downloadedFiles == null;

        Map<String, List<Message>> hashesToSearchFor = new HashMap<>();
        Map<Pair<String, Long>, List<Message>> fileNameAndSizeToSearchFor = new HashMap<>();
        // First search for hashes
        for (Message m : messages) {
            if (m.getMediaItem() != null) {
                continue;
            }
            String hash = m.getMediaHash();
            if (hash != null && !hash.isEmpty()) {
                List<Message> messageList = hashesToSearchFor.get(hash);
                if (messageList == null) {
                    messageList = new ArrayList<>();
                    hashesToSearchFor.put(hash, messageList);
                }
                messageList.add(m);
            } else {
                String fileName = m.getMediaName();
                long fileSize = m.getMediaSize();
                if (fileName != null && !fileName.isEmpty() && fileSize > 0) {
                    if (fileName.contains("/")) { //$NON-NLS-1$
                        fileName = fileName.substring(fileName.lastIndexOf('/') + 1); // $NON-NLS-1$
                    }
                    Pair<String, Long> key = Pair.of(fileName, fileSize);
                    List<Message> messageList = fileNameAndSizeToSearchFor.get(key);
                    if (messageList == null) {
                        messageList = new ArrayList<>();
                        fileNameAndSizeToSearchFor.put(key, messageList);
                    }
                    messageList.add(m);
                }
            }
        }

        if (!hashesToSearchFor.isEmpty()) {
            StringBuilder allHashesQueryBuilder = new StringBuilder();
            allHashesQueryBuilder.append("sha-256:("); //$NON-NLS-1$
            for (String h : hashesToSearchFor.keySet()) {
                allHashesQueryBuilder.append(h).append(" "); //$NON-NLS-1$
            }
            allHashesQueryBuilder.append(")"); //$NON-NLS-1$
            String allHashesQuery = allHashesQueryBuilder.toString();
            List<IItemReader> result = iped.parsers.util.Util.getItems(allHashesQuery, searcher);
            for (IItemReader item : result) {
                String hash = (String) item.getExtraAttribute("sha-256"); //$NON-NLS-1$
                List<Message> messageList = hashesToSearchFor.remove(hash);

                setItemToMessage(item, messageList, "sha-256:" + hash, true, saveItemRef);

            }
        }

        // for media messages without hash, try to find by filename and size
        if (!fileNameAndSizeToSearchFor.isEmpty()) {
            StringBuilder fileNameAndSizeQueryBuilder = new StringBuilder();
            for (Pair<String, Long> key : fileNameAndSizeToSearchFor.keySet()) {
                fileNameAndSizeQueryBuilder.append("("); //$NON-NLS-1$
                fileNameAndSizeQueryBuilder.append(BasicProps.NAME).append(":\""); //$NON-NLS-1$
                fileNameAndSizeQueryBuilder.append(searcher.escapeQuery(key.getLeft()));
                fileNameAndSizeQueryBuilder.append("\" AND "); //$NON-NLS-1$
                fileNameAndSizeQueryBuilder.append(BasicProps.LENGTH).append(":"); //$NON-NLS-1$
                fileNameAndSizeQueryBuilder.append(key.getRight().toString());
                fileNameAndSizeQueryBuilder.append(") "); //$NON-NLS-1$

            }

            String fileNameAndSizeQuery = fileNameAndSizeQueryBuilder.toString();
            List<IItemReader> result = iped.parsers.util.Util.getItems(fileNameAndSizeQuery, searcher);
            for (IItemReader item : result) {
                if (item.getName() != null && !item.getName().isEmpty() && item.getLength() != null
                        && item.getLength() > 0) {
                    String fileName = item.getName();
                    long fileSize = item.getLength();
                    if (fileName.contains("/")) { //$NON-NLS-1$
                        fileName = fileName.substring(fileName.lastIndexOf('/') + 1); // $NON-NLS-1$
                    }
                    Pair<String, Long> key = Pair.of(fileName, fileSize);
                    List<Message> messageList = fileNameAndSizeToSearchFor.get(key);
                    String query = BasicProps.NAME + ":\"" + searcher.escapeQuery(fileName) + "\" AND " //$NON-NLS-1$ //$NON-NLS-2$
                            + BasicProps.LENGTH + ":" + fileSize;
                    setItemToMessage(item, messageList, query, false, saveItemRef);
                }
            }
        }

        // ** linkMediasByNameAndApproxSizeFallback **
        // Fallback search for media items that have hash in database, but hashes were
        // not found. It is possible that the the file has been padded with zeros (see
        // issue #486). Try to to find by name and by approximate size, then check if it
        // ends with zeros.

        // ** linkMediasByLongPathFallback **
        // Similar to the previous fallback, but file size is not used to match. Media
        // path length must be at least the value specified in the parameter. 0 to
        // disable this fallback.

        if (!hashesToSearchFor.isEmpty()
                && (linkMediasByNameAndApproxSizeFallback || linkMediasByLongPathFallback > 0)) {
            Map<String, List<Message>> fileNamesToSearchFor = new HashMap<>();
            Map<String, List<Message>> pathsToSearchFor = new HashMap<>();

            for (List<Message> messageList : hashesToSearchFor.values()) {
                for (Message m : messageList) {
                    if (linkMediasByNameAndApproxSizeFallback) {
                        String fileName = m.getMediaName();
                        if (fileName != null && !fileName.isEmpty()) {
                            if (fileName.contains("/")) { //$NON-NLS-1$
                                fileName = fileName.substring(fileName.lastIndexOf('/') + 1); // $NON-NLS-1$
                            }
                            List<Message> newMessageList = fileNamesToSearchFor.get(fileName);
                            if (newMessageList == null) {
                                newMessageList = new ArrayList<>();
                                fileNamesToSearchFor.put(fileName, newMessageList);
                            }
                            newMessageList.add(m);
                        }
                    }

                    if (linkMediasByLongPathFallback > 0) {
                        // If media path contains at least one folder separator and is large enough
                        String path = m.getMediaName();
                        if (path != null && path.indexOf('/') > 0 && path.length() >= linkMediasByLongPathFallback) {
                            List<Message> newMessageList = pathsToSearchFor.get(path);
                            if (newMessageList == null) {
                                pathsToSearchFor.put(path, newMessageList = new ArrayList<>());
                            }
                            newMessageList.add(m);
                        }
                    }
                }
            }

            if (!fileNamesToSearchFor.isEmpty() || !pathsToSearchFor.isEmpty()) {
                StringBuilder fallBackQueryBuilder = new StringBuilder();
                fallBackQueryBuilder.append(BasicProps.NAME).append(":("); //$NON-NLS-1$
                for (String fileName : fileNamesToSearchFor.keySet()) {
                    fileName = searcher.escapeQuery(fileName);
                    fallBackQueryBuilder.append("\"").append(fileName).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
                }
                fallBackQueryBuilder.append(")"); //$NON-NLS-1$

                String fallBackQuery = fallBackQueryBuilder.toString();
                List<IItemReader> result = iped.parsers.util.Util.getItems(fallBackQuery, searcher);
                for (IItemReader item : result) {
                    if (!fileNamesToSearchFor.isEmpty()) {
                        // Match by file name and approximate file size
                        String fileName = item.getName();
                        if (fileName != null && item.getLength() != null && item.getLength() > 0) {
                            if (fileName.contains("/")) { //$NON-NLS-1$
                                fileName = fileName.substring(fileName.lastIndexOf('/') + 1); // $NON-NLS-1$
                            }
                            List<Message> messageList = fileNamesToSearchFor.get(fileName);
                            if (messageList != null) {
                                messageList = messageList.stream().filter(m -> {
                                    long mediaSize = m.getMediaSize();
                                    long fileSize = item.getLength();
                                    return (fileSize >= mediaSize + 1 && fileSize <= mediaSize + 15
                                            && itemStreamEndsWithZeros(item, mediaSize));
                                }).collect(Collectors.toList());
                                setItemToMessage(item, messageList, BasicProps.HASH + ":" + item.getHash(), true,
                                        saveItemRef);
                            }
                        }
                    }

                    if (!pathsToSearchFor.isEmpty()) {
                        // Match long paths
                        String filePath = item.getPath();
                        if (filePath != null) {
                            for (String mediaPath : pathsToSearchFor.keySet()) {
                                if (filePath.endsWith(mediaPath)) {
                                    List<Message> messageList = pathsToSearchFor.get(mediaPath);
                                    if (messageList != null) {
                                        for (Message m : messageList) {
                                            if (m.getMediaItem() == null) {
                                                logger.info("Item matched by long path {}", mediaPath);
                                                m.setMediaItem(item);
                                                m.setMediaQuery(
                                                        escapeQuery(BasicProps.HASH + ":" + item.getHash(), true));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // if download files from the internet is allowed
        ArrayList<Future<?>> futures = new ArrayList<>();
        if (isDownloadMediaFilesEnabled() && downloadedFiles != null) {
            if (!hashesToSearchFor.isEmpty()) {

                ArrayList<LinkDownloader> links = new ArrayList<>();
                try (LinkExtractor le = new LinkExtractor(dbPath, new HashSet<String>(hashesToSearchFor.keySet()),
                        downloadConnectionTimeout, downloadReadTimeout)) {
                    le.extractLinks();
                    links = le.getLinks();

                } catch (Exception e) {
                    // cannot extract link
                    logger.warn("Could not extract links from database " + dbPath, e);
                    return futures;
                }

                for (LinkDownloader ld : links) {
                    if (ld == null || ld.getHash() == null || !hashesDownloaded.add(ld.getHash())) {
                        continue;
                    }

                    Runnable r = new Runnable() {

                        @Override
                        public void run() {

                            try (TemporaryResources tmp = new TemporaryResources()) {

                                File f = tmp.createTemporaryFile(), fout = tmp.createTemporaryFile();

                                ld.downloadUsingStream(f);

                                ld.decript(f, fout);

                                Metadata downloadMetadata = new Metadata();

                                downloadMetadata.set(ExtraProperties.DOWNLOADED_DATA, "true");
                                downloadMetadata.set(TikaCoreProperties.TITLE,
                                        "Dowloaded_item_" + downloadedFiles.incrementAndGet());

                                try (FileInputStream out = new FileInputStream(fout)) {
                                    synchronized (extractor) {
                                        extractor.parseEmbedded(out, handler, downloadMetadata, false);
                                    }
                                }

                            } catch (URLnotFound ex) {
                                // do not log this error as it is expected

                            } catch (Exception e) {
                                logger.warn("Error trying to download medias referenced by " + dbPath, e);
                            }

                        }
                    };

                    futures.add(getExecutor().submit(r));
                }

            }
        }
        return futures;
    }

    /**
     * Check it the media file is padded with zeros (check if all bytes beyond
     * mediaSize are zeros)
     * 
     * @param item
     * @param mediaSize
     * @return
     */
    private boolean itemStreamEndsWithZeros(IItemReader item, long mediaSize) {
        try (SeekableInputStream sis = item.getSeekableInputStream()) {
            sis.seek(mediaSize);
            byte[] bytes = new byte[15];
            int read = org.apache.commons.io.IOUtils.read(sis, bytes);
            // this loop will run at most 15 times
            while (--read >= 0) {
                if (bytes[read] != 0) {
                    return false;
                }
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
