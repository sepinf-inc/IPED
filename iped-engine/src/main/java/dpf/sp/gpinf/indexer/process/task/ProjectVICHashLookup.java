package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.IPEDException;
import iped3.IHashValue;
import iped3.IItem;

public class ProjectVICHashLookup extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(ProjectVICHashLookup.class);

    private static final String CACHE_PATH = System.getProperty("user.home") + "/.indexador/projectvic.cache";

    private static final String JSON_PATH_KEY = "projectVicHashSetPath";

    private static final String ENABLE_KEY = "enableProjectVicHashLookup";

    private static final String DATA_MODEL = "http://github.com/ICMEC/ProjectVic/DataModels/1.3.xml#Media";

    private static boolean isMd5Enabled;

    private static VicSet vicSet, photoDnaSet;

    private static Boolean enabled;
    
    private static boolean photoDNAEnabled = false;

    public static class VicEntry extends HashValue {
        
        /**
         * 
         */
        private static final long serialVersionUID = -2345691046599918684L;
        
        private byte category;
        private boolean victimIdentified;
        private boolean ofenderIdentified;
        private boolean isDistributed;
        private int seriesId;
        private byte[] photoDNA;

        private VicEntry() {
            super();
        }

        private VicEntry(byte[] bytes) {
            super(bytes);
        }

    }
    
    public static class ProjectVicPhotoDNA implements IHashValue{
        
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        
        private int index;
        
        private ProjectVicPhotoDNA(int idx) {
            index = idx;
        }

        @Override
        public int compareTo(IHashValue hash) {
            byte[] compBytes = hash.getBytes();
            byte[] bytes = getBytes();
            for (int i = 0; i < bytes.length; i++) {
                int cmp = Integer.compare(bytes[i] & 0xFF, compBytes[i] & 0xFF);
                if (cmp != 0)
                    return cmp;
            }
            return 0;
        }

        @Override
        public byte[] getBytes() {
            return photoDnaSet.getHash(index);
        }
        
        public VicEntry getVicEntry() {
            return photoDnaSet.getEntry(index);
        }
        
        @Override
        public String toString() {
            return new String(Hex.encodeHex(getBytes(), false));
        }

    }
    
    public static List<ProjectVicPhotoDNA> buildPhotoDNAReferenceList(){
        ArrayList<ProjectVicPhotoDNA> list = new ArrayList<>();
        if(photoDnaSet != null) {
            for(int i = 0; i < photoDnaSet.getNumRecords(); i++) {
                list.add(new ProjectVicPhotoDNA(i));
            }
        }else {
            logger.warn("ProjectVic photoDNA hashes not loaded!");
        }
        return list;
        
    }

    private static class VicSet implements Serializable {

        private static final long serialVersionUID = 9091381179250834450L;

        private HashMap<Integer, String> seriesMap = new HashMap<>();
        /**
         * TODO we could store and access this byte[] from a mmap file instead of heap
         */
        private byte[] compressedHashArray;
        private Integer hashSize;
        
        private byte[] getHash(int idx) {
            int pos = idx * getRecordSize();
            byte[] hash = new byte[hashSize];
            System.arraycopy(compressedHashArray, pos, hash, 0, hashSize);
            return hash;
        }

        private VicEntry getEntry(int idx) {
            byte[] hash = getHash(idx);
            VicEntry e = new VicEntry(hash);
            int pos = idx * getRecordSize();
            int offset = pos + hashSize;
            e.category = compressedHashArray[offset];
            e.seriesId = (compressedHashArray[offset + 1] & (int) 0xFF) << 8 | (compressedHashArray[offset + 2] & (int) 0xFF);
            byte flags = compressedHashArray[offset + 3];
            e.victimIdentified = (flags & 1) != 0;
            e.ofenderIdentified = (flags & 2) != 0;
            e.isDistributed = (flags & 4) != 0;
            return e;
        }
        
        private int binarySearch(byte[] value) {
            int recordSize = getRecordSize();
            int start = 0, end = (compressedHashArray.length / recordSize) - 1;
            while (start <= end) {
                int mid = (start + end) / 2;
                int comp = compare(compressedHashArray, mid * recordSize, value);
                if (comp < 0) {
                    start = mid + 1;
                } else if (comp > 0) {
                    end = mid - 1;
                } else {
                    return mid;
                }
            }
            return -1;
        }
        
        private int getNumRecords() {
            return compressedHashArray.length / getRecordSize();
        }
        
        /**
         * Record format: hash | category (1 byte) | series (2 bytes) | flags (1 byte)
         * 
         * @param hashSize
         * @return size of vic record
         */
        private int getRecordSize() {
            return hashSize + 4;
        }

        public int compare(byte[] bytes, int offset, byte[] compBytes) {
            for (int i = 0; i < compBytes.length; i++) {
                int cmp = Integer.compare(bytes[offset + i] & 0xFF, compBytes[i] & 0xFF);
                if (cmp != 0)
                    return cmp;
            }
            return 0;
        }
        
    }

    @Override
    public void finish() throws Exception {
        vicSet = null;
        photoDnaSet = null;
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

        if (vicSet != null)
            return;
        
        try {
            Class<?> c = Class.forName("br.dpf.sepinf.photodna.PhotoDNA");
            String value = confParams.getProperty(PhotoDNATask.ENABLE_PHOTO_DNA);
            if (value != null && !value.trim().isEmpty())
                photoDNAEnabled = Boolean.valueOf(value.trim());

        } catch (ClassNotFoundException e) {
            photoDNAEnabled = false;
        }

        File vicJsonFile;
        String path = confParams.getProperty(JSON_PATH_KEY);
        if (path != null && !path.trim().isEmpty()) {
            vicJsonFile = new File(path.trim());
        } else {
            logger.error(JSON_PATH_KEY + " not configured in LocalConfig. ProjectVic hashset lookup will be disabled.");
            enabled = false;
            return;
        }

        String hashes = confParams.getProperty(HashTask.HASH_PROP);
        String hashAlgorithm;
        if (hashes != null && hashes.contains(HashTask.HASH.MD5.toString())) {
            isMd5Enabled = true;
            hashAlgorithm = HashTask.HASH.MD5.toString();
        } else if (hashes != null && hashes.contains(HashTask.HASH.SHA1.toString())) {
            isMd5Enabled = false;
            hashAlgorithm = HashTask.HASH.SHA1.toString();
        } else {
            logger.error("Neither md5 nor sha-1 were enabled. ProjectVic hashset lookup will be disabled.");
            enabled = false;
            return;
        }

        long jsonDate = vicJsonFile.lastModified();

        if (photoDNAEnabled) {
            File cacheFile = new File(CACHE_PATH + ".pdna");
            if (cacheFile.exists() && cacheFile.lastModified() == jsonDate) {
                photoDnaSet = loadCache(cacheFile);
            } else {
                Files.deleteIfExists(cacheFile.toPath());
            }
        }
        
        File cacheFile = new File(CACHE_PATH + "." + hashAlgorithm);
        if (cacheFile.exists() && cacheFile.lastModified() == jsonDate) {
            vicSet = loadCache(cacheFile);
        } else {
            Files.deleteIfExists(cacheFile.toPath());
        }
        
        if (vicSet != null && (!photoDNAEnabled || photoDnaSet != null)) {
            printStats();
            return;
        }

        List<VicEntry> vicHashList = new ArrayList<>();
        logger.info("Loading ProjectVic json " + vicJsonFile.getAbsolutePath());

        int nullHashes = 0;
        int photoDnaCount = 0;
        int invalidPdna = 0;

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
                    boolean isPhotoDNANext = false;
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
                                ve.setHash(jp.nextTextValue().trim());
                            } else if (!isMd5Enabled && "SHA1".equals(jp.currentName())) {
                                ve.setHash(jp.nextTextValue().trim());
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
                                if (ve.getBytes() != null) {
                                    vicHashList.add(ve);
                                }else {
                                    nullHashes++;
                                }
                            }
                        } else if (arrayDepth == 2 && photoDNAEnabled) {
                            if("HashName".equals(jp.currentName()) && "PhotoDNA".equals(jp.nextTextValue())) {
                                isPhotoDNANext = true;
                            }else if (isPhotoDNANext && "HashValue".equals(jp.currentName())) {
                                String pdna = jp.nextTextValue().trim();
                                try {
                                    ve.photoDNA = Base64.getDecoder().decode(pdna);
                                    photoDnaCount++;
                                } catch (Exception e) {
                                    invalidPdna++;
                                }
                                isPhotoDNANext = false;
                            }
                        }
                    } while (arrayDepth > 0);

                } else {
                    throw new IPEDException("Unexpected property in ProjectVic json: " + jp.currentName());
                }
            }
            jp.close();
        }

        vicSet = new VicSet();
        if (photoDNAEnabled) {
            photoDnaSet = new VicSet();
        }
        for (Entry<String, Integer> e : seriesMap.entrySet()) {
            vicSet.seriesMap.put(e.getValue(), e.getKey());
            if(photoDnaSet != null) {
                photoDnaSet.seriesMap.put(e.getValue(), e.getKey());
            }
        }

        logger.debug("Number of ProjectVic invalid photoDNA hashes: " + invalidPdna);
        logger.debug("Number of ProjectVic null " + hashAlgorithm + " values: " + nullHashes);

        VicEntry[] hashArray = vicHashList.toArray(new VicEntry[vicHashList.size()]);
        vicHashList = null;

        logger.info("Sorting ProjectVic hashes.");
        Arrays.parallelSort(hashArray);

        logger.info("Compressing ProjectVic hashes.");
        VicSet[] sets = {vicSet, photoDnaSet};
        
        for(VicSet set : sets) {
            if(set == null) {
                continue;
            }
            boolean isPDNA = set == photoDnaSet;
            int hashSize = isPDNA ? 144 : hashArray[0].getBytes().length;
            set.hashSize = hashSize;
            int recordSize = set.getRecordSize();
            int numHashes = isPDNA ? photoDnaCount : hashArray.length;
            set.compressedHashArray = new byte[numHashes * recordSize];
            int k = 0;
            for (int i = 0; i < hashArray.length; i++) {
                byte[] hash = isPDNA ? hashArray[i].photoDNA : hashArray[i].getBytes();
                if(hash == null || hash.length == 0) {
                    continue;
                }
                System.arraycopy(hash, 0, set.compressedHashArray, k * recordSize, hashSize);
                byte flags = 0;
                if (hashArray[i].victimIdentified)
                    flags |= 1;
                if (hashArray[i].ofenderIdentified)
                    flags |= 1 << 1;
                if (hashArray[i].isDistributed)
                    flags |= 1 << 2;
                set.compressedHashArray[(k + 1) * recordSize - 4] = hashArray[i].category;
                set.compressedHashArray[(k + 1) * recordSize - 3] = (byte) (hashArray[i].seriesId >> 8);
                set.compressedHashArray[(k + 1) * recordSize - 2] = (byte) (hashArray[i].seriesId & 0xFF);
                set.compressedHashArray[(k + 1) * recordSize - 1] = flags;
                k++;
            }
            
            String cacheSuffix = isPDNA ? ".pdna" : "." + hashAlgorithm;
            cacheFile = new File(CACHE_PATH + cacheSuffix);
            cacheFile.getParentFile().mkdirs();
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
                    ObjectOutputStream oos = new ObjectOutputStream(bos)){
                oos.writeObject(set);
            }
            cacheFile.setLastModified(jsonDate);
        }

        printStats();

    }
    
    private void printStats() {
        logger.info("Number of ProjectVic hashes loaded: " + vicSet.getNumRecords());
        logger.info("Number of ProjectVic photoDNA hashes loaded: "
                + (photoDnaSet != null ? photoDnaSet.getNumRecords() : 0));
        logger.info("Number of ProjectVic series loaded: " + vicSet.seriesMap.size());
    }

    private VicSet loadCache(File cacheFile) throws IOException, ClassNotFoundException {
        logger.info("Loading ProjectVic cache from " + cacheFile.getAbsolutePath());
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(cacheFile.toPath()));
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (VicSet) ois.readObject();
        } catch (InvalidClassException e) {
            e.printStackTrace();
            return null;
        }
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

        int idx = vicSet.binarySearch(hash);
        if (idx >= 0) {
            VicEntry ve = vicSet.getEntry(idx);
            storeProjectVicEntryInfo(item, ve);
            if (ve.category == 1 || ve.category == 2) {
                item.setExtraAttribute(KFFTask.KFF_STATUS, "pedo");
                item.setExtraAttribute(KFFTask.KFF_GROUP, "projectvic");
            }
        }

    }

    public static void storeProjectVicEntryInfo(IItem item, VicEntry ve) {
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
