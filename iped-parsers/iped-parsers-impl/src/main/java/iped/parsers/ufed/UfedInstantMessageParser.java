package iped.parsers.ufed;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.gson.GsonBuilder;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.ChatActivity;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.reference.ReferencedFile;
import iped.parsers.ufed.util.UfedChatMetadataUtils;
import iped.parsers.ufed.util.UfedChatStringUtils;
import iped.parsers.ufed.util.UfedModelReferenceLoader;
import iped.parsers.ufed.util.UfedUtils;
import iped.parsers.util.HashUtils;
import iped.parsers.util.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

public class UfedInstantMessageParser extends AbstractParser {

    private static final long serialVersionUID = -4738095481615972119L;

    private static Logger logger = LoggerFactory.getLogger(UfedInstantMessageParser.class);

    public static final MediaType UFED_INSTANT_MESSAGE_MIME = MediaType.application("x-ufed-instantmessage");

    private static Set<MediaType> SUPPORTED_TYPES = Set.of(UFED_INSTANT_MESSAGE_MIME);

    private GsonBuilder gsonBuilder = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .setDateFormat(Messages.getString("UFEDChatParser.DateFormat"));

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
    public void parse(InputStream inputStream, ContentHandler handler, Metadata metadata, ParseContext context)
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
            if (item instanceof IItem) {
                message = (InstantMessage) ((IItem) item).getTempAttribute(UfedUtils.MODEL_TEMP_ATTRIBUTE);
            }
            if (message == null) {
                return;
            }

            UfedModelReferenceLoader.build(message).load(searcher);

            UfedChatMetadataUtils.fillInstantMessageMetadata(message, metadata);

            renderMessageView(message, xhtml);

            storeLinkedHashes(message, metadata);

            if (extractor.shouldParseEmbedded(metadata)) {

                String messageVirtualId = message.getId();

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
        } catch (Exception e) {
            logger.error("Error processing chat", e);
            throw e;
        } finally {
            xhtml.endDocument();
        }
    }

    private void renderMessageView(InstantMessage message, XHTMLContentHandler handler) throws SAXException {

        String json = gsonBuilder.create().toJson(message);

        handler.element("pre", json);
    }

    private void storeLinkedHashes(InstantMessage message, Metadata metadata) {
        message.getAttachments().stream() //
                .map(Attachment::getReferencedFile) //
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
            Metadata activityMeta = UfedChatMetadataUtils.createGenericMetadata(activity);
            activityMeta.set(TikaCoreProperties.TITLE, "ChatActivity_" + activity.getId());
            activityMeta.set(StandardParser.INDEXER_CONTENT_TYPE, activity.getContentType().toString());
            activityMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            activityMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, activityMeta, false);
        }
    }

    private void extractAttachments(InstantMessage message, String messageVirtualId, ContentHandler handler, EmbeddedDocumentExtractor extractor)
            throws SAXException, IOException {

        for (Attachment attach : message.getAttachments()) {
            Metadata attachMeta = UfedChatMetadataUtils.createGenericMetadata(attach);
            attachMeta.set(TikaCoreProperties.TITLE, UfedChatStringUtils.getAttachmentTitle(attach));
            attachMeta.set(StandardParser.INDEXER_CONTENT_TYPE, attach.getContentType().toString());
            attachMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            attachMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, attachMeta, false);
        }
    }

    private void extractShareContacts(InstantMessage message, String messageVirtualId, ContentHandler handler, EmbeddedDocumentExtractor extractor)
            throws SAXException, IOException {

        for (Contact shareContact : message.getSharedContacts()) {
            Metadata shareContactMeta = UfedChatMetadataUtils.createGenericMetadata(shareContact);
            shareContactMeta.set(TikaCoreProperties.TITLE, UfedChatStringUtils.getContactTitle(shareContact));
            shareContactMeta.set(StandardParser.INDEXER_CONTENT_TYPE, shareContact.getContentType().toString());
            shareContactMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, messageVirtualId);
            shareContactMeta.set(BasicProps.LENGTH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, shareContactMeta, false);
        }
    }
}
