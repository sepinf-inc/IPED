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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import iped.configuration.Configurable;

public class ParsersConfig implements Configurable<String> {

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
                Document currDoc = db.parse(new InputSource(new StringReader(parserConfigXml)));
                Document newDoc = db.parse(resource.toFile());

                Node currParsersNode = currDoc.getDocumentElement().getElementsByTagName("parsers").item(0);
                NodeList currParsers = currParsersNode.getChildNodes();
                NodeList newElements = newDoc.getDocumentElement().getElementsByTagName("parsers");
                if (newElements != null && newElements.getLength() > 0) {
                    NodeList newParsers = newElements.item(0).getChildNodes();
                    for (int i = 0; i < newParsers.getLength(); i++) {
                        Node newParser = newParsers.item(i);
                        if (newParser instanceof Element) {
                            Element newElement = (Element) newParser;
                            if (newElement.getTagName().equals("parser")) {
                                if (!mergeParserNode(newParser, currParsers)) {
                                    Node newNode = currDoc.importNode(newElement, true);
                                    currParsersNode.appendChild(newNode);
                                }
                            }
                        }
                    }
                }
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(currDoc), new StreamResult(writer));
                parserConfigXml = writer.getBuffer().toString();
            } catch (ParserConfigurationException | SAXException | TransformerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static boolean mergeParserNode(Node nodeNew, NodeList nodes) {
        String newKey = buildParserKey(nodeNew);
        if (newKey == null) {
            return false;
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            Node currNode = nodes.item(i);
            String currKey = buildParserKey(currNode);
            if (newKey.equals(currKey)) {
                Document doc = currNode.getOwnerDocument();
                Node importedNode = doc.importNode(nodeNew, true);
                currNode.getParentNode().replaceChild(importedNode, currNode);
                return true;
            }
        }
        return false;
    }

    private static String buildParserKey(Node parserNode) {
        if (parserNode.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        Element parserElement = (Element) parserNode;
        String className = parserElement.getAttribute("class");
        String parserName = "";
        NodeList children = parserElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals("params")) {
                NodeList params = child.getChildNodes();
                for (int j = 0; j < params.getLength(); j++) {
                    Node param = params.item(j);
                    if (param.getNodeType() == Node.ELEMENT_NODE && param.getNodeName().equals("param")) {
                        Element paramElement = (Element) param;
                        if ("parserName".equals(paramElement.getAttribute("name"))) {
                            parserName = paramElement.getTextContent().trim();
                            break;
                        }
                    }
                }
                break;
            }
        }
        return className + (parserName.isEmpty() ? "" : ":" + parserName);
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
        String[] slices = parserConfigXml.split(PARSER_DISABLED_ATTR + "=\"true\"");
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < slices.length; i++) {
            String part = slices[i];
            if (i > 0) {
                int disabledParserEndIndex = part.indexOf(">");
                if (disabledParserEndIndex == 0 || part.charAt(disabledParserEndIndex - 1) != '/') {
                    disabledParserEndIndex = part.indexOf("</parser>");
                }
                part = part.substring(disabledParserEndIndex + 1);
            }
            if (i < slices.length - 1) {
                int disabledParserIndex = part.lastIndexOf("<parser");
                result.append(part.substring(0, disabledParserIndex));
            } else {
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

}
