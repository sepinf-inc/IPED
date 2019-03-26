package dpf.sp.gpinf.carver;

import dpf.sp.gpinf.carver.api.CarvedItemListener;
import dpf.sp.gpinf.carver.api.Carver;
import dpf.sp.gpinf.carver.api.CarverConfiguration;
import dpf.sp.gpinf.carver.api.CarverConfigurationException;
import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Hit;
import dpf.sp.gpinf.carver.api.Signature;
import dpf.sp.gpinf.carving.JSCarver;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.util.IOUtil;
import gpinf.dev.data.ItemImpl;
import iped3.Item;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.arabidopsis.ahocorasick.AhoCorasick;
import org.arabidopsis.ahocorasick.SearchResult;
import org.arabidopsis.ahocorasick.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Classe responsável pelo Data Carving. Utiliza o algoritmo aho-corasick, o
 * qual gera uma máquina de estados a partir dos padrões a serem pesquisados.
 * Assim, o algoritmo é independente do número de assinaturas pesquisadas, sendo
 * proporcional ao volume de dados de entrada e ao número de padrões
 * descobertos.
 */
public class CarverTask extends BaseCarverTask {

    private static final long serialVersionUID = 1L;
    public static MediaType UNALLOCATED_MIMETYPE = MediaType.parse("application/x-unallocated"); //$NON-NLS-1$
    public static boolean enableCarving = false;
    public static boolean ignoreCorrupted = true;
    
    private static CarverType[] carverTypes;
    private static Logger LOGGER = LoggerFactory.getLogger(CarverTask.class);
    private static int largestPatternLen = 100;
    
    private CarvedItemListener carvedItemListener = null; 
    
    private static MediaTypeRegistry registry;

    private CarverConfiguration carverConfig = null;
    
    Item evidence;

    long prevLen = 0;
    int len = 0, k = 0;
    byte[] buf = new byte[1024 * 1024];
    byte[] cBuf;

    public CarverTask() {
        if(registry == null)
            registry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();
    }

    @Override
    public boolean isEnabled() {
        return enableCarving;
    }

    public void process(Item evidence) {
        if (!enableCarving) {
            return;
        }

        // Nova instancia pois o mesmo objeto é reusado e nao é imutável
        CarverTask carver = new CarverTask();
        carver.setWorker(worker);
        carver.carverConfig = this.carverConfig;
        carver.safeProcess(evidence);

        // Ao terminar o tratamento do item, caso haja referência ao mesmo no mapa de
        // itens
        // carveados através do KFF, esta pode ser removida.
        synchronized (kffCarved) {
            kffCarved.remove(evidence);
        }
    }

