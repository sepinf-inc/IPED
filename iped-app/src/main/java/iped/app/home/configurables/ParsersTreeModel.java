package iped.app.home.configurables;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.Parser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import iped.configuration.Configurable;
import iped.engine.config.CategoryConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ParsersConfig;
import iped.engine.data.Category;
import iped.parsers.misc.MultipleParser;
import iped.utils.XMLUtil;

public class ParsersTreeModel implements TreeModel {
    Configurable<String> configurable;
    final static String ROOT = "Parsers";
    final static String MIME_NODE = "myme-types";
    private HashMap<Element, Set<MediaType>> parsersMediaType = new HashMap<Element, Set<MediaType>>();
    private HashSet<Element> parsers = new HashSet<Element>();
    private HashMap<Category, Set<String>> categoryMediaType = new HashMap<Category, Set<String>>();
    private HashMap<Category, List<ParserElementName>> categoryParsers = new HashMap<Category, List<ParserElementName>>();
    private HashMap<Element, List<Field>> parserFields = new HashMap<Element, List<Field>>();
    Category category = null;
    Document doc;
    private CategoryConfig categoryConfig;

    boolean toListParameters = false;
    private boolean externalParsers;
    private ArrayList<ParserElementName> categorizableParsers;
    private ArrayList<ParserElementName> uncategorizableParsers;

    private ParsersTreeModel() {
    }

    public ParsersTreeModel(Configurable<String> configurable, Category cat, Document doc) {
        this.configurable = configurable;
        this.doc = doc;
        categoryConfig = ConfigurationManager.get().findObject(CategoryConfig.class);
        loadModel(cat);
    }

    public ParsersTreeModel(Configurable<String> configurable, Category cat) {
        this.configurable = configurable;
        categoryConfig = ConfigurationManager.get().findObject(CategoryConfig.class);
        loadModel(cat);
    }

    private void loadModel(Category cat) {
        getDocument();
        populateAvailableParsers();
        if (cat != null) {
            populatesCategoryMimes(cat);
        }
        categorizableParsers = updateFilteredParsers(cat);
        uncategorizableParsers = new ArrayList<ParserElementName>();
        for (Iterator iterator = parsers.iterator(); iterator.hasNext();) {
            Element el = (Element) iterator.next();
            ParserElementName parserElementName = new ParserElementName(el);
            if (!categorizableParsers.contains(parserElementName)) {
                uncategorizableParsers.add(parserElementName);
            }
        }
        categoryParsers.put(null, uncategorizableParsers);
        category = cat;
        if (!externalParsers) {
            updateParsersFields();
        }
    }

    public ParsersTreeModel(ParsersConfig configurable) {
        this(configurable, null);
    }

    @Override
    public Object getRoot() {
        return ROOT;
    }

    public ParsersTreeModel copy() {
        ParsersTreeModel copy = new ParsersTreeModel();
        copy.configurable = configurable;
        copy.parsers = parsers;
        copy.parsersMediaType = parsersMediaType;
        copy.categoryMediaType = categoryMediaType;
        copy.category = category;
        copy.doc = doc;
        copy.parserFields = this.parserFields;
        copy.categoryConfig = categoryConfig;
        copy.categoryParsers = categoryParsers;
        return copy;
    }

    class ParserElementName implements Comparable<ParserElementName> {
        Element el;

        public ParserElementName(Element el) {
            this.el = el;
        }

        public Element getElement() {
            return el;
        }

        @Override
        public String toString() {
            if (externalParsers) {
                return ((Element) el.getElementsByTagName("name").item(0)).getTextContent();
            } else {
                /* if there is a param named parserName returns its content */
                Element paramsList = (Element) el.getElementsByTagName("params").item(0);
                if (paramsList != null) {
                    NodeList params = paramsList.getElementsByTagName("param");
                    Set<MediaType> result = new TreeSet<MediaType>();
                    for (int i = 0; i < params.getLength(); i++) {
                        if (((Element) params.item(i)).getAttribute("name").equals("parserName")) {
                            return ((Element) params.item(i)).getTextContent();
                        }
                    }
                }

                /* else return parser class name */
                String className = el.getAttributes().getNamedItem("class").getNodeValue();
                return className;
            }
        }

