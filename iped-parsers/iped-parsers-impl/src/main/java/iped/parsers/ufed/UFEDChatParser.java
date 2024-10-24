package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;
import static iped.parsers.ufed.UfedUtils.readUfedMetadataArray;
import static iped.parsers.ufed.UfedUtils.removeUfedMetadata;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import iped.parsers.util.Messages;
import iped.parsers.whatsapp.WAContact;
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

    public static final MediaType UFED_CHAT_MIME = MediaType.application("x-ufed-chat");
    public static final MediaType UFED_CHAT_WA_MIME = MediaType.application("x-ufed-chat-whatsapp");
    public static final MediaType UFED_CHAT_TELEGRAM_MIME = MediaType.application("x-ufed-chat-telegram");

    public static final MediaType UFED_CHAT_PREVIEW_MIME = MediaType.application("x-ufed-chat-preview");

    public static final String UFED_REPLIED_MESSAGE_ATTR = "ufedOriginalMessage";

    public static final Map<String, MediaType> appToMime = ImmutableMap.of( //
            "whatsapp", MediaType.application("x-ufed-chat-preview-whatsapp"), //
            "telegram", MediaType.application("x-ufed-chat-preview-telegram"), //
            "skype", MediaType.application("x-ufed-chat-preview-skype"), //
            "facebook", MediaType.application("x-ufed-chat-preview-facebook"), //
            "instagram", MediaType.application("x-ufed-chat-preview-instagram"));

    public static final Property META_FROM_OWNER = Property
            .internalBoolean(ExtraProperties.UFED_META_PREFIX + "fromOwner");

    public static final String ATTACHED_MEDIA_MSG = "ATTACHED_MEDIA: ";

    protected static final String WHATSAPP = "WhatsApp";
    protected static final String WHATSAPP_BUSINESS = "WhatsApp Business";
    protected static final String TELEGRAM = "Telegram";

    // Types used by UFED
    protected static final String CHATTYPE_ONEONONE = "OneOnOne";
    protected static final String CHATTYPE_GROUP = "Group";
    protected static final String CHATTYPE_BROADCAST = "Broadcast";
    protected static final String CHATTYPE_UNKNOWN = "Unknown";

    // Strings used in item names and titles
    protected static final String CHATTYPE_GROUP_TITLE = Messages.getString("UFEDChatParser.Group");
    protected static final String CHATTYPE_BROADCAST_TITLE = Messages.getString("UFEDChatParser.Broadcast");
    protected static final String CHATTYPE_STATUS_TITLE = Messages.getString("UFEDChatParser.Status");
    protected static final String CHATTYPE_UNKNOWN_TITLE = Messages.getString("UFEDChatParser.Unknown");

    private boolean extractMessages = true;
    private boolean extractActivityLogs = true;
    private boolean extractAttachments = true;
    private boolean extractSharedContacts = true;
    private boolean ignoreEmptyChats = false;
    private int minChatSplitSize = 6000000;

    private Map<String, IItemReader> participantsCache = new HashMap<>();

    private static Set<MediaType> supportedTypes = MediaType.set(UFED_CHAT_MIME, UFED_CHAT_WA_MIME,
            UFED_CHAT_TELEGRAM_MIME);

    private static final Map<String, String> chatTypeMap = ImmutableMap.of( //
            "OneOnOne", ConversationUtils.TYPE_PRIVATE, //
            "Group", ConversationUtils.TYPE_GROUP, //
            "Broadcast", ConversationUtils.TYPE_BROADCAST);

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

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    @Field
    public void setExtractActivityLogs(boolean extractActivityLogs) {
        this.extractActivityLogs = extractActivityLogs;
    }

    @Field
    public void setExtractAttachments(boolean extractAttachments) {
        this.extractAttachments = extractAttachments;
    }

    @Field
    public void setExtractSharedContacts(boolean extractSharedContacts) {
        this.extractSharedContacts = extractSharedContacts;
    }

    @Field
    public void setIgnoreEmptyChats(boolean ignoreEmptyChats) {
        this.ignoreEmptyChats = ignoreEmptyChats;
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
    public void parse(InputStream inputStream, ContentHandler handler, Metadata chatMeta, ParseContext context)
            throws IOException, SAXException, TikaException {

        // process Chat
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, chatMeta);
        xhtml.startDocument();
        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemReader chatItem = context.get(IItemReader.class);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (chatItem == null || searcher == null)
                return;

            updateChatMetadata(chatMeta, searcher);
            Chat chat = createChat(chatItem, searcher);

            List<Message> messages = new ArrayList<>();
            List<IItemReader> chatChildren = chatItem.getChildren();
            if (chatChildren != null) {
                for (IItemReader chatChild : chatChildren) {

                    if (chatChild.getMediaType().equals(MediaTypes.UFED_MESSAGE_MIME)) {

                        updateMessageMetadata(chatChild.getMetadata(), chatMeta, searcher);

                        Message message = createMessage(chatChild, chat, searcher);
                        updateQuotedMessage(message, chat, searcher, messages);
                        messages.add(message);
                    } else {
                        logger.error("Unknown chat child: {}", chatChild);
                    }

                    // reset timeout counter
                    char[] nameChars = (chatChild.getName() + "\n").toCharArray();
                    handler.characters(nameChars, 0, nameChars.length);
                }
            }

            int messagesCount = (int) messages.stream().filter(m -> !m.isSystemMessage()).count();
            if (messagesCount == 0 && ignoreEmptyChats) {
                return;
            }
            chatMeta.set(ExtraProperties.CONVERSATION_MESSAGES_COUNT, messagesCount);

            Collections.sort(messages);

            String virtualId = chat.getId();
            String chatPrefix = getChatName(chat.getItem());

            if (extractor.shouldParseEmbedded(chatMeta)) {
                ReportGenerator reportGenerator = new ReportGenerator(minChatSplitSize);
                byte[] bytes = reportGenerator.generateNextChatHtml(chat, messages);
                int frag = 0;
                int firstMsg = 0;
                MediaType previewMime = getMediaType(readUfedMetadata(chatMeta, "Source"));
                while (bytes != null) {
                    Metadata chatPreviewMeta = new Metadata();
                    int nextMsg = reportGenerator.getNextMsgNum();
                    List<Message> subList = messages.subList(firstMsg, nextMsg);
                    storeLinkedHashes(subList, chatPreviewMeta);

                    firstMsg = nextMsg;
                    byte[] nextBytes = reportGenerator.generateNextChatHtml(chat, messages);

                    // copy parent metadata
                    for (String name : chatMeta.names()) {
                        if (name.startsWith(ExtraProperties.UFED_META_PREFIX) || name.startsWith(ExtraProperties.CONVERSATION_PREFIX))
                            for (String val : chatMeta.getValues(name))
                                chatPreviewMeta.add(name, val);
                    }

                    String chatName = chatPrefix;
                    if (frag > 0 || nextBytes != null)
                        chatName += "_" + frag++;

                    chatPreviewMeta.set(TikaCoreProperties.TITLE, chatName);
                    chatPreviewMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, virtualId);
                    chatPreviewMeta.set(StandardParser.INDEXER_CONTENT_TYPE, previewMime.toString());
                    chatPreviewMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                    if (extractMessages && !subList.isEmpty()) {
                        chatPreviewMeta.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
                    }

                    ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(chatStream, handler, chatPreviewMeta, false);
                    bytes = nextBytes;

                    if (extractMessages) {
                        extractMessages(subList, virtualId, handler, extractor);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw e;

        } finally {
            xhtml.endDocument();
            participantsCache.clear();
        }
    }

    private void updateChatMetadata(Metadata chatMetadata, IItemSearcher searcher) {

        // Communication:Account
        fillAccountInfo(searcher, chatMetadata);

        // Communication:{Participants, Admins}
        fillParticipantInfo(searcher, chatMetadata, chatMetadata, "Participants", ExtraProperties.CONVERSATION_PARTICIPANTS);
        fillParticipantInfo(searcher, chatMetadata, chatMetadata, "Admins", ExtraProperties.CONVERSATION_ADMINS);

        // Communication:IsAdmin
        if (Boolean.parseBoolean(readUfedMetadata(chatMetadata, "IsOwnerGroupAdmin"))) {
            chatMetadata.set(ExtraProperties.CONVERSATION_IS_ADMIN, true);
        }
        removeUfedMetadata(chatMetadata, "IsOwnerGroupAdmin");

        // Communication:Type
        String ufedChatType = readUfedMetadata(chatMetadata, "ChatType");
        if (ufedChatType != null && chatTypeMap.containsKey(ufedChatType)) {
            chatMetadata.set(ExtraProperties.CONVERSATION_TYPE, chatTypeMap.get(ufedChatType));
        } else {
            chatMetadata.set(ExtraProperties.CONVERSATION_TYPE, ConversationUtils.TYPE_UNKONWN);
        }

        // Communication:{ID, Name, MessagesCount}
        chatMetadata.set(ExtraProperties.CONVERSATION_ID,                readUfedMetadata(chatMetadata,  "Identifier"));
        chatMetadata.set(ExtraProperties.CONVERSATION_NAME,                readUfedMetadata(chatMetadata,  "Name"));
    }

    private void updateMessageMetadata(Metadata messageMeta, Metadata chatMeta, IItemSearcher searcher) {

        // Communication:Direction
        if (Boolean.parseBoolean(messageMeta.get(META_FROM_OWNER))) {
            messageMeta.set(ExtraProperties.COMMUNICATION_DIRECTION, ConversationUtils.DIRECTION_OUTGOING);
        } else {
            messageMeta.set(ExtraProperties.COMMUNICATION_DIRECTION, ConversationUtils.DIRECTION_INCOMING);
        }

        // Fix missing "ufed:To" metadata
        List<String> fromIds = readUfedMetadataArray(messageMeta, "From:ID");
        List<String> toIds = readUfedMetadataArray(messageMeta, "To:ID");
        if (toIds.size() != 1) {
            List<String> toList;
            String toName = null;
            if (toIds.size() > 0) {
                toList = toIds;
            } else {
                toList = new ArrayList<>();
                List<String> partiesIds = readUfedMetadataArray( chatMeta, "Participants:ID");
                for (int i = 0; i < partiesIds.size(); i++) {
                    String partyId = partiesIds.get(i);
                    if (!fromIds.contains(partyId)) {
                        toList.add(partyId);
                        toName = readUfedMetadataArray(chatMeta, "Participants:Name").get(i);
                    }
                }
            }
            if (toList.size() == 1) {
                messageMeta.set(ExtraProperties.UFED_META_PREFIX + "To:ID", defaultIfEmpty(toList.get(0), ""));
                messageMeta.set(ExtraProperties.UFED_META_PREFIX + "To:Name", defaultIfEmpty(toName, ""));
            } else if (toList.size() > 1) {
                messageMeta.set(ExtraProperties.UFED_META_PREFIX + "To:ID", defaultIfEmpty(readUfedMetadata(chatMeta, "Identifier"), ""));
                messageMeta.set(ExtraProperties.UFED_META_PREFIX + "To:Name", defaultIfEmpty(readUfedMetadata(chatMeta, "name"), ""));
                messageMeta.set(ExtraProperties.COMMUNICATION_IS_GROUP_MESSAGE, true);
            }

        }

        // Communication:{From, To}
        fillParticipantInfo(searcher, chatMeta, messageMeta, "From", ExtraProperties.COMMUNICATION_FROM);
        fillParticipantInfo(searcher, chatMeta, messageMeta, "To", ExtraProperties.COMMUNICATION_TO);
    }

    private IItemReader lookupAccount(IItemSearcher searcher, Metadata chatMetadata) {
        String account = readUfedMetadata(chatMetadata, "Account");
        if (StringUtils.isBlank(account)) {
            return null;
        }

        String source = readUfedMetadata(chatMetadata, "Source");
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
        String source = readUfedMetadata(chatMetadata, "Source");
        IItemReader account = lookupAccount(searcher, chatMetadata);
        if (account != null) {
            name = readUfedMetadata(account, "name");
            id = readUfedMetadata(account, "UserID");
            phone = readUfedMetadata(account, "PhoneNumber");
            username = readUfedMetadata(account, "Username");
            if (phone != null) {
                phone = StringUtils.substringBefore(phone, " ");
            }

            chatMetadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.ID + ":" + account.getId());
        } else {
            id = readUfedMetadata(chatMetadata, "Owner:ID");
            name = readUfedMetadata(chatMetadata, "Owner:Name");
        }

        chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT, ConversationUtils.buidPartyString(name, id, phone, username, source));
        if (name != null)
            chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_NAME, name);
        if (id != null)
            chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_ID, id);
        if (phone != null)
            chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_PHONE, phone);
        if (username != null)
            chatMetadata.add(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_USERNAME, username);

        removeUfedMetadata(chatMetadata, "Owner:ID");
        removeUfedMetadata(chatMetadata, "Owner:Name");
    }

    private IItemReader lookupParticipant(IItemSearcher searcher, Metadata chatMetadata, String userID) {
        if (StringUtils.isBlank(userID)) {
            return null;
        }

        if (participantsCache.containsKey(userID)) {
            return participantsCache.get(userID);
        }

        String account = readUfedMetadata(chatMetadata, "Account");
        String source = readUfedMetadata(chatMetadata, "Source");
        String query = BasicProps.CONTENTTYPE + ":\"" + MediaTypes.UFED_CONTACT_MIME.toString() + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Account") + ":\"" + account + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Source") + ":\"" + source + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Type") + ":ChatParticipant" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "UserID") + ":\"" + userID + "\"";

        List<IItemReader> results = searcher.search(query);
        IItemReader result = null;
        if (!results.isEmpty()) {
            if (results.size() > 1) {
                logger.warn("Found more than one participant for [{}]: {}", account, results);
            }
            result = results.get(0);
        }

        participantsCache.put(userID, result);
        return result;
    }

    private void fillParticipantInfo(IItemSearcher searcher, Metadata chatMetadata, Metadata targetMetadata,
            String ufedProperty, String conversationProperty) {
        String source = readUfedMetadata(chatMetadata, "Source");
        List<String> partyIDs = readUfedMetadataArray(targetMetadata, ufedProperty + ":ID");
        List<String> partyNames = readUfedMetadataArray(targetMetadata, ufedProperty + ":Name");
        for (int i = 0; i < partyIDs.size(); i++) {
            String partyID = partyIDs.get(i);
            String name = null, phone = null, username = null;

            IItemReader participant = lookupParticipant(searcher, chatMetadata, partyID);
            if (participant != null) {
                name = readUfedMetadata(participant, "name");
                List<String> ids = readUfedMetadataArray(participant, "UserID");
                phone = readUfedMetadata(participant, "PhoneNumber");
                if (phone != null) {
                    phone = StringUtils.substringBefore(phone, " ");
                }
                if (ids.size() >= 2) {
                    if (ids.get(0).equals(partyID)) {
                        username = ids.get(1);
                    } else {
                        username = ids.get(0);
                    }
                }
                targetMetadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.ID + ":" + participant.getId());
            } else {
                name = partyNames.get(i);
                if (name.isEmpty())
                    name = null;
            }

            targetMetadata.add(conversationProperty, ConversationUtils.buidPartyString(name, partyID, phone, username, source));
            if (!partyID.isEmpty())
                targetMetadata.add(conversationProperty + ExtraProperties.CONVERSATION_SUFFIX_ID, partyID);
            if (name != null)
                targetMetadata.add(conversationProperty + ExtraProperties.CONVERSATION_SUFFIX_NAME, name);
            if (phone != null)
                targetMetadata.add(conversationProperty + ExtraProperties.CONVERSATION_SUFFIX_PHONE, phone);
            if (username != null)
                targetMetadata.add(conversationProperty + ExtraProperties.CONVERSATION_SUFFIX_USERNAME, username);
        }

        removeUfedMetadata(targetMetadata, ufedProperty + ":ID");
        removeUfedMetadata(targetMetadata, ufedProperty + ":Name");
    }

    private void extractMessages(List<Message> subList, String chatVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        for (Message message : subList) {

            IItemReader messageItem = message.getItem();
            String messageVirtualId = message.getUfedId();

            Metadata messageMetaData = messageItem.getMetadata();
            messageMetaData.set(TikaCoreProperties.TITLE, messageItem.getName());
            messageMetaData.set(ExtraProperties.ITEM_VIRTUAL_ID, messageVirtualId);
            messageMetaData.set(StandardParser.INDEXER_CONTENT_TYPE, messageItem.getMediaType().toString());
            messageMetaData.set(ExtraProperties.PARENT_VIRTUAL_ID, chatVirtualId);
            messageMetaData.set(ExtraProperties.PARENT_VIEW_POSITION, message.getSourceIndex());
            messageMetaData.set(BasicProps.LENGTH, "");
            if (!message.getAttachments().isEmpty()) {
                messageMetaData.set(ExtraProperties.MESSAGE_ATTACHMENT_COUNT, message.getAttachments().size());
            }

            if (messageItem.isDeleted()) {
                messageMetaData.set(ExtraProperties.DELETED, Boolean.toString(true));
            }

            extractor.parseEmbedded(new EmptyInputStream(), handler, messageMetaData, false);

            if (extractActivityLogs) {
                extractActivityLog(message, messageVirtualId, handler, extractor);
            }
            if (extractAttachments) {
                extractAttachments(message, messageVirtualId, handler, extractor);
            }
            if (extractSharedContacts) {
                extractShareContacts(message, messageVirtualId, handler, extractor);
            }
        }
    }

    private void extractActivityLog(Message message, String messageVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        for (MessageChatActivity activity : message.getActivityLog()) {
            IItemReader activityItem = activity.getItem();
            Metadata activityMeta = activityItem.getMetadata();
            activityMeta.set(TikaCoreProperties.TITLE, activityItem.getName());
            activityMeta.set(StandardParser.INDEXER_CONTENT_TYPE, activityItem.getMediaType().toString());
            activityMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            activityMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, activityMeta, false);
        }
    }

    private void extractAttachments(Message message, String messageVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        for (MessageAttachment attach : message.getAttachments()) {
            IItemReader attachItem = attach.getItem();
            Metadata attachMeta = attachItem.getMetadata();
            attachMeta.set(TikaCoreProperties.TITLE, attachItem.getName());
            attachMeta.set(StandardParser.INDEXER_CONTENT_TYPE, attachItem.getMediaType().toString());
            attachMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            attachMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, attachMeta, false);
        }
    }

    private void extractShareContacts(Message message, String messageVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        for (MessageContact shareContact : message.getSharedContacts()) {
            IItemReader shareContactItem = shareContact.getItem();
            Metadata shareContactMeta = shareContactItem.getMetadata();
            shareContactMeta.set(TikaCoreProperties.TITLE, shareContact.getType() + shareContactItem.getName());
            shareContactMeta.set(StandardParser.INDEXER_CONTENT_TYPE, shareContactItem.getMediaType().toString());
            shareContactMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            shareContactMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, shareContactMeta, false);
        }
    }

    private Chat createChat(IItemReader chatItem, IItemSearcher searcher) {

        Chat chat = new Chat(chatItem);

        // look for chat photo thumb
        String contactPhotoId = readUfedMetadata(chatItem, "contactphoto_id");
        if (contactPhotoId != null) {
            String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + contactPhotoId + "\"";
            List<IItemReader> result = searcher.search(query);
            if (!result.isEmpty()) {
                IItemReader contactPhoto = result.get(0);
                chat.setContactPhotoThumb(contactPhoto.getThumb());
                removeUfedMetadata(chatItem, "contactphoto_extracted_path");
                removeUfedMetadata(chatItem, "contactphoto_id");
            }
        }

        return chat;
    }

    private Message createMessage(IItemReader messageItem, Chat chat, IItemSearcher searcher) {

        Message message = new Message(messageItem, chat);
        handleMessageLocation(message, searcher);

        List<IItemReader> msgChildren = messageItem.getChildren();
        if (msgChildren != null) {
            for (IItemReader msgChild : msgChildren) {
                if (msgChild.getMediaType().equals(MediaTypes.UFED_CHATACTIVITY_MIME)) {
                    handleActivityLog(message, msgChild, searcher);
                } else if (msgChild.getMediaType().equals(MediaTypes.UFED_ATTACH_MIME)) {
                    handleMessageAttachment(message, msgChild, searcher);
                } else if (msgChild.getMediaType().equals(MediaTypes.UFED_CONTACT_MIME)) {
                    handleMessageSharedContact(message, msgChild, searcher);
                } else {
                    logger.error("Unknown message child: {}", msgChild);
                }
            }
        }

        Collections.sort(message.getAttachments());
        Collections.sort(message.getSharedContacts());

        return message;
    }

    private void handleActivityLog(Message message, IItemReader activityItem, IItemSearcher searcher) {
        message.addActivityLog(activityItem);
    }

    private void handleMessageAttachment(Message message, IItemReader attachmentItem, IItemSearcher searcher) {
        MessageAttachment attach = message.addAttachment(attachmentItem);

        // attachment "ufed:file_id" metadata contains the "ufed:id" metadata of the file
        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + attach.getFileId() + "\"";
        List<IItemReader> fileItems = searcher.search(query);
        if (!fileItems.isEmpty()) {
            if (fileItems.size() > 1) {
                logger.warn("Found more than 1 file for attachment: {}", fileItems);
            }
            attach.setReferencedFile(fileItems.get(0));
        }
    }

    private void handleMessageSharedContact(Message message, IItemReader sharedContactItem, IItemSearcher searcher) {

        MessageContact contact = message.addSharedContact(sharedContactItem);

        // shared contact and indexed contact have the same "ufed:id" metadata
        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + contact.getUfedId() + "\"";
        List<IItemReader> contactItems = searcher.search(query);
        if (!contactItems.isEmpty()) {
            if (contactItems.size() > 1) {
                logger.warn("Found more than 1 contact for shared contact: {}", contactItems);
            }
            contact.setReferencedContact(contactItems.get(0));
        }
    }

    private void handleMessageLocation(Message message, IItemSearcher searcher) {

        if (!StringUtils.isBlank(message.getCoordinateId())) {

            // the message and location shares the same "ufed:coordinate_id" that was added when merging in UfedXmlReader
            String query = searcher.escapeQuery(ExtraProperties.UFED_COORDINATE_ID) + ":\"" + message.getCoordinateId() + "\"";
            List<IItemReader> locationItems = searcher.search(query);
            if (!locationItems.isEmpty()) {
                if (locationItems.size() > 1) {
                    logger.warn("Found more than 1 location for coordinate: {}", locationItems);
                }
                message.setReferencedLocation(locationItems.get(0));
            }
        }

        if (message.isLocationSharing() && message.getReferencedLocation() == null) {

            // the location item is reference by jumptargets
            String[] jumpTargets = message.getItem().getMetadata().getValues(ExtraProperties.UFED_JUMP_TARGETS);
            if (jumpTargets.length > 0) {
                String query = BasicProps.CONTENTTYPE + ":\"application/x-ufed-location\" && " //
                        + searcher.escapeQuery(ExtraProperties.UFED_ID) + ":(\"" + StringUtils.join(jumpTargets, "\" \"") + "\")";
                List<IItemReader> locationItems = searcher.search(query);
                if (!locationItems.isEmpty()) {
                    if (locationItems.size() > 1) {
                        logger.warn("Found more than 1 location for jumptargets: {}", locationItems);
                    }
                    message.setReferencedLocation(locationItems.get(0));
                }
            }
        }
    }

    private void updateQuotedMessage(Message message, Chat chat, IItemSearcher searcher, List<Message> messagesSoFar) {
        if (!message.isQuoted()) {
            return;
        }

        // loookup in previous messages
        String originalMsgId = message.getOriginalMessageID();
        Message quotedMessage = null;
        if (originalMsgId != null && messagesSoFar != null) {
            for (Message prevMessage : messagesSoFar) {
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
                quotedMessage = createMessage(repliedMessage, chat, searcher);
            }
        }

        message.getItem().getExtraAttributeMap().remove(UFED_REPLIED_MESSAGE_ATTR);

        message.setMessageQuote(quotedMessage);
    }

    public static String getChatName(IItemReader item) {
        String name = "Chat";
        String source = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Source");
        String account = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Account");
        String phoneOwner = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "phoneOwner");
        String idProperty = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "id");
        String nameProperty = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name");
        String chatType = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "ChatType");
        String[] parties = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants");

        if (source != null) {
            name += "_" + source;
        }

        if (account != null) {
            name += "_" + clean(account);
        } else if (phoneOwner != null) {
            name += "_" + clean(phoneOwner);
        }

        if (chatType != null) {
            if (chatType.equals(CHATTYPE_ONEONONE)) {
                if (parties != null) {
                    name += "_" + clean(
                            ((parties.length > 1) && (parties[0].equals(phoneOwner)) ? parties[1] : parties[0]));
                } else {
                    name += "_" + idProperty;
                }

            } else if (chatType.equals(CHATTYPE_GROUP)) {
                name += "_" + CHATTYPE_GROUP_TITLE + "_" + (nameProperty != null ? nameProperty : idProperty);

            } else if (chatType.equals(CHATTYPE_BROADCAST)) {
                if (parties != null) {
                    if ((parties.length == 1) && ((source != null) && (source.equals(WHATSAPP)
                            || source.equals(WHATSAPP_BUSINESS) || source.equals(TELEGRAM)))) {
                        // "Status" chat type (known from behaviour)
                        // NOTE: Apps with this behaviour should be added to this if condition
                        name += "_" + CHATTYPE_STATUS_TITLE + "_" + clean(parties[0]);
                    } else {
                        name += "_" + CHATTYPE_BROADCAST_TITLE + "_"
                                + (nameProperty != null ? nameProperty : idProperty);
                    }
                } else {
                    name += "_" + CHATTYPE_BROADCAST_TITLE + "_" + (nameProperty != null ? nameProperty : idProperty);
                }

            } else if (chatType.equals(CHATTYPE_UNKNOWN)) {
                if ((source != null)
                        && (source.equals(WHATSAPP) || source.equals(WHATSAPP_BUSINESS) || source.equals(TELEGRAM))) {
                    // "Unknown" chat type regarding apps for which there are specific chat types
                    // NOTE: Apps with similar behavior should be added to this if condition
                    name += "_" + CHATTYPE_UNKNOWN_TITLE + "_" + idProperty;

                } else {
                    // "Unknown" chat type regarding apps for which there aren't specific chat types
                    // Communication type is derived from the number of participants
                    if ((parties != null) && (parties.length > 0)) {
                        if (parties.length > 2) {
                            name += "_" + CHATTYPE_GROUP_TITLE + "_" + idProperty;
                        } else {
                            name += "_" + clean(
                                    (parties.length > 1) && (parties[0].equals(phoneOwner)) ? parties[1] : parties[0]);
                        }
                    } else {
                        name += "_" + CHATTYPE_UNKNOWN_TITLE + "_" + idProperty;
                    }
                }

            } else {
                name += "_" + chatType + "_" + idProperty;
            }

        } else {
            name += "_" + idProperty;
        }

        return name;
    }

    private static String clean(String s) {
        if (s != null) {
            s = s.trim();
            if (s.endsWith(")")) {
                int p = s.indexOf("(");
                if (p > 0) {
                    int cnt = 0;
                    for (int i = p + 1; i < s.length() - 1; i++) {
                        if (Character.isDigit(s.charAt(i))) {
                            cnt++;
                        }
                    }
                    if (cnt >= 5) {
                        s = s.substring(0, p);
                    }
                }
            }
            s = StringUtils.remove(s, WAContact.waSuffix);
        }
        return s;
    }
    
    public static String getChatName2(Chat chat) {

        Metadata chatMeta = chat.getItem().getMetadata();
        String accountId = chatMeta.get(ExtraProperties.CONVERSATION_ACCOUNT + ExtraProperties.CONVERSATION_SUFFIX_ID);
        String source = readUfedMetadata(chatMeta, "Source");
        String chatType = readUfedMetadata(chatMeta, "ChatType");
        String name = readUfedMetadata(chatMeta, "Name");
        String id = readUfedMetadata(chatMeta, "Identifier");
        String ufedId = readUfedMetadata(chatMeta, "id");
        String[] participants = chatMeta.getValues(ExtraProperties.CONVERSATION_PARTICIPANTS);
        String[] participantsIds = chatMeta.getValues(ExtraProperties.CONVERSATION_PARTICIPANTS + ExtraProperties.CONVERSATION_SUFFIX_ID);

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
        } else if (participantsIds.length == 2) {
            if (participantsIds[0].equals(accountId)) {
                sb.append(participants[1]);
            } else if (participantsIds[1].equals(accountId)) {
                sb.append(participants[0]);
            } else {
                sb.append(participants[0]).append('_').append(participants[1]);
            }
        } else if (participants.length == 1) {
            sb.append(participants[0]);
        } else if (id != null) {
            sb.append("ID:").append(id);
        } else if (ufedId != null) {
            sb.append(ufedId);
        }

        String result = sb.toString();
        if ("WhatsApp".equalsIgnoreCase(source)) {
            result = StringUtils.remove(result, "@s.whatsapp.net");
        }

        return result;
    }

    private void storeLinkedHashes(List<Message> messages, Metadata chatMetadata) {
        for (Message message : messages) {

            for (MessageAttachment attachment : message.getAttachments()) {

                if (attachment.getReferencedFile() != null) {
                    String hash = attachment.getReferencedFile().getHash();

                    if (hash != null) {
                        chatMetadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + hash);
                        if (message.isFromMe())
                            chatMetadata.add(ExtraProperties.SHARED_HASHES, hash);

                        // replace linkedItems metadata that used "ufed:id" as linker
                        attachment.getItem().getMetadata().set(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + hash);
                    }
                }
            }
        }
    }
}
