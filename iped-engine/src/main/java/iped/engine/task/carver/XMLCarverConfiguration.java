package iped.engine.task.carver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.tika.mime.MediaType;
import org.arabidopsis.ahocorasick.AhoCorasick;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import iped.carvers.api.CarvedItemListener;
import iped.carvers.api.Carver;
import iped.carvers.api.CarverConfiguration;
import iped.carvers.api.CarverConfigurationException;
import iped.carvers.api.CarverType;
import iped.carvers.api.Signature;
import iped.carvers.api.Signature.SignatureType;
import iped.carvers.standard.DefaultCarver;
import iped.carvers.standard.JSCarver;
import iped.utils.XMLUtil;

public class XMLCarverConfiguration implements CarverConfiguration, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static AhoCorasick tree = null;
    private static String CARVE_DIR_INDIVIDUAIS = "carvers";
    Document mergedDoc = null;

    private ArrayList<String> originalXmls = new ArrayList<>();
    private boolean ignoreCorrupted = true;
    protected HashSet<MediaType> TYPES_TO_PROCESS= new HashSet<MediaType>();
    protected HashSet<String> TYPES_TO_NOT_PROCESS = new HashSet<String>();
    protected HashSet<MediaType> TYPES_TO_CARVE = new HashSet<MediaType>();
    private ArrayList<CarverType> enabledCarverTypes = new ArrayList<CarverType>();
    
    private ArrayList<CarverType> availableCarverTypes = new ArrayList<CarverType>();

    private int idseq = 0;
    
    public static URL xsdFile=null;

    static {
        try {
            xsdFile = XMLCarverConfiguration.class.getResource("CarverConfig.xsd");
        }catch(Exception e) {
            System.out.println("Warning: CarverConfig XSD file not loaded.");
        }
    }
    
    public DocumentBuilder getDocBuilder() throws SAXException, ParserConfigurationException {
        SchemaFactory factory = 
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(xsdFile);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setSchema(schema);
        dbf.setNamespaceAware(true);
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
    
    public void loadXMLConfigFile(File confFile) throws IOException {
        try {
            Document doc = getDocBuilder().parse(confFile);
            loadConfigDocument(doc);
            //adds the file if it was parsed successfully
            originalXmls.add(Files.readString(confFile.toPath()));
        }catch (SAXException | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    public void loadXMLConfigFile(String xml) throws IOException, SAXException, ParserConfigurationException {
        ByteArrayInputStream bis = new ByteArrayInputStream(xml.getBytes());
        Document doc = getDocBuilder().parse(bis);
        reset();
        loadConfigDocument(doc);
    }
    
    public void loadConfigDocument(Document docParam) throws IOException {
        try {
            Document doc = docParam;
            if(mergedDoc==null) {
                mergedDoc = doc;
            }

            Element root = doc.getDocumentElement();

            NodeList toProcessEls = root.getElementsByTagName("typesToProcess");
            for (int i = 0; i < toProcessEls.getLength(); i++) {
                Element toProcessEl = (Element) toProcessEls.item(i);

                String line = toProcessEl.getTextContent().trim();
                String[] types = line.split(";"); //$NON-NLS-1$ //$NON-NLS-2$
                for (String type : types) {
                    TYPES_TO_PROCESS.add(MediaType.parse(type.trim()));
                }
                if(mergedDoc!=doc) {
                    Node imported = mergedDoc.importNode(toProcessEl, true);
                    mergedDoc.getDocumentElement().appendChild(imported);
                }
            }

            NodeList toNotProcessEls = root.getElementsByTagName("typesToNotProcess");
            for (int i = 0; i < toNotProcessEls.getLength(); i++) {
                Element toNotProcessEl = (Element) toNotProcessEls.item(i);

                if (TYPES_TO_NOT_PROCESS == null) {
                    TYPES_TO_NOT_PROCESS = new HashSet<String>();
                }
                String line = toNotProcessEl.getTextContent().trim();
                String[] types = line.split(";"); //$NON-NLS-1$ //$NON-NLS-2$
                for (String type : types) {
                    TYPES_TO_NOT_PROCESS.add(type.trim());
                }
                if(mergedDoc!=doc) {
                    Node imported = mergedDoc.importNode(toNotProcessEl, true);
                    mergedDoc.getDocumentElement().appendChild(imported);
                }
            }

            NodeList ignoreCorruptedEls = root.getElementsByTagName("ignoreCorrupted");
            ignoreCorrupted = Boolean.valueOf(ignoreCorruptedEls.item(0).getTextContent().trim());
            if(mergedDoc!=doc) {
                NodeList mergedIgnoreCorruptedEls = mergedDoc.getDocumentElement().getElementsByTagName("ignoreCorrupted");
                if(mergedIgnoreCorruptedEls!=null) {
                    mergedIgnoreCorruptedEls.item(0).setTextContent(ignoreCorruptedEls.item(0).getTextContent().trim());
                }else {
                    Node imported = mergedDoc.importNode(ignoreCorruptedEls.item(0), true);
                    mergedDoc.getDocumentElement().appendChild(imported);
                }
            }

            NodeList carversEls = root.getElementsByTagName("carverTypes");
            for (int i = 0; i < carversEls.getLength(); i++) {
                Element carverEls = (Element) carversEls.item(i);

                if(mergedDoc!=doc) {
                    Node imported = mergedDoc.importNode(carverEls, true);
                    mergedDoc.getDocumentElement().appendChild(imported);
                }

                NodeList carverTypeEls = carverEls.getElementsByTagName("carverType");
                for (int j = 0; j < carverTypeEls.getLength(); j++) {
                    Element carverTypeEl = (Element) carverTypeEls.item(j);
                    
                    Element carverClass = XMLUtil.getFirstElement(carverTypeEl, "carverClass");

                    Element sigsEl = XMLUtil.getFirstElement(carverTypeEl, "signatures");
                    if (sigsEl != null) {
                        NodeList headerSignatureEls = sigsEl.getElementsByTagName("headerSignature");
                        NodeList footerSignatureEls = sigsEl.getElementsByTagName("footerSignature");
                        NodeList escapeFooterSignatureEls = sigsEl.getElementsByTagName("escapeFooterSignature");
                        NodeList lengthRefSignatureEls = sigsEl.getElementsByTagName("lengthRefSignature");
                        NodeList controlSignatureEls = sigsEl.getElementsByTagName("controlSignature");

                        CarverType ct = new CarverType();
                        configCarverType(ct, carverTypeEl, CARVE_DIR_INDIVIDUAIS);

                        for (int k = 0; k < headerSignatureEls.getLength(); k++) {
                            Element headerSignatureEl = (Element) headerSignatureEls.item(k);
                            ct.addHeader(headerSignatureEl.getTextContent().trim());
                        }
                        for (int l = 0; l < footerSignatureEls.getLength(); l++) {
                            Element footerSignatureEl = (Element) footerSignatureEls.item(l);
                            if (footerSignatureEl != null) {
                                ct.addFooter(footerSignatureEl.getTextContent().trim());
                            }
                        }
                        for (int l = 0; l < escapeFooterSignatureEls.getLength(); l++) {
                            Element escapeFooterSignatureEl = (Element) escapeFooterSignatureEls.item(l);
                            if (escapeFooterSignatureEl != null) {
                                ct.addSignature(escapeFooterSignatureEl.getTextContent().trim(),
                                        SignatureType.ESCAPEFOOTER);
                            }
                        }
                        for (int l = 0; l < lengthRefSignatureEls.getLength(); l++) {
                            Element lengthRefSignatureEl = (Element) lengthRefSignatureEls.item(l);
                            if (lengthRefSignatureEl != null) {
                                ct.addSignature(lengthRefSignatureEl.getTextContent().trim(), SignatureType.LENGTHREF);
                            }
                        }
                        for (int l = 0; l < controlSignatureEls.getLength(); l++) {
                            Element controlSignatureEl = (Element) controlSignatureEls.item(l);
                            if (controlSignatureEl != null) {
                                ct.addSignature(controlSignatureEl.getTextContent().trim(), SignatureType.CONTROL);
                            }
                        }
                        TYPES_TO_CARVE.add(ct.getMimeType());
                        if("false".equals(carverTypeEl.getAttribute("enabled"))) {
                            ct.setEnabled(false);
                        }else {
                            enabledCarverTypes.add(ct);
                            ct.setEnabled(true);
                        }
                        ct.id = idseq++;
                        availableCarverTypes.add(ct);
                    } else {
                        Class<?> classe = this.getClass().getClassLoader().loadClass(carverClass.getTextContent());
                        Carver cv = (Carver) classe.getDeclaredConstructor().newInstance();
                        CarverType[] cts = cv.getCarverTypes();
                        boolean ctEnabled;
                        int ctid = idseq++;
                        if("false".equals(carverTypeEl.getAttribute("enabled"))) {
                            ctEnabled=false;
                        }else {
                            ctEnabled=true;
                        }
                        if(cts.length>0) {
                            availableCarverTypes.add(cts[0]);
                        }
                        for (int k = 0; k < cts.length; k++) {
                            configCarverType(cts[k], carverTypeEl, CARVE_DIR_INDIVIDUAIS);
                            cts[k].setEnabled(ctEnabled);
                            if(ctEnabled) {
                                enabledCarverTypes.add(cts[k]);
                            }
                            cts[k].id = ctid;
                        }
                    }

                }
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void loadXMLConfigXML(File confFile) throws IOException {
    }

    private void configCarverType(CarverType ct, Element carverTypeEl, String scriptDir) {
        Element name = XMLUtil.getFirstElement(carverTypeEl, "name");
        Element mediaType = XMLUtil.getFirstElement(carverTypeEl, "mediaType");
        Element carverClass = XMLUtil.getFirstElement(carverTypeEl, "carverClass");
        Element lengthOffset = XMLUtil.getFirstElement(carverTypeEl, "lengthOffset");
        Element lengthSizeBytes = XMLUtil.getFirstElement(carverTypeEl, "lengthSizeBytes");
        Element lengthBigEndian = XMLUtil.getFirstElement(carverTypeEl, "lengthBigEndian");
        Element minLength = XMLUtil.getFirstElement(carverTypeEl, "minLength");
        Element maxLength = XMLUtil.getFirstElement(carverTypeEl, "maxLength");
        Element carverScriptFile = XMLUtil.getFirstElement(carverTypeEl, "carverScriptFile");
        Element stopOnNextHeader = XMLUtil.getFirstElement(carverTypeEl, "stopOnNextHeader");
        
        if (name != null) {
            ct.setName(name.getTextContent());
        }
        if (mediaType != null) {
            ct.setMimeType(MediaType.parse(mediaType.getTextContent().trim()));
        }
        if (lengthOffset != null) {
            ct.setSizePos(Integer.parseInt(lengthOffset.getTextContent().trim()));
        }
        if (lengthSizeBytes != null) {
            ct.setSizeBytes(Integer.parseInt(lengthSizeBytes.getTextContent().trim()));
        }
        if (lengthBigEndian != null) {
            ct.setBigendian(lengthBigEndian.getTextContent().trim().equals("true"));
        } else {
            ct.setBigendian(false);
        }
        if (minLength != null) {
            ct.setMinLength(Integer.parseInt(minLength.getTextContent().trim()));
        }
        if (maxLength != null) {
            ct.setMaxLength(Long.parseLong(maxLength.getTextContent().trim()));
        }
        if (stopOnNextHeader != null) {
            ct.setStopOnNextHeader(Boolean.parseBoolean(stopOnNextHeader.getTextContent().trim()));
        }
        if (carverClass != null) {
            ct.setCarverClass(carverClass.getTextContent().trim());
            // registerCarver(ct.carverClass);
        } else {
            if ((carverScriptFile != null)) {
                // caso um script seja definido instancia uma classe
                if (scriptDir != null) {
                    ct.setCarverScript(scriptDir + "/" + carverScriptFile.getTextContent().trim());
                } else {
                    ct.setCarverScript(carverScriptFile.getTextContent().trim());
                }
                ct.setCarverClass(JSCarver.class.getName());
            } else {
                ct.setCarverClass(DefaultCarver.class.getName());
            }
        }
        TYPES_TO_CARVE.add(ct.getMimeType());
    }

    /* Verify if a mediaType is configured to be processed */
    public boolean isToProcess(MediaType mediaType) {
        if (TYPES_TO_PROCESS != null && TYPES_TO_PROCESS.size() > 0) {
            return TYPES_TO_PROCESS.contains(mediaType);
        } else {
            if (TYPES_TO_NOT_PROCESS != null && TYPES_TO_NOT_PROCESS.size() > 0) {
                return !isToNotProcess(mediaType);
            } else {
                return true; // se não estiver definido os tipos a se processar, processa todos
            }
        }
    }

    public boolean isToCarve(MediaType mediaType) {
        return TYPES_TO_CARVE.contains(mediaType);
    }

    public boolean isToNotProcess(MediaType mediaType) {
        if (TYPES_TO_NOT_PROCESS != null) {
            return TYPES_TO_NOT_PROCESS.contains(mediaType.toString())
                    || TYPES_TO_NOT_PROCESS.contains(mediaType.getType());
        } else {
            return false;
        }
    }

    public CarverType[] getCarverTypes() {
        return enabledCarverTypes.toArray(new CarverType[0]);
    }

    public CarverType[] getAvailableCarverTypes() {
        return availableCarverTypes.toArray(new CarverType[0]);
    }

    public List<CarverType> getAvailableCarverTypesList() {
        return availableCarverTypes;
    }

    /**
     * Configures the Task passed as parameter
     */
    @Override
    synchronized public void configListener(CarvedItemListener carvedItemListener)
            throws CarverConfigurationException {
        try {
            CarverType[] carverTypes = enabledCarverTypes.toArray(new CarverType[0]);

            if (tree == null) {
                tree = new AhoCorasick();
                for (int i = 0; i < carverTypes.length; i++) {
                    CarverType ct = carverTypes[i];
                    ArrayList<Signature> sigs = ct.getSignatures();
                    for (int j = 0; j < sigs.size(); j++) {
                        Signature sig = sigs.get(j);
                        if (sig.seqs != null) {
                            for (int k = 0; k < sig.seqs.length; k++) {
                                Object[] out = new Object[2];
                                out[0] = sig;
                                out[1] = k;
                                tree.add(sig.seqs[k], out);
                            }
                        }
                    }
                }
                tree.prepare();
            }

        } catch (Exception e) {
            throw new CarverConfigurationException(e);
        }
    }

    public AhoCorasick getPopulatedTree() {
        return tree;
    }

    @Override
    public Carver createCarverFromJSName(File file) {
        try {
            return (Carver) new JSCarver(file);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public HashMap<CarverType, Carver> getRegisteredCarvers() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isToIgnoreCorrupted() {
        return this.ignoreCorrupted;
    }
    
    public void setEnableCarverType(CarverType ct, boolean enabled) {
        ct.setEnabled(enabled);
        
        NodeList ctNodes = mergedDoc.getElementsByTagName("carverType");
        for(int i=0; i<ctNodes.getLength(); i++) {
            Node node = ctNodes.item(i);
            NodeList childs = node.getChildNodes();
            for(int j=0; j<childs.getLength(); j++) {
                Node cnode = childs.item(j);
                if(cnode.getLocalName()!=null && cnode.getLocalName().equals("name")) {
                    if(((Element)node).getTextContent()!=null && cnode.getTextContent().equals(ct.getName())) {
                        ((Element)node).setAttribute("enabled", Boolean.toString(enabled));
                    }
                }
            }
        }
    }

    public String getXMLString() {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(mergedDoc);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(bos);
            transformer.transform(source, result);
            String strResult = bos.toString();
            if(strResult.endsWith("?>")) {
                return null;
            }
            return strResult;
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";
    }

    public void reset() {
        idseq  = 0;
        originalXmls.clear();
        TYPES_TO_PROCESS.clear();
        availableCarverTypes.clear();
        TYPES_TO_NOT_PROCESS.clear();
        TYPES_TO_CARVE.clear();
        enabledCarverTypes.clear();
        mergedDoc=null;
   }

}