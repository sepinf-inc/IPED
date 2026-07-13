package iped.parsers.ufed;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.ufed.handler.AccountableHandler;
import iped.parsers.ufed.handler.BaseModelHandler;
import iped.parsers.ufed.handler.ContactHandler;
import iped.parsers.ufed.handler.UserAccountHandler;
import iped.parsers.ufed.model.Accountable;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.ContactPhoto;
import iped.parsers.ufed.model.UserAccount;
import iped.parsers.ufed.reference.ReferencedFile;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;

public class UfedAccountableParser extends AbstractParser {

    private static final long serialVersionUID = -4738095481615972119L;

    private static final Logger logger = LoggerFactory.getLogger(UfedAccountableParser.class);

    private static Set<MediaType> SUPPORTED_TYPES = Set.of(
            MediaTypes.UFED_CONTACT_MIME,
            MediaTypes.UFED_USER_ACCOUNT_MIME);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        try {
            IItemReader item = context.get(IItemReader.class);
            IItemSearcher searcher = context.get(IItemSearcher.class);
            if (item == null || searcher == null) {
                return;
            }

            Accountable accountable = null;
            if (stream instanceof TikaInputStream) {
                accountable = (Accountable) TikaInputStream.cast(stream).getOpenContainer();
            }
            if (accountable == null) {
                // view is already generated — proceed to parse with HtmlParser
                HtmlParser parser = new HtmlParser();
                parser.parse(stream, handler, metadata, context);
                return;
            }

            AccountableHandler<?> accountableHandler;
            if (accountable instanceof Contact) {
                accountableHandler = new ContactHandler((Contact) accountable, item);
            } else if (accountable instanceof UserAccount) {
                accountableHandler = new UserAccountHandler((UserAccount) accountable, item);
            } else {
                accountableHandler = new AccountableHandler<>(accountable, item);
            }
            accountableHandler.fillMetadata(metadata);
            accountableHandler.loadReferences(searcher);
            accountableHandler.addLinkedItemsAndSharedHashes(metadata, searcher);
            accountableHandler.updateItemNameWithTitle();

            // update category for shared contacts
            if (accountable instanceof Contact && "Shared".equalsIgnoreCase(((Contact) accountable).getType()) && item instanceof IItem) {
                ((IItem) item).setCategory("Shared Contacts");
            }

            // parse accountable content into the XHTML handler
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            try {
                xhtml.startElement("style");
                xhtml.characters(
                        "table { border-collapse: collapse; }" + //
                        "table, th, td { border: 1px solid black; }" + //
                        "th, td { padding: 5px; text-align: left; }" + //
                        "th { width: 150px; min-width: 150px; }" + "td img { max-width: 300px; margin: 4px }" + //
                        ".ellipsis { display: inline-block; max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }" + //
                        ".ellipsis:hover { white-space: normal; max-width: initial; }" //
                );
                xhtml.endElement("style");

                parserAccountable(accountable, xhtml);

            } finally {
                xhtml.endDocument();
            }

        } catch (Exception e) {
            logger.error("Error processing Contact/UserAccount", e);
            throw e;
        }
    }

    private void parserAccountable(Accountable contact, XHTMLContentHandler xhtml) throws SAXException, IOException {

        xhtml.startElement("table");

        // Contact Photo
        List<ContactPhoto> validPhotos = contact.getPhotos().stream()
                .filter(p -> !StringUtils.isAllBlank(p.getPhotoNodeId(), p.getUrl()))
                .collect(Collectors.toList());
        if (!validPhotos.isEmpty()) {
            xhtml.startElement("tr");
            xhtml.element("th", "Photo");
            xhtml.startElement("td");
            HashSet<String> seenPhoto = new HashSet<>();
            for (ContactPhoto photo : contact.getPhotos()) {
                byte[] photoData = photo.getReferencedFile().map(ReferencedFile::getThumb).orElse(null);
                if (photoData == null && StringUtils.isNotEmpty(photo.getPhotoNodeId())) {
                    photoData = IOUtils.resourceToByteArray("/iped/parsers/common/img/avatar-unavailable.png");
                }
                if (photoData != null) {
                    String hash = DigestUtils.md5Hex(photoData);
                    if (seenPhoto.add(hash)) {
                        xhtml.startElement("img", "src", "data:image/jpg;base64," + Base64.encodeBase64String(photoData));
                        xhtml.startElement("br");
                    }
                }
                if (StringUtils.isNotBlank(photo.getUrl())) {
                    xhtml.element("p", photo.getUrl());
                }
            }
            xhtml.endElement("td");
            xhtml.endElement("tr");
        }

        // Contact Fields
        for (Entry<String, Object> e : contact.getFields().entrySet()) {
            if (BaseModelHandler.ignoreFields.contains(e.getKey())) {
                continue;
            }
            xhtml.startElement("tr");
            xhtml.element("th", iped.utils.StringUtil.convertCamelCaseToSpaces(e.getKey()));
            String valueStr;
            if (e.getValue() instanceof Date) {
                valueStr = DateUtil.dateToString((Date) e.getValue());
            } else {
                valueStr = e.getValue().toString();
            }
            xhtml.element("td", valueStr);
            xhtml.endElement("tr");
        }

        // Contact Entries
        for (List<ContactEntry> entryList : contact.getContactEntries().values()) {
            for (ContactEntry entry: entryList) {
                xhtml.startElement("tr");
                xhtml.element("th", StringUtils.firstNonBlank(entry.getDomain(), entry.getModelType()));
                xhtml.startElement("td");
                xhtml.startElement("span", "class", "ellipsis");
                xhtml.characters(entry.getValue());
                xhtml.endElement("span");
                if (StringUtils.isNotBlank(entry.getCategory())) {
                    xhtml.element("i", " (" + entry.getCategory() + ")");
                }
                xhtml.endElement("td");
                xhtml.endElement("tr");
            }
        }

        // Contact Model Fields
        for (Entry<String, List<BaseModel>> e : contact.getOtherModelFields().entrySet()) {
            xhtml.startElement("tr");
            xhtml.element("th", iped.utils.StringUtil.convertCamelCaseToSpaces(e.getKey()));
            xhtml.startElement("td");

            int i = 0;
            for (BaseModel fieldValue : e.getValue()) {
                if (i++ > 0) {
                    xhtml.startElement("hr");
                    xhtml.endElement("hr");
                }
                int j = 0;
                for (Entry<String, Object> fieldEntry : fieldValue.getFields().entrySet()) {
                    if (j++ > 0) {
                        xhtml.startElement("br");
                        xhtml.endElement("br");
                    }
                    xhtml.element("b", fieldEntry.getKey() + ": ");
                    xhtml.characters(fieldEntry.getValue().toString());
                }
            }
            xhtml.endElement("td");
            xhtml.endElement("tr");
        }

        xhtml.endElement("table");
    }
}
