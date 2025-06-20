package iped.parsers.ufed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.ChatActivity;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactPhoto;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.JumpTarget;
import iped.parsers.ufed.model.ReplyMessageData;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

public class UFEDChatParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    private static Logger logger = LoggerFactory.getLogger(UFEDChatParser.class);

    public static final MediaType UFED_CHAT_MIME = MediaType.application("x-ufed-chat");
    public static final MediaType UFED_CHAT_WA_MIME = MediaType.application("x-ufed-chat-whatsapp");
    public static final MediaType UFED_CHAT_TELEGRAM = MediaType.application("x-ufed-chat-telegram");

    public static final MediaType UFED_CHAT_PREVIEW_MIME = MediaType.application("x-ufed-chat-preview");

    public static final Map<String, MediaType> appToMime = Map.of( //
                    "whatsapp", MediaType.application("x-ufed-chat-preview-whatsapp"), //
                    "telegram", MediaType.application("x-ufed-chat-preview-telegram"), //
                    "skype", MediaType.application("x-ufed-chat-preview-skype"), //
                    "facebook", MediaType.application("x-ufed-chat-preview-facebook"), //
                    "instagram", MediaType.application("x-ufed-chat-preview-instagram"));

    public static final String META_PHONE_OWNER = ExtraProperties.UFED_META_PREFIX + "phoneOwner";
    public static final String META_FROM_OWNER = ExtraProperties.UFED_META_PREFIX + "fromOwner";
    public static final String CHILD_MSG_IDS = ExtraProperties.UFED_META_PREFIX + "msgChildIds";

    public static final String ATTACHED_MEDIA_MSG = "ATTACHED_MEDIA: ";

    public static final String CHAT_TEMP_ATTRIBUTE = "ufed:chat";

    private boolean extractMessages = true;
    private boolean extractActivityLogs = true;
    private boolean extractAttachments = true;
    private boolean extractSharedContacts = true;
    private boolean ignoreEmptyChats = false;
    private int minChatSplitSize = 6000000;

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(UFED_CHAT_MIME, UFED_CHAT_WA_MIME,
            UFED_CHAT_TELEGRAM);

    public static void setSupportedTypes(Set<MediaType> supportedTypes) {
        SUPPORTED_TYPES = supportedTypes;
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
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream inputStream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemReader item = context.get(IItemReader.class);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (item == null || searcher == null) {
                return;
            }

            Chat chat = null;
            if (item instanceof IItem) {
                chat = (Chat) ((IItem) item).getTempAttribute(CHAT_TEMP_ATTRIBUTE);
            }
            if (chat == null) {
                return;
            }

            int messagesCount = (int) chat.getMessages().stream().filter(m -> !m.isSystemMessage()).count();
            if (messagesCount == 0 && ignoreEmptyChats) {
                return;
            }

            UfedChatMetadataUtils.fillChatMetadata(chat, metadata);

            Collections.sort(chat.getMessages());

            loadChatReferences(chat, searcher);

            String virtualId = chat.getId();
            String chatPrefix = UfedChatStringUtils.getChatTitle(chat, true, true);

            if (extractor.shouldParseEmbedded(metadata)) {
                ReportGenerator reportGenerator = new ReportGenerator(chat, minChatSplitSize);
                byte[] bytes = reportGenerator.generateNextChatHtml();
                int frag = 0;
                int firstMsg = 0;
                MediaType previewMime = getMediaType(chat.getSource());
                while (bytes != null) {
                    Metadata chatPreviewMeta = new Metadata();
                    int nextMsg = reportGenerator.getNextMsgNum();
                    List<InstantMessage> subList = chat.getMessages().subList(firstMsg, nextMsg);
                    storeLinkedHashes(subList, chatPreviewMeta);

                    firstMsg = nextMsg;
                    byte[] nextBytes = reportGenerator.generateNextChatHtml();

                    // copy parent metadata
                    for (String meta : metadata.names()) {
                        if (meta.contains(ExtraProperties.UFED_META_PREFIX)) {
                            for (String val : metadata.getValues(meta)) {
                                chatPreviewMeta.add(meta, val);
                            }
                        }
                    }

                    String chatName = chatPrefix;
                    if (frag > 0 || nextBytes != null) {
                        chatName += "_" + frag++;
                    }

                    chatPreviewMeta.set(TikaCoreProperties.TITLE, chatName);
                    chatPreviewMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, virtualId);
                    chatPreviewMeta.set(StandardParser.INDEXER_CONTENT_TYPE, previewMime.toString());
                    chatPreviewMeta.set(ExtraProperties.DECODED_DATA, Boolean.toString(true));

                    if (extractMessages && !subList.isEmpty()) {
                        chatPreviewMeta.set(BasicProps.HASCHILD, Boolean.toString(true));
                    }

                    ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(chatStream, handler, chatPreviewMeta, false);
                    bytes = nextBytes;

                    if (extractMessages) {
                        extractMessages(subList, virtualId, handler, extractor, chat);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing chat", e);
            throw e;
        }
    }

    private void extractMessages(List<InstantMessage> subList, String chatVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor, Chat chat) throws SAXException, IOException {

        for (InstantMessage message : subList) {

            String messageVirtualId = message.getId();

            Metadata messageMetaData = UfedChatMetadataUtils.createInstantMessageMetadata(message, chat);
            messageMetaData.set(TikaCoreProperties.TITLE, UfedChatStringUtils.getInstantMessageTitle(message));
            messageMetaData.set(ExtraProperties.ITEM_VIRTUAL_ID, messageVirtualId);
            messageMetaData.set(StandardParser.INDEXER_CONTENT_TYPE, message.getContentType().toString());
            messageMetaData.set(ExtraProperties.PARENT_VIRTUAL_ID, chatVirtualId);
            messageMetaData.set(ExtraProperties.PARENT_VIEW_POSITION, Integer.toString(message.getSourceIndex()));
            messageMetaData.set(BasicProps.LENGTH, "");
            if (!message.getAttachments().isEmpty()) {
                messageMetaData.set(ExtraProperties.MESSAGE_ATTACHMENT_COUNT, message.getAttachments().size());
            }

            if (message.isDeleted()) {
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

    private void extractActivityLog(InstantMessage message, String messageVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        ChatActivity activity = message.getActivityLog();
        if (activity != null) {
            Metadata activityMeta = UfedChatMetadataUtils.createGenericMetadata(activity);
            activityMeta.set(TikaCoreProperties.TITLE, "ChatActivity_" + activity.getId());
            activityMeta.set(StandardParser.INDEXER_CONTENT_TYPE, activity.getContentType().toString());
            activityMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            activityMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, activityMeta, false);
        }
    }

    private void extractAttachments(InstantMessage message, String messageVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        for (Attachment attach : message.getAttachments()) {
            Metadata attachMeta = UfedChatMetadataUtils.createGenericMetadata(attach);
            attachMeta.set(TikaCoreProperties.TITLE, UfedChatStringUtils.getAttachmentTitle(attach));
            attachMeta.set(StandardParser.INDEXER_CONTENT_TYPE, attach.getContentType().toString());
            attachMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            attachMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, attachMeta, false);
        }
    }

    private void extractShareContacts(InstantMessage message, String messageVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        for (Contact shareContact : message.getSharedContacts()) {
            Metadata shareContactMeta = UfedChatMetadataUtils.createGenericMetadata(shareContact);
            shareContactMeta.set(TikaCoreProperties.TITLE, UfedChatStringUtils.getContactTitle(shareContact));
            shareContactMeta.set(StandardParser.INDEXER_CONTENT_TYPE, shareContact.getContentType().toString());
            shareContactMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            shareContactMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, shareContactMeta, false);
        }
    }

    private void storeLinkedHashes(List<InstantMessage> messages, Metadata metadata) {
        for (InstantMessage m : messages) {
            for (Attachment a : m.getAttachments()) {
                if (a.getReferencedFile() != null && a.getReferencedFile().getHash() != null) {
                    metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + a.getReferencedFile().getHash());
                    if (m.isFromPhoneOwner()) {
                        metadata.add(ExtraProperties.SHARED_HASHES, a.getReferencedFile().getHash());
                    }
                }
            }
        }
    }

    private void loadChatReferences(Chat chat, IItemSearcher searcher) {

        loadAccountReference(chat, searcher);

        // load photo thumb
        chat.getPhotos().stream().forEach(p -> {
            loadContactPhotoData(p, searcher);
        });

        // load message items
        chat.getMessages().stream().forEach(m -> {
            loadInstantMessageReferences(m, searcher);
        });
    }

    private void loadAccountReference(Chat chat, IItemSearcher searcher) {
        String account = chat.getAccount();
        if (StringUtils.isBlank(account)) {
            return;
        }

        String source = chat.getSource();
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
            chat.setReferencedAccount(results.get(0));
        }
    }

    private void loadInstantMessageReferences(InstantMessage message, IItemSearcher searcher) {

        message.getAttachments().stream().forEach(a -> {
            loadAttachmentReference(a, searcher);
        });
        message.getSharedContacts().stream().forEach(c -> {
            loadContactReference(c, searcher);
        });
        loadLocationReference(message, searcher);
        message.getEmbeddedMessage().ifPresent(em -> {
            loadInstantMessageReferences(em, searcher);
        });
        message.getExtraData().getReplyMessage().map(ReplyMessageData::getInstantMessage).ifPresent(rm -> {
            loadInstantMessageReferences(rm, searcher);
        });
    }

    private void loadContactPhotoData(ContactPhoto photo, IItemSearcher searcher) {
        if (photo.getPhotoNodeId() == null) {
            return;
        }

        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + photo.getPhotoNodeId() + "\"";
        List<IItemReader> result = searcher.search(query);
        if (!result.isEmpty()) {
            IItemReader contactPhoto = result.get(0);
            photo.setImageData(contactPhoto.getThumb());
        }
    }

    private void loadAttachmentReference(Attachment attachment, IItemSearcher searcher) {
        if (attachment.getFileId() == null) {
            return;
        }

        // attachment "ufed:file_id" metadata contains the "ufed:id" metadata of the file
        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + attachment.getFileId() + "\"";
        List<IItemReader> fileItems = searcher.search(query);
        if (!fileItems.isEmpty()) {
            if (fileItems.size() > 1) {
                logger.warn("Found more than 1 file for attachment: {}", fileItems);
            }
            attachment.setReferencedFile(fileItems.get(0));
        }
    }

    private void loadContactReference(Contact contact, IItemSearcher searcher) {
        if (contact.getId() == null) {
            return;
        }

        // shared contact and indexed contact have the same "ufed:id" metadata
        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + contact.getId() + "\"";
        List<IItemReader> contactItems = searcher.search(query);
        if (!contactItems.isEmpty()) {
            if (contactItems.size() > 1) {
                logger.warn("Found more than 1 contact for shared contact: {}", contactItems);
            }
            contact.setReferencedContact(contactItems.get(0));
        }
    }

    private void loadLocationReference(InstantMessage message, IItemSearcher searcher) {
        if (message.getPosition() == null || message.getPosition().getId() == null) {
            return;
        }

        {
            // the message and location shares the same "ufed:coordinate_id" that was added when merging in UfedXmlReader
            String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + message.getPosition().getId() + "\"";
            List<IItemReader> locationItems = searcher.search(query);
            if (!locationItems.isEmpty()) {
                if (locationItems.size() > 1) {
                    logger.warn("Found more than 1 location for coordinate: {}", locationItems);
                }
                message.getPosition().setReferencedLocation(locationItems.get(0));
                return;
            }
        }

        if (message.isLocationSharing()) {

            // the location item is referenced by jumptargets
            String[] jumpTargets = message.getJumpTargets().stream().map(JumpTarget::getId).toArray(String[]::new);
            if (jumpTargets.length > 0) {
                String query = BasicProps.CONTENTTYPE + ":\"application/x-ufed-location\" && " //
                        + searcher.escapeQuery(ExtraProperties.UFED_ID) + ":(\"" + StringUtils.join(jumpTargets, "\" \"") + "\")";
                List<IItemReader> locationItems = searcher.search(query);
                if (!locationItems.isEmpty()) {
                    if (locationItems.size() > 1) {
                        logger.warn("Found more than 1 location for jumptargets: {}", locationItems);
                    }
                    message.getPosition().setReferencedLocation(locationItems.get(0));
                }
            }
        }
    }
}
