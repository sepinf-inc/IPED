package iped.parsers.ufed;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.common.collect.ImmutableMap;

import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ConversationUtils;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

public class UFEDChatParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static Logger logger = LoggerFactory.getLogger(UFEDChatParser.class);

    public static final MediaType UFED_CHAT_MIME = MediaType.application("x-ufed-chat"); //$NON-NLS-1$
    public static final MediaType UFED_CHAT_WA_MIME = MediaType.application("x-ufed-chat-whatsapp"); //$NON-NLS-1$
    public static final MediaType UFED_CHAT_TELEGRAM_MIME = MediaType.application("x-ufed-chat-telegram"); //$NON-NLS-1$

    public static final MediaType UFED_CHAT_PREVIEW_MIME = MediaType.application("x-ufed-chat-preview");

    public static final String UFED_REPLIED_MESSAGE_ATTR = "originalMessage";

    public static final Map<String, MediaType> appToMime = ImmutableMap.of( //
            "whatsapp", MediaType.application("x-ufed-chat-preview-whatsapp"), //
            "telegram", MediaType.application("x-ufed-chat-preview-telegram"), //
            "skype", MediaType.application("x-ufed-chat-preview-skype"), //
            "facebook", MediaType.application("x-ufed-chat-preview-facebook"), //
            "instagram", MediaType.application("x-ufed-chat-preview-instagram"));

    public static final Property META_FROM_OWNER = Property
            .internalBoolean(ExtraProperties.UFED_META_PREFIX + "fromOwner"); //$NON-NLS-1$

    public static final String ATTACHED_MEDIA_MSG = "ATTACHED_MEDIA: ";

    private int minChatSplitSize = 6000000;

    private static Set<MediaType> supportedTypes = MediaType.set(UFED_CHAT_MIME, UFED_CHAT_WA_MIME,
            UFED_CHAT_TELEGRAM_MIME);

    private static final Map<String, String> chatTypeMap = ImmutableMap.of( //
            "OneOnOne", ConversationUtils.TYPE_PRIVATE, //
            "Group", ConversationUtils.TYPE_GROUP, //
            "Broadcast", ConversationUtils.TYPE_BROADCAST);

    private static final Map<Integer, List<IItemReader>> messagesMap = new ConcurrentHashMap<>();

    private boolean extractMessages = true;

    public static void ignoreSupportedChats() {
        supportedTypes = MediaType.set(UFED_CHAT_MIME);
    }

    public static MediaType getMediaType(String source) {
        if (source != null) {
            source = source.split(" ")[0].toLowerCase();
            if (appToMime.containsKey(source)) {
                return appToMime.get(source);
            }
        }
        return UFED_CHAT_PREVIEW_MIME;
    }

    public static void addMessageToBeParsed(IItemReader item) {
        messagesMap.computeIfAbsent(item.getParentId(), k -> Collections.synchronizedList(new ArrayList<>())).add(item);
    }

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    @Field
    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return supportedTypes;
    }

    @Override
    public void parse(InputStream inputStream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // process Chat
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemReader chat = context.get(IItemReader.class);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (chat == null || searcher == null)
                return;

            List<Message> messages = new ArrayList<>();
            List<IItemReader> messageItems = messagesMap.remove(chat.getId());

            if (messageItems != null) {
                for (IItemReader messageItem : messageItems) {

                    updateMessageMetadata(metadata, messageItem.getMetadata(), searcher);

                    Message message = createMessage(messageItem, searcher);
                    updateQuotedMessage(message, searcher, messages);
                    messages.add(message);
                }
            }

            Collections.sort(messages);

            String virtualId = metadata.get(ExtraProperties.UFED_META_PREFIX + "id");

            if (extractor.shouldParseEmbedded(metadata)) {
                ReportGenerator reportGenerator = new ReportGenerator(searcher);
                reportGenerator.setMinChatSplitSize(this.minChatSplitSize);
                byte[] bytes = reportGenerator.generateNextChatHtml(chat, messages);
                int frag = 0;
                int firstMsg = 0;
                MediaType previewMime = getMediaType(metadata.get(ExtraProperties.UFED_META_PREFIX + "Source"));
                int messagesCount = (int) messages.stream().filter(m -> !m.isSystemMessage()).count();
                while (bytes != null) {
                    Metadata chatMetadata = new Metadata();
                    int nextMsg = reportGenerator.getNextMsgNum();
                    List<Message> subList = messages.subList(firstMsg, nextMsg);
                    storeLinkedHashes(subList, chatMetadata);

                    firstMsg = nextMsg;
                    byte[] nextBytes = reportGenerator.generateNextChatHtml(chat, messages);

                    // copy parent metadata
                    for (String meta : metadata.names()) {
                        if (meta.startsWith(ExtraProperties.UFED_META_PREFIX))
                            for (String val : metadata.getValues(meta))
                                chatMetadata.add(meta, val);
                    }

                    String chatName = getChatName(metadata);
                    if (frag > 0 || nextBytes != null)
                        chatName += "_" + frag++; //$NON-NLS-1$

                    chatMetadata.set(TikaCoreProperties.TITLE, chatName);
                    chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, virtualId);
                    chatMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, previewMime.toString());
                    chatMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                    // Communication:Account
                    fillAccountInfo(searcher, chatMetadata);

                    // Communication:{Participants, Admins}
                    fillParticipantInfo(searcher, chatMetadata, chatMetadata, "Participants",
                            ExtraProperties.CONVERSATION_PARTICIPANTS);
                    fillParticipantInfo(searcher, chatMetadata, chatMetadata, "Admins",
                            ExtraProperties.CONVERSATION_ADMINS);

                    // Communication:IsAdmin
                    if (Boolean
                            .parseBoolean(chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "IsOwnerGroupAdmin"))) {
                        chatMetadata.set(ExtraProperties.CONVERSATION_IS_ADMIN, true);
                    }
                    chatMetadata.remove(ExtraProperties.UFED_META_PREFIX + "IsOwnerGroupAdmin");

                    // Communication:Type
                    String ufedChatType = metadata.get(ExtraProperties.UFED_META_PREFIX + "ChatType");
                    if (ufedChatType != null && chatTypeMap.containsKey(ufedChatType)) {
                        chatMetadata.set(ExtraProperties.CONVERSATION_TYPE, chatTypeMap.get(ufedChatType));
                    } else {
                        chatMetadata.set(ExtraProperties.CONVERSATION_TYPE, ConversationUtils.TYPE_UNKONWN);
                    }

                    // Communication:{ID, Name, MessagesCount}
                    chatMetadata.set(ExtraProperties.CONVERSATION_ID,
                            metadata.get(ExtraProperties.UFED_META_PREFIX + "Identifier"));
                    chatMetadata.set(ExtraProperties.CONVERSATION_NAME,
                            metadata.get(ExtraProperties.UFED_META_PREFIX + "Name"));
                    chatMetadata.set(ExtraProperties.CONVERSATION_MESSAGES_COUNT, messagesCount);

                    if (extractMessages && !subList.isEmpty()) {
                        chatMetadata.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
                    }

                    ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                    bytes = nextBytes;

                    if (extractMessages) {
                        if (messageItems != null) {
                            extractMessages(subList, virtualId, handler, extractor);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw e;

        } finally {
            xhtml.endDocument();
        }
    }

    private void updateMessageMetadata(Metadata chatMetadata, Metadata messageMetadata, IItemSearcher searcher) {

        // Communication:Direction
        if (Boolean.parseBoolean(messageMetadata.get(META_FROM_OWNER))) {
            messageMetadata.set(ExtraProperties.COMMUNICATION_DIRECTION, ConversationUtils.DIRECTION_OUTGOING);
        } else {
            messageMetadata.set(ExtraProperties.COMMUNICATION_DIRECTION, ConversationUtils.DIRECTION_INCOMING);
        }

        // Fix missing "ufed:To" metadata
        List<String> fromIds = Arrays.asList(messageMetadata.getValues(ExtraProperties.UFED_META_PREFIX + "From:ID"));
        String[] toIds = messageMetadata.getValues(ExtraProperties.UFED_META_PREFIX + "To:ID");
        if (toIds.length != 1) {
            List<String> toList;
            String toName = null;
            if (toIds.length > 0) {
                toList = Arrays.asList(toIds);
            } else {
                toList = new ArrayList<>();
                String[] partiesIds = chatMetadata.getValues(ExtraProperties.UFED_META_PREFIX + "Participants:ID");
                for (int i = 0; i < partiesIds.length; i++) {
                    String partyId = partiesIds[i];
                    if (!fromIds.contains(partyId)) {
                        toList.add(partyId);
                        toName = chatMetadata.getValues(ExtraProperties.UFED_META_PREFIX + "Participants:Name")[i];
                    }
                }
            }
            if (toList.size() == 1) {
                messageMetadata.set(ExtraProperties.UFED_META_PREFIX + "To:ID", defaultIfEmpty(toList.get(0), ""));
                messageMetadata.set(ExtraProperties.UFED_META_PREFIX + "To:Name", defaultIfEmpty(toName, ""));
            } else if (toList.size() > 1) {
                messageMetadata.set(ExtraProperties.UFED_META_PREFIX + "To:ID",
                        defaultIfEmpty(chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "Identifier"), ""));
                messageMetadata.set(ExtraProperties.UFED_META_PREFIX + "To:Name",
                        defaultIfEmpty(chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "name"), ""));
                messageMetadata.set(ExtraProperties.COMMUNICATION_IS_GROUP_MESSAGE, true);
            }

        }

        // Communication:{From, To}
        fillParticipantInfo(searcher, chatMetadata, messageMetadata, "From", ExtraProperties.COMMUNICATION_FROM);
        fillParticipantInfo(searcher, chatMetadata, messageMetadata, "To", ExtraProperties.COMMUNICATION_TO);
    }

    private IItemReader lookupAccount(IItemSearcher searcher, Metadata chatMetadata) {
        String account = chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "Account");
        if (StringUtils.isBlank(account)) {
            return null;
        }

        String source = chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "Source");
        String query = BasicProps.CONTENTTYPE + ":\"" + MediaTypes.UFED_USER_ACCOUNT_MIME.toString() + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Source") + ":\"" + source + "\"" //
                + " && (" + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "UserID") + ":\"" + account + "\"" //
                + " || " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "PhoneNumber") + ":\"" + account
                + "\"" //
                + " || " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Username") + ":\"" + account + "\"" //
                + ")";

        List<IItemReader> results = searcher.search(query);
        if (!results.isEmpty()) {
            if (results.size() > 1) {
                logger.warn("Found more than one account for [{}]: {}", account, results);
            }
            return results.get(0);
        }

        return null;
    }

    private void fillAccountInfo(IItemSearcher searcher, Metadata chatMetadata) {
        String name, id, phone = null, username = null;
        IItemReader account = lookupAccount(searcher, chatMetadata);
        if (account != null) {
            name = account.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "name");
            id = account.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "UserID");
            phone = account.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "PhoneNumber");
            username = account.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Username");
            if (phone != null) {
                phone = StringUtils.substringBefore(phone, " ");
            }

            chatMetadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.ID + ":" + account.getId());
        } else {
            id = chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "Owner:ID");
            name = chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "Owner:Name");
        }

        chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT,
                ConversationUtils.buidPartyString(name, id, phone, username));
        if (name != null)
            chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_NAME, name);
        if (id != null)
            chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_ID, id);
        if (phone != null)
            chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_PHONE, phone);
        if (username != null)
            chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_USERNAME,
                    username);

        chatMetadata.remove(ExtraProperties.UFED_META_PREFIX + "Owner:ID");
        chatMetadata.remove(ExtraProperties.UFED_META_PREFIX + "Owner:Name");
    }

    private IItemReader lookupParticipant(IItemSearcher searcher, Metadata chatMetadata, String userID) {
        if (StringUtils.isBlank(userID)) {
            return null;
        }

        String account = chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "Account");
        String source = chatMetadata.get(ExtraProperties.UFED_META_PREFIX + "Source");
        String query = BasicProps.CONTENTTYPE + ":\"" + MediaTypes.UFED_CONTACT_MIME.toString() + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Account") + ":\"" + account + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Source") + ":\"" + source + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Type") + ":ChatParticipant" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "UserID") + ":\"" + userID + "\"";

        List<IItemReader> results = searcher.search(query);
        if (!results.isEmpty()) {
            if (results.size() > 1) {
                logger.warn("Found more than one participant for [{}]: {}", account, results);
            }
            return results.get(0);
        }

        return null;
    }

    private void fillParticipantInfo(IItemSearcher searcher, Metadata chatMetadata, Metadata targetMetadata,
            String ufedProperty, String conversationProperty) {
        String[] partyIDs = targetMetadata.getValues(ExtraProperties.UFED_META_PREFIX + ufedProperty + ":ID");
        String[] partyNames = targetMetadata.getValues(ExtraProperties.UFED_META_PREFIX + ufedProperty + ":Name");
        for (int i = 0; i < partyIDs.length; i++) {
            String partyID = partyIDs[i];
            String name = null, phone = null, username = null;

            IItemReader participant = lookupParticipant(searcher, chatMetadata, partyID);
            if (participant != null) {
                name = participant.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "name");
                String[] ids = participant.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "UserID");
                phone = participant.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "PhoneNumber");
                if (phone != null) {
                    phone = StringUtils.substringBefore(phone, " ");
                }
                if (ids.length >= 2) {
                    if (ids[0].equals(partyID)) {
                        username = ids[1];
                    } else {
                        username = ids[0];
                    }
                }
                targetMetadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.ID + ":" + participant.getId());
            } else {
                name = partyNames[i];
                if (name.isEmpty())
                    name = null;
            }

            targetMetadata.add(conversationProperty, ConversationUtils.buidPartyString(name, partyID, phone, username));
            if (!partyID.isEmpty())
                targetMetadata.add(conversationProperty + ExtraProperties.CONVERSATION_SUFFIX_ID, partyID);
            if (name != null)
                targetMetadata.add(conversationProperty + ExtraProperties.CONVERSATION_SUFFIX_NAME, name);
            if (phone != null)
                targetMetadata.add(conversationProperty + ExtraProperties.CONVERSATION_SUFFIX_PHONE, phone);
            if (username != null)
                targetMetadata.add(conversationProperty + ExtraProperties.CONVERSATION_SUFFIX_USERNAME, username);
        }
        targetMetadata.remove(ExtraProperties.UFED_META_PREFIX + ufedProperty + ":ID");
        targetMetadata.remove(ExtraProperties.UFED_META_PREFIX + ufedProperty + ":Name");
    }

    private void extractMessages(List<Message> subList, String virtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        for (Message message : subList) {

            IItemReader messageItem = message.getItem();

            Metadata messageMetaData = messageItem.getMetadata();
            messageMetaData.set(TikaCoreProperties.TITLE, messageItem.getName());
            messageMetaData.set(BasicProps.ID, Integer.toString(messageItem.getId()));
            messageMetaData.set(StandardParser.INDEXER_CONTENT_TYPE, messageItem.getMediaType().toString());
            messageMetaData.set(ExtraProperties.PARENT_VIRTUAL_ID, virtualId);
            messageMetaData.set(ExtraProperties.PARENT_VIEW_POSITION, Integer.toString(messageItem.getId()));
            messageMetaData.set(BasicProps.LENGTH, "");
            if (!message.getAttachments().isEmpty()) {
                messageMetaData.set(ExtraProperties.MESSAGE_ATTACHMENT_COUNT, message.getAttachments().size());
            }

            if (messageItem.isDeleted()) {
                messageMetaData.set(ExtraProperties.DELETED, Boolean.toString(true));
            }

            extractor.parseEmbedded(new EmptyInputStream(), handler, messageMetaData, false);
        }
    }

    private Message createMessage(IItemReader messageItem, IItemSearcher searcher) {

        Message message = new Message(messageItem);

        String attachQuery = BasicProps.PARENTID + ":" + message.getId() + " && " //
                + BasicProps.CONTENTTYPE + ":\"" + MediaTypes.UFED_MESSAGE_ATTACH_MIME.toString() + "\"";
        for (IItemReader attachItem : searcher.searchIterable(attachQuery)) {
            Attachment attach = message.addAttachment(attachItem);

            String attachFileQuery = searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "id") + ":\""
                    + attach.getFileId() + "\"";
            List<IItemReader> fileItems = searcher.search(attachFileQuery);
            if (!fileItems.isEmpty()) {
                if (fileItems.size() >= 2) {
                    logger.warn("Found more than 2 files for attachment: {}", fileItems);
                }
                attach.setFile(fileItems.get(0));
            }
        }

        Collections.sort(message.getAttachments());

        return message;
    }

    private void updateQuotedMessage(Message message, IItemSearcher searcher, List<Message> messageUntilNow) {
        if (!message.isQuoted()) {
            return;
        }

        // loookup in previous messages
        String originalMsgId = message.getOriginalMessageID();
        Message quotedMessage = null;
        if (originalMsgId != null && messageUntilNow != null) {
            for (Message prevMessage : messageUntilNow) {
                if (originalMsgId.equals(prevMessage.getIdentifier())) {
                    quotedMessage = prevMessage;
                    break;
                }
            }
        }

        // get the replied set in report.xml
        if (quotedMessage == null) {
            IItemReader repliedMessage = (IItemReader) message.getItem().getExtraAttribute(UFED_REPLIED_MESSAGE_ATTR);
            if (repliedMessage != null) {
                quotedMessage = createMessage(repliedMessage, searcher);
            }
        }

        message.getItem().getExtraAttributeMap().remove(UFED_REPLIED_MESSAGE_ATTR);

        message.setMessageQuote(quotedMessage);
    }

    public static String getChatName(Metadata metadata) {

        String account = metadata.get(ExtraProperties.CONVERSATION_ACCOUNT);
        String source = metadata.get(ExtraProperties.UFED_META_PREFIX + "Source");
        String chatType = metadata.get(ExtraProperties.UFED_META_PREFIX + "ChatType");
        String name = metadata.get(ExtraProperties.UFED_META_PREFIX + "Name");
        String id = metadata.get(ExtraProperties.UFED_META_PREFIX + "Identifier");
        String[] participants = metadata.getValues(ExtraProperties.CONVERSATION_PARTICIPANTS);

        StringBuilder sb = new StringBuilder();
        sb.append(source).append(' ');

        if (!"Unknown".equalsIgnoreCase(chatType)) {
            if ("OneOnOne".equalsIgnoreCase(chatType)) {
                sb.append("Chat");
            } else if ("Telegram".equalsIgnoreCase(source) && "Broadcast".equalsIgnoreCase(chatType)) {
                sb.append("Channel");
            } else if (chatTypeMap.containsKey(chatType)) {
                sb.append(chatTypeMap.get(chatType));
            } else {
                sb.append(chatType);
            }
            sb.append(' ');
        }
        sb.append("- ");

        if (name != null) {
            sb.append(name);
            if (id != null) {
                sb.append(" (ID:").append(id).append(")");
            }
        } else if (participants.length == 2) {
            if (participants[0].equals(account)) {
                sb.append(participants[1]);
            } else if (participants[1].equals(account)) {
                sb.append(participants[0]);
            } else {
                sb.append(participants[0]).append('_').append(participants[1]);
            }
        } else if (participants.length == 1) {
            sb.append(participants[0]);
        } else {
            sb.append("ID:").append(id);
        }

        return sb.toString();
    }

    private void storeLinkedHashes(List<Message> messages, Metadata metadata) {
        for (Message message : messages) {

            for (Attachment attachment : message.getAttachments()) {

                if (attachment.getFile() != null) {
                    String hash = attachment.getFile().getHash();

                    if (hash != null) {
                        metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + hash);
                        if (message.isFromMe())
                            metadata.add(ExtraProperties.SHARED_HASHES, hash);
                    }
                }
            }
        }
    }
}
