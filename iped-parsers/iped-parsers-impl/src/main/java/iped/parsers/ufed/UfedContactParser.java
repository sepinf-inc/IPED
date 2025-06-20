package iped.parsers.ufed;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.ContactPhoto;
import iped.parsers.ufed.util.UfedChatMetadataUtils;
import iped.parsers.ufed.util.UfedModelReferenceLoader;
import iped.parsers.ufed.util.UfedUtils;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;

public class UfedContactParser extends AbstractParser {

    private static final long serialVersionUID = -4738095481615972119L;

    private static Logger logger = LoggerFactory.getLogger(UfedContactParser.class);

    public static final MediaType UFED_CONTACT_MIME = MediaType.application("x-ufed-contact");

    private static Set<MediaType> SUPPORTED_TYPES = Set.of(UFED_CONTACT_MIME);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream inputStream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemReader item = context.get(IItemReader.class);

            if (item == null || searcher == null) {
                return;
            }

            Contact contact = null;
            if (item instanceof IItem) {
                contact = (Contact) ((IItem) item).getTempAttribute(UfedUtils.MODEL_TEMP_ATTRIBUTE);
            }
            if (contact == null) {
                return;
            }

            UfedModelReferenceLoader.build(contact).load(searcher);

            UfedChatMetadataUtils.fillCommonMetadata(contact, metadata);

            renderContactView(contact, xhtml);

        } catch (Exception e) {
            logger.error("Error processing chat", e);
            throw e;
        } finally {
            xhtml.endDocument();
        }
    }

    private void renderContactView(Contact contact, XHTMLContentHandler xhtml) throws SAXException {

        xhtml.startElement("table");

        if (!contact.getPhotos().isEmpty()) {
            xhtml.startElement("tr");
            xhtml.element("th", "Photo");
            xhtml.startElement("td");
            for (ContactPhoto photo : contact.getPhotos()) {
                byte[] photoData = photo.getImageData();
                if (photoData != null) {
                    xhtml.startElement("img", "src", "data:image/jpg;base64," + Base64.encodeBase64String(photoData));
                    xhtml.element("br", null);
                }
            }
            xhtml.endElement("td");
            xhtml.endElement("tr");
        }

        // Contact Entry - PhoneNumber
        if (contact.getPhoneNumber().isPresent()) {
            renderContactEntry(contact.getPhoneNumber().get(), xhtml);
        }

        // Contact Entry - UserID
        if (contact.getUserID().isPresent()) {
            renderContactEntry(contact.getUserID().get(), xhtml);
        }

        // Contact Entry - Others
        for (ContactEntry entry : contact.getOtherEntries().values()) {
            renderContactEntry(entry, xhtml);
        }

        // Contact Fields
        for (Entry<String, Object> e : contact.getFields().entrySet()) {
            xhtml.startElement("tr");
            xhtml.element("th", iped.utils.StringUtil.convertCamelCaseToSpaces(e.getKey()));
            xhtml.startElement("td");
            String valueStr;
            if (e.getValue() instanceof Date) {
                valueStr = DateUtil.dateToString((Date) e.getValue());
            } else {
                valueStr = e.getValue().toString();
            }
            xhtml.element("td", valueStr);
            xhtml.endElement("tr");
        }

        // Contact Model Fields
        for (Entry<String, BaseModel> e : contact.getOtherModelFields().entrySet()) {
            xhtml.startElement("tr");
            xhtml.element("th", iped.utils.StringUtil.convertCamelCaseToSpaces(e.getKey()));
            xhtml.startElement("td");
            xhtml.element("td", e.getValue().toString());
            xhtml.endElement("tr");
        }

        xhtml.endElement("table");
    }

    private void renderContactEntry(ContactEntry entry, XHTMLContentHandler xhtml) throws SAXException {
        xhtml.startElement("tr");
        xhtml.element("th", StringUtils.firstNonBlank(entry.getDomain(), entry.getModelType()));
        xhtml.startElement("td");
        xhtml.element("b", entry.getValue());
        if (StringUtils.isNotBlank(entry.getCategory())) {
            xhtml.element("i", " (" + entry.getCategory() + ")");
        }
        xhtml.endElement("td");
        xhtml.endElement("tr");
    }
}
