package iped.parsers.ufed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.sax.xpath.Matcher;
import org.apache.tika.sax.xpath.MatchingContentHandler;
import org.apache.tika.sax.xpath.XPathParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import iped.data.IItemReader;
import iped.localization.LocaleResolver;
import iped.parsers.chat.EmailPartyStringBuilder;
import iped.parsers.standard.StandardParser;
import iped.parsers.ufed.handler.AttachmentHandler;
import iped.parsers.ufed.handler.EmailHandler;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.Email;
import iped.parsers.ufed.model.Party;
import iped.parsers.util.Messages;
import iped.parsers.util.MetadataUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

public class UfedEmailParser extends AbstractParser {

    private static final long serialVersionUID = -4583682558447807906L;

    private static final Logger logger = LoggerFactory.getLogger(UfedEmailParser.class);

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaTypes.UFED_EMAIL_MIME);

    private static final Set<String> formattingTags = new HashSet<>(Arrays
            .asList("b", "strong", "i", "em", "u", "mark", "s", "del", "ins", "code", "kbd", "samp", "var", "sub", "sup", "small"));

    private static final String COLON = ":";
    private static final String SRC_ATTR = "src";

    private DateTimeFormatter formatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.LONG)
            .localizedBy(LocaleResolver.getLocale())
            .withZone(ZoneId.systemDefault());

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
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

            if (item == null || searcher == null) {
                return;
            }

            final Email email;
            if (stream instanceof TikaInputStream) {
                email = (Email) TikaInputStream.cast(stream).getOpenContainer();
            } else {
                email = null;
            }
            if (email == null) {
                // view is already generated — proceed to parse with HtmlParser
                HtmlParser parser = new HtmlParser();
                parser.parse(stream, handler, metadata, context);
                return;
            }

            EmailHandler emailHandler = new EmailHandler(email, item);
            emailHandler.loadReferences(searcher);
            emailHandler.fillMetadata(metadata);
            emailHandler.addLinkedItemsAndSharedHashes(metadata, searcher);
            emailHandler.updateItemNameWithTitle();

            extractAttachments(email, handler, extractor);

            // parse email content into the XHTML handler
            Metadata xhtmlMetadata = MetadataUtil.clone(metadata);
            xhtmlMetadata.remove(ExtraProperties.MESSAGE_BODY);
            xhtmlMetadata.remove(ExtraProperties.UFED_JUMP_TARGETS);
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, xhtmlMetadata) ;
            xhtml.startDocument();
            try {
                xhtml.startElement("style");
                xhtml.characters("th, td { padding: 1px; }" + //
                        "th { padding-right: 6px; text-align: right; vertical-align: top; text-wrap-mode: nowrap; }" + //
                        "td { text-align: left; }" + //
                        "ul { margin: 0; padding: 0; list-style-position: inside; }");
                xhtml.endElement("style");

                parseHeaders(email, xhtml);

                parseBody(email, xhtml);

            } finally {
                xhtml.endDocument();
            }
        } catch (Exception e) {
            logger.error("Error processing Email", e);
            throw e;
        }
    }

    private void extractAttachments(Email email, ContentHandler handler, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        for (Attachment attach : email.getAttachments()) {

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

    private void parseHeaders(Email email, XHTMLContentHandler xhtml) throws SAXException, IOException {

        xhtml.startElement("table");
        if (email.getFrom().isPresent()) {
            parseParties(Messages.getString("UfedEmailParser.From"), Collections.singletonList(email.getFrom().get()), xhtml);
        }
        parseParties(Messages.getString("UfedEmailParser.To"), email.getTo(), xhtml);
        parseParties(Messages.getString("UfedEmailParser.Cc"), email.getCc(), xhtml);
        parseParties(Messages.getString("UfedEmailParser.Bcc"), email.getBcc(), xhtml);

        Date timeStamp = email.getTimeStamp();
        String subject = email.getSubject();
        if (timeStamp != null) {
            parseField(Messages.getString("UfedEmailParser.Date"), formatter.format(timeStamp.toInstant()), xhtml);
        }
        if (subject != null) {
            parseField(Messages.getString("UfedEmailParser.Subject"), subject, xhtml);
        }

        parseAttachments(Messages.getString("UfedEmailParser.Attachments"), email.getAttachments(), xhtml);

        xhtml.endElement("table");
        xhtml.startElement("hr");
        xhtml.endElement("hr");
    }

    private void parseField(String field, String value, XHTMLContentHandler xhtml) throws SAXException {
        xhtml.startElement("tr");
        xhtml.element("th", field + COLON);
        xhtml.element("td", value);
        xhtml.endElement("tr");
    }

    private void parseParties(String field, List<Party> parties, XHTMLContentHandler xhtml) throws SAXException {
        if (parties.isEmpty()) {
            return;
        }
        String partiesStr = parties.stream()
                .map(p -> new EmailPartyStringBuilder().withParty(p).build())
                .collect(Collectors.joining(",\n"));
        parseField(field, partiesStr, xhtml);
    }

    private void parseAttachments(String field, List<Attachment> attachments, XHTMLContentHandler xhtml) throws SAXException {

        attachments = attachments.stream()
                .filter(a -> a.getFilename() != null && a.isFileRelated())
                .collect(Collectors.toList());

        if (attachments.isEmpty()) {
            return;
        }

        xhtml.startElement("tr");
        xhtml.element("th", field + " (" + attachments.size() + ")" + COLON);
        xhtml.startElement("td");
        xhtml.startElement("ul");
        for (Attachment attach : attachments) {
            xhtml.element("li", attach.getFilename());
        }
        xhtml.endElement("ul");
        xhtml.endElement("td");
        xhtml.endElement("tr");
    }

    private void parseBody(Email email, XHTMLContentHandler xhtml) throws IOException, SAXException, TikaException {
        String body = email.getBody();
        boolean isFromSnippet = false;
        if (StringUtils.isBlank(body)) {
            body = email.getSnippet();
            isFromSnippet = true;
        }

        if (StringUtils.isBlank(body)) {
            return;
        }

        /**
         * An extended BodyContentHandler that processes HTML content to embed email attachments directly into the output.
         * It overrides the startElement method to find elements with a 'src' attribute (like <img> tags)
         * that reference an attachment. When a match is found, it replaces the 'src' attribute's value
         * with a Base64-encoded data URI of the attachment's content.
         */
        Matcher MATCHER =
                new XPathParser("xhtml", XHTMLContentHandler.XHTML).parse("/xhtml:html/xhtml:body/xhtml:body/descendant::node()");
        MatchingContentHandler bodyHandler = new MatchingContentHandler(xhtml, MATCHER) {

            /**
             * Intercepts the start of an XML element to check for 'src' attributes that can be replaced
             * with inline attachment data.
             *
             * The matching logic for attachments includes:
             * - Exact URL match.
             * - Content-ID (cid:) match with the attachment filename.
             * - A fallback for single-attachment emails with a 'cid:' link.
             * - A special-case comparison for Gmail URLs, ignoring certain parameters.
             */
            @Override
            public void startElement(String uri, String localName, String name, Attributes atts) throws SAXException {
                String src = atts.getValue(SRC_ATTR);
                if (src != null) {
                    // Attempt to find a matching attachment for the given src attribute.
                    String newSrc = email.getAttachments().stream()
                    .filter(a -> src.equals(a.getURL())
                            || src.startsWith("cid:" + a.getFilename())
                            || src.startsWith("cid:") && email.getAttachments().size() == 1
                            || "Gmail".equals(email.getSource()) && StringUtils.substringBefore(src, "&view=").equals(StringUtils.substringBefore(a.getURL(), "&view=")))
                    .map(a -> {
                        byte[] attachData = null;
                        String contentType = null;
                        if (a.getReferencedFile() != null) {
                            try {
                                attachData = IOUtils.toByteArray(a.getReferencedFile().getItem().getBufferedInputStream());
                                contentType = StringUtils.firstNonBlank(a.getContentType(), a.getReferencedFile().getItem().getMediaType().toString());
                            } catch (IOException e) {
                                logger.warn("Error reading attachment referenced file: " + a, e);
                            }
                        } else if (a.getUnreferencedContent() != null) {
                            attachData = a.getUnreferencedContent();
                            contentType = a.getContentType();
                        }
                        if (attachData != null) {
                            return String.format("data:%s;base64,%s", StringUtils.defaultString(contentType), Base64.encodeBase64String(attachData));
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

                    // If a data URI was successfully created, replace the original src attribute.
                    if (newSrc != null) {
                        logger.info("Found replaced src attribute: " + email + " / " + src);
                        AttributesImpl newAtts = new AttributesImpl(atts);
                        newAtts.removeAttribute(atts.getIndex(SRC_ATTR));
                        newAtts.addAttribute("", SRC_ATTR, SRC_ATTR, "", newSrc);
                        atts = newAtts;
                    }
                }

                super.startElement(uri, localName, name, atts);

                // Prevents self-closing of formatting tags
                if (formattingTags.contains(localName)) {
                    xhtml.characters(new char[0], 0, 0);
                }
            }
        };

        // add html break line if snippet
        if (isFromSnippet) {
            body = body.replace("\n", "<br>\n");
        }

        ParseContext context = new ParseContext();
        context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);

        HtmlParser parser = new HtmlParser();
        parser.parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), bodyHandler, new Metadata(), context);
    }
}