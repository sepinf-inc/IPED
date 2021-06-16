package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;
import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup.LookupProvider;
import gpinf.hashdb.HashDB;
import gpinf.hashdb.HashDBDataSource;
import iped3.IItem;

public class HashDBLookupTask extends AbstractTask {
    private static final String ENABLE_PARAM = "enableHashDBLookup";
    public static final String ATTRIBUTES_PREFIX = "hashDb:";
    private static final String STATUS_PROPERTY = "status";
    public static final String STATUS_ATTRIBUTE = ATTRIBUTES_PREFIX + STATUS_PROPERTY;
    private static final String KNOWN_VALUE = "known";
    private static final String NSRL_PRODUCT_NAME_PROPERTY = "nsrlProductName";

    private Logger logger = LoggerFactory.getLogger(HashDBLookupTask.class);

    public static int excluded;

    private static boolean taskEnabled;
    private static boolean excludeKnown;

    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finish = new AtomicBoolean(false);

    private static final AtomicLong totTime = new AtomicLong();
    private static final AtomicLong totProcessed = new AtomicLong();
    private static final AtomicLong totFound = new AtomicLong();

    private static File hashDBFile;
    private static String[] hashesAttributes;

    private HashDBDataSource hashDBDataSource;

    private byte[][] hashes;
    private final Map<String, String> properties = new HashMap<String, String>();

    private static final String NSRL_CONFIG_FILE = "NSRLConfig.json";
    private static String nsrlDefaultStatus;
    private static boolean nsrlDefaultMerge;
    private static final Map<String, String> nsrlStatusByProdName = new HashMap<String, String>();
    private static final Map<String, Boolean> nsrlMergeByProdName = new HashMap<String, Boolean>();

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                String config = confParams.getProperty(ENABLE_PARAM);
                taskEnabled = config != null && Boolean.parseBoolean(config.trim());
                if (taskEnabled) {
                    String hashes = confParams.getProperty("hash");
                    if (hashes == null) {
                        logger.warn("No hash enabled.");
                        taskEnabled = false;
                    } else {
                        hashesAttributes = new String[HashDB.hashTypes.length];
                        String[] hashTypes = hashes.split(";");
                        for (String hashType : hashTypes) {
                            hashType = hashType.trim();
                            int idx = HashDB.hashType(hashType);
                            if (idx >= 0) {
                                hashesAttributes[idx] = hashType;
                            }
                        }
                        String hashDBPath = confParams.getProperty("hashesDB");
                        if (hashDBPath == null) {
                            logger.error("Hashes database path (hashesDB) must be configured in {}", Configuration.LOCAL_CONFIG);
                            taskEnabled = false;
                        } else {
                            hashDBFile = new File(hashDBPath.trim());
                            if (!hashDBFile.exists() || !hashDBFile.canRead() || !hashDBFile.isFile()) {
                                String msg = (!hashDBFile.exists() ? "Missing": "Invalid") + " hashes database file: " + hashDBFile.getAbsolutePath();
                                if (hasIpedDatasource()) {
                                    logger.warn(msg);
                                } else {
                                    logger.error(msg);
                                }
                                taskEnabled = false;
                            } else {
                                String s = confParams.getProperty("excludeKnown");
                                excludeKnown = s != null && Boolean.parseBoolean(s.trim());
                                hashDBDataSource = new HashDBDataSource(hashDBFile);
                                addLookupProvider(hashDBDataSource);
                                File nsrlConfigFile = new File(confDir, NSRL_CONFIG_FILE);
                                if (nsrlConfigFile.exists()) {
                                    loadNsrlConfig(nsrlConfigFile);
                                    if (!nsrlStatusByProdName.isEmpty()) {
                                        logger.info("NSRL product configurations loaded: {}", nsrlStatusByProdName.size());
                                    }
                                }
                                logger.info("HashDB: {}", hashDBFile.getAbsolutePath());
                                logger.info("Exclude Known: {}", excludeKnown);
                            }
                        }
                    }
                }
                logger.info("Task {}.", taskEnabled ? "enabled" : "disabled");
                init.set(true);
            }
        }
        if (taskEnabled) {
            hashes = new byte[hashesAttributes.length][];
            if (hashDBDataSource == null) hashDBDataSource = new HashDBDataSource(hashDBFile);
        }
    }

    private void addLookupProvider(HashDBDataSource hashDBDataSource) {
        ChildPornHashLookup.addLookupProvider(new LookupProvider() {
            public List<String> lookupHash(String algorithm, String hash) {
                try {
                    return hashDBDataSource.lookupSets(algorithm, hash);
                } catch (Exception e) {
                    logger.warn("Error in lookupHash " + algorithm + " : " + hash, e);
                }
                return null;
            }
        });
    }

    @Override
    public void finish() throws Exception {
        synchronized (finish) {
            if (!finish.get()) {
                if (hashDBDataSource != null) {
                    hashDBDataSource.close();
                }
                if (excluded > 0) {
                    logger.info("Items ignored by hash database lookup: {}", excluded);
                }
                logger.info("Total items processed: {}", totProcessed.longValue());
                if (totProcessed.longValue() > 0) {
                    logger.info("Total items found: {}", totFound.longValue());
                    logger.info("Average processing time (ms/item): {}", String.format("%.2f", totTime.longValue() / 1e6 / totProcessed.longValue()));
                }
                finish.set(true);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    public static void setEnabled(boolean enabled) {
        taskEnabled = enabled;
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        if (!isEnabled()) return;
        if (evidence.isDir() || evidence.isRoot() || evidence.getLength() == null) {
            return;
        }
        if (evidence.getLength() == 0) {
            // ignore zero sized files
            if (excludeKnown) {
                evidence.setToIgnore(true);
                synchronized (init) {
                    excluded++;
                }
            }
            return;
        }
        long t = System.nanoTime();
        Arrays.fill(hashes, null);
        boolean hasHash = false;
        boolean found = false;
        for (int i = 0; i < hashesAttributes.length; i++) {
            String key = hashesAttributes[i];
            if (key != null) {
                Object hashValue = evidence.getExtraAttribute(key);
                if (hashValue != null && (hashValue instanceof String)) {
                    String value = (String) hashValue;
                    if (!value.isEmpty()) {
                        hashes[i] = HashDB.hashStrToBytes(value, HashDB.hashBytesLen[i]);
                        hasHash = true;
                    }
                }
            }
        }
        if (hasHash) {
            properties.clear();
            try {
                hashDBDataSource.lookup(hashes, properties);
            } catch (Exception e) {
                logger.warn("Error looking up evidence " + evidence, e);
                return;
            }
            if (!properties.isEmpty()) {
                List<String> status = null;
                String nsrlProductName = null;
                found = true;
                for (String key : properties.keySet()) {
                    String value = properties.get(key);
                    List<String> list = new ArrayList<String>();
                    String[] c = value.split("\\|");
                    for (String a : c) {
                        a = a.trim();
                        if (!a.isEmpty()) {
                            list.add(a);
                        }
                    }
                    if (!list.isEmpty()) {
                        evidence.setExtraAttribute(ATTRIBUTES_PREFIX + key, list);
                        if (key.equalsIgnoreCase(STATUS_PROPERTY)) {
                            status = list;
                        } else if (key.equalsIgnoreCase(NSRL_PRODUCT_NAME_PROPERTY)) {
                            nsrlProductName = value;
                        }
                    }
                }
                //NSRL specific: set item status based on product name  
                if (nsrlProductName != null) {
                    boolean modified = false;
                    boolean productFound = false;
                    String[] productNames = nsrlProductName.split("\\|");
                    for (String name : productNames) {
                        String s = nsrlStatusByProdName.get(name);
                        if (s != null) {
                            productFound = true;
                            Boolean merge = nsrlMergeByProdName.get(name);
                            if (status == null || status.isEmpty() || Boolean.TRUE.equals(merge)) {
                                if (status == null) {
                                    status = new ArrayList<String>();
                                }
                                if (!status.contains(s)) {
                                    status.add(s);
                                    modified = true;
                                }
                            }
                        }
                    }
                    if (!productFound && nsrlDefaultStatus != null) {
                        if (status == null || status.isEmpty() || nsrlDefaultMerge) {
                            if (status == null) {
                                status = new ArrayList<String>();
                            }
                            if (!status.contains(nsrlDefaultStatus)) {
                                status.add(nsrlDefaultStatus);
                                modified = true;
                            }
                        }
                    }
                    if (modified) {
                        Collections.sort(status);
                        evidence.setExtraAttribute(STATUS_ATTRIBUTE, status);
                    }
                }
                //Ignore only if there is a single status = "known"
                if (excludeKnown && status != null && status.size() == 1 && status.get(0).equalsIgnoreCase(KNOWN_VALUE)) {
                    evidence.setToIgnore(true);
                    synchronized (init) {
                        excluded++;
                    }
                }
            }
        }
        t = System.nanoTime() - t;
        totTime.addAndGet(t);
        totProcessed.incrementAndGet();
        if (found) totFound.incrementAndGet();
    }

    private void loadNsrlConfig(File file) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(file);
            JsonNode node = root.get("defaultStatus");
            if (node != null) {
                nsrlDefaultStatus = node.asText();
            }
            node = root.get("defaultWhenPresent");
            if (node != null) {
                nsrlDefaultMerge = "merge".equalsIgnoreCase(node.asText());
            }
            JsonNode arr = root.get("productStatus");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    node = arr.get(i);
                    JsonNode child = node.get("status");
                    String status = child.asText();
                    child = node.get("whenPresent");
                    boolean merge = "merge".equalsIgnoreCase(child.asText());
                    child = node.get("productNames");
                    for (int j = 0; j < child.size(); j++) {
                        String prodName = child.get(j).asText();
                        nsrlStatusByProdName.put(prodName, status);
                        nsrlMergeByProdName.put(prodName, merge);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error reading NSRL configuration file: " + file.getPath(), e);
        }
    }
}