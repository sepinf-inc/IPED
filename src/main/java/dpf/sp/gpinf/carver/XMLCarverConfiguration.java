package dpf.sp.gpinf.carver;

/*
 * Implementa as funcionalidades de persistência das configurações de parametrização do CarverTask
 */

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
import iped3.configuration.ConfigurationDirectory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

public class XMLCarverConfiguration implements CarverConfiguration {
    public static boolean ignoreCorrupted = true;
    static AhoCorasick tree = null;
    static String CARVE_DIR_INDIVIDUAIS = "carvers";
    protected HashSet<MediaType> TYPES_TO_PROCESS;
    protected HashSet<String> TYPES_TO_NOT_PROCESS = new HashSet<String>();
    protected HashSet<MediaType> TYPES_TO_CARVE = new HashSet<MediaType>();
    ArrayList<CarverType> carverTypesArray = new ArrayList<CarverType>();
    //private int CLUSTER_SIZE = 1;
    private ConfigurationDirectory localConfig;
    File confDir;
    
    /* initializes with the parameters */
    @Override
	public void init(ConfigurationDirectory localConfig, Properties props) throws CarverConfigurationException {
		this.localConfig = localConfig;

		File confFile = new File(props.getProperty("XML_CONFIG_FILE"));
		Path confDir = Paths.get(props.getProperty("XML_CONFIG_DIR"));
		try {
	        loadXMLConfigFile(confFile);
	        loadXMLConfigDir(confDir);
		}catch(Exception e) {
			throw new CarverConfigurationException(e);
		}
	}

	/* carrega as configurações a partir de arquivos XML em um diretório */
    public void loadXMLConfigDir(Path baseDir) throws Exception {
    	List<Path> xmlpaths = localConfig.lookUpResource(new Predicate<Path>() {
			public boolean test(Path path) {
				if(path.getFileName()!=null) {
					return path.getFileName().toString().endsWith(".xml") && path.getFileName().toString().startsWith("carver-");
				}
				return false;
			}
		});

    	for (Iterator iterator = xmlpaths.iterator(); iterator.hasNext();) {
			Path path = (Path) iterator.next();
            loadXMLConfigFile(path.toFile());
		}

    }

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
                            } else {
                                ct.addSignature(new Signature(ct, footerSignatureEl.getTextContent().trim(), SignatureType.FOOTER));// em branco
                            }
                        }
                        for (int l = 0; l < escapeFooterSignatureEls.getLength(); l++) {
                            Element escapeFooterSignatureEl = (Element) escapeFooterSignatureEls.item(l);
                            if (escapeFooterSignatureEl != null) {
                                ct.addSignature(escapeFooterSignatureEl.getTextContent().trim(), SignatureType.ESCAPEFOOTER);
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
                    }else {
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

    /* Configures the Task passed as parameter */
    synchronized public void configTask(File confDir, CarvedItemListener carvedItemListener)
            throws CarverConfigurationException {
        try {
        	this.confDir = confDir;

            CarverType[] carverTypes = carverTypesArray.toArray(new CarverType[0]);

            if (tree == null) {
                tree = new AhoCorasick();
                for (int i = 0; i < carverTypes.length; i++) {
                    CarverType ct = carverTypes[i];
                    ArrayList<Signature> sigs = ct.getSignatures();
                    for (int j = 0; j < sigs.size(); j++) {
                        Signature sig = sigs.get(j);
                        if(sig.seqs!=null) {
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
	public Carver createCarverFromJSName(String scriptName) {
        File file = new File(confDir, scriptName);
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

}