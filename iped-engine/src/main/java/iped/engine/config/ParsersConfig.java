package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import iped.configuration.Configurable;

public class ParsersConfig implements Configurable<String> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String PARSER_CONFIG = "ParserConfig.xml"; //$NON-NLS-1$

    public static final String PARSER_DISABLED_ATTR = "iped:disabled";

    private String parserConfigXml;
    private transient Path tmp;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(PARSER_CONFIG);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        if (parserConfigXml == null) {
            parserConfigXml = new String(Files.readAllBytes(resource), StandardCharsets.UTF_8);
            parserConfigXml = parserConfigXml.trim().replaceFirst("^([\\W]+)<", "<");
        } else {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
            dbf.setNamespaceAware(false);
            DocumentBuilder db;
            try {
                db = dbf.newDocumentBuilder();
                Document doc = db.parse(new InputSource(new StringReader(parserConfigXml)));
                Document changedDoc = db.parse(resource.toFile());

                Element root = changedDoc.getDocumentElement();
                NodeList rootNl = root.getElementsByTagName("parsers").item(0).getChildNodes();
                for (int i = 0; i < rootNl.getLength(); i++) {
                    Node child = rootNl.item(i);
                    if (child instanceof Element) {
                        Element element = (Element) child;
                        if (element.getTagName().equals("parser")) {
                            String className = element.getAttribute("class");
                            XPath xPath = XPathFactory.newInstance().newXPath();
                            String expression = "/properties/parsers/parser[@class='" + className + "']";
                            NodeList nlParser = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);

                            expression = "/properties/parsers";
                            NodeList nlParsers = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);
                            Node newnode = doc.importNode(element, true);
                            for (int j = 0; j < nlParsers.getLength(); j++) {
                                for (int k = 0; k < nlParser.getLength(); k++) {
                                    nlParsers.item(j).removeChild(nlParser.item(k));
                                }
                                nlParsers.item(j).appendChild(newnode);
                            }
                        }
                    }
                }

                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(doc), new StreamResult(writer));
                parserConfigXml = writer.getBuffer().toString();
            } catch (ParserConfigurationException | SAXException | XPathExpressionException | TransformerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String getConfiguration() {
        return parserConfigXml;
    }

    @Override
    public void setConfiguration(String config) {
        parserConfigXml = config;
    }
    
    public String removeDisabledParsers(String parserConfigXml) {
        String[] slices = parserConfigXml.split(PARSER_DISABLED_ATTR+"=\"true\"");
        StringBuffer result=new StringBuffer();
        for (int i = 0; i < slices.length; i++) {
            String part = slices[i];
            if(i>0) {
                int disabledParserEndIndex = part.indexOf(">");
                if(disabledParserEndIndex==0 || part.charAt(disabledParserEndIndex-1)!='/') {
                    disabledParserEndIndex = part.indexOf("</parser>");
                }
                part=part.substring(disabledParserEndIndex+1);
            }
            if(i<slices.length-1) {
                int disabledParserIndex = part.lastIndexOf("<parser");
                result.append(part.substring(0, disabledParserIndex));
            }else {
                result.append(part);
            }
        }
        return result.toString();
    }

    public synchronized File getTmpConfigFile() {
        if (tmp == null) {
            try {
                tmp = Files.createTempFile("parser-config", ".xml");
                Files.write(tmp, removeDisabledParsers(parserConfigXml).getBytes(StandardCharsets.UTF_8));
                tmp.toFile().deleteOnExit();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tmp.toFile();
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, PARSER_CONFIG);            

            Files.write(confFile.toPath(), parserConfigXml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub        
    }
}
