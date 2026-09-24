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

    // Service name used in USER_ACCOUNT_TYPE, as WhatsAppParser.WHATSAPP does:
    // it becomes the service of the account nodes in link analysis.
    public static final String SIGNAL = "Signal";

    public static final MediaType SIGNAL_DB      = MediaType.application("x-signal-db");
    public static final MediaType SIGNAL_CHAT    = MediaType.application("x-signal-chat");
    public static final MediaType SIGNAL_MESSAGE = MediaType.parse("message/x-signal-message");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(SIGNAL_DB);

    // ReportGenerator uses ThreadLocal formatters; singleton is safe
    private static final ReportGenerator REPORT_GEN = new ReportGenerator();

    private boolean extractMessages = true;

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
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

            try {
                SignalExtractor signalExtractor = new SignalExtractor(connection, itemPath);
                if (!signalExtractor.isValidSignalDatabase()) {
                    LOGGER.debug("Skipping: DB at {} does not have required Signal tables", itemPath);
                    return;
                }
                List<SignalChat> chats = signalExtractor.extractChats();
                SignalContact selfContact = signalExtractor.findSelfContact();
                if (selfContact == null)
                    LOGGER.warn("Could not identify the device owner in {}: outgoing messages will have no sender",
                            itemPath);
                createReports(chats, selfContact, handler, extractor);
            } finally {
                try { connection.close(); } catch (SQLException e) { /* ignore */ }
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

    private void createReports(List<SignalChat> chats, SignalContact selfContact,
            ContentHandler handler, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        int chatVirtualId = 0;
        for (SignalChat chat : chats) {
            String chatTitle = chat.getTitle();

            // Count only indexable (non-system) messages for HASCHILD
            long indexableCount = chat.getMessages().stream()
                    .filter(m -> m.getMessageType() != SignalMessage.MessageType.SYSTEM)
                    .count();

            Metadata chatMeta = new Metadata();
            chatMeta.set(TikaCoreProperties.TITLE, chatTitle);
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

            if (extractMessages && indexableCount > 0)
                chatMeta.set(BasicProps.HASCHILD, Boolean.TRUE.toString());

            if (!extractor.shouldParseEmbedded(chatMeta)) {
                chatVirtualId++;
                continue;
            }

            byte[] reportBytes = REPORT_GEN.generateChatHtml(chat);
            extractor.parseEmbedded(new ByteArrayInputStream(reportBytes), handler, chatMeta, false);

            if (extractMessages) {
                extractMessages(chatTitle, chat, selfContact, chatVirtualId, handler, extractor);
            }
            chatVirtualId++;
        }
    }

    private void extractMessages(String chatTitle, SignalChat chat, SignalContact selfContact,
            int parentVirtualId, ContentHandler handler, EmbeddedDocumentExtractor extractor)
            throws SAXException, IOException {

        String selfId = selfContact != null ? selfContact.getFullId() : null;

        int msgCount = 0;
        for (SignalMessage m : chat.getMessages()) {
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

            SignalContact contact = chat.getContact();
            // Includes the Signal group_id so untitled or same-named groups do not
            // collapse into a single node in link analysis
            String groupTo = chat.isGroupChat()
                    ? chatTitle + " (id:" + chat.getContact().getGroupId() + ")"
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

    private static String resolveBody(SignalMessage m) {
        // Calls read from the call table describe their own type and outcome
        if (m.getCallDetail() != null)
            return "[" + m.getCallDetail() + "]";
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
