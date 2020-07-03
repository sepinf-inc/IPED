package dpf.sp.gpinf.indexer.desktop.api;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.util.XMLUtil;
import iped3.desktop.ResultSetViewer;
import iped3.desktop.ResultSetViewerConfiguration;
import iped3.exception.IPEDException;

public class XMLResultSetViewerConfiguration implements ResultSetViewerConfiguration {

    Document doc;
    List<ResultSetViewer> viewers = null;

    public XMLResultSetViewerConfiguration(File xmlFile) throws IPEDException {
        try {
            loadViewers(xmlFile);
        } catch (Exception e) {
            throw new IPEDException(e);
        }

    }

    @Override
    public List<ResultSetViewer> getResultSetViewers() {
        return viewers;
    }

    private void loadViewers(File xmlFile) throws ClassNotFoundException, DOMException, InstantiationException,
            IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
            SecurityException, ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        doc = docBuilder.parse(xmlFile);

        viewers = new ArrayList<ResultSetViewer>();

        Element root = doc.getDocumentElement();

        NodeList resultSetViewerEls = root.getElementsByTagName("resultSetViewer");

        for (int i = 0; i < resultSetViewerEls.getLength(); i++) {
            Element resultSetViewerEl = (Element) resultSetViewerEls.item(i);

            Element rsViewerClass = XMLUtil.getFirstElement(resultSetViewerEl, "class");
            Class<?> classe = Class.forName(rsViewerClass.getTextContent());
            ResultSetViewer rsViewer = (ResultSetViewer) classe.getDeclaredConstructor().newInstance();
            viewers.add(rsViewer);
        }
    }

}
