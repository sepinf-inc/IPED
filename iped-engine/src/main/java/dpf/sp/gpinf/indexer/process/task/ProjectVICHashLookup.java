package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.IPEDException;
import iped3.IItem;

public class ProjectVICHashLookup extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(ProjectVICHashLookup.class);

    private static final String CACHE_PATH = System.getProperty("user.home") + "/.indexador/projectvic.cache";

    private static final String JSON_PATH_KEY = "projectVicHashSetPath";

    private static final String ENABLE_KEY = "enableProjectVicHashLookup";

    private static final String DATA_MODEL = "http://github.com/ICMEC/ProjectVic/DataModels/1.3.xml#Media";

    private static boolean isMd5Enabled;

    private static VicSet vicSet = new VicSet();

    private static Boolean enabled;

    private class VicEntry extends HashValue {

        private static final long serialVersionUID = 1L;

        private byte category;
        private boolean victimIdentified;
        private boolean ofenderIdentified;
        private boolean isDistributed;
        private int seriesId;

        private VicEntry() {
            super();
        }

        private VicEntry(byte[] bytes) {
            super(bytes);
        }

    }

    private static class VicSet implements Serializable {

        private static final long serialVersionUID = 9091381179250834449L;

        private HashMap<Integer, String> seriesMap = new HashMap<>();
        private byte[] compressedHashArray;
    }

    @Override
    public void finish() throws Exception {
        vicSet = null;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        if (enabled == null)
            enabled = Boolean.parseBoolean(confParams.getProperty(ENABLE_KEY, "false"));

        if (!enabled)
            return;

        if (vicSet.compressedHashArray != null)
            return;

        File vicJsonFile = null;
        String path = confParams.getProperty(JSON_PATH_KEY);
        if (path != null && !path.trim().isEmpty()) {
            vicJsonFile = new File(path.trim());
        }
        if (vicJsonFile == null || !vicJsonFile.exists()) {
            String msg = vicJsonFile == null ? " not configured." : " not found.";
            logger.error(JSON_PATH_KEY + msg + " ProjectVic hashset lookup will be disabled.");
            enabled = false;
            return;
        }

        String hashes = confParams.getProperty(HashTask.HASH_PROP);
        String hash;
        if (hashes != null && hashes.contains(HashTask.HASH.MD5.toString())) {
            isMd5Enabled = true;
            hash = HashTask.HASH.MD5.toString();
        } else if (hashes != null && hashes.contains(HashTask.HASH.SHA1.toString())) {
            isMd5Enabled = false;
            hash = HashTask.HASH.SHA1.toString();
        } else {
            logger.error("Neither md5 nor sha-1 were enabled. ProjectVic hashset lookup will be disabled.");
            enabled = false;
            return;
        }
        String cacheSuffix = "." + hash;

        long jsonDate = vicJsonFile.lastModified();

        File cacheFile = new File(CACHE_PATH + cacheSuffix);
        if (cacheFile.exists() && cacheFile.lastModified() == jsonDate) {
            logger.info("Loading ProjectVic cache from " + new Date(jsonDate) + ": " + cacheFile.getAbsolutePath());
            try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(cacheFile.toPath()));
                    ObjectInputStream ois = new ObjectInputStream(bis)) {
                vicSet = (VicSet) ois.readObject();
            }
            return;
        } else {
            Files.deleteIfExists(cacheFile.toPath());
        }

        List<VicEntry> vicHashList = new ArrayList<>();
        logger.info("Loading ProjectVic json " + vicJsonFile.getAbsolutePath());

        int nullHashes = 0;

        HashMap<String, Integer> seriesMap = new HashMap<>();

        JsonFactory jfactory = new JsonFactory();
        try (Reader reader = Files.newBufferedReader(vicJsonFile.toPath())) {
            JsonParser jp = jfactory.createParser(reader);
            if (jp.nextToken() != JsonToken.START_OBJECT) {
                throw new IPEDException("Error: root should be object: quiting.");
            }
            while (jp.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jp.getCurrentName();
                if (!fieldName.equals("odata.metadata") || !jp.nextTextValue().equals(DATA_MODEL)) {
                    throw new IPEDException("Unknown ProjectVic json data model!");
                }
                if (jp.nextFieldName().equals("value")) {
                    int arrayDepth = 0;
                    VicEntry ve = null;
                    int seriesNum = 1;
                    do {
                        JsonToken token = jp.nextToken();
                        if (token == JsonToken.START_ARRAY)
                            arrayDepth++;
                        else if (token == JsonToken.END_ARRAY)
                            arrayDepth--;
                        else if (arrayDepth == 1) {
                            if (token == JsonToken.START_OBJECT) {
                                ve = new VicEntry();
                            }else if ("Category".equals(jp.currentName())) {
                                ve.category = (byte) jp.nextIntValue(-1);
                            } else if (isMd5Enabled && "MD5".equals(jp.currentName())) {
                                ve.setHash(jp.nextTextValue());
                            } else if (!isMd5Enabled && "SHA1".equals(jp.currentName())) {
                                ve.setHash(jp.nextTextValue());
                            } else if ("VictimIdentified".equals(jp.currentName())) {
                                ve.victimIdentified = Boolean.valueOf(jp.nextTextValue());
                            } else if ("OffenderIdentified".equals(jp.currentName())) {
                                ve.ofenderIdentified = Boolean.valueOf(jp.nextTextValue());
                            } else if ("IsDistributed".equals(jp.currentName())) {
                                ve.isDistributed = Boolean.valueOf(jp.nextTextValue());
                            } else if ("Series".equals(jp.currentName())) {
                                String seriesName = jp.nextTextValue();
                                Integer id = seriesMap.get(seriesName);
                                if (id == null) {
                                    id = seriesNum++;
                                    if (id >= 1 << 16)
                                        throw new IPEDException(
                                                "This ProjectVic json has more than max supported number of series. Please report to iped dev team.");
                                    seriesMap.put(seriesName, id);
                                }
                                ve.seriesId = id;
                            } else if ("IsPrecategorized".equals(jp.currentName())) {
                                // ignore
                            } else if ("Tags".equals(jp.currentName())) {
                                // TODO
                            } else if (token == JsonToken.END_OBJECT) {
                                if (ve.getBytes() != null)
                                    vicHashList.add(ve);
                                else
                                    nullHashes++;
                            }
                        } else if (arrayDepth == 2) {
                            // TODO PhotoDNA loading and search. PhotoDNA is large to keep on heap with
                            // current hashset size. Maybe we could use mmap files...
                        }
                    } while (arrayDepth > 0);

                } else {
                    throw new IPEDException("Unexpected property in ProjectVic json: " + jp.currentName());
                }
            }
            jp.close();
        }

        for (Entry<String, Integer> e : seriesMap.entrySet()) {
            vicSet.seriesMap.put(e.getValue(), e.getKey());
        }

        logger.info("Number of ProjectVic hashes loaded: " + vicHashList.size());
        logger.debug("Number of ProjectVic series loaded: " + vicSet.seriesMap.size());
        logger.info("Number of ProjectVic null " + hash + " values: " + nullHashes);

        VicEntry[] hashArray = vicHashList.toArray(new VicEntry[vicHashList.size()]);
        vicHashList = null;

        logger.info("Sorting ProjectVic hashes.");
        Arrays.parallelSort(hashArray);

        logger.info("Compressing ProjectVic hashes.");
        int hashSize = hashArray[0].getBytes().length;
        int recordSize = getRecordSize(hashSize);
        vicSet.compressedHashArray = new byte[hashArray.length * recordSize];
        for (int i = 0; i < hashArray.length; i++) {
            System.arraycopy(hashArray[i].getBytes(), 0, vicSet.compressedHashArray, i * recordSize, hashSize);
            byte flags = 0;
            if (hashArray[i].victimIdentified)
                flags |= 1;
            if (hashArray[i].ofenderIdentified)
                flags |= 1 << 1;
            if (hashArray[i].isDistributed)
                flags |= 1 << 2;
            vicSet.compressedHashArray[(i + 1) * recordSize - 4] = hashArray[i].category;
            vicSet.compressedHashArray[(i + 1) * recordSize - 3] = (byte) (hashArray[i].seriesId >> 8);
            vicSet.compressedHashArray[(i + 1) * recordSize - 2] = (byte) (hashArray[i].seriesId & 0xFF);
            vicSet.compressedHashArray[(i + 1) * recordSize - 1] = flags;
        }

        cacheFile.getParentFile().mkdirs();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
                ObjectOutputStream oos = new ObjectOutputStream(bos)){
            oos.writeObject(vicSet);
        }
        cacheFile.setLastModified(jsonDate);

    }

    /**
     * Record format: hash | category (1 byte) | series (2 bytes) | flags (1 byte)
     * 
     * @param hashSize
     * @return size of vic record
     */
    private int getRecordSize(int hashLen) {
        return hashLen + 4;
    }

    private int binarySearch(byte[] array, byte[] value) {
        int hashSize = value.length;
        int recordSize = getRecordSize(hashSize);
        int start = 0, end = (array.length / recordSize) - 1;
        while (start <= end) {
            int mid = (start + end) / 2;
            int comp = compare(array, mid * recordSize, value);
            if (comp < 0) {
                start = mid + 1;
            } else if (comp > 0) {
                end = mid - 1;
            } else {
                return mid * recordSize;
            }
        }
        return -1;
    }

    public int compare(byte[] bytes, int offset, byte[] compBytes) {
        for (int i = 0; i < compBytes.length; i++) {
            int cmp = Integer.compare(bytes[offset + i] & 0xFF, compBytes[i] & 0xFF);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Override
    protected void process(IItem item) throws Exception {
        
        if(item.getHash() == null || item.getHash().isEmpty())
            return;
        
        byte[] hash;
        if (isMd5Enabled)
            hash = new HashValue((String) item.getExtraAttribute(HashTask.HASH.MD5.toString())).getBytes();
        else
            hash = new HashValue((String) item.getExtraAttribute(HashTask.HASH.SHA1.toString())).getBytes();

        int pos = binarySearch(vicSet.compressedHashArray, hash);
        if (pos >= 0) {
            VicEntry ve = getEntry(vicSet.compressedHashArray, pos, hash.length);
            item.setExtraAttribute("projectvic_category", ve.category);
            item.setExtraAttribute("projectvic_victimIdentified", ve.victimIdentified);
            item.setExtraAttribute("projectvic_ofenderIdentified", ve.ofenderIdentified);
            item.setExtraAttribute("projectvic_isDistributed", ve.isDistributed);
            String series = vicSet.seriesMap.get(ve.seriesId);
            if (series != null) {
                item.setExtraAttribute("projectvic_Series", series);
            }
        }

    }

    private VicEntry getEntry(byte[] array, int index, int hashSize) {
        VicEntry e = new VicEntry();
        e.category = array[index + hashSize];
        e.seriesId = (array[index + hashSize + 1] & (int) 0xFF) << 8 | (array[index + hashSize + 2] & (int) 0xFF);
        byte flags = array[index + hashSize + 3];
        e.victimIdentified = (flags & 1) != 0;
        e.ofenderIdentified = (flags & 2) != 0;
        e.isDistributed = (flags & 4) != 0;
        return e;
    }

}
