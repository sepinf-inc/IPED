package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.swing.ProgressMonitor;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;
import dpf.sp.gpinf.indexer.util.IPEDException;
import iped3.IItem;

/**
 * Tarefa de KFF com implementação simples utilizando base local, sem servidor
 * de banco de dados. É altamente recomendável armazenar a base em disco SSD,
 * sob pena de degradar o processamento. Isso porque a biblioteca utiliza o
 * cache do sistema, mas o page size geralmente é de apenas 4KB, sendo difícil
 * que novos hashes pesquisados (aleatórios) caiam em blocos já carregados no
 * cache do SO antes que sejam descartados, pois os hashes são de difícil
 * repetição.
 *
 * @author Nassif
 *
 */
public class KFFTask extends AbstractTask {

    private Logger LOGGER = LoggerFactory.getLogger(KFFTask.class);
    private static final String CONF_FILE = "KFFTaskConfig.txt"; //$NON-NLS-1$
    private static final String ENABLE_PARAM = "enableKff"; //$NON-NLS-1$

    public static final String KFF_STATUS = "kffstatus"; //$NON-NLS-1$
    public static final String KFF_GROUP = "kffgroup"; //$NON-NLS-1$

    public static int excluded = 0;
    private static Object lock = new Object();

    /*
     * valor negativo no mapa indica hash ignorável valor positivo indica alerta
     */
    private static Map<HashValue, Integer> map;
    private static Map<HashValue, Integer> md5Map;
    private static Map<HashValue, Integer> sha1Map;

    private static Map<Integer, String[]> products;
    private static Set<String> alertProducts;
    private static DB db;
    private static boolean taskEnabled = true;

    private boolean excludeKffIgnorable = true;
    private boolean md5 = true;
    private boolean importing = false;

    public static void setEnabled(boolean enabled) {
        taskEnabled = enabled;
    }
    
    public void init(Properties confParams, File confDir, boolean importing) throws Exception {
        this.importing = importing;
        init(confParams, confDir);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        String hashes = confParams.getProperty("hash"); //$NON-NLS-1$
        if (hashes == null || !taskEnabled) {
            return;
        }
        if (hashes.contains("md5")) { //$NON-NLS-1$
            md5 = true;
        } else if (hashes.contains("sha-1")) { //$NON-NLS-1$
            md5 = false;
        }

        String enableParam = confParams.getProperty(ENABLE_PARAM);
        if (enableParam != null)
            taskEnabled = Boolean.valueOf(enableParam.trim());

        excludeKffIgnorable = Boolean.valueOf(confParams.getProperty("excludeKffIgnorable").trim()); //$NON-NLS-1$

        String kffDbPath = confParams.getProperty("kffDb"); //$NON-NLS-1$
        if (kffDbPath == null) {
            String msg = "Configure hash database path (kffDb) on " + Configuration.LOCAL_CONFIG; //$NON-NLS-1$
            if(importing) {
            	throw new IPEDException(msg);
            }
            if(taskEnabled) {
            	LOGGER.error(msg);
                taskEnabled = false;
                return;
            }
        }

        // backwards compatibility
        if (enableParam == null && kffDbPath != null)
            taskEnabled = true;

        if (!importing && !taskEnabled)
            return;

        if (map == null) {
            excluded = 0;

            File kffDb = new File(kffDbPath.trim());
            if(importing)
                kffDb.getParentFile().mkdirs();
            
            if (!kffDb.exists() && !importing) {
                String msg = "Invalid hash database path on " + kffDb.getAbsolutePath(); //$NON-NLS-1$
                LOGGER.error(msg);
                taskEnabled = false;
                return;
            }

            try {
                DBMaker dbMaker = DBMaker.newFileDB(kffDb).transactionDisable().mmapFileEnableIfSupported();
                if(importing) {
                    dbMaker.asyncWriteEnable().asyncWriteFlushDelay(1000).asyncWriteQueueSize(1024000);
                }else {
                    dbMaker.readOnly();
                }
                db  = dbMaker.make();

            } catch (ArrayIndexOutOfBoundsException e) {
                throw new Exception("Hash database seems corrupted. Import or copy again BOTH files " //$NON-NLS-1$
                        + "kff.db and kff.db.p to database folder " + kffDb.getParent()); //$NON-NLS-1$
            }

            md5Map = db.getHashMap("md5Map"); //$NON-NLS-1$
            sha1Map = db.getHashMap("sha1Map"); //$NON-NLS-1$
            products = db.getHashMap("productMap"); //$NON-NLS-1$

            if (md5) {
                map = md5Map;
            } else {
                map = sha1Map;
            }

            if (confDir != null) {
                alertProducts = new HashSet<String>();
                File confFile = new File(kffDb.getParentFile(), CONF_FILE); 
                if(!confFile.exists())
                    confFile = new File(confDir, CONF_FILE);
                BufferedReader reader = new BufferedReader(new FileReader(confFile));
                String line = reader.readLine();
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) { //$NON-NLS-1$
                        continue;
                    }
                    alertProducts.add(line.trim());
                }
                reader.close();
            }

        }
    }

    @Override
    public void finish() throws Exception {
        if (excluded != -1) {
            LOGGER.info("Items ignored by hash database lookup: {}", excluded); //$NON-NLS-1$
        }
        excluded = -1;
        
        if(db != null) {
            db.close();
            db = null;
        }
        md5Map = null;
        sha1Map = null;
        map = null;
    }

    public void importKFF(File kffDir) throws IOException {

        File NSRLProd = new File(kffDir, "NSRLProd.txt"); //$NON-NLS-1$
        BufferedReader reader = new BufferedReader(new FileReader(NSRLProd));
        String line = reader.readLine();
        while ((line = reader.readLine()) != null) {
            int idx = line.indexOf(',');
            String key = line.substring(0, idx);
            String[] values = line.substring(idx + 2).split("\",\""); //$NON-NLS-1$
            String[] prod = { values[0], values[1] };
            products.put(Integer.valueOf(key), prod);
        }
        reader.close();
        for (File kffFile : kffDir.listFiles()) {
            if (!kffFile.getName().equals("NSRLFile.txt")) { //$NON-NLS-1$
                continue;
            }
            long length = kffFile.length();
            long progress = 0;
            int i = 0;
            ProgressMonitor monitor = new ProgressMonitor(null, "", "Importing " + kffFile.getName(), 0, //$NON-NLS-1$ //$NON-NLS-2$
                    (int) (length / 1000));
            reader = new BufferedReader(new FileReader(kffFile));
            line = reader.readLine();
            String[] ignoreStrs = { "\"\"", "\"D\"" }; //$NON-NLS-1$ //$NON-NLS-2$
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(","); //$NON-NLS-1$
                KffAttr attr = new KffAttr();
                attr.group = Integer.valueOf(values[values.length - 3]);
                if (values[values.length - 1].equals(ignoreStrs[0])
                        || values[values.length - 1].equals(ignoreStrs[1])) {
                    attr.group *= -1;
                }
                // else
                // System.out.println(line);

                HashValue md5 = new HashValue(values[1].substring(1, 33));
                HashValue sha1 = new HashValue(values[0].substring(1, 41));

                Integer value = md5Map.get(md5);
                if (value == null || (value > 0 && attr.group < 0)) {
                    md5Map.put(md5, attr.group);
                    sha1Map.put(sha1, attr.group);
                }

                progress += line.length() + 2;
                if (progress > i * length / 1000) {
                    if (monitor.isCanceled()) {
                        return;
                    }
                    monitor.setProgress((int) (progress / 1000));
                    i++;
                }

            }
            reader.close();
            db.commit();
            db.close();
            monitor.close();
        }
    }

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isEnabled())
            return;

        HashValue hash = null;
        if (evidence.getHash() != null && !evidence.getHash().isEmpty()) {
            hash = new HashValue(evidence.getHash());
        }
        if (map != null && hash != null && !evidence.isDir() && !evidence.isRoot()) {
            Integer attr = map.get(hash);
            if (attr != null) {
                String[] product = products.get(Math.abs(attr));
                if (attr > 0 || alertProducts.contains(product[0])) // evidence.addCategory(ALERT);
                {
                    evidence.setExtraAttribute(KFF_STATUS, "alert"); //$NON-NLS-1$
                } else {
                    if (excludeKffIgnorable) {
                        evidence.setToIgnore(true);
                        synchronized (lock) {
                            excluded++;
                        }
                    } else {
                        evidence.setExtraAttribute(KFF_STATUS, "ignore"); //$NON-NLS-1$
                    }
                }
                evidence.setExtraAttribute(KFF_GROUP, product[0] + " " + product[1]); //$NON-NLS-1$
            }
        }
    }

    private static class KffAttr implements Serializable {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        boolean alert = false;
        int group;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null) {
                return false;
            }

            KffAttr attr = (KffAttr) o;

            if (alert != attr.alert) {
                return false;
            }
            if (group != attr.group) {
                return false;
            }

            return true;
        }
    }

    static class ValueSerializer implements Serializer<KffAttr>, Serializable {

        @Override
        public void serialize(DataOutput out, KffAttr value) throws IOException {
            out.writeBoolean(value.alert);
            // out.writeUTF(value.group);
        }

        @Override
        public KffAttr deserialize(DataInput in, int available) throws IOException {
            KffAttr value = new KffAttr();
            value.alert = in.readBoolean();
            // value.group = in.readUTF();
            return value;
        }

        @Override
        public int fixedSize() {
            return -1;
        }

    }

}
