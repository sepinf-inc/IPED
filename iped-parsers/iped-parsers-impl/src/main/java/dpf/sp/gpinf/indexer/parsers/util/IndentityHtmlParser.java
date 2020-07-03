package dpf.sp.gpinf.indexer.parsers.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Schema;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class IndentityHtmlParser {
    
    /**
     * HTML schema singleton used to amortise the heavy instantiation time.
     */
    public static final Schema HTML_SCHEMA = new HTMLSchema();
    
    public void parse(InputStream is, ParseContext context, ContentHandler handler) throws IOException, SAXException {
        
        // this part of the code was adapted from org.apache.tika.parser.html.HtmlParser
        // Get the HTML mapper from the parse context
        // Parse the HTML document
        org.ccil.cowan.tagsoup.Parser parser =
                new org.ccil.cowan.tagsoup.Parser();

        // Use schema from context or default
        Schema schema = context.get(Schema.class, HTML_SCHEMA);
        // TIKA-528: Reuse share schema to avoid heavy instantiation
        parser.setProperty(
                org.ccil.cowan.tagsoup.Parser.schemaProperty, schema);
        // TIKA-599: Shared schema is thread-safe only if bogons are ignored
        parser.setFeature(
                org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, true);
        // Ignore extra Whitespaces
        parser.setFeature(
                org.ccil.cowan.tagsoup.Parser.ignorableWhitespaceFeature, true);

        parser.setContentHandler(new XHTMLDowngradeHandler(handler));
        
        InputSource source = new InputSource(is);
        source.setEncoding(StandardCharsets.UTF_8.toString());
        parser.parse(source);
        
    }
    
    /**
     * Content handler decorator that downgrades XHTML elements to
     * old-style HTML elements before passing them on to the decorated
     * content handler. This downgrading consists of dropping all namespaces
     * (and namespaced attributes) and uppercasing all element names.
     * Used by the {@link HtmlParser} to make all incoming HTML look the same.
     * 
     * Copied from org.apache.tika.parser.html.XHTMLDowngradeHandler with some adjusts:
     *  - drop HTML elements
     *  - drop BODY elements
     *  - drop HEAD elements
     */ 
    public static class XHTMLDowngradeHandler extends ContentHandlerDecorator {
        private static Set<String> IGNORE_ELEMENTS = new HashSet<>();
        
        static {
            IGNORE_ELEMENTS.add("HTML");
            IGNORE_ELEMENTS.add("HEAD");
            IGNORE_ELEMENTS.add("BODY");
        }

        public XHTMLDowngradeHandler(ContentHandler handler) {
            super(handler);
        }

        @Override
        public void startElement(
                String uri, String localName, String name, Attributes atts)
                throws SAXException {
            String upper = localName.toUpperCase(Locale.ENGLISH);
            if (IGNORE_ELEMENTS.contains(upper)) {
                return;
            }

            AttributesImpl attributes = new AttributesImpl();
            for (int i = 0; i < atts.getLength(); i++) {
                String auri = atts.getURI(i);
                String local = atts.getLocalName(i);
                String qname = atts.getQName(i);
                if (XMLConstants.NULL_NS_URI.equals(auri)
                        && !local.equals(XMLConstants.XMLNS_ATTRIBUTE)
                        && !qname.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                    attributes.addAttribute(
                            auri, local, qname, atts.getType(i), atts.getValue(i));
                }
            }

            super.startElement(XMLConstants.NULL_NS_URI, upper, upper, attributes);
        }

        @Override
        public void endElement(String uri, String localName, String name)
                throws SAXException {
            String upper = localName.toUpperCase(Locale.ENGLISH);
            if (IGNORE_ELEMENTS.contains(upper)) {
                return;
            }
            super.endElement(XMLConstants.NULL_NS_URI, upper, upper);
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
        }

        @Override
        public void endPrefixMapping(String prefix) {
        }

    }

}
