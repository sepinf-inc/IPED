package dpf.sp.gpinf.carver;

/*
 * Implementa as funcionalidades de persistência das configurações de parametrização do CarverTask
 */

import org.apache.commons.codec.DecoderException;
import org.apache.tika.mime.MediaType;
import org.arabidopsis.ahocorasick.AhoCorasick;
import org.w3c.dom.DOMException;
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

import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

public class XMLCarverConfiguration implements CarverConfiguration {
    public static boolean ignoreCorrupted = true;
    static AhoCorasick tree = null;
    static String CARVE_DIR_INDIVIDUAIS = "carvers";
    protected HashSet<MediaType> TYPES_TO_PROCESS;
    protected HashSet<String> TYPES_TO_NOT_PROCESS = new HashSet<String>();
    protected HashSet<MediaType> TYPES_TO_CARVE = new HashSet<MediaType>();
    ArrayList<CarverType> carverTypesArray = new ArrayList<CarverType>();
    private int CLUSTER_SIZE = 1;
    
    // keeps only one instance per carvertype
    protected HashMap<CarverType, Carver> registeredCarvers = new HashMap<CarverType, Carver>();

    public HashMap<CarverType, Carver> getRegisteredCarvers() {
		return registeredCarvers;
	}

	/* carrega as configurações a partir de arquivos XML em um diretório */
    public void loadXMLConfigDir(File baseDir) throws Exception {
        File confDir = new File(baseDir, CARVE_DIR_INDIVIDUAIS);

        // procura por configurações adicionais de carvers em arquivos xml do
        // subdiretório
        File carversDir = confDir;
        File[] xmlfiles = carversDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xml");
            }
        });

        if(xmlfiles!=null) {
            for (int i = 0; i < xmlfiles.length; i++) {
                // adiciona os carvers das configurações individuais
                loadXMLConfigFile(xmlfiles[i]);
            }
        }
    }

    public void loadXMLConfigFile(File confFile) throws Exception {
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

            NodeList carversEls = root.getElementsByTagName("carverTypes");
            for (int i = 0; i < carversEls.getLength(); i++) {
                Element carverEls = (Element) carversEls.item(i);
                NodeList carverTypeEls = carverEls.getElementsByTagName("carverType");
                for (int j = 0; j < carverTypeEls.getLength(); j++) {
                    Element carverTypeEl = (Element) carverTypeEls.item(j);

                    Element carverClass = getFirstElement(carverTypeEl, "carverClass");

                    Element sigsEl = getFirstElement(carverTypeEl, "signatures");
                    if (sigsEl != null) {
                        NodeList headerSignatureEls = sigsEl.getElementsByTagName("headerSignature");
                        NodeList footerSignatureEls = sigsEl.getElementsByTagName("footerSignature");

                        CarverType ct = createCarverType(carverTypeEl, CARVE_DIR_INDIVIDUAIS);

                        for (int k = 0; k < headerSignatureEls.getLength(); k++) {
                            Element headerSignatureEl = (Element) headerSignatureEls.item(k);
                            ct.addHeader(headerSignatureEl.getTextContent().trim());
                        }
                        for (int l = 0; l < footerSignatureEls.getLength(); l++) {
                            Element footerSignatureEl = (Element) footerSignatureEls.item(l);
                            if (footerSignatureEl != null) {
                                ct.addFooter(footerSignatureEl.getTextContent().trim());
                            } else {
                                ct.addSignature(new Signature(ct, SignatureType.FOOTER));// em branco
                            }
                        }
                        TYPES_TO_CARVE.add(ct.getMimeType());
                        carverTypesArray.add(ct);
                    }

                }
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public CarverType createCarverType(Element carverTypeEl, String scriptDir)
            throws DOMException, DecoderException, UnsupportedEncodingException, FileNotFoundException, IOException,
            ScriptException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        Element name = getFirstElement(carverTypeEl, "name");
        Element mediaType = getFirstElement(carverTypeEl, "mediaType");
        Element carverClass = getFirstElement(carverTypeEl, "carverClass");
        Element lengthOffset = getFirstElement(carverTypeEl, "lengthOffset");
        Element lengthSizeBytes = getFirstElement(carverTypeEl, "lengthSizeBytes");
        Element lengthBigEndian = getFirstElement(carverTypeEl, "lengthBigEndian");
        Element minLength = getFirstElement(carverTypeEl, "minLength");
        Element maxLength = getFirstElement(carverTypeEl, "maxLength");
        Element carverScriptFile = getFirstElement(carverTypeEl, "carverScriptFile");

        CarverType ct = new CarverType();

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
            ct.setMaxLength(Integer.parseInt(maxLength.getTextContent().trim()));
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

        return ct;
    }

    public Element getFirstElement(Element el, String tagName) {
        NodeList snl = el.getElementsByTagName(tagName);
        if (snl != null) {
            return (Element) snl.item(0);
        } else {
            return null;
        }
    }

    public Carver registerCarver(CarverType ct, File confDir,
                                 CarvedItemListener carvedItemListener) throws Exception {
        Carver carver = registeredCarvers.get(ct);
        if (carver == null) {
            if (ct.getCarverClass().equals(JSCarver.class.getName())) {
                File file = new File(confDir, ct.getCarverScript());
                carver = (Carver) new JSCarver(file);
                carver.registerCarvedItemListener(carvedItemListener);
                registeredCarvers.put(ct, carver);
            } else {
                Class<?> classe = Thread.currentThread().getContextClassLoader().loadClass(ct.getCarverClass());
                carver = (Carver) classe.newInstance();
                registeredCarvers.put(ct, carver);
                carver.registerCarvedItemListener(carvedItemListener);
            }
        }
        return carver;
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

    /* Configures the Task passed as parameter */
    synchronized public void configTask(File confDir, CarvedItemListener carvedItemListener)
            throws CarverConfigurationException {
        try {
            // creates the instances of the carvers
            for (Iterator<CarverType> iterator = carverTypesArray.iterator(); iterator.hasNext(); ) {
                CarverType ct = (CarverType) iterator.next();
                Carver carver = null;
                carver = registerCarver(ct, confDir, carvedItemListener);
            }

            CarverType[] carverTypes = carverTypesArray.toArray(new CarverType[0]);

            if (tree == null) {
                tree = new AhoCorasick();
                for (int i = 0; i < carverTypes.length; i++) {
                    CarverType ct = carverTypes[i];
                    ArrayList<Signature> sigs = ct.getSignatures();
                    for (int j = 0; j < sigs.size(); j++) {
                        Signature sig = sigs.get(j);
                        for (int k = 0; k < sig.seqs.length; k++) {
                            Object[] out = new Object[2];
                            out[0] = sig;
                            out[1] = k;
                            tree.add(sig.seqs[k], out);
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
	public void init(Properties props) throws CarverConfigurationException {
		File confFile = new File(props.getProperty("XML_CONFIG_FILE"));
		File confDir = new File(props.getProperty("XML_CONFIG_DIR"));
		try {
	        loadXMLConfigFile(confFile);
	        loadXMLConfigDir(confDir);
		}catch(Exception e) {
			throw new CarverConfigurationException(e);
		}
	}

}
