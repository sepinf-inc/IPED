package dpf.mg.udi.gpinf.vcardparser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;

import org.apache.commons.io.IOUtils;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.DefaultHtmlMapper;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.XHTMLContentHandler;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Schema;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import dpf.mg.udi.gpinf.whatsappextractor.Util;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.HtmlToHtmlHandler;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.io.chain.ChainingHtmlWriter;
import ezvcard.property.Photo;
import ezvcard.property.RawProperty;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;
import ezvcard.property.TextListProperty;
import ezvcard.property.TextProperty;
import ezvcard.property.VCardProperty;
import freemarker.template.Configuration;
import freemarker.template.Template;

public class VCardParser extends AbstractParser {

    private static final long serialVersionUID = -7436203736342471550L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.text("x-vcard")); //$NON-NLS-1$
    
    private static final Configuration TEMPLATE_CFG = new Configuration(Configuration.VERSION_2_3_23);
    private static Template TEMPLATE = null;
    /**
     * HTML schema singleton used to amortise the heavy instantiation time.
     */
    private static final Schema HTML_SCHEMA = new HTMLSchema();
    
    static {
        TEMPLATE_CFG.setClassForTemplateLoading(VCardParser.class, "");
        TEMPLATE_CFG.setWhitespaceStripping(true);
        try {
            TEMPLATE = TEMPLATE_CFG.getTemplate("hcard-template.html");
        } catch (Exception e) {
        }
    }
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        String text = readInputStream(stream);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        try {
            xhtml.startDocument();
            List<VCard> vcards = Ezvcard.parse(text).all();

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try(PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8))){
                new ChainingHtmlWriter(vcards).template(TEMPLATE).go(out);
            }
            InputStream is = new ByteArrayInputStream(bout.toByteArray());
            
            // this part of the code was adapted from org.apache.tika.parser.html.HtmlParser
            // Get the HTML mapper from the parse context
            HtmlMapper mapper =
                    context.get(HtmlMapper.class, DefaultHtmlMapper.INSTANCE);

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
            parser.setContentHandler(new XHTMLDowngradeHandler(
                    new HtmlToHtmlHandler(mapper, xhtml, metadata, context)));
            parser.parse(new InputSource(is));
        } finally {
            xhtml.endDocument();
        }
    }

    public static void printHtmlFromString(PrintWriter out, String text) {
        List<VCard> vcards = Ezvcard.parse(text).all();
        if (!vcards.isEmpty()) {
            HtmlGenerator gen = new HtmlGenerator(out);
            for (VCard vcard : vcards) {
                gen.generateVcardHtml(vcard);
            }
        }
    }

    private static class HtmlGenerator {
        private PrintWriter out;
        private boolean inTable = false;
        private boolean inRow = false;
        private boolean inCell = false;
        private Set<VCardProperty> propertiesParsed = new HashSet<>();

        public HtmlGenerator(PrintWriter out) {
            this.out = out;
        }

        public void generateVcardHtml(VCard vcard) {
            newTable();
            generatePhotos(vcard.getPhotos());
            generatePropertyText(Messages.getString("VCardParser.FormattedName"), vcard.getFormattedNames()); //$NON-NLS-1$
            generatePropertyName(Messages.getString("VCardParser.Name"), vcard.getStructuredNames()); //$NON-NLS-1$
            generatePropertyTextList(Messages.getString("VCardParser.Nickname"), vcard.getNicknames()); //$NON-NLS-1$
            generatePropertyText(Messages.getString("VCardParser.Email"), vcard.getEmails()); //$NON-NLS-1$
            generatePropertyTelephone(Messages.getString("VCardParser.Telephone"), vcard.getTelephoneNumbers()); //$NON-NLS-1$
            generatePropertyTextList(Messages.getString("VCardParser.Organization"), vcard.getOrganizations()); //$NON-NLS-1$
            generatePropertyText(Messages.getString("VCardParser.Notes"), vcard.getNotes()); //$NON-NLS-1$
            addPropertyParsed(vcard.getFormattedNames());
            addPropertyParsed(vcard.getNicknames());
            addPropertyParsed(vcard.getEmails());
            addPropertyParsed(vcard.getTelephoneNumbers());
            addPropertyParsed(vcard.getOrganizations());
            addPropertyParsed(vcard.getNotes());
            addPropertyParsed(vcard.getPhotos());
            addPropertyParsed(vcard.getStructuredNames());
            generateRemainingProperties(propertiesParsed, vcard);
            closeTable();
        }

        private static String getClassName(VCardProperty prop) {
            return prop.getClass().getSimpleName();
        }

        @SuppressWarnings("unchecked")
        private void generateRemainingProperties(Set<VCardProperty> propertiesParsed, VCard vcard) {
            for (VCardProperty prop : vcard.getProperties()) {
                if (!propertiesParsed.contains(prop)) {
                    if (prop instanceof List) {
                        List<VCardProperty> plist = (List<VCardProperty>) prop;
                        if (!plist.isEmpty()) {
                            VCardProperty p0 = plist.get(0);
                            if (p0 instanceof TextProperty) {
                                generatePropertyText(getClassName(p0), (List<TextProperty>) prop);
                            } else if (p0 instanceof Telephone) {
                                generatePropertyTelephone(getClassName(p0), (List<Telephone>) prop);
                            } else if (p0 instanceof TextListProperty) {
                                generatePropertyTextList(getClassName(p0), (List<TextListProperty>) prop);
                            }
                        }
                    } else if (prop instanceof Telephone) {
                        generatePropertyTelephone(getClassName(prop), (Telephone) prop);
                    } else if (prop instanceof RawProperty) {
                        generatePropertyRaw((RawProperty) prop);
                    } else if (prop instanceof TextProperty) {
                        generatePropertyText(getClassName(prop), (TextProperty) prop);
                    } else if (prop instanceof TextListProperty) {
                        List<TextListProperty> propList = Arrays.asList((TextListProperty) prop);
                        generatePropertyTextList(getClassName(prop), propList);
                    }
                }
            }

        }

        private void addPropertyParsed(VCardProperty prop) {
            if (prop != null) {
                propertiesParsed.add(prop);
            }
        }

        private void addPropertyParsed(List<? extends VCardProperty> props) {
            if (props != null) {
                for (VCardProperty prop : props) {
                    addPropertyParsed(prop);
                }
            }
        }

        private void generatePropertyText(String propName, List<? extends TextProperty> props) {
            boolean first = true;
            if (props == null || props.isEmpty())
                return;
            newRow();
            println(propName);
            newCell();
            for (TextProperty prop : props) {
                if (first) {
                    first = false;
                } else {
                    println(";<br/>"); //$NON-NLS-1$
                }
                if (prop.getValue() != null)
                    print(prop.getValue());
            }
        }

        private void generatePropertyName(String propName, List<? extends StructuredName> names) {
            boolean first = true;
            if (names == null || names.isEmpty())
                return;
            newRow();
            println(propName);
            newCell();

            for (StructuredName name : names) {
                if (first) {
                    first = false;
                } else {
                    println(";<br/>"); //$NON-NLS-1$
                }
                boolean ff = true;
                for (String pre : name.getPrefixes()) {
                    if (ff) {
                        ff = false;
                    } else {
                        print(" "); //$NON-NLS-1$
                    }
                    print(pre);
                }
                if (!ff) {
                    print(" "); //$NON-NLS-1$
                }
                print(name.getGiven());
                print(" "); //$NON-NLS-1$
                print(name.getFamily());
                for (String suf : name.getSuffixes()) {
                    print(" "); //$NON-NLS-1$
                    print(suf);
                }

            }
        }

        private void generatePropertyTelephone(String propName, List<? extends Telephone> props) {
            boolean first = true;
            if (props == null || props.isEmpty())
                return;
            newRow();
            println(propName);
            newCell();
            for (Telephone prop : props) {
                if (first) {
                    first = false;
                } else {
                    println(";<br/>"); //$NON-NLS-1$
                }
                if (prop.getText() != null)
                    print(prop.getText());
            }
        }

        private void generatePropertyTextList(String propName, List<? extends TextListProperty> props) {
            boolean first = true;
            if (props == null || props.isEmpty())
                return;
            newRow();
            println(propName);
            newCell();
            for (TextListProperty prop : props) {
                if (first) {
                    first = false;
                } else {
                    println(";<br/>\n"); //$NON-NLS-1$
                }
                print(expandTextListProp(prop));
            }
        }

        private static String expandTextListProp(TextListProperty prop) {
            boolean first = true;
            String result = ""; //$NON-NLS-1$
            for (String p : prop.getValues()) {
                if (first) {
                    first = false;
                } else {
                    result += ";"; //$NON-NLS-1$
                }
                result += p;
            }
            return result;
        }

        private void generatePropertyText(String propName, TextProperty prop) {
            if (prop == null || prop.getValue() == null)
                return;
            printRow(propName, prop.getValue());
        }

        private void generatePropertyRaw(RawProperty prop) {
            if (prop == null || prop.getValue() == null)
                return;
            printRow(prop.getPropertyName(), prop.getValue());
        }

        private void generatePropertyTelephone(String propName, Telephone prop) {
            if (prop == null || prop.getText() == null)
                return;
            printRow(propName, prop.getText());
        }

        private void generatePhoto(Photo photo) {
            if (photo == null)
                return;
            newRow();
            println(Messages.getString("VCardParser.Photo")); //$NON-NLS-1$
            newCell();
            byte[] data = photo.getData();
            if (data != null) {
                println("<img src=\"data:image/jpg;base64," + Util.encodeBase64(data) //$NON-NLS-1$
                        + "\" width=\"112\"/>"); //$NON-NLS-1$
            } else {
                String url = photo.getUrl();
                if (url != null) {
                    println("<img src=\"" + url + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }

        private void generatePhotos(List<Photo> photos) {
            if (photos == null || photos.isEmpty())
                return;
            for (Photo photo : photos)
                generatePhoto(photo);
        }

        private void printRow(String cell1, String cell2) {
            newRow();
            println(cell1);
            newCell();
            println(cell2);
            closeRow();
        }

        private void print(String data) {
            if (data == null) {
                return;
            }
            if (!inCell) {
                newCell();
            }
            out.print(data);
        }

        private void println(String data) {
            if (data == null) {
                return;
            }
            if (!inCell) {
                newCell();
            }
            out.println(data);
        }

        private void newTable() {
            if (inTable) {
                closeTable();
            }
            out.println("<table class=\"tab\">"); //$NON-NLS-1$
            inTable = true;
        }

        private void newRow() {
            if (!inTable) {
                newTable();
            }
            if (inRow) {
                closeRow();
            }
            out.println("<tr>"); //$NON-NLS-1$
            inRow = true;
        }

        private void newCell() {
            if (inCell) {
                closeCell();
            }
            if (!inRow) {
                newRow();
            }
            out.println("<td class=\"cel\">"); //$NON-NLS-1$
            inCell = true;
        }

        private void closeCell() {
            if (inCell) {
                out.println("</td>"); //$NON-NLS-1$
                inCell = false;
            }
        }

        private void closeRow() {
            if (inRow) {
                closeCell();
                out.println("</tr>"); //$NON-NLS-1$
                inRow = false;
            }
        }

        private void closeTable() {
            if (inTable) {
                closeRow();
                out.println("</table>"); //$NON-NLS-1$
                inTable = false;
            }
        }
    }

    private static String readInputStream(InputStream is) throws IOException, TikaException {
        AutoDetectReader reader = new AutoDetectReader(is);
        return IOUtils.toString(reader);
    }

    public static final String HTML_STYLE = "<style>\n" //$NON-NLS-1$
            + ".tab {display: inline-block; border-collapse: collapse; border: 1px solid black;}\n" //$NON-NLS-1$
            + ".cel {border-colapse: colapse; border: 1px solid black; font-family: Arial, sans-serif;}\n" //$NON-NLS-1$
            + "</style>\n"; //$NON-NLS-1$
    
    /**
     * Content handler decorator that downgrades XHTML elements to
     * old-style HTML elements before passing them on to the decorated
     * content handler. This downgrading consists of dropping all namespaces
     * (and namespaced attributes) and uppercasing all element names.
     * Used by the {@link HtmlParser} to make all incoming HTML look the same.
     * 
     * Copied from org.apache.tika.parser.html.XHTMLDowngradeHandler
     */
    private static class XHTMLDowngradeHandler extends ContentHandlerDecorator {

        public XHTMLDowngradeHandler(ContentHandler handler) {
            super(handler);
        }

        @Override
        public void startElement(
                String uri, String localName, String name, Attributes atts)
                throws SAXException {
            String upper = localName.toUpperCase(Locale.ENGLISH);

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
