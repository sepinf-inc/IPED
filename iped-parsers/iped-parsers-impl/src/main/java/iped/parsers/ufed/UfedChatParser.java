package iped.parsers.ufed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
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
import iped.parsers.ufed.handler.BaseModelHandler;
import iped.parsers.ufed.handler.ChatActivityHandler;
import iped.parsers.ufed.handler.ChatHandler;
import iped.parsers.ufed.handler.InstantMessageHandler;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.ChatActivity;
import iped.parsers.ufed.model.InstantMessage;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

public class UfedChatParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(UfedChatParser.class);

    public static final MediaType UFED_CHAT_MIME = MediaType.application("x-ufed-chat");

    public static final MediaType UFED_CHAT_PREVIEW_MIME = MediaType.application("x-ufed-chat-preview");

    public static final Map<String, MediaType> appToMime = Map.ofEntries( //
                    Map.entry("whatsapp", MediaType.application("x-ufed-chat-preview-whatsapp")), //
                    Map.entry("telegram", MediaType.application("x-ufed-chat-preview-telegram")), //
                    Map.entry("skype", MediaType.application("x-ufed-chat-preview-skype")), //
                    Map.entry("facebook", MediaType.application("x-ufed-chat-preview-facebook")), //
                    Map.entry("signal", MediaType.application("x-ufed-chat-preview-signal")), //
                    Map.entry("snapchat", MediaType.application("x-ufed-chat-preview-snapchat")), //
                    Map.entry("threema", MediaType.application("x-ufed-chat-preview-threema")), //
                    Map.entry("tiktok", MediaType.application("x-ufed-chat-preview-tiktok")), //
                    Map.entry("viber", MediaType.application("x-ufed-chat-preview-viber")), //
                    Map.entry("discord", MediaType.application("x-ufed-chat-preview-discord")), //
                    Map.entry("instagram", MediaType.application("x-ufed-chat-preview-instagram")));

    private boolean extractMessages = true;
    private boolean extractActivityLogs = true;
    private boolean ignoreEmptyChats = false;
    private int minChatSplitSize = 6000000;

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(UFED_CHAT_MIME);

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

            if (item instanceof IItem) {
                ((IItem) item).setExtraAttribute(BasicProps.TREENODE, Boolean.toString(true));
            }

            Chat chat = null;
            if (inputStream instanceof TikaInputStream) {
                chat = (Chat) TikaInputStream.cast(inputStream).getOpenContainer();
            }
            if (chat == null) {
                return;
            }

            int messagesCount = (int) chat.getMessages().stream().filter(m -> !m.isSystemMessage()).count();
            if (messagesCount == 0 && ignoreEmptyChats) {
                return;
            }

            ChatHandler chatHandler = new ChatHandler(chat, item);
            chatHandler.loadReferences(searcher);
            chatHandler.fillMetadata(metadata);
            chatHandler.addLinkedItemsAndSharedHashes(metadata, searcher);

            Collections.sort(chat.getMessages());

            String virtualId = chat.getId();
            String chatPrefix = chatHandler.getTitle(true, true);

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
                    firstMsg = nextMsg;
                    byte[] nextBytes = reportGenerator.generateNextChatHtml();

                    // copy parent metadata
                    for (String name : metadata.names()) {
                        if (name.startsWith(ExtraProperties.UFED_META_PREFIX) || name.startsWith(ExtraProperties.CONVERSATION_PREFIX)
                                || name.equals(ExtraProperties.LINKED_ITEMS)) {
                            for (String val : metadata.getValues(name)) {
                                chatPreviewMeta.add(name, val);
                            }
                        }
                    }

                    storeLinkedHashes(subList, chatPreviewMeta, searcher);

                    String chatName = chatPrefix;
                    if (frag > 0 || nextBytes != null) {
                        chatName += "_" + frag++;
                    }

                    chatPreviewMeta.set(TikaCoreProperties.TITLE, chatName);
                    chatPreviewMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, virtualId);
                    chatPreviewMeta.set(StandardParser.INDEXER_CONTENT_TYPE, previewMime.toString());
                    chatPreviewMeta.set(ExtraProperties.DECODED_DATA, Boolean.toString(true));

                    ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(chatStream, handler, chatPreviewMeta, false);
                    bytes = nextBytes;

                    if (extractor.shouldParseEmbedded(metadata)) {
                        if (extractMessages) {
                            extractMessages(subList, virtualId, handler, extractor, chat);
                        }
                        if (extractActivityLogs) {
                            extractActivityLog(chat, virtualId, handler, extractor);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing Chat", e);
            e.printStackTrace();
            throw e;
        }
    }

    private void extractMessages(List<InstantMessage> subList, String chatVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor, Chat chat) throws SAXException, IOException {

        for (InstantMessage message : subList) {

            InstantMessageHandler messageHandler = new InstantMessageHandler(message, null);

            Metadata messageMeta = new Metadata();
            messageMeta.set(TikaCoreProperties.TITLE, messageHandler.getTitle());
            messageMeta.set(StandardParser.INDEXER_CONTENT_TYPE, message.getMediaType().toString());
            messageMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, chatVirtualId);
            messageMeta.set(BasicProps.LENGTH, "");

            TikaInputStream messageInput = TikaInputStream.get(new EmptyInputStream());
            messageInput.setOpenContainer(message);
            extractor.parseEmbedded(messageInput, handler, messageMeta, false);

            UfedMessageParser.addChatMessageUfedId(message.getId());
        }
    }

    private void extractActivityLog(Chat chat, String chatVirtualId, ContentHandler handler, EmbeddedDocumentExtractor extractor)
            throws SAXException, IOException {

        for (ChatActivity activity : chat.getActivityLog()) {

            ChatActivityHandler activityHandler = new ChatActivityHandler(activity, chat.getSource());

            Metadata activityMeta = activityHandler.createMetadata();
            activityMeta.set(TikaCoreProperties.TITLE, activityHandler.getTitle());
            activityMeta.set(StandardParser.INDEXER_CONTENT_TYPE, activity.getMediaType().toString());
            activityMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, chatVirtualId);
            activityMeta.set(BasicProps.LENGTH, "");

            extractor.parseEmbedded(new EmptyInputStream(), handler, activityMeta, false);
        }
    }

    private void storeLinkedHashes(List<InstantMessage> messages, Metadata chatMetadata, IItemSearcher searcher) {

        Set<String> newLinkedItems = ConcurrentHashMap.newKeySet();
        Set<String> newSharedHashes = ConcurrentHashMap.newKeySet();

        messages.parallelStream().forEach(message -> {
            InstantMessageHandler messageHandler = new InstantMessageHandler(message, null);
            messageHandler.setSkipJumpTargetsCheckOnAddLinkedItems(true);
            messageHandler.addLinkedItemsAndSharedHashes(newLinkedItems, newSharedHashes, searcher);
        });

        BaseModelHandler.updateLinkedItemsAndSharedHashes(chatMetadata, newLinkedItems, newSharedHashes);
    }
}