        @Override
        public int compareTo(ParserElementName o) {
            return o.toString().compareTo(this.toString());
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof ParserElementName) && ((ParserElementName) obj).el == this.el;
        }
    }

    public List<ParserElementName> getCategoryMediaTypesNames(Category category) {
        return categoryParsers.get(category);
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (parent == ROOT) {
            List<ParserElementName> filteredParsers = categoryParsers.get(category);
            return filteredParsers.get(index);
        } else {
            if (parent instanceof ParserElementName) {
                Element el = ((ParserElementName) parent).getElement();
                Set<MediaType> mimes = parsersMediaType.get(el);
                int mimesSize = mimes != null ? mimes.size() : 0;
                if (index < mimesSize) {
                    return mimes.toArray(new MediaType[0])[index];
                } else {
                    return parserFields.get(el).get(index - mimesSize);
                }

            }
        }
        return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if (parent == ROOT) {
            List<ParserElementName> filteredParsers = categoryParsers.get(category);
            return filteredParsers == null ? 0 : filteredParsers.size();
        } else {
            if (parent instanceof ParserElementName) {
                Element el = ((ParserElementName) parent).getElement();
                Set<MediaType> mimes = parsersMediaType.get(el);
                int result = 0;
                if (mimes != null) {
                    result += mimes.size();
                }
                if (toListParameters) {
                    List fs = parserFields.get(el);
                    if (fs != null) {
                        result += fs.size();
                    }
                }
                return result;
            }
        }
        return 0;
    }

    public String getXMLString() {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            Transformer transformer = transformerFactory.newTransformer();
            Element documentElement = doc.getDocumentElement();
            documentElement.setAttribute("xmlns:" + ParsersConfig.PARSER_DISABLED_ATTR.split(":")[0], XMLUtil.IPED_NAMESAPCE);
            DOMSource source = new DOMSource(doc);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(bos);
            transformer.transform(source, result);
            String strResult = bos.toString();
            if (strResult.endsWith("?>")) {
                return null;
            }
            return strResult;
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public boolean isLeaf(Object node) {
        if (node == ROOT) {
            return false;
        } else {
            if (node instanceof ParserElementName) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // TODO Auto-generated method stub
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        return 0;
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
    }

    private HashSet<String> populatesCategoryMimes(Category cat) {
        HashSet<String> result = new HashSet<String>();
        result.addAll(cat.getMimes());
        SortedSet<Category> subcats = cat.getChildren();
        for (Iterator iterator = subcats.iterator(); iterator.hasNext();) {
            Category category = (Category) iterator.next();
            result.addAll(populatesCategoryMimes(category));
        }
        categoryMediaType.put(cat, result);
        return result;

    }

    private void populateAvailableParsers() {
        parsers.clear();
        doc = getDocument();

        externalParsers = doc.getDocumentElement().getTagName().equals("external-parsers");

        NodeList nl = doc.getElementsByTagName("parser");
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            if (externalParsers) {
                Set<MediaType> supportedMimes = null;

                supportedMimes = getSupportedMimeTypes(e);
                if (supportedMimes != null) {
                    parsersMediaType.put(e, supportedMimes);
                }
            } else {
                Set<MediaType> supportedMimes = null;

                supportedMimes = getSupportedMimeTypes(e);
                if (supportedMimes != null) {
                    parsersMediaType.put(e, supportedMimes);
                }
            }

            parsers.add(e);
        }
    }

    private Set<MediaType> getSupportedMimeTypes(Element el) {
        try {
            if (externalParsers) {
                Element mimeTypes = (Element) el.getElementsByTagName("mime-types").item(0);
                NodeList mimes = mimeTypes.getElementsByTagName("mime-type");
                Set<MediaType> result = new TreeSet<MediaType>();
                for (int i = 0; i < mimes.getLength(); i++) {
                    Element mimeType = (Element) mimes.item(i);
                    String[] mime = mimeType.getTextContent().split("/");
                    result.add(new MediaType(mime[0], mime[1]));
                }
                return result;
            } else {
                String parserClass = el.getAttributes().getNamedItem("class").getNodeValue();
                Class<?> clazz = Class.forName((java.lang.String) parserClass);
                Parser parser = (Parser) clazz.newInstance();
                if (parser instanceof MultipleParser) {
                    MultipleParser mparser = ((MultipleParser) parser);
                    NodeList mimes = el.getElementsByTagName("mime");
                    Set<MediaType> result = new TreeSet<MediaType>();
                    for (int i = 0; i < mimes.getLength(); i++) {
                        String[] mime = ((Element) mimes.item(i)).getTextContent().split("/");
                        result.add(new MediaType(mime[0], mime[1]));
                    }
                    return result;
                } else {
                    return parser.getSupportedTypes(null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public DocumentBuilder getDocBuilder() throws SAXException, ParserConfigurationException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        docBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return docBuilder;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    private void updateParsersFields() {
        parserFields.clear();
        Class annotation;
        try {
            annotation = org.apache.tika.config.Field.class;
            for (Iterator iterator = parsers.iterator(); iterator.hasNext();) {
                Element element = (Element) iterator.next();
                String parserClass = element.getAttributes().getNamedItem("class").getNodeValue();
                Class klass = Class.forName(parserClass);
                while (klass != Object.class) { // need to traverse a type hierarchy in order to process methods from super
                                                // types
                    // iterate though the list of methods declared in the class represented by klass
                    // variable, and add those annotated with the specified annotation
                    for (final Field field : klass.getDeclaredFields()) {
                        if (field.isAnnotationPresent(annotation)) {
                            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                                field.setAccessible(true);
                                return null;
                            });
                            Annotation annotInstance = field.getAnnotation(annotation);
                            List<Field> fs = parserFields.get(element);
                            if (fs == null) {
                                fs = new ArrayList<Field>();
                                parserFields.put(element, fs);
                            }
                            fs.add(field);
                        }
                    }
                    // move to the upper class in the hierarchy in search for more methods
                    klass = klass.getSuperclass();
                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private ArrayList<ParserElementName> updateFilteredParsers(Category category) {
        ArrayList<ParserElementName> filteredParsers = new ArrayList<ParserElementName>();

        for (Iterator iterator = parsers.iterator(); iterator.hasNext();) {
            Element element = (Element) iterator.next();
            if (category != null) {
                Set<MediaType> mts = parsersMediaType.get(element);
                if (mts != null) {
                    for (Iterator iterator2 = mts.iterator(); iterator2.hasNext();) {
                        MediaType mediaType = (MediaType) iterator2.next();
                        if (categoryMediaType.get(category) != null && (categoryMediaType.get(category).contains(mediaType.toString()) || categoryMediaType.get(category).contains(mediaType.getType()))) {
                            filteredParsers.add(new ParserElementName(element));
                            break;
                        }
                    }
                }
            } else {
                filteredParsers.add(new ParserElementName(element));
            }
        }
        SortedSet<Category> catChildren = category.getChildren();
        for (Iterator iterator = catChildren.iterator(); iterator.hasNext();) {
            Category catChild = (Category) iterator.next();
            updateFilteredParsers(catChild);
        }

        categoryParsers.put(category, filteredParsers);

        Collections.sort((List) filteredParsers);
        return filteredParsers;
    }

    public boolean isExternalParsers() {
        return externalParsers;
    }

    public Document getDocument() {
        if (doc == null) {
            String xml = (String) configurable.getConfiguration();
            BOMInputStream bis = new BOMInputStream(new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8"))));
            try {
                doc = PositionalXMLReader.readXML(bis);
            } catch (IOException | SAXException e) {
                e.printStackTrace();
            }
        }
        return doc;
    }

}
