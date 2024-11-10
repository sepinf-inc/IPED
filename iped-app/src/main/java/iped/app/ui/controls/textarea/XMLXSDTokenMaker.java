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

import javax.swing.text.Segment;
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
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.modes.XMLTokenMaker;

import iped.engine.task.carver.XMLCarverConfiguration;

public class XMLXSDTokenMaker extends XMLTokenMaker {
    static public int MARKUP_TAG_NAME = 26;
    static final public String SYNTAX_STYLE_XMLXSD = "application/xml+xsd";
    public static final int RESERVED_WORD = 6;
    URL xsdFile;
    private HashMap<String, Color> tagNames;
    
    public XMLXSDTokenMaker() {
        xsdFile = XMLCarverConfiguration.xsdFile;
        loadXSDTagName();
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        Token first = super.getTokenList(text, initialTokenType, startOffset);
        
        Token next = first;
        while(next!=null) {
            if(next.getType() == XMLXSDTokenMaker.MARKUP_TAG_NAME) {
                String tagName = next.getLexeme();
                if(!tagNames.keySet().contains(tagName)) {
                    next.setType(XMLXSDTokenMaker.RESERVED_WORD);
                }
            }else {
                
            }
            next=next.getNextToken();
        }
        
        
        return first;
    }

    protected void loadXSDTagName(){
        if(xsdFile!=null) {
            tagNames = new HashMap<String, Color>();
            try {                
                XmlSchemaCollection xsc = new XmlSchemaCollection();
                XmlSchema xs = xsc.read(new StreamSource(xsdFile.openStream()));
                Map<QName, XmlSchemaElement> elements = xs.getElements();      
                String elementPattern;
                String unknownElementPattern;
                for (Iterator iterator = elements.entrySet().iterator(); iterator.hasNext();) {
                    Entry<QName, XmlSchemaElement> element = (Entry<QName, XmlSchemaElement>) iterator.next();
                    elementPattern = element.getKey().getLocalPart();
                    unknownElementPattern = element.getKey().getLocalPart();
                    
                    tagNames.put(elementPattern , new Color(63, 127, 127));
                    Map<QName, List<XmlSchemaElement>> childElementHierarchy = new HashMap<QName, List<XmlSchemaElement>>();
                    ArrayList<XmlSchemaElement> childElements = new ArrayList<XmlSchemaElement>();                    
                    getChildElementNames(element.getValue(), childElementHierarchy,childElements);
                    for (Iterator iterator2 = childElements.iterator(); iterator2.hasNext();) {
                        XmlSchemaElement xmlSchemaElement = (XmlSchemaElement) iterator2.next();
                        elementPattern = xmlSchemaElement.getQName().getLocalPart();
                        tagNames.put(elementPattern , new Color(63, 127, 127));
                    }
                }
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
}
