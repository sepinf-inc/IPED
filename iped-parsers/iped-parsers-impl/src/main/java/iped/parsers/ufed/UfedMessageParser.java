package iped.parsers.ufed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
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
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.ufed.handler.AttachmentHandler;
import iped.parsers.ufed.handler.ChatActivityHandler;
import iped.parsers.ufed.handler.ContactHandler;
import iped.parsers.ufed.handler.InstantMessageHandler;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.ChatActivity;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.util.Messages;
import iped.parsers.util.OmitEmptyArraysTypeAdapterFactory;
import iped.properties.BasicProps;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

public class UfedMessageParser extends AbstractParser {

    private static final long serialVersionUID = -4738095481615972119L;

    private static final Logger logger = LoggerFactory.getLogger(UfedMessageParser.class);

    private static final Set<MediaType> SUPPORTED_TYPES = Set.of(MediaTypes.UFED_MESSAGE_MIME);

    private static final String JSON_TEMP_ATTR = "message:json";

    private static final Set<String> chatMessageUfedIds = ConcurrentHashMap.newKeySet();

    private Gson gson = new GsonBuilder() //
            .registerTypeAdapterFactory(new OmitEmptyArraysTypeAdapterFactory()) //
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return f.getName().equals("attributes"); // don't print attributes in JSON
                }
                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .setPrettyPrinting() //
            .disableHtmlEscaping() //
            .setDateFormat(Messages.getString("UfedMessageParser.DateFormat")) //
            .create();

    private boolean extractActivityLogs = true;
    private boolean extractAttachments = true;
    private boolean extractSharedContacts = true;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
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

    public static void addChatMessageUfedId(String ufedId) {
        chatMessageUfedIds.add(ufedId);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemReader item = context.get(IItemReader.class);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

            if (item == null || searcher == null) {
                return;
            }

            InstantMessage message = null;
            if (stream instanceof TikaInputStream) {
                message = (InstantMessage) TikaInputStream.cast(stream).getOpenContainer();
            }
            if (message == null) {
                // view is already generated — proceed to parse with HtmlParser
                HtmlParser parser = new HtmlParser();
                parser.parse(stream, handler, metadata, context);
                return;
            }

            if (!item.isSubItem() && item instanceof IItem && chatMessageUfedIds.contains(message.getId())) {
                // ignore orphan messages (not related to chats) whose IDs were from messages already extracted from chats
                ((IItem) item).setToIgnore(true);
                return;
            }

            InstantMessageHandler messageHandler = new InstantMessageHandler(message, item);
            messageHandler.loadReferences(searcher);
            messageHandler.fillMetadata(metadata);
            messageHandler.updateItemNameWithTitle();
            messageHandler.addLinkedItemsAndSharedHashes(metadata, searcher);

            if (extractor.shouldParseEmbedded(metadata)) {

                if (extractActivityLogs) {
                    extractActivityLog(message, handler, extractor);
                }
                if (extractAttachments) {
                    extractAttachments(message, handler, extractor, false);
                } else {
                    extractAttachments(message, handler, extractor, true);
                }
                if (extractSharedContacts) {
                    extractSharedContacts(message, handler, extractor);
                }
            }

            // parse message content into the XHTML handler
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            try {
                parseMessage(item, message, xhtml);
            } finally {
                xhtml.endDocument();
            }

        } catch (Exception e) {
            logger.error("Error processing InstantMessage", e);
            throw e;
        }
    }

    private void parseMessage(IItemReader item, InstantMessage message, XHTMLContentHandler handler) throws SAXException {

        String json = null;
        if (item instanceof IItem) {
            // restore previously generated JSON
            json = (String) ((IItem) item).getTempAttribute(JSON_TEMP_ATTR);
        }

        if (json == null) {

            // serialize to JSON
            json = gson.toJson(message);

            // remove empty objects
            json = Arrays
                    .stream(json.split("\n")) //
                    .filter(line -> !StringUtils.endsWithAny(line, ": {}", ": {},")) //
                    .collect(Collectors.joining("\n"));
        }

        // render
        handler.element("pre", json);

        if (item instanceof IItem) {
            // to be reused by MakePreviewTask
            ((IItem) item).setTempAttribute(JSON_TEMP_ATTR, json);
        }
    }

    private void extractActivityLog(InstantMessage message, ContentHandler handler, EmbeddedDocumentExtractor extractor)
            throws SAXException, IOException {

        ChatActivity activity = message.getActivityLog();
        if (activity != null) {

            ChatActivityHandler activityHandler = new ChatActivityHandler(activity, message.getSource());

            Metadata activityMeta = activityHandler.createMetadata();
            activityMeta.set(TikaCoreProperties.TITLE, activityHandler.getTitle());
            activityMeta.set(StandardParser.INDEXER_CONTENT_TYPE, activity.getMediaType().toString());
            activityMeta.set(BasicProps.LENGTH, "");

            extractor.parseEmbedded(new EmptyInputStream(), handler, activityMeta, false);
        }
    }

    private void extractAttachments(InstantMessage message, ContentHandler handler, EmbeddedDocumentExtractor extractor,
            boolean onlyUnreferenced) throws SAXException, IOException {

        for (Attachment attach : message.getAttachments()) {
            if (onlyUnreferenced && attach.getUnreferencedContent() == null) {
                continue;
            }

            AttachmentHandler attachHandler = new AttachmentHandler(attach, null);
            Metadata attachMeta = attachHandler.createMetadata();

            InputStream inputStream;
            if (attach.getUnreferencedContent() != null) {
                inputStream = new ByteArrayInputStream(attach.getUnreferencedContent());
                attachMeta.set(TikaCoreProperties.RESOURCE_NAME_KEY, attach.getFilename());
                attachMeta.set(StandardParser.INDEXER_CONTENT_TYPE, attach.getContentType());
                attachMeta.set(BasicProps.LENGTH, Integer.toString(attach.getUnreferencedContent().length));
            } else {
                inputStream = new EmptyInputStream();
                attachMeta.set(TikaCoreProperties.TITLE, attachHandler.getTitle());
                attachMeta.set(StandardParser.INDEXER_CONTENT_TYPE, attach.getMediaType().toString());
                attachMeta.set(BasicProps.LENGTH, "");
            }

            extractor.parseEmbedded(inputStream, handler, attachMeta, false);
        }
    }

    private void extractSharedContacts(InstantMessage message, ContentHandler handler, EmbeddedDocumentExtractor extractor)
            throws SAXException, IOException {

        for (Contact sharedContact : message.getSharedContacts()) {

            // don't extract shared contact already present in the case
            if (sharedContact.getReferencedContact().isPresent()) {
                continue;
            }

            ContactHandler contactHandler = new ContactHandler(sharedContact);

            Metadata shareContactMeta = contactHandler.createMetadata();
            shareContactMeta.set(TikaCoreProperties.TITLE, contactHandler.getTitle());
            shareContactMeta.set(StandardParser.INDEXER_CONTENT_TYPE, sharedContact.getMediaType().toString());
            shareContactMeta.set(BasicProps.LENGTH, "");

            TikaInputStream sharedContactInput = TikaInputStream.get(new EmptyInputStream());
            sharedContactInput.setOpenContainer(sharedContact);
            extractor.parseEmbedded(sharedContactInput, handler, shareContactMeta, false);
        }
    }
}
