package iped.parsers.ufed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import iped.parsers.ufed.handler.BaseModelHandler;
import iped.parsers.ufed.handler.ContactHandler;
import iped.parsers.ufed.handler.InstantMessageHandler;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.ChatActivity;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.reference.ReferencedFile;
import iped.parsers.util.HashUtils;
import iped.parsers.util.Messages;
import iped.parsers.util.OmitEmptyArraysTypeAdapterFactory;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

public class UfedMessageParser extends AbstractParser {

    private static final long serialVersionUID = -4738095481615972119L;

    private static Logger logger = LoggerFactory.getLogger(UfedMessageParser.class);

    private static Set<MediaType> SUPPORTED_TYPES = Set.of(MediaTypes.UFED_MESSAGE_MIME);

    private static final String JSON_EMPTY_OBJECT_KEYWORD = ": {},";

    private static final String JSON_TEMP_ATTR = "message:json";

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
            .setDateFormat(Messages.getString("UFEDChatParser.DateFormat")) //
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

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        xhtml.startDocument();
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
                HtmlParser parser = new HtmlParser();
                parser.parse(stream, handler, metadata, context);
                return;
            }

            InstantMessageHandler messageHandler = new InstantMessageHandler(message, item);
            messageHandler.loadReferences(searcher);
            messageHandler.fillMetadata(metadata);

            if (item instanceof IItem) {
                ((IItem) item).setName(messageHandler.getTitle());
            }

            xhtml.startElement("meta", "charset", "UTF-8");

            renderMessageView(item, message, xhtml);

            storeLinkedHashes(message, metadata);

            if (extractor.shouldParseEmbedded(metadata)) {

                String messageVirtualId = message.getId();

                if (extractActivityLogs) {
                    extractActivityLog(message, messageVirtualId, handler, extractor);
                }
                if (extractAttachments) {
                    extractAttachments(message, messageVirtualId, handler, extractor, false);
                } else {
                    extractAttachments(message, messageVirtualId, handler, extractor, true);
                }
                if (extractSharedContacts) {
                    extractShareContacts(message, messageVirtualId, handler, extractor);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing chat", e);
            throw e;
        } finally {
            xhtml.endDocument();
        }
    }

    private void renderMessageView(IItemReader item, InstantMessage message, XHTMLContentHandler handler) throws SAXException {

        String json = null;
        if (item instanceof IItem) {
            json = (String) ((IItem) item).getTempAttribute(JSON_TEMP_ATTR);
        }

        if (json == null) {

            // serialize to JSON
            json = gson.toJson(message);

            // remove empty objects
            json = Arrays
                    .stream(json.split("\n")) //
                    .filter(line -> !line.endsWith(JSON_EMPTY_OBJECT_KEYWORD)) //
                    .collect(Collectors.joining("\n"));
        }

        // render
        handler.element("pre", json);

        if (item instanceof IItem) {
            // to be reused by MakePreviewTask
            ((IItem) item).setTempAttribute(JSON_TEMP_ATTR, json);
        }
    }

    public static void storeLinkedHashes(InstantMessage message, Metadata metadata) {
        message
                .getAttachments()
                .stream() //
                .map(Attachment::getReferencedFile) //
                .filter(Objects::nonNull) //
                .map(ReferencedFile::getHash) //
                .filter(HashUtils::isValidHash) //
                .forEach(hash -> {
                    metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + hash);
                    if (message.isFromPhoneOwner()) {
                        metadata.add(ExtraProperties.SHARED_HASHES, hash);
                    }
                });
    }

    private void extractActivityLog(InstantMessage message, String messageVirtualId, ContentHandler handler, EmbeddedDocumentExtractor extractor)
            throws SAXException, IOException {

        ChatActivity activity = message.getActivityLog();
        if (activity != null) {

            BaseModelHandler<ChatActivity> activityHandler = new BaseModelHandler<>(activity);

            Metadata activityMeta = activityHandler.createMetadata();
            activityMeta.set(TikaCoreProperties.TITLE, activityHandler.getTitle());
            activityMeta.set(StandardParser.INDEXER_CONTENT_TYPE, activity.getContentType().toString());
            activityMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            activityMeta.set(BasicProps.LENGTH, "");

            extractor.parseEmbedded(new EmptyInputStream(), handler, activityMeta, false);
        }
    }

    private void extractAttachments(InstantMessage message, String messageVirtualId, ContentHandler handler, EmbeddedDocumentExtractor extractor,
            boolean onlyUnreferenced) throws SAXException, IOException {

        for (Attachment attach : message.getAttachments()) {
            if (onlyUnreferenced && attach.getUnreferencedContent() == null) {
                continue;
            }

            AttachmentHandler attachHandler = new AttachmentHandler(attach, null);
            Metadata attachMeta = attachHandler.createMetadata();

            attachMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);

            InputStream inputStream;
            if (attach.getUnreferencedContent() != null) {
                inputStream = new ByteArrayInputStream(attach.getUnreferencedContent());
                attachMeta.set(TikaCoreProperties.RESOURCE_NAME_KEY, attach.getFilename());
                attachMeta.set(StandardParser.INDEXER_CONTENT_TYPE, attach.getAttachmentContentType());
                attachMeta.set(BasicProps.LENGTH, Integer.toString(attach.getUnreferencedContent().length));
            } else {
                inputStream = new EmptyInputStream();
                attachMeta.set(TikaCoreProperties.TITLE, attachHandler.getTitle());
                attachMeta.set(StandardParser.INDEXER_CONTENT_TYPE, attach.getContentType().toString());
                attachMeta.set(BasicProps.LENGTH, "");
            }

            extractor.parseEmbedded(inputStream, handler, attachMeta, false);
        }
    }

    private void extractShareContacts(InstantMessage message, String messageVirtualId, ContentHandler handler, EmbeddedDocumentExtractor extractor)
            throws SAXException, IOException {

        for (Contact shareContact : message.getSharedContacts()) {
            ContactHandler contactHandler = new ContactHandler(shareContact);

            Metadata shareContactMeta = contactHandler.createMetadata();
            shareContactMeta.set(TikaCoreProperties.TITLE, contactHandler.getTitle());
            shareContactMeta.set(StandardParser.INDEXER_CONTENT_TYPE, shareContact.getContentType().toString());
            shareContactMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            shareContactMeta.set(BasicProps.LENGTH, "");
            contactHandler.fillMetadata(shareContactMeta);

            extractor.parseEmbedded(new EmptyInputStream(), handler, shareContactMeta, false);
        }
    }
}
