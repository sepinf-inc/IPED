package dpf.sp.gpinf.carver;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.tika.mime.MediaType;
import org.arabidopsis.ahocorasick.AhoCorasick;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import dpf.sp.gpinf.carver.api.CarvedItemListener;
import dpf.sp.gpinf.carver.api.Carver;
import dpf.sp.gpinf.carver.api.CarverConfiguration;
import dpf.sp.gpinf.carver.api.CarverConfigurationException;
import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Signature;
import dpf.sp.gpinf.carver.api.Signature.SignatureType;
import dpf.sp.gpinf.carving.DefaultCarver;
import dpf.sp.gpinf.carving.JSCarver;
import dpf.sp.gpinf.indexer.util.XMLUtil;

public class XMLCarverConfiguration implements CarverConfiguration, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static AhoCorasick tree = null;
    private static String CARVE_DIR_INDIVIDUAIS = "carvers";

    private boolean ignoreCorrupted = true;
    protected HashSet<MediaType> TYPES_TO_PROCESS;
    protected HashSet<String> TYPES_TO_NOT_PROCESS = new HashSet<String>();
    protected HashSet<MediaType> TYPES_TO_CARVE = new HashSet<MediaType>();
    private ArrayList<CarverType> carverTypesArray = new ArrayList<CarverType>();

    public void loadXMLConfigFile(File confFile) throws IOException {
        Document doc = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();

            doc = docBuilder.parse(confFile);

            Element root = doc.getDocumentElement();

            NodeList toProcessEls = root.getElementsByTagName("typesToProcess");
            for (int i = 0; i < toProcessEls.getLength(); i++) {
                Element toProcessEl = (Element) toProcessEls.item(i);

                if (TYPES_TO_PROCESS == null) {
                    TYPES_TO_PROCESS = new HashSet<MediaType>();
                }
                String line = toProcessEl.getTextContent().trim();
                String[] types = line.split(";"); //$NON-NLS-1$ //$NON-NLS-2$
                for (String type : types) {
                    TYPES_TO_PROCESS.add(MediaType.parse(type.trim()));
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
            }

            NodeList ignoreCorruptedEls = root.getElementsByTagName("ignoreCorrupted");
            ignoreCorrupted = Boolean.valueOf(ignoreCorruptedEls.item(0).getTextContent().trim());

            NodeList carversEls = root.getElementsByTagName("carverTypes");
            for (int i = 0; i < carversEls.getLength(); i++) {
                Element carverEls = (Element) carversEls.item(i);
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
                        carverTypesArray.add(ct);
                    } else {
                        Class<?> classe = this.getClass().getClassLoader().loadClass(carverClass.getTextContent());
                        Carver cv = (Carver) classe.getDeclaredConstructor().newInstance();
                        CarverType[] cts = cv.getCarverTypes();
                        for (int k = 0; k < cts.length; k++) {
                            configCarverType(cts[k], carverTypeEl, CARVE_DIR_INDIVIDUAIS);
                            carverTypesArray.add(cts[k]);
                        }
                    }

                }
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
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
        return carverTypesArray.toArray(new CarverType[0]);
    }

    /**
     * Configures the Task passed as parameter
     */
    @Override
    synchronized public void configListener(CarvedItemListener carvedItemListener)
            throws CarverConfigurationException {
        try {
            CarverType[] carverTypes = carverTypesArray.toArray(new CarverType[0]);

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

}