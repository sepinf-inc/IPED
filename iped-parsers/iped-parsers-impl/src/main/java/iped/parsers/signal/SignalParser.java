package iped.parsers.signal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
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
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLite3Parser;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.EmptyInputStream;

/**
 * Parser for Signal Messenger Android databases (signal.db).
 *
 * Extracts individual and group conversations with messages and metadata.
 * Supports plaintext SQLite databases obtained through full-filesystem
 * forensic acquisition of Android devices.
 */
public class SignalParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalParser.class);

    // Service name used in USER_ACCOUNT_TYPE, as TelegramParser and ThreemaParser do:
    // it becomes the service of the account nodes in link analysis. Note this keeps
    // Signal accounts on their own nodes instead of merging them into phone nodes,
    // which is what WhatsAppParser opts into (see GraphTask.getAccountNodeValues).
    public static final String SIGNAL = "Signal";

    public static final MediaType SIGNAL_DB      = MediaType.application("x-signal-db");
    public static final MediaType SIGNAL_CHAT    = MediaType.application("x-signal-chat");
    public static final MediaType SIGNAL_MESSAGE = MediaType.parse("message/x-signal-message");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(SIGNAL_DB);

    // ReportGenerator uses ThreadLocal formatters; singleton is safe
    private static final ReportGenerator REPORT_GEN = new ReportGenerator();

    private final SQLite3Parser sqliteParser = new SQLite3Parser();

    private boolean extractMessages = true;
    private int minChatSplitSize = 6000000;

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    @Field
    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        if (!extractor.shouldParseEmbedded(metadata))
            return;

        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            ItemInfo itemInfo = context.get(ItemInfo.class);
            String itemPath = itemInfo != null ? itemInfo.getPath() : "";

            Connection connection;
            try {
                connection = getConnection(tis, metadata, context);
            } catch (IOException e) {
                // Signal databases are SQLCipher-encrypted by default and are matched by
                // file name, so an unreadable file is expected here: skip the item instead
                // of reporting a parsing error for it.
                LOGGER.debug("Could not open {} as a SQLite database, skipping it: {}", itemPath, e.getMessage());
                return;
            }

            boolean unsupported;
            try {
                SignalExtractor signalExtractor = new SignalExtractor(connection, itemPath);
                unsupported = !signalExtractor.isValidSignalDatabase();
                if (unsupported) {
                    // Signal encrypts its database by default and the media type comes
                    // from the file name alone, so an unreadable file is expected here
                    if (!signalExtractor.isReadableDatabase()) {
                        LOGGER.debug("DB at {} could not be read as SQLite, skipping it", itemPath);
                        unsupported = false;
                    }
                } else {
                    List<SignalChat> chats = signalExtractor.extractChats();
                    SignalContact selfContact = signalExtractor.findSelfContact();
                    markOwnMessagesAsOutgoing(chats, selfContact);
                    if (selfContact == null)
                        LOGGER.warn("Could not identify the device owner in {}: outgoing messages will have no sender",
                                itemPath);
                    createReports(chats, selfContact, handler, extractor);
                }
            } finally {
                // Closed before any fallback: the generic parser opens the same temporary
                // file and would rewrite and delete its -wal/-shm under this connection
                try { connection.close(); } catch (SQLException e) { /* ignore */ }
            }

            if (unsupported) {
                // An older Signal database or an unrelated file with that name: the generic
                // SQLite parser still gives the examiner a table preview. A failure here is
                // not a Signal parsing error, so it is logged instead of thrown.
                LOGGER.debug("DB at {} is not a supported Signal database, parsing it as plain SQLite", itemPath);
                try {
                    sqliteParser.parse(tis, handler, metadata, context);
                } catch (SAXException e) {
                    throw e;
                } catch (Exception e) {
                    LOGGER.warn("Could not parse {} as a plain SQLite database: {}", itemPath, e.getMessage());
                }
            }

        } catch (SAXException e) {
            // Let SAXException (including WriteLimitReachedException) propagate so
            // Tika's pipeline stop mechanism works correctly
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Error parsing Signal database", e);
            throw new TikaException("SignalParser error", e);
        }
    }

    /**
     * Base types carry no direction for some rows, group calls among them, so a message
     * whose sender is the device owner is marked as outgoing here, where the owner is known.
     */
    private static void markOwnMessagesAsOutgoing(List<SignalChat> chats, SignalContact selfContact) {
        if (selfContact == null)
            return;
        for (SignalChat chat : chats) {
            for (SignalMessage m : chat.getMessages()) {
                // Only where the base type carries no direction: the owner is found by a
                // heuristic, and an incoming row already knows it is incoming
                if (m.getMessageType() != SignalMessage.MessageType.INCOMING
                        && m.getSender() != null && m.getSender().getId() == selfContact.getId())
                    m.setFromMe(true);
            }
        }
    }

    private void createReports(List<SignalChat> chats, SignalContact selfContact,
            ContentHandler handler, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        int chatVirtualId = 0;
        for (SignalChat chat : chats) {
            String chatTitle = chat.getTitle();
            List<SignalMessage> allMessages = chat.getMessages();

            int firstMessage = 0;
            int fragmentNum = 0;
            boolean hasMore;

            // A long conversation becomes several items, as the WhatsApp, Telegram and
            // Threema parsers do, instead of one huge preview held whole in memory
            do {
                ReportGenerator.Fragment fragment =
                        REPORT_GEN.generateChatFragment(chat, firstMessage, minChatSplitSize);
                List<SignalMessage> fragmentMessages =
                        allMessages.subList(firstMessage, fragment.getNextMessage());
                hasMore = fragment.getNextMessage() < allMessages.size();

                String fragmentTitle = (fragmentNum > 0 || hasMore)
                        ? chatTitle + "_" + fragmentNum
                        : chatTitle;

                // Count only indexable (non-system) messages for HASCHILD
                long indexableCount = fragmentMessages.stream()
                        .filter(m -> m.getMessageType() != SignalMessage.MessageType.SYSTEM)
                        .count();

                Metadata chatMeta = new Metadata();
                chatMeta.set(TikaCoreProperties.TITLE, fragmentTitle);
                chatMeta.set(StandardParser.INDEXER_CONTENT_TYPE, SIGNAL_CHAT.toString());
                chatMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(chatVirtualId));
                chatMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                if (chat.isGroupChat()) {
                    chatMeta.add(ExtraProperties.GROUP_ID, chat.getContact().getGroupId());
                    // Self first (device owner); then members, excluding self to avoid duplication
                    if (selfContact != null)
                        chatMeta.add(ExtraProperties.PARTICIPANTS, selfContact.getFullId());
                    for (SignalContact member : chat.getParticipants()) {
                        if (selfContact == null || member.getId() != selfContact.getId())
                            chatMeta.add(ExtraProperties.PARTICIPANTS, member.getFullId());
                    }
                } else if (chat.getContact() != null) {
                    if (selfContact != null)
                        chatMeta.add(ExtraProperties.PARTICIPANTS, selfContact.getFullId());
                    // Note to Self has the owner as the chat contact: do not list them twice
                    if (selfContact == null || chat.getContact().getId() != selfContact.getId())
                        chatMeta.add(ExtraProperties.PARTICIPANTS, chat.getContact().getFullId());
                }

                // Period covered by this fragment, so split conversations are readable
                // on the timeline without opening each one
                Date firstDate = messageDate(fragmentMessages, true);
                Date lastDate = messageDate(fragmentMessages, false);
                if (firstDate != null)
                    chatMeta.set(TikaCoreProperties.CREATED, firstDate);
                if (lastDate != null)
                    chatMeta.set(TikaCoreProperties.MODIFIED, lastDate);

                if (extractMessages && indexableCount > 0)
                    chatMeta.set(BasicProps.HASCHILD, Boolean.TRUE.toString());

                if (extractor.shouldParseEmbedded(chatMeta)) {
                    extractor.parseEmbedded(new ByteArrayInputStream(fragment.getHtml()), handler,
                            chatMeta, false);

                    if (extractMessages) {
                        extractMessages(fragmentTitle, chat, fragmentMessages, selfContact,
                                chatVirtualId, handler, extractor);
                    }
                }

                firstMessage = fragment.getNextMessage();
                fragmentNum++;
                chatVirtualId++;
            } while (hasMore);
        }
    }

    private void extractMessages(String chatTitle, SignalChat chat, List<SignalMessage> messages,
            SignalContact selfContact, int parentVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        String selfId = selfContact != null ? selfContact.getFullId() : null;

        int msgCount = 0;
        for (SignalMessage m : messages) {
            // System messages are rendered only in the HTML report; not indexed individually
            if (m.getMessageType() == SignalMessage.MessageType.SYSTEM)
                continue;

            Metadata msgMeta = new Metadata();
            msgMeta.set(TikaCoreProperties.TITLE, chatTitle + "_message_" + msgCount++);
            msgMeta.set(StandardParser.INDEXER_CONTENT_TYPE, SIGNAL_MESSAGE.toString());
            msgMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentVirtualId));
            msgMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

            // fall back to dateReceived like ReportGenerator does, so a message
            // with no dateSent (e.g. a locally-originated draft/failed send)
            // doesn't silently drop out of date-based search/filtering while
            // still showing a date in the HTML report
            Date msgDate = m.getDateSent() != null ? m.getDateSent() : m.getDateReceived();
            if (msgDate != null) {
                msgMeta.set(ExtraProperties.MESSAGE_DATE, msgDate);
                msgMeta.set(TikaCoreProperties.CREATED, msgDate);
            }

            msgMeta.set(ExtraProperties.MESSAGE_BODY, resolveBody(m));
            msgMeta.set(ExtraProperties.USER_ACCOUNT_TYPE, SIGNAL);

            if (chat.isGroupChat())
                msgMeta.set(ExtraProperties.IS_GROUP_MESSAGE, "true");
            // Sub-items have no content of their own: an empty length becomes null,
            // instead of being indexed as 0 bytes (same as WhatsApp/Telegram/Threema)
            msgMeta.set(BasicProps.LENGTH, "");

            SignalContact contact = chat.getContact();
            // Built from the conversation title, not the fragment one, so splitting a long
            // conversation does not split its group node; the Signal group_id keeps
            // untitled or same-named groups apart
            String groupTo = chat.isGroupChat()
                    ? chat.getTitle() + " (id:" + chat.getContact().getGroupId() + ")"
                    : null;
            if (m.isFromMe()) {
                // The row's own sender is more precise than the global heuristic, which
                // picks a single recipient row even when the owner changed numbers
                String fromId = m.getSender() != null ? m.getSender().getFullId() : selfId;
                if (fromId != null)
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, fromId);
                // For groups, TO is the chat title; for individual, TO is the contact
                if (chat.isGroupChat()) {
                    msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, groupTo);
                } else if (contact != null) {
                    msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, contact.getFullId());
                }
            } else {
                if (chat.isGroupChat()) {
                    // Resolved from the recipient table, so former members are named too
                    // Left unset when the sender cannot be resolved: an "Unknown" literal
                    // would merge unrelated people into one node in link analysis
                    SignalContact sender = m.getSender();
                    if (sender != null)
                        msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, sender.getFullId());
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_TO, groupTo);
                } else if (contact != null) {
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, contact.getFullId());
                    if (selfId != null)
                        msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, selfId);
                }
            }

            if (!extractor.shouldParseEmbedded(msgMeta))
                continue;

            extractor.parseEmbedded(new EmptyInputStream(), handler, msgMeta, false);
        }
    }

    private static Date messageDate(List<SignalMessage> messages, boolean first) {
        for (int i = 0; i < messages.size(); i++) {
            SignalMessage m = messages.get(first ? i : messages.size() - 1 - i);
            Date date = m.getDateSent() != null ? m.getDateSent() : m.getDateReceived();
            if (date != null)
                return date;
        }
        return null;
    }

    private static String resolveBody(SignalMessage m) {
        // Calls read from the call table describe their own type and outcome
        if (m.isRemoteDeleted())
            return "[Message deleted by sender]";
        if (m.getCallDetail() != null)
            return "[" + m.getCallDetail() + "]";
        if (m.isEarlierRevision())
            return "[Edited, earlier version] " + (m.getBody() != null ? m.getBody() : "[Empty message]");
        if (m.isScheduled())
            return "[Scheduled, never sent] " + (m.getBody() != null ? m.getBody() : "[Empty message]");
        switch (m.getMessageType()) {
            case CALL_OUTGOING: return "[Outgoing Call]";
            case CALL_INCOMING: return "[Incoming Call]";
            case CALL_MISSED:   return "[Missed Call]";
            case CALL_GROUP:    return "[Group Call]";
            default:
                // Body is also null for deleted, expired and reaction-only rows, and
                // attachments are not extracted yet, so do not claim there is one
                return m.getBody() != null ? m.getBody() : "[Empty message]";
        }
    }
}
