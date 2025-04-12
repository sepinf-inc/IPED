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
package iped.parsers.threema;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.plist.detector.PListDetector;
import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLite3Parser;
import iped.parsers.standard.StandardParser;
import iped.parsers.threema.Message.MessageType;
import iped.parsers.util.ItemInfo;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;
import iped.utils.SimpleHTMLEncoder;

/**
 * Parser para banco de dados do Threema Secure Messenger
 *
 * @author André Rodrigues Costa <andre.arc@pf.gov.br>
 */
public class ThreemaParser extends SQLite3DBParser {

    private static final Logger logger = LoggerFactory.getLogger(ThreemaParser.class);

    private static final long serialVersionUID = 1L;

    public static final MediaType CHAT_STORAGE = MediaType.application("x-threema-chatstorage"); //$NON-NLS-1$

    public static final MediaType CHAT_STORAGE_F = MediaType.application("x-threema-chatstorage-f");

    public static final MediaType THREEMA_CHAT = MediaType.parse("application/x-threema-chat"); //$NON-NLS-1$

    public static final MediaType THREEMA_MESSAGE = MediaType.parse("message/x-threema-message"); //$NON-NLS-1$

    public static final MediaType THREEMA_ATTACHMENT = MediaType.parse("message/x-threema-attachment"); //$NON-NLS-1$

    public static final MediaType THREEMA_CALL = MediaType.parse("call/x-threema-call"); //$NON-NLS-1$

    private static final String MESSAGE_TYPE_PREFIX = "! "; //$NON-NLS-1$

    private static final int MESSAGE_SEARCH_BATCH_SIZE = 512;

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(CHAT_STORAGE, CHAT_STORAGE_F);

    public static final MediaType THREEMA_USER_PLIST = PListDetector.THREEMA_USER_PLIST;

    // This fallback parser is thread safe
    private final SQLite3Parser sqliteParser = new SQLite3Parser();

    private boolean extractMessages = true;

    private boolean extractMediasAsFiles = false;

    // TODO not implemented yet
    private boolean recoverDeletedRecords = false;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    @Field
    public void setExtractMediasAsFiles(boolean extractMediasAsFiles) {
        this.extractMediasAsFiles = extractMediasAsFiles;
    }

    // TODO not implemented yet
    @Field
    public void setRecoverDeletedRecords(boolean recoverDeletedRecords) {
        this.recoverDeletedRecords = recoverDeletedRecords;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        try (TemporaryResources tmp = new TemporaryResources()) {
            String mimetype = metadata.get(StandardParser.INDEXER_CONTENT_TYPE);

            if (mimetype.equals(CHAT_STORAGE.toString())) {
                extractMediaFilesFromDatabase(stream, handler, metadata, context, new ExtractorIOSFactory(), tmp);
            } else if (mimetype.equals(CHAT_STORAGE_F.toString())) {
                parseThreemaMessages(stream, handler, metadata, context, new ExtractorIOSFactory(), tmp);
            }

        } catch (Exception e) {
            // log all threma exceptions
            logger.warn("Error parsing ThreemaData", e);
            throw e;
        }
    }

    private List<Chat> extractChatList(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context, ExtractorFactory extractorIOSFactory, TemporaryResources tmp) throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
        IItemSearcher searcher = context.get(IItemSearcher.class);

        if (extractor.shouldParseEmbedded(metadata)) {
            TikaInputStream tis = null;
            try {
                String filePath = null;

                ItemInfo itemInfo = context.get(ItemInfo.class);

                if (itemInfo != null) {
                    filePath = itemInfo.getPath();
                }

                tis = TikaInputStream.get(stream, tmp);
                File tempDbFile = tis.getFile();
                ThreemaAccount account = getUserAccount(searcher);

                extractorIOSFactory.setConnectionParams(tis, metadata, context, this);
                Extractor threemaExtractor = extractorIOSFactory.createMessageExtractor(tmp, filePath, tempDbFile, account, recoverDeletedRecords);
                List<Chat> chatList = threemaExtractor.getChatList();
                return chatList;

            } catch (Exception e) {
                if (tis != null) {
                    sqliteParser.parse(tis, handler, metadata, context);
                }
                // always throw exceptions to flag the file caused a parsing error
                throw e;
            } finally {
                metadata.set(StandardParser.INDEXER_CONTENT_TYPE, extractorIOSFactory.getType2().toString());
            }
        }
        return Collections.emptyList();
    }

    private void extractMediaFilesFromDatabase(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context, ExtractorFactory extractorIOSFactory, TemporaryResources tmp)
            throws IOException, SAXException, TikaException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
        List<Chat> chatList = extractChatList(stream, handler, metadata, context, new ExtractorIOSFactory(), tmp);
        for (Chat c : chatList) {
            // First search for hashes
            for (Message m : c.getMessages()) {
                if (m.getMediaItem() != null || m.getData() == null) {
                    continue;
                }
                if (m.getData() != null) {
                    try (InputStream is = new FileInputStream(m.getData())) {
                        Metadata embedFileData = new Metadata();

                        String name = getMediaName(m);
                        embedFileData.set(BasicProps.NAME, name);
                        embedFileData.set(StandardParser.INDEXER_CONTENT_TYPE, m.getMediaMime());
                        embedFileData.set(ExtraProperties.CARVEDBY_METADATA_NAME, this.getClass().getName());

                        // This property forces media item to be extracted to sub-items folder (instead
                        // of db storage), allowing it to be accessible from External HTML viewers
                        // (useful chat reports). Since it doesn't work for medias found outside the DB,
                        // a mixed behavior can confuse users, so it's disabled by default.
                        if (extractMediasAsFiles) {
                            embedFileData.set(ExtraProperties.EXTRACTED_FILE, Boolean.TRUE.toString());
                        }

                        embedFileData.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                        embedFileData.set(TikaCoreProperties.TITLE, name);

                        extractor.parseEmbedded(is, handler, embedFileData, false);

                    } catch (Exception e) {
                        logger.warn("Error trying to parse Threema Database embedded files", e);
                    }
                }
            }
        }
    }

    private void parseThreemaMessages(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context, ExtractorFactory extractorIOSFactory, TemporaryResources tmp) throws TikaException {
        IItemSearcher searcher = context.get(IItemSearcher.class);
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                ThreemaAccount account = getUserAccount(searcher);
                List<Chat> chatList = extractChatList(stream, handler, metadata, context, new ExtractorIOSFactory(), tmp);
                createReport(chatList, searcher, handler, extractor, account);

            } catch (Exception e) {
                if (e instanceof TikaException)
                    throw (TikaException) e;
                else
                    throw new TikaException("ThreemaParser Exception", e); //$NON-NLS-1$

            }
        }
    }

    private void createReport(List<Chat> chatList, IItemSearcher searcher, ContentHandler handler, EmbeddedDocumentExtractor extractor, ThreemaAccount account) throws Exception {
        int chatVirtualId = 0;
        for (Chat c : chatList) {
            searchMediaFilesForMessagesInBatches(c.getMessages(), searcher);
            int frag = 0;
            int firstMsg = 0;
            ReportGenerator reportGenerator = new ReportGenerator();
            int minChatSplitSize = 6000000;
            reportGenerator.setMinChatSplitSize(minChatSplitSize);
            byte[] bytes = reportGenerator.generateNextChatHtml(c, account);
            while (bytes != null) {
                Metadata chatMetadata = new Metadata();
                int nextMsg = reportGenerator.getNextMsgNum();

                List<Message> msgSubset = c.getMessages().subList(firstMsg, nextMsg);
                storeLinkedHashes(msgSubset, chatMetadata);

                // condition to avoid duplicate locations being saved in chat & messages
                if (!extractMessages) {
                    storeLocations(msgSubset, chatMetadata);
                }
                firstMsg = nextMsg;
                byte[] nextBytes = reportGenerator.generateNextChatHtml(c, account);

                String chatName = c.getTitle();
                if (frag > 0 || nextBytes != null)
                    chatName += "_" + frag++; //$NON-NLS-1$

                chatMetadata.set("chatId", Long.toString(c.getId()));
                chatMetadata.set(TikaCoreProperties.TITLE, chatName);
                chatMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, THREEMA_CHAT.toString());
                chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(chatVirtualId));
                chatMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                if (extractMessages && !msgSubset.isEmpty()) {
                    chatMetadata.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
                }
                if (account != null) {
                    String local = account.getFullId();
                    chatMetadata.add(ExtraProperties.PARTICIPANTS, local);
                }
                if (c.isGroupChat()) {
                    for (ThreemaContact member : c.getParticipants()) {
                        chatMetadata.add(ExtraProperties.PARTICIPANTS, member.getFullId());
                    }
                    chatMetadata.add(ExtraProperties.GROUP_ID, "Conversation_" + c.getId());
                } else {
                    if (c.getContact() != null) {
                        chatMetadata.add(ExtraProperties.PARTICIPANTS, c.getContact().getFullId());
                    }
                }

                ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                bytes = nextBytes;

                if (extractMessages) {
                    extractMessages(chatName, c, msgSubset, account, chatVirtualId++, handler, extractor);
                }
            }
            // clear heavy items references (possibly with thumbs loaded)
            c.getMessages().forEach(m -> m.setMediaItem(null));
        }

    }

    private ThreemaAccount getUserAccount(IItemSearcher searcher) {
        ThreemaAccount account = new ThreemaAccount();

        if (searcher != null) {
            String query = BasicProps.CONTENTTYPE + ":\"" + THREEMA_USER_PLIST.toString() + "\"";

            List<IItemReader> result = searcher.search(query);

            for (IItemReader item : result) {
                try (InputStream is = item.getBufferedInputStream()) {
                    ThreemaAccount a = ThreemaAccount.getFromIOSPlist(is);
                    if (a != null) {
                        account = a;
                        break;
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing Threema Account", e);
                }
            }
        }
        return account;
    }

    private void fillGroupRecipients(Metadata meta, Chat c) {
        String to = "Group";
        if (c.getSubject() != null) {
            to += " " + c.getSubject().strip();
        }
        to += " (id:" + c.getId() + ")";
        meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, to);
        meta.set(ExtraProperties.IS_GROUP_MESSAGE, "true");
    }

    private void extractMessages(String chatName, Chat c, List<Message> messages, ThreemaAccount account, int parentVirtualId, ContentHandler handler, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {
        int msgCount = 0;
        for (Message m : messages) {

            Metadata meta = new Metadata();
            meta.set(TikaCoreProperties.TITLE, chatName + "_message_" + msgCount++); //$NON-NLS-1$
            meta.set(StandardParser.INDEXER_CONTENT_TYPE, THREEMA_MESSAGE.toString());
            meta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentVirtualId));
            meta.set(ExtraProperties.PARENT_VIEW_POSITION, String.valueOf(m.getId()));
            meta.set(ExtraProperties.USER_ACCOUNT_TYPE, THREEMA_MESSAGE.toString());
            meta.set(ExtraProperties.MESSAGE_DATE, m.getTimeStamp());
            meta.set(TikaCoreProperties.CREATED, m.getTimeStamp());
            meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

            String local = account.getFullId();
            String remote = m.getRemoteResource();

            if (m.isFromMe()) {
                meta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, local);
                if (c.isGroupChat()) {
                    fillGroupRecipients(meta, c);
                } else {
                    meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, remote);
                }
            } else {
                meta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, remote);
                if (c.isGroupChat()) {
                    fillGroupRecipients(meta, c);
                } else {
                    meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, local);
                }
            }

            meta.set(ExtraProperties.MESSAGE_BODY, m.getText());
            meta.set(ExtraProperties.URL, m.getUrl());

            meta.set("mediaName", m.getMediaName()); //$NON-NLS-1$
            meta.set("mediaMime", m.getMediaMime()); //$NON-NLS-1$
            if (m.getMediaSize() != 0) {
                meta.set("mediaSize", Long.toString(m.getMediaSize()));
            }
            if (m.getMediaQuery() != null && m.getMediaSize() > 2) {
                meta.set(StandardParser.INDEXER_CONTENT_TYPE, THREEMA_ATTACHMENT.toString());
                meta.set(ExtraProperties.LINKED_ITEMS, revertEscapeQuery(m.getMediaQuery())); // $NON-NLS-1$
            }
            if (m.getMediaItem() != null && m.getMediaItem().getThumb() != null) {
                meta.set(ExtraProperties.THUMBNAIL_BASE64, Base64.getEncoder().encodeToString(m.getMediaItem().getThumb()));
            }
            if (!m.getChildPornSets().isEmpty()) {
                meta.set("hash:status", "pedo"); //$NON-NLS-1$ //$NON-NLS-2$
                for (String set : m.getChildPornSets()) {
                    meta.add("hash:set", set); //$NON-NLS-1$
                }
            }

            if (m.getMessageType() == MessageType.LOCATION_MESSAGE) {
                meta.set(ExtraProperties.LOCATIONS, m.getLatitude() + ";" + m.getLongitude()); //$NON-NLS-1$
            }

            if (m.getMessageStatus() != null) {
                meta.set("messageStatus", m.getMessageStatus().toString()); //$NON-NLS-1$
            }

            if (m.getMessageType() == MessageType.THREEMA_CALL) {
                meta.set(StandardParser.INDEXER_CONTENT_TYPE, THREEMA_CALL.toString());
                meta.set("duration", m.getMediaDuration()); //$NON-NLS-1$
            }

            if (meta.get(ExtraProperties.MESSAGE_BODY) == null) {
                meta.set(ExtraProperties.MESSAGE_BODY, MESSAGE_TYPE_PREFIX + m.getMessageType().toString());
            }
            meta.set(BasicProps.LENGTH, ""); //$NON-NLS-1$
            extractor.parseEmbedded(new EmptyInputStream(), handler, meta, false);
        }
    }

    private void storeLinkedHashes(List<Message> messages, Metadata metadata) {
        for (Message m : messages) {
            if (m.getMediaQuery() != null && m.getMediaSize() > 2) {
                String query = revertEscapeQuery(m.getMediaQuery());
                metadata.add(ExtraProperties.LINKED_ITEMS, query);
                if (m.isFromMe()) {
                    metadata.add(ExtraProperties.SHARED_HASHES, "(" + query + ")");
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

    private String escapeQuery(String query) {
        return SimpleHTMLEncoder.htmlEncode("\"" + query.replace("\"", "\\\"") + "\"");
    }

    private void storeLocations(List<Message> messages, Metadata metadata) {
        for (Message m : messages) {
            if (m.getMessageType() == MessageType.LOCATION_MESSAGE) {
                if (m.getLatitude() != 0.0 && m.getLongitude() != 0.0) {
                    metadata.add(ExtraProperties.LOCATIONS, m.getLatitude() + ";" + m.getLongitude()); //$NON-NLS-1$
                }
            }
        }
    }

    private static abstract class ExtractorFactory {

        InputStream is;
        Metadata metadata;
        ParseContext context;
        ThreemaParser connFactory;

        abstract Extractor createMessageExtractor(TemporaryResources tmp, String itemPath, File file, ThreemaAccount account, boolean recoverDeletedRecords);

        private void setConnectionParams(InputStream is, Metadata metadata, ParseContext context, ThreemaParser connFactory) {
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
    protected static class ExtractorIOSFactory extends ExtractorFactory {

        @Override
        public Extractor createMessageExtractor(TemporaryResources tmp, String itemPath, File file, ThreemaAccount account, boolean recoverDeletedRecords) {
            return new ExtractorIOS(tmp, itemPath, file, account, recoverDeletedRecords) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorIOSFactory.this.getConnection();
                }
            };
        }

        @Override
        public MediaType getType2() {
            return CHAT_STORAGE_F;
        }

    }

    private void searchMediaFilesForMessagesInBatches(List<Message> messages, IItemSearcher searcher) {

        List<List<Message>> listsToProcess = new ArrayList<>();
        List<Message> messagesToProcess = new ArrayList<>();
        int count = 0;
        for (Message m : messages) {
            if (m.getData() != null || m.getDataName() != null) {
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

        for (List<Message> listToProcess : listsToProcess) {
            searchMediaFilesForMessages(listToProcess, searcher);
        }
    }

    private void setItemToMessage(IItemReader item, List<Message> messageList, String query) {
        if (messageList != null) {
            for (Message m : messageList) {
                m.setMediaItem(item);
                m.setMediaQuery(escapeQuery(query));
                if (m.getMediaSize() != item.getLength()) {
                    m.setMediaSize(item.getLength());
                }
            }
        }
    }

    private String getMediaName(Message m) {
        if (m.getMediaName() != null && !m.getMediaName().isBlank()) {
            return m.getMediaName().strip();
        } else {
            return "Threema_Media_Item_" + m.getId();
        }
    }

    private void searchMediaFilesForMessages(List<Message> messages, IItemSearcher searcher) {

        Map<Pair<String, Long>, List<Message>> fileNameAndSizeToSearchFor = new HashMap<>();

        // First search for hashes
        for (Message m : messages) {
            if (m.getMediaItem() != null) {
                continue;
            }
            String fileName = "";
            long fileSize = 0;

            if (m.getDataName() != null) {
                fileName = m.getDataName();
                fileSize = m.getMediaSize();
            } else if (m.getData() != null) {
                fileName = getMediaName(m);
                fileSize = m.getData().length();
            }

            if (!fileName.isEmpty() && fileSize > 0) {
                Pair<String, Long> key = Pair.of(fileName, fileSize);
                List<Message> messageList = fileNameAndSizeToSearchFor.computeIfAbsent(key, k -> new ArrayList<>());
                messageList.add(m);
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
                if (item.getName() != null && !item.getName().isEmpty() && item.getLength() != null && item.getLength() > 0) {
                    String fileName = item.getName();
                    long fileSize = item.getLength();
                    Pair<String, Long> key = Pair.of(fileName, fileSize);
                    List<Message> messageList = fileNameAndSizeToSearchFor.get(key);
                    // ReferencedBy tab works checking hash references only for now
                    String query = BasicProps.HASH + ":" + item.getHash();
                    setItemToMessage(item, messageList, query);
                }
            }
        }
    }
}
