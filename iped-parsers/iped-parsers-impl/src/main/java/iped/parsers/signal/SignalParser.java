package iped.parsers.signal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
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

    public static final MediaType SIGNAL_DB      = MediaType.application("x-signal-db");
    public static final MediaType SIGNAL_CHAT    = MediaType.application("x-signal-chat");
    public static final MediaType SIGNAL_MESSAGE = MediaType.parse("message/x-signal-message");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(SIGNAL_DB);

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

            Connection connection = getConnection(tis, metadata, context);
            if (connection == null)
                return;

            try {
                SignalExtractor signalExtractor = new SignalExtractor(connection, itemPath);
                if (!signalExtractor.isValidSignalDatabase()) {
                    LOGGER.debug("Skipping: DB at {} does not have required Signal tables", itemPath);
                    return;
                }
                List<SignalChat> chats = signalExtractor.extractChats();
                SignalContact selfContact = signalExtractor.findSelfContact();
                createReports(chats, selfContact, handler, extractor);
            } finally {
                try { connection.close(); } catch (SQLException e) { /* ignore */ }
            }

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
                chatMeta.add(ExtraProperties.PARTICIPANTS, chat.getContact().getFullId());
            }

            if (extractMessages && indexableCount > 0)
                chatMeta.set(BasicProps.HASCHILD, Boolean.TRUE.toString());

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

        String selfId = selfContact != null ? selfContact.getFullId() : "";

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

            if (m.getDateSent() != null) {
                msgMeta.set(ExtraProperties.MESSAGE_DATE, m.getDateSent());
                msgMeta.set(TikaCoreProperties.CREATED, m.getDateSent());
            }

            msgMeta.set(ExtraProperties.MESSAGE_BODY, resolveBody(m));
            msgMeta.set(ExtraProperties.USER_ACCOUNT_TYPE, SIGNAL_MESSAGE.toString());

            if (chat.isGroupChat())
                msgMeta.set(ExtraProperties.IS_GROUP_MESSAGE, "true");

            SignalContact contact = chat.getContact();
            if (m.isFromMe()) {
                msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, selfId);
                // For groups, TO is the chat title; for individual, TO is the contact
                if (chat.isGroupChat()) {
                    msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, chatTitle);
                } else if (contact != null) {
                    msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, contact.getFullId());
                }
            } else {
                if (chat.isGroupChat()) {
                    String senderName = chat.getParticipants().stream()
                            .filter(p -> p.getId() == m.getFromRecipientId())
                            .map(SignalContact::getFullId)
                            .findFirst()
                            .orElse("Unknown");
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, senderName);
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_TO, chatTitle);
                } else if (contact != null) {
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, contact.getFullId());
                    msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, selfId);
                }
            }

            extractor.parseEmbedded(new EmptyInputStream(), handler, msgMeta, false);
        }
    }

    private static String resolveBody(SignalMessage m) {
        switch (m.getMessageType()) {
            case CALL_OUTGOING: return "[Outgoing Call]";
            case CALL_INCOMING: return "[Incoming Call]";
            case CALL_MISSED:   return "[Missed Call]";
            default:
                return m.getBody() != null ? m.getBody() : "[Attachment]";
        }
    }
}
