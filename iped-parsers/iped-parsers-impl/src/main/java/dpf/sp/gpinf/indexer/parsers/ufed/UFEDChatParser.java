package dpf.sp.gpinf.indexer.parsers.ufed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.mg.udi.gpinf.whatsappextractor.Message;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.util.DateUtil;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class UFEDChatParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final MediaType UFED_CHAT_MIME = MediaType.application("x-ufed-chat"); //$NON-NLS-1$
    public static final MediaType UFED_CHAT_WA_MIME = MediaType.application("x-ufed-chat-whatsapp"); //$NON-NLS-1$
    public static final MediaType UFED_CHAT_PREVIEW_MIME = MediaType.application("x-ufed-chat-preview"); //$NON-NLS-1$

    public static final String META_PHONE_OWNER = ExtraProperties.UFED_META_PREFIX + "phoneOwner"; //$NON-NLS-1$
    public static final String META_FROM_OWNER = ExtraProperties.UFED_META_PREFIX + "fromOwner"; //$NON-NLS-1$

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(UFED_CHAT_MIME, UFED_CHAT_WA_MIME);

    public static void setSupportedTypes(Set<MediaType> supportedTypes) {
        SUPPORTED_TYPES = supportedTypes;
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
            IItemBase chat = context.get(IItemBase.class);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (chat == null || searcher == null)
                return;

            String query = BasicProps.PARENTID + ":" + chat.getId(); //$NON-NLS-1$
            List<IItemBase> msgs = searcher.search(query);

            List<Message> messages = new ArrayList<>();

            for (IItemBase msg : msgs) {
                String META_PREFIX = ExtraProperties.UFED_META_PREFIX;
                Message m = new Message();
                m.setData(msg.getMetadata().get(ExtraProperties.MESSAGE_BODY));
                m.setFromMe(Boolean.valueOf(msg.getMetadata().get(META_FROM_OWNER)));
                String str = msg.getMetadata().get(ExtraProperties.MESSAGE_DATE);
                if (str != null) {
                    Date date = DateUtil.tryToParseDate(str);
                    m.setTimeStamp(date);
                }
                if (!m.isFromMe()) {
                    m.setRemoteResource(msg.getMetadata().get(org.apache.tika.metadata.Message.MESSAGE_FROM));
                    m.setLocalResource(msg.getMetadata().get(org.apache.tika.metadata.Message.MESSAGE_TO));
                } else {
                    m.setRemoteResource(msg.getMetadata().get(org.apache.tika.metadata.Message.MESSAGE_TO));
                    m.setLocalResource(msg.getMetadata().get(org.apache.tika.metadata.Message.MESSAGE_FROM));
                }

                if (msg.hasChildren()) {
                    query = BasicProps.PARENTID + ":" + msg.getId(); //$NON-NLS-1$
                    List<IItemBase> attachs = searcher.search(query);
                    if (attachs.size() != 0) {
                        IItemBase attach = attachs.get(0);
                        m.setMediaHash(attach.getHash(), false);
                        m.setMediaName(attach.getName());
                        m.setMediaUrl(attach.getMetadata().get(META_PREFIX + "URL")); //$NON-NLS-1$
                        m.setMediaCaption(attach.getMetadata().get(META_PREFIX + "Title")); //$NON-NLS-1$
                        m.setThumbData(attach.getThumb());
                        if (attach.isDeleted())
                            m.setDeleted(true);
                        if (attach.getLength() != null)
                            m.setMediaSize(attach.getLength());
                        if (attach.getMediaType() != null && !attach.getMediaType().equals(MediaType.OCTET_STREAM))
                            m.setMediaMime(attach.getMediaType().toString());
                        else
                            m.setMediaMime(attach.getMetadata().get(META_PREFIX + "ContentType")); //$NON-NLS-1$
                        if (attachs.size() > 1)
                            System.out.println("multiple_attachs: " //$NON-NLS-1$
                                    + msg.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "id")); //$NON-NLS-1$
                    }
                }

                messages.add(m);
            }

            Collections.sort(messages, new MessageComparator());

            if (extractor.shouldParseEmbedded(metadata)) {
                ReportGenerator reportGenerator = new ReportGenerator(searcher);
                byte[] bytes = reportGenerator.generateNextChatHtml(chat, messages);
                int frag = 0;
                int firstMsg = 0;
                while (bytes != null) {
                    Metadata chatMetadata = new Metadata();
                    int nextMsg = reportGenerator.getNextMsgNum();
                    storeLinkedHashes(messages.subList(firstMsg, nextMsg), chatMetadata);

                    firstMsg = nextMsg;
                    byte[] nextBytes = reportGenerator.generateNextChatHtml(chat, messages);

                    for (String meta : chat.getMetadata().names()) {
                        if (meta.contains(ExtraProperties.UFED_META_PREFIX))
                            for (String val : chat.getMetadata().getValues(meta))
                                chatMetadata.add(meta, val);
                    }

                    String chatName = getChatName(chat);
                    if (frag > 0 || nextBytes != null)
                        chatName += "_" + frag++; //$NON-NLS-1$
                    chatMetadata.set(TikaCoreProperties.TITLE, chatName);
                    chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, UFED_CHAT_PREVIEW_MIME.toString());

                    ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                    bytes = nextBytes;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw e;

        }

    }
    
    private String getChatName(IItemBase item) {
        String name = "Chat"; //$NON-NLS-1$
        String source = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Source"); //$NON-NLS-1$
        if (source != null)
            name += "_" + source; //$NON-NLS-1$
        String[] parties = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants"); //$NON-NLS-1$
        if (parties != null && parties.length > 2) {
            name += "_Group_" + item.getName().split("_")[1]; //$NON-NLS-1$ //$NON-NLS-2$
        } else if (parties != null && parties.length > 0) {
            name += "_" + parties[0]; //$NON-NLS-1$
            if (parties.length > 1)
                name += "_" + parties[1]; //$NON-NLS-1$
        }
        return name;
    }

    private void storeLinkedHashes(List<Message> messages, Metadata metadata) {
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
