package iped.parsers.ufed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.common.collect.ImmutableMap;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.CommunicationConstants;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;
import iped.utils.EmptyInputStream;

public class UFEDChatParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final MediaType UFED_CHAT_MIME = MediaType.application("x-ufed-chat"); //$NON-NLS-1$
    public static final MediaType UFED_CHAT_WA_MIME = MediaType.application("x-ufed-chat-whatsapp"); //$NON-NLS-1$
    public static final MediaType UFED_CHAT_TELEGRAM = MediaType.application("x-ufed-chat-telegram"); //$NON-NLS-1$

    public static final MediaType UFED_CHAT_PREVIEW_MIME = MediaType.application("x-ufed-chat-preview");

    public static final Map<String, MediaType> appToMime = ImmutableMap.of("whatsapp",
            MediaType.application("x-ufed-chat-preview-whatsapp"), "telegram",
            MediaType.application("x-ufed-chat-preview-telegram"), "skype",
            MediaType.application("x-ufed-chat-preview-skype"), "facebook",
            MediaType.application("x-ufed-chat-preview-facebook"), "instagram",
            MediaType.application("x-ufed-chat-preview-instagram"));

    public static final String META_PHONE_OWNER = ExtraProperties.UFED_META_PREFIX + "phoneOwner"; //$NON-NLS-1$
    public static final String META_FROM_OWNER = ExtraProperties.UFED_META_PREFIX + "fromOwner"; //$NON-NLS-1$
    public static final String CHILD_MSG_IDS = ExtraProperties.UFED_META_PREFIX + "msgChildIds"; //$NON-NLS-1$

    public static final String ATTACHED_MEDIA_MSG = "ATTACHED_MEDIA: ";

    private int minChatSplitSize = 6000000;

    private static Set<MediaType> supportedTypes = MediaType.set(UFED_CHAT_MIME, UFED_CHAT_WA_MIME,
            UFED_CHAT_TELEGRAM, MediaTypes.UFED_MESSAGE_MIME);

    private static final Map<String, String> chatTypeMap;
    static {
        HashMap<String, String> map = new HashMap<>();
        map.put("OneOnOne", CommunicationConstants.TYPE_PRIVATE);
        map.put("Group", CommunicationConstants.TYPE_GROUP);
        map.put("Broadcast", CommunicationConstants.TYPE_BROADCAST);

        chatTypeMap = Collections.unmodifiableMap(map);
    }

    private static final Map<Integer, List<IItemReader>> messagesMap = new ConcurrentHashMap<>();

    private boolean extractMessages = true;

    public static void ignoreSupportedChats() {
        supportedTypes = MediaType.set(UFED_CHAT_MIME, MediaTypes.UFED_MESSAGE_MIME);
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

        IItemReader item = context.get(IItemReader.class);
        if (MediaTypes.isInstanceOf(item.getMediaType(), MediaTypes.UFED_MESSAGE_MIME)) {

            // add InstantMessage to map (if child of a Chat)
            if (item instanceof IItem && !((IItem) item).isToAddToCase()) {
                messagesMap.computeIfAbsent(item.getParentId(), k -> Collections.synchronizedList(new ArrayList<>())).add(item);
                ((IItem) item).setToIgnore(true, false);
            }
            return;
        }

        // process Chat
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemReader chat = item;
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (chat == null || searcher == null)
                return;

            List<UfedMessage> messages = new ArrayList<>();
            List<IItemReader> itemMgs = messagesMap.remove(chat.getId());

            if (itemMgs != null) {
                for (IItemReader msg : itemMgs) {
                    Iterator<IItemReader> subItems = null;
                    String[] attachRefs = msg.getMetadata().getValues(ExtraProperties.LINKED_ITEMS);
                    if (attachRefs.length > 0) {
                        String attachQuery = Arrays.asList(attachRefs).stream().collect(Collectors.joining(" ")); //$NON-NLS-1$
                        subItems = searcher.searchIterable(attachQuery).iterator();
                    } else if (msg.hasChildren()) {
                        String contactQuery = BasicProps.PARENTID + ":" + msg.getId() + " && " + BasicProps.CONTENTTYPE
                                + ":\"" + MediaTypes.UFED_CONTACT_MIME.toString() + "\"";
                        subItems = searcher.searchIterable(contactQuery).iterator();
                    }
                    if (subItems == null || !subItems.hasNext()) {
                        UfedMessage m = createMessage(msg);
                        messages.add(m);
                    } else {
                        HashSet<String> uuids = new HashSet<>();
                        while (subItems.hasNext()) {
                            IItemReader subitem = subItems.next();
                            if (uuids.add(subitem.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "id"))) {
                                UfedMessage m = createMessage(msg, subitem);
                                messages.add(m);
                            }
                        }
                    }
                }
            }

            Collections.sort(messages, new MessageComparator());

            String virtualId = chat.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "id");

            if (extractor.shouldParseEmbedded(metadata)) {
                ReportGenerator reportGenerator = new ReportGenerator(searcher);
                reportGenerator.setMinChatSplitSize(this.minChatSplitSize);
                byte[] bytes = reportGenerator.generateNextChatHtml(chat, messages);
                int frag = 0;
                int firstMsg = 0;
                MediaType previewMime = getMediaType(
                        chat.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Source"));
                while (bytes != null) {
                    Metadata chatMetadata = new Metadata();
                    int nextMsg = reportGenerator.getNextMsgNum();
                    List<UfedMessage> subList = messages.subList(firstMsg, nextMsg);
                    storeLinkedHashes(subList, chatMetadata);
                    storeMsgIds(subList, chatMetadata);

                    firstMsg = nextMsg;
                    byte[] nextBytes = reportGenerator.generateNextChatHtml(chat, messages);

                    // copy parent metadata
                    for (String meta : chat.getMetadata().names()) {
                        if (meta.contains(ExtraProperties.UFED_META_PREFIX) || meta.startsWith(ExtraProperties.COMMUNICATION_PREFIX))
                            for (String val : chat.getMetadata().getValues(meta))
                                chatMetadata.add(meta, val);
                    }

                    String chatName = getChatName(chat);
                    if (frag > 0 || nextBytes != null)
                        chatName += "_" + frag++; //$NON-NLS-1$

                    chatMetadata.set(TikaCoreProperties.TITLE, chatName);
                    chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, virtualId);
                    chatMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, previewMime.toString());
                    chatMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                    // Communication:Type
                    String ufedChatType = chat.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "ChatType");
                    if (ufedChatType != null && chatTypeMap.containsKey(ufedChatType)) {
                        chatMetadata.set(ExtraProperties.COMMUNICATION_TYPE, chatTypeMap.get(ufedChatType));
                    } else {
                        chatMetadata.set(ExtraProperties.COMMUNICATION_TYPE, CommunicationConstants.TYPE_UNKONWN);
                    }

                    // Communication:ID
                    chatMetadata.set(ExtraProperties.COMMUNICATION_ID, chat.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Id"));

                    // Communication:Account
                    String ufedAccount = chat.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Account");
                    if (ufedAccount != null) {
                        chatMetadata.set(ExtraProperties.COMMUNICATION_ACCOUNT, ufedAccount);
                    }

                    // Communication:MessagesCount
                    chatMetadata.set(ExtraProperties.COMMUNICATION_MESSAGES_COUNT,
                            Long.toString(messages.stream().filter(m -> !m.isSystemMessage()).count()));

                    if (extractMessages && !subList.isEmpty()) {
                        chatMetadata.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
                    }

                    ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                    bytes = nextBytes;
                }
            }

            if (extractMessages) {
                extractMessages(itemMgs, virtualId, handler, extractor);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw e;

        } finally {
            xhtml.endDocument();
        }

    }

    private void extractMessages(List<IItemReader> messages, String virtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {
        for (IItemReader msg : messages) {
            Metadata meta = msg.getMetadata();
            meta.set(TikaCoreProperties.TITLE, msg.getName()); // $NON-NLS-1$
            meta.set(BasicProps.ID, Integer.toString(msg.getId()));
            meta.set(StandardParser.INDEXER_CONTENT_TYPE, msg.getMediaType().toString());
            meta.set(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(msg.getId()));
            meta.set(ExtraProperties.PARENT_VIRTUAL_ID, virtualId);
            meta.set(ExtraProperties.PARENT_VIEW_POSITION, String.valueOf(msg.getIdInDataSource()));
            meta.set(ExtraProperties.MESSAGE_DATE, msg.getCreationDate());
            meta.set(TikaCoreProperties.CREATED, msg.getCreationDate());
            meta.set(BasicProps.LENGTH, "");

            if (Boolean.parseBoolean(meta.get(META_FROM_OWNER))) {
                meta.set(ExtraProperties.COMMUNICATION_DIRECTION, CommunicationConstants.DIRECTION_OUTGOING);
            } else {
                meta.set(ExtraProperties.COMMUNICATION_DIRECTION, CommunicationConstants.DIRECTION_INCOMING);
            }

            if (msg.isDeleted()) {
                meta.set(ExtraProperties.DELETED, Boolean.toString(true));
            }

            extractor.parseEmbedded(new EmptyInputStream(), handler, meta, false);
        }
    }

    private UfedMessage createMessage(IItemReader msg) {
        return createMessage(msg, null);
    }

    private UfedMessage createMessage(IItemReader msg, IItemReader attach) {
        UfedMessage m = new UfedMessage();
        m.setId(msg.getId());
        m.setDeleted(msg.isDeleted());
        for (String body : msg.getMetadata().getValues(ExtraProperties.MESSAGE_BODY)) {
            if (!body.startsWith(ATTACHED_MEDIA_MSG))
                m.setData(body);
        }
        m.setFromMe(Boolean.valueOf(msg.getMetadata().get(META_FROM_OWNER)));
        String str = msg.getMetadata().get(ExtraProperties.MESSAGE_DATE);
        if (str != null) {
            Date date = DateUtil.tryToParseDate(str);
            m.setTimeStamp(date);
        }
        if (!m.isFromMe()) {
            m.setRemoteResource(msg.getMetadata().get(ExtraProperties.COMMUNICATION_FROM));
            m.setLocalResource(msg.getMetadata().get(ExtraProperties.COMMUNICATION_TO));
        } else {
            m.setRemoteResource(msg.getMetadata().get(ExtraProperties.COMMUNICATION_TO));
            m.setLocalResource(msg.getMetadata().get(ExtraProperties.COMMUNICATION_FROM));
        }
        if (attach != null) {
            if (attach.getLength() != null && attach.getLength() > 0) {
                m.setMediaHash(attach.getHash(), false);
            }
            m.setMediaName(attach.getName());
            m.setMediaTrueExt(attach.getType());
            m.setMediaUrl(getMetaFromAttachOrMsg(ExtraProperties.UFED_META_PREFIX + "URL", msg, attach)); //$NON-NLS-1$
            m.setMediaCaption(getMetaFromAttachOrMsg(ExtraProperties.UFED_META_PREFIX + "Title", msg, attach)); //$NON-NLS-1$
            m.setThumbData(attach.getThumb());
            m.setTranscription(attach.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR));
            m.setTranscriptConfidence(attach.getMetadata().get(ExtraProperties.CONFIDENCE_ATTR));
            if (attach.isDeleted())
                m.setDeleted(true);
            if (attach.getLength() != null)
                m.setMediaSize(attach.getLength());
            if (attach.getMediaType() != null && !attach.getMediaType().equals(MediaType.OCTET_STREAM))
                m.setMediaMime(attach.getMediaType().toString());
            else
                m.setMediaMime(getMetaFromAttachOrMsg(ExtraProperties.UFED_META_PREFIX + "ContentType", msg, attach)); //$NON-NLS-1$
        }
        return m;
    }

    private String getMetaFromAttachOrMsg(String key, IItemReader msg, IItemReader attach) {
        String result = attach.getMetadata().get(key);
        if (result == null) {
            result = msg.getMetadata().get(key);
        }
        return result;
    }

    public static String getChatName(IItemReader item) {

        String source = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Source");
        String chatType = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "ChatType");
        String name = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name");
        String id = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "id");
        String[] participants = item.getMetadata().getValues(ExtraProperties.COMMUNICATION_PARTICIPANTS);
        String account = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Account");

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
        } else if (participants.length == 2) {
            if (participants[0].contains(account)) {
                sb.append(participants[1]);
            } else if (participants[1].contains(account)) {
                sb.append(participants[0]);
            } else {
                sb.append(participants[0]).append('_').append(participants[1]);
            }
        } else if (participants.length == 1) {
            sb.append(participants[0]);
        } else {
            sb.append(id);
        }

        return sb.toString();
    }

    private void storeMsgIds(List<UfedMessage> messages, Metadata metadata) {
        for (UfedMessage m : messages) {
            metadata.add(CHILD_MSG_IDS, Long.toString(m.getId()));
        }
    }

    private void storeLinkedHashes(List<UfedMessage> messages, Metadata metadata) {
        for (UfedMessage m : messages) {
            if (m.getMediaHash() != null) {
                metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + m.getMediaHash());
                if (m.isFromMe())
                    metadata.add(ExtraProperties.SHARED_HASHES, m.getMediaHash());
            }
        }
    }

    private class MessageComparator implements Comparator<UfedMessage> {

        @Override
        public int compare(UfedMessage o1, UfedMessage o2) {
            if (o1.getTimeStamp() == null) {
                if (o2.getTimeStamp() == null)
                    return 0;
                else
                    return -1;
            } else if (o2.getTimeStamp() == null)
                return 1;
            else
                return o1.getTimeStamp().compareTo(o2.getTimeStamp());
        }

    }

}
