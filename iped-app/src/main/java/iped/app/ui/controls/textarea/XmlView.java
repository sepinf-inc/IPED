package iped.app.ui.controls.textarea;

import java.awt.Color;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.swing.text.Element;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaChoiceMember;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaParticle;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaSequenceMember;
import org.apache.ws.commons.schema.XmlSchemaType;

public class XmlView extends RegexView {
    URL xsdFile = null;

    private static String TAG_PATTERN = "(</?[a-zA-Z\\-]*)\\s?>?";
    private static String OPEN_TAG_PATTERN = "</?\\s?";
    private static String CLOSE_TAG_PATTERN = "\\s?>";
    private static String TAG_END_PATTERN = "(/>)";
    private static String TAG_ATTRIBUTE_PATTERN = "\\s(\\w*)\\=";
    private static String TAG_ATTRIBUTE_VALUE = "[a-zA-Z-]*\\=(\"[^\"]*\")";
    private static String TAG_COMMENT = "(<!--.*-->)";
    private static String TAG_CDATA_START = "(\\<!\\[CDATA\\[).*";
    private static String TAG_CDATA_END = ".*(]]>)";

    protected void configPatterns(){
        if(xsdFile==null) {
            patternColors = new HashMap<Pattern, Color>();
            patternColors.put(Pattern.compile(TAG_CDATA_START), new Color(128, 128, 128));
            patternColors.put(Pattern.compile(TAG_CDATA_END), new Color(128, 128, 128));
            patternColors
                    .put(Pattern.compile(TAG_PATTERN), new Color(63, 127, 127));
            patternColors.put(Pattern.compile(TAG_ATTRIBUTE_PATTERN), new Color(
                    127, 0, 127));
            patternColors.put(Pattern.compile(TAG_END_PATTERN), new Color(63, 127,
                    127));
            patternColors.put(Pattern.compile(TAG_ATTRIBUTE_VALUE), new Color(42,
                    0, 255));
            patternColors.put(Pattern.compile(TAG_COMMENT), new Color(63, 95, 191));
        }else {
            patternColors = new HashMap<Pattern, Color>();
            try {                
                XmlSchemaCollection xsc = new XmlSchemaCollection();
                XmlSchema xs = xsc.read(new StreamSource(xsdFile.openStream()));
                Map<QName, XmlSchemaElement> elements = xs.getElements();      
                String unknownElementPattern = "("+OPEN_TAG_PATTERN + "(";
                String elementPattern = "("+OPEN_TAG_PATTERN + "(";
                for (Iterator iterator = elements.entrySet().iterator(); iterator.hasNext();) {
                    Entry<QName, XmlSchemaElement> element = (Entry<QName, XmlSchemaElement>) iterator.next();
                    elementPattern += "("+element.getKey().getLocalPart()+")|";
                    unknownElementPattern += "("+element.getKey().getLocalPart()+")|";
                    
                    Map<QName, List<XmlSchemaElement>> childElementHierarchy = new HashMap<QName, List<XmlSchemaElement>>();
                    ArrayList<XmlSchemaElement> childElements = new ArrayList<XmlSchemaElement>();                    
                    getChildElementNames(element.getValue(), childElementHierarchy,childElements);
                    for (Iterator iterator2 = childElements.iterator(); iterator2.hasNext();) {
                        XmlSchemaElement xmlSchemaElement = (XmlSchemaElement) iterator2.next();
                        elementPattern += "("+xmlSchemaElement.getQName().getLocalPart()+")|";
                        unknownElementPattern += "("+xmlSchemaElement.getQName().getLocalPart()+")|"; 
                    }
                }
                elementPattern += ")"+CLOSE_TAG_PATTERN+")";
                unknownElementPattern += ")"+CLOSE_TAG_PATTERN+")";
                patternColors.put(Pattern.compile(elementPattern), new Color(63, 127, 127));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
        }
    }
    

    // Method to check for the child elements and return list
    private static void getChildElementNames(XmlSchemaElement element, Map<QName, List<XmlSchemaElement>> xsdElements, List<XmlSchemaElement> elements) {

        // Get the type of the element
        XmlSchemaType elementType = element != null ? element.getSchemaType() : null;
        

        // Confirm if the element is of Complex type
        if (elementType instanceof XmlSchemaComplexType) {
            // Get all particles associated with that element Type
            XmlSchemaParticle allParticles = ((XmlSchemaComplexType) elementType).getParticle();

            // Check particle belongs to which type
            if ((allParticles instanceof XmlSchemaSequence)) {
                // System.out.println("Sequence Schema Type");
                final XmlSchemaSequence xmlSchemaSequence = (XmlSchemaSequence) allParticles;
                final List<XmlSchemaSequenceMember> items = xmlSchemaSequence.getItems();

                items.forEach((item) -> {
                    XmlSchemaElement itemElements = (XmlSchemaElement) item;
                    addChild(element.getQName(), itemElements, xsdElements);
                    elements.add(itemElements);
                    // Call method recursively to get all subsequent element
                    getChildElementNames(itemElements, xsdElements, elements);
                });

            }
            // Check particle belongs to which type
            if ((allParticles instanceof XmlSchemaChoice)) {
                // System.out.println("Sequence Schema Type");
                final XmlSchemaChoice xmlSchemaSequence = (XmlSchemaChoice) allParticles;
                final List<XmlSchemaChoiceMember> items = xmlSchemaSequence.getItems();

                items.forEach((item) -> {
                    XmlSchemaElement itemElements = (XmlSchemaElement) item;
                    addChild(element.getQName(), itemElements, xsdElements);
                    elements.add(itemElements);
                    // Call method recursively to get all subsequent element
                    getChildElementNames(itemElements, xsdElements, elements);
                });

            }
        }
    }

    // Add child elements based on its parent
    private static void addChild(QName qName, XmlSchemaElement child, Map<QName, List<XmlSchemaElement>> xsdElements) {
        List<XmlSchemaElement> values = xsdElements.get(qName);
        if (values == null) {
            values = new ArrayList<XmlSchemaElement>();
        }
        values.add(child);
        xsdElements.put(qName, values);
    }

    public XmlView(Element element) {
        super(element);
    }

    public XmlView(Element element, URL xsdFile2) {
        super(element);
        this.xsdFile=xsdFile2;
    }

    public void setSchema(URL xsdFile) {
        this.xsdFile = xsdFile;
    }
}