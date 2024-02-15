package iped.app.config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import iped.engine.task.ExportFileTask;
import iped.exception.IPEDException;
import iped.utils.XMLUtil;
import iped.viewers.api.ResultSetViewer;
import iped.viewers.api.ResultSetViewerConfiguration;

public class XMLResultSetViewerConfiguration implements ResultSetViewerConfiguration {

    Document doc;
    List<ResultSetViewer> viewers = null;
    private static Logger LOGGER = LoggerFactory.getLogger(XMLResultSetViewerConfiguration.class);    

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

            try {
                Class<?> classe = Class.forName(rsViewerClass.getTextContent());
                ResultSetViewer rsViewer = (ResultSetViewer) classe.getDeclaredConstructor().newInstance();
                viewers.add(rsViewer);
            }catch(Exception e) {
            	LOGGER.warn("ResultSetViewer class not found in classpath: {}.",rsViewerClass.getTextContent());
            	e.printStackTrace();
            }
        }
    }

}