    private void safeProcess(Item evidence) {

        this.evidence = evidence;

        if (!isToProcess(evidence)) {
            return;
        }

        InputStream tis = null;
        try {
            MediaType type = evidence.getMediaType();

            tis = evidence.getBufferedStream();

            // faz um loop na hierarquia de tipos mime
            while (!MediaType.OCTET_STREAM.equals(type)) {
                if (carverConfig.isToNotProcess(type)) {
                    tis.close();
                    return;
                }
                // avança 1 byte para não recuperar o próprio arquivo analisado
                if (carverConfig.isToCarve(type)) {
                    prevLen = (int) tis.skip(1);
                    // break;
                }

                type = registry.getSupertype(type);
            }
            
            findSig(tis);

        } catch (Exception t) {
            LOGGER.warn("{} Error carving on {} {}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                    t.toString());
            t.printStackTrace();

        } finally {
            IOUtil.closeQuietly(tis);
        }

    }

    private void fillBuf(InputStream in) throws IOException {
        prevLen += len;
        len = 0;
        k = 0;
        while (k != -1 && (len += k) < buf.length) {
            k = in.read(buf, len, buf.length - len);
        }

        cBuf = new byte[len];
        System.arraycopy(buf, 0, cBuf, 0, len);

    }

    private Hit findSig(InputStream in) throws Exception {
        HashMap<CarverType, TreeMap<Long, Integer>> map = new HashMap<>();
        for (int i = 0; i < carverTypes.length; i++) {
            map.put(carverTypes[i], new TreeMap<Long, Integer>());
        }

        AhoCorasick tree = carverConfig.getPopulatedTree();
        SearchResult lastResult = new SearchResult(tree.root, null, 0);
        do {
            fillBuf(in);
            lastResult = new SearchResult(lastResult.lastMatchedState, cBuf, 0);
            Iterator<SearchResult> searcher = new Searcher(tree, tree.continueSearch(lastResult));

            while (searcher.hasNext()) {
                lastResult = searcher.next();

                for (Object out : lastResult.getOutputs()) {
                    Object[] oarray = (Object[]) out;
                    Signature sig = (Signature) oarray[0];
                    int seq = (int) oarray[1];
                    int i = lastResult.getLastIndex() - sig.seqEndPos[seq];

                    // tratamento para assinaturas com ? (divididas)
                    if (sig.seqs.length > 1) {
                        Integer hits = (Integer) map.get(sig.getCarverType()).get(prevLen + i);
                        if (hits == null) {
                            hits = 0;
                        }
                        if (hits != seq) {
                            continue;
                        }
                        map.get(sig.getCarverType()).put(prevLen + i, ++hits);
                        if (map.get(sig.getCarverType()).size() > largestPatternLen) {
                            map.get(sig.getCarverType()).remove(map.get(sig.getCarverType()).firstKey());
                        }

                        if (hits < sig.seqs.length) {
                            continue;
                        }
                    }

                    Hit hit = null;
                    hit = new Hit(sig, prevLen + i);

                    Carver carver = getCarver(sig.getCarverType());

                    try {
                        carver.notifyHit(this.evidence, hit);
                    }catch(Exception e) {
                        LOGGER.warn("{} Skipping unexpected error carving on hit {} {} - CarverClass {}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                                hit.getOffset(), carver.getClass().getName());
                        e.printStackTrace();
                    }
                }
            }

        } while (k != -1);

        for (Carver carver : registeredCarvers.values()) {
            carver.notifyEnd(this.evidence);
		}
        
        return null;
    }
    
    @Override
    public void init(Properties confProps, File confDir) throws Exception {
    	
        AppCarverTaskConfig ctConfig = new AppCarverTaskConfig();
    	ConfigurationManager.getInstance().addObject(ctConfig);
    	ConfigurationManager.getInstance().loadConfigs();

    	enableCarving = ctConfig.getCarvingEnabled();

        IPEDConfig ipedConfig = (IPEDConfig) ConfigurationManager.getInstance().findObjects(IPEDConfig.class).iterator().next();
        if (carverTypes == null && ctConfig.getCarvingEnabled() && !ipedConfig.isToAddUnallocated())
            LOGGER.error("addUnallocated is disabled, so carving will NOT be done in unallocated space!"); //$NON-NLS-1$

        carvedItemListener = getCarvedItemListener();
        
        carverConfig = ctConfig.getCarverConfiguration();
        carverConfig.configTask(confDir, carvedItemListener);
        carverTypes = carverConfig.getCarverTypes();

        ignoreCorrupted = carverConfig.isToIgnoreCorrupted();
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }
    
    public CarvedItemListener getCarvedItemListener() {
    	if(carvedItemListener==null) {
    		carvedItemListener=new CarvedItemListener() {
                public void processCarvedItem(Item parentEvidence, Item carvedEvidence, long off) {
                    addCarvedEvidence((ItemImpl) parentEvidence, (ItemImpl) carvedEvidence, off);                
                }
            };
    	}
    	return carvedItemListener;
    }

    @Override
    protected boolean isToProcess(Item evidence) {
        return super.isToProcess(evidence) && carverConfig.isToProcess(evidence.getMediaType());
    }
    
    protected HashMap<CarverType, Carver> registeredCarvers = new HashMap<CarverType, Carver>();

    public Carver getCarver(CarverType ct) {
        Carver carver = registeredCarvers.get(ct);
        try {
        	if(carver==null) {
                if (ct.getCarverClass().equals(JSCarver.class.getName())) {
                	carver=carverConfig.createCarverFromJSName(ct.getCarverScript());
                    carver.registerCarvedItemListener(getCarvedItemListener());
                } else {
            		Class<?> classe = this.getClass().getClassLoader().loadClass(ct.getCarverClass());
            		carver = (Carver) classe.getDeclaredConstructor().newInstance();
            		carver.registerCarvedItemListener(getCarvedItemListener());
                }
                carver.setIgnoreCorrupted(carverConfig.isToIgnoreCorrupted());
                registeredCarvers.put(ct, carver);
        	}
        } catch (Exception e) {
            e.printStackTrace();
        }

        return carver;
    }
}