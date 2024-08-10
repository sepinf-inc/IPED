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

import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.CommunicationConstants;
import iped.parsers.whatsapp.Message;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;

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

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(UFED_CHAT_MIME, UFED_CHAT_WA_MIME,
            UFED_CHAT_TELEGRAM);

    private static final Map<String, String> chatTypeMap;
    static {
        HashMap<String, String> map = new HashMap<>();
        map.put("OneOnOne", CommunicationConstants.TYPE_PRIVATE);
        map.put("Group", CommunicationConstants.TYPE_GROUP);
        map.put("Broadcast", CommunicationConstants.TYPE_BROADCAST);

        chatTypeMap = Collections.unmodifiableMap(map);
    }

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

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemReader chat = context.get(IItemReader.class);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (chat == null || searcher == null)
                return;

            String query = BasicProps.PARENTID + ":" + chat.getId(); //$NON-NLS-1$

            List<UfedMessage> messages = new ArrayList<>();

            for (IItemReader msg : searcher.searchIterable(query)) {
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

            Collections.sort(messages, new MessageComparator());

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

                    ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                    bytes = nextBytes;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw e;

        } finally {
            xhtml.endDocument();
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
        for (Message m : messages) {
            metadata.add(CHILD_MSG_IDS, Long.toString(m.getId()));
        }
    }

    private void storeLinkedHashes(List<UfedMessage> messages, Metadata metadata) {
        for (Message m : messages) {
            if (m.getMediaHash() != null) {
                metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + m.getMediaHash());
                if (m.isFromMe())
                    metadata.add(ExtraProperties.SHARED_HASHES, m.getMediaHash());
            }
        }
    }

    private class MessageComparator implements Comparator<Message> {

        @Override
        public int compare(Message o1, Message o2) {
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
