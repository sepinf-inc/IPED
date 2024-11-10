package iped.engine.task.carver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.apache.tika.mime.MediaType;
import org.arabidopsis.ahocorasick.AhoCorasick;
import org.arabidopsis.ahocorasick.SearchResult;
import org.arabidopsis.ahocorasick.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.carvers.api.CarvedItemListener;
import iped.carvers.api.Carver;
import iped.carvers.api.CarverType;
import iped.carvers.api.Hit;
import iped.carvers.api.Signature;
import iped.carvers.standard.JSCarver;
import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.data.Item;
import iped.properties.MediaTypes;
import iped.utils.IOUtil;

/**
 * Classe responsável pelo Data Carving. Utiliza o algoritmo aho-corasick, o
 * qual gera uma máquina de estados a partir dos padrões a serem pesquisados.
 * Assim, o algoritmo é independente do número de assinaturas pesquisadas, sendo
 * proporcional ao volume de dados de entrada e ao número de padrões
 * descobertos.
 */
public class CarverTask extends BaseCarveTask {

    public static boolean enableCarving = false;
    public static boolean ignoreCorrupted = true;

    private static CarverType[] carverTypes;
    private static Logger LOGGER = LoggerFactory.getLogger(CarverTask.class);
    private static int largestPatternLen = 100;

    protected HashMap<CarverType, Carver> registeredCarvers = new HashMap<CarverType, Carver>();
    private CarvedItemListener carvedItemListener = null;
    IItem evidence;

    long prevLen = 0;
    int len = 0, k = 0;
    byte[] buf = new byte[1024 * 1024];
    byte[] cBuf;

    public static void setEnabled(boolean enabled) {
        enableCarving = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enableCarving;
    }

    public void process(IItem evidence) {
        if (!enableCarving) {
            return;
        }

        // Nova instancia pois o mesmo objeto é reusado e nao é imutável
        CarverTask carver = new CarverTask();
        carver.setWorker(worker);
        carver.safeProcess(evidence);

        // Ao terminar o tratamento do item, caso haja referência ao mesmo no mapa de
        // itens carveados através do LedCarving, esta pode ser removida.
        synchronized (ledCarved) {
            ledCarved.remove(evidence);
        }
    }

    private void safeProcess(IItem evidence) {

        this.evidence = evidence;

        if (!isToProcess(evidence)) {
            return;
        }

        InputStream tis = null;
        try {
            MediaType type = evidence.getMediaType();

            tis = evidence.getBufferedInputStream();

            // Images used to be carved from PUB files. But with Tika-2.4 PUB is a subtype
            // of OLE (correct) and default carving config skips OLE files.
            // TODO externalize this to CarverConfig.xml and implement a general approach
            boolean isPUBFile = MediaTypes.MS_PUBLISHER.equals(type);

            // faz um loop na hierarquia de tipos mime
            while (!MediaType.OCTET_STREAM.equals(type)) {
                if (carverConfig.isToNotProcess(type) && !isPUBFile) {
                    tis.close();
                    return;
                }
                // avança 1 byte para não recuperar o próprio arquivo analisado
                if (carverConfig.isToCarve(type)) {
                    prevLen = (int) tis.skip(1);
                    // break;
                }

                type = MediaTypes.getParentType(type);
            }

            clearExtraAttributes(evidence);

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
                    } catch (Exception e) {
                        LOGGER.warn("{} Skipping unexpected error carving on hit {} {} - CarverClass {}", //$NON-NLS-1$
                                Thread.currentThread().getName(), evidence.getPath(), hit.getOffset(),
                                carver.getClass().getName());
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
    public List<Configurable<?>> getConfigurables() {
        CarverTaskConfig result = ConfigurationManager.get().findObject(CarverTaskConfig.class);
        if(result == null) {
            result = new CarverTaskConfig();
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        CarverTaskConfig ctConfig = configurationManager.findObject(CarverTaskConfig.class);
        FileSystemConfig fsConfig = configurationManager.findObject(FileSystemConfig.class);

        enableCarving = ctConfig.isEnabled();

        if (carverTypes == null && enableCarving && !fsConfig.isToAddUnallocated())
            LOGGER.error("addUnallocated is disabled, so carving will NOT be done in unallocated space!"); //$NON-NLS-1$

        carvedItemListener = getCarvedItemListener();

        if (carverConfig == null) {
            carverConfig = ctConfig.getConfiguration();
            carverConfig.configListener(carvedItemListener);
            carverTypes = carverConfig.getCarverTypes();
            ignoreCorrupted = carverConfig.isToIgnoreCorrupted();
        }
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    private CarvedItemListener getCarvedItemListener() {
        if (carvedItemListener == null) {
            carvedItemListener = new CarvedItemListener() {
                public void processCarvedItem(IItem parentEvidence, IItem carvedEvidence, long off) {
                    addCarvedEvidence((Item) parentEvidence, (Item) carvedEvidence, off);
                }
            };
        }
        return carvedItemListener;
    }

    private Carver getCarver(CarverType ct) {
        Carver carver = registeredCarvers.get(ct);
        try {
            if (carver == null) {
                if (ct.getCarverClass().equals(JSCarver.class.getName())) {
                    File script = new File(new File(this.output, "conf"), ct.getCarverScript());
                    carver = carverConfig.createCarverFromJSName(script);
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