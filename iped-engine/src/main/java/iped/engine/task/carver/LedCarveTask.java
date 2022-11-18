package iped.engine.task.carver;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.config.LocalConfig;
import iped.engine.hashdb.HashDBDataSource;
import iped.engine.hashdb.LedHashDB;
import iped.engine.hashdb.LedItem;
import iped.properties.MediaTypes;
import iped.utils.IOUtil;

public class LedCarveTask extends BaseCarveTask {

    private static final String ENABLE_PARAM = "enableLedCarving";

    private static Logger logger = LoggerFactory.getLogger(LedCarveTask.class);

    /**
     * Indica se a tarefa está habilitada ou não.
     */
    private static boolean taskEnabled = false;

    /**
     * Indicador de inicialização, para controle de sincronização entre instâncias
     * da classe.
     */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Objeto estático para sincronizar finalização.
     */
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * Contador de arquivos recuperados.
     */
    private static final AtomicInteger numCarvedItems = new AtomicInteger();

    /**
     * Contador de bytes com hash calculado.
     */
    private static final AtomicLong bytesHashed = new AtomicLong();

    /**
     * Contador do total de blocos de 512 processados.
     */
    private static final AtomicLong num512total = new AtomicLong();

    /**
     * Contador do total de hits em blocos de 512 bytes.
     */
    private static final AtomicLong num512hit = new AtomicLong();

    /**
     * Digest utilizado para cálculo do MD5.
     */
    private MessageDigest digest = null;

    /**
     * Base de hashes, com MD5 dos 512 bytes e 64 KBytes iniciais, e respectivos registros na base.
     */
    private static LedHashDB ledHashDB;

    private static HashDBDataSource hashDBDataSource;

    private static final String cachePath = System.getProperty("user.home") + "/.iped/ledcarve.cache";

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    public static void setEnabled(boolean enabled) {
        taskEnabled = enabled;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        EnableTaskProperty result = ConfigurationManager.get().findObject(EnableTaskProperty.class);
        if(result == null) {
            result = new EnableTaskProperty(ENABLE_PARAM);
        }
        return Arrays.asList(result);
    }

    /**
     * Inicializa tarefa.
     */
    public void init(ConfigurationManager configurationManager) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                boolean enableParam = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
                if (enableParam) {
                    LocalConfig localConfig = (LocalConfig) configurationManager.findObject(LocalConfig.class);
                    File hashDBFile = localConfig.getHashDbFile();
                    if (hashDBFile == null) {
                        logger.error("Hashes database path (hashesDB) must be configured in {}", Configuration.LOCAL_CONFIG);
                    } else {
                        if (!hashDBFile.exists() || !hashDBFile.canRead() || !hashDBFile.isFile()) {
                            String msg = (!hashDBFile.exists() ? "Missing": "Invalid") + " hashes database file: " + hashDBFile.getAbsolutePath();
                            if (hasIpedDatasource()) {
                                logger.warn(msg);
                            } else {
                                logger.error(msg);
                            }
                        } else {
                            hashDBDataSource = new HashDBDataSource(hashDBFile);
                            long t = System.currentTimeMillis();
                            if (readCache(hashDBFile)) {
                                logger.info("Load from cache file {}.", cachePath);
                            } else {
                                ledHashDB = hashDBDataSource.readLedHashDB();
                                if (ledHashDB == null || ledHashDB.size() == 0) {
                                    logger.error("LED hashes must be loaded into IPED hashes database to enable LedCarveTask.");
                                } else if (writeCache(hashDBFile)) {
                                    logger.info("Cache file {} was created.", cachePath);
                                }
                            }
                            if (ledHashDB != null && ledHashDB.size() > 0) {
                                logger.info("{} LED Hashes loaded in {} ms.", ledHashDB.size(), System.currentTimeMillis() - t);
                                taskEnabled = true;
                            }
                        }
                    }
                }
                logger.info("Task {}.", taskEnabled ? "enabled" : "disabled");
                init.set(true);
            }
        }
        if (taskEnabled) digest = MessageDigest.getInstance("MD5");
    }

    /**
     * Finaliza a tarefa.
     */
    public void finish() throws Exception {
        synchronized (finished) {
            if (taskEnabled && !finished.get()) {
                ledHashDB = null;
                hashDBDataSource.close();
                ledCarved.clear();
                NumberFormat nf = new DecimalFormat("#,##0");
                logger.info("Carved files: " + nf.format(numCarvedItems.get()));
                logger.info("512 blocks (Hits / Total): " + nf.format(num512hit.get()) + " / " + nf.format(num512total.get()));
                logger.info("Bytes hashed: " + nf.format(bytesHashed.get()));
                finished.set(true);
            }
        }
    }

    protected void process(IItem evidence) throws Exception {
        // Verifica se está desabilitado e se o tipo de arquivo é tratado
        if (!taskEnabled || caseData.isIpedReport() || !isAcceptedType(evidence.getMediaType()) || !isToProcess(evidence)) return;

        byte[] buf512 = new byte[512];
        byte[] buf64K = new byte[65536 - buf512.length];
        BufferedInputStream is = null;

        int cntCarvedItems = 0;
        long cnt512hit = 0;
        long cnt512total = 0;
        long cntBytesHashed = 0;
        Set<Long> offsets = null;
        try {
            long offset = 0;
            int read512 = 0;
            is = evidence.getBufferedInputStream();
            while ((read512 = is.read(buf512)) > 0) {
                if (read512 != buf512.length) break;
                cnt512total++;
                boolean empty = true;
                byte first = buf512[0];
                for (int i = 1; i < read512; i++) {
                    if (buf512[i] != first) {
                        empty = false;
                        break;
                    }
                }
                if (!empty) {
                    digest.update(buf512, 0, read512);
                    cntBytesHashed += read512;
                    byte[] hash512 = digest.digest();
                    if (ledHashDB.containsMD5_512(hash512)) {
                        cnt512hit++;
                        is.mark(65536);
                        int read64K = is.read(buf64K);
                        is.reset();
                        if (read64K == buf64K.length) {
                            cntBytesHashed += read512 + read64K;
                            digest.update(buf512, 0, read512);
                            digest.update(buf64K, 0, read64K);
                            byte[] hash64K = digest.digest();
                            int hashId = ledHashDB.hashIdFromMD5_64K(hash64K);
                            if (hashId >= 0) {
                                LedItem ledItem = hashDBDataSource.getLedItem(hashId);
                                if (ledItem != null) {
                                    String name = "CarvedLed-" + offset;
                                    String ext = ledItem.getExt();
                                    if (ext != null) name += '.' + ext.toLowerCase();
                                    IItem carvedItem = createCarvedFile(evidence, offset, ledItem.getLength(), name, null);
                                    if (carvedItem != null) {
                                        carvedItem.setExtraAttribute("ledCarvedMD5", ledItem.getMD5());
                                        cntCarvedItems++;
                                        if (offsets == null) {
                                            offsets = new HashSet<Long>();
                                            synchronized (ledCarved) {
                                                ledCarved.put(evidence, offsets);
                                            }
                                        }
                                        offsets.add(offset);
                                        addOffsetFile(carvedItem, evidence);
                                    }
                                }
                            }
                        }
                    }
                }
                offset += read512;
            }
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        } finally {
            IOUtil.closeQuietly(is);
        }
        numCarvedItems.addAndGet(cntCarvedItems);
        num512hit.addAndGet(cnt512hit);
        num512total.addAndGet(cnt512total);
        bytesHashed.addAndGet(cntBytesHashed);
    }

    private static boolean isAcceptedType(MediaType mediaType) {
        return mediaType.getBaseType().equals(UNALLOCATED_MIMETYPE)
                || mediaType.getBaseType().equals(MediaType.OCTET_STREAM)
                || mediaType.getBaseType().equals(MediaTypes.VDI)
                || mediaType.getBaseType().equals(MediaTypes.VHDX)
                || mediaType.getBaseType().equals(mtPageFile)
                || mediaType.getBaseType().equals(mtVolumeShadow);
    }

    private boolean writeCache(File hashDBFile) {
        File cacheFile = new File(cachePath);
        boolean ret = false;
        DataOutputStream os = null;
        try {
            if (cacheFile.getParentFile() != null && !cacheFile.getParentFile().exists()) {
                cacheFile.getParentFile().mkdirs();
            }
            os = new DataOutputStream(new FileOutputStream(cacheFile));
            os.writeLong(hashDBFile.length());
            os.writeLong(hashDBFile.lastModified());
            os.writeInt(ledHashDB.getMD5_512().length);
            os.write(ledHashDB.getMD5_512());
            os.writeInt(ledHashDB.getMD5_64K().length);
            os.write(ledHashDB.getMD5_64K());
            os.writeInt(ledHashDB.getHashIds().length);
            IOUtil.writeIntArray(os, ledHashDB.getHashIds());
            ret = true;
        } catch (Exception e) {
            logger.warn("Error writing cache file " + cacheFile.getPath(), e);
            return false;
        } finally {
            IOUtil.closeQuietly(os);
            if (!ret) {
                try {
                    cacheFile.delete();
                } catch (Exception e) {}
            }
        }
        return ret;
    }

    private boolean readCache(File hashDBFile) {
        File cacheFile = new File(cachePath);
        if (!cacheFile.exists()) return false;
        DataInputStream is = null;
        boolean ret = false;
        try {
            is = new DataInputStream(new FileInputStream(cacheFile));
            long fileLen = is.readLong();
            if (fileLen == hashDBFile.length()) {
                long fileLastModified = is.readLong();
                if (fileLastModified == hashDBFile.lastModified()) {
                    int len = is.readInt();
                    byte[] md5_512 = IOUtil.readByteArray(is, len);
                    if (md5_512 != null) {
                        len = is.readInt();
                        byte[] md5_64k = IOUtil.readByteArray(is, len);
                        if (md5_64k != null) {
                            len = is.readInt();
                            int[] hashIds = IOUtil.readIntArray(is, len);
                            if (hashIds != null) {
                                ledHashDB = new LedHashDB(md5_512, md5_64k, hashIds);
                                ret = true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error reading cache file " + cacheFile.getPath(), e);
            return false;
        } finally {
            IOUtil.closeQuietly(is);
            if (!ret) {
                try {
                    cacheFile.delete();
                } catch (Exception e) {}
            }
        }
        return ret;
    }
}