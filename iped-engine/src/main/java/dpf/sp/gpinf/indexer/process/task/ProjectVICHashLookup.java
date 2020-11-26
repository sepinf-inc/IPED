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
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
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

import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;
import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup.LookupProvider;
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

    private static VicSet md5Set, sha1Set, photoDnaSet;

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
        private byte[] photoDNA, sha1;

        private VicEntry() {
            super();
        }

        private VicEntry(byte[] bytes) {
            super(bytes);
        }

    }
    
    private class VicEntrySha1Comparator implements Comparator<VicEntry> {

        @Override
        public int compare(VicEntry arg0, VicEntry arg1) {
            byte[] bytes = arg0.sha1;
            byte[] compBytes = arg1.sha1;
            if (bytes == null && compBytes != null) {
                return -1;
            } else if (bytes != null && compBytes == null) {
                return 1;
            } else if (bytes == null && compBytes == null) {
                return 0;
            }
            for (int i = 0; i < bytes.length; i++) {
                int cmp = Integer.compare(bytes[i] & 0xFF, compBytes[i] & 0xFF);
                if (cmp != 0)
                    return cmp;
            }
            return 0;
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
        
        private VicSet(int hashSize) {
            this.hashSize = hashSize;
        }

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
        ChildPornHashLookup.dispose();
        md5Set = null;
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

        if (md5Set != null && sha1Set != null)
            return;
        
        try {
            Class<?> c = Class.forName("br.dpf.sepinf.photodna.PhotoDNA");
            String value = confParams.getProperty(PhotoDNATask.ENABLE_PHOTO_DNA);
            if (value != null && !value.trim().isEmpty())
                photoDNAEnabled = Boolean.valueOf(value.trim());

        } catch (ClassNotFoundException e) {
            photoDNAEnabled = false;
        }

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
        if (hashes != null && !hashes.contains(HashTask.HASH.MD5.toString())
                && !hashes.contains(HashTask.HASH.SHA1.toString())) {
            logger.error("Neither md5 nor sha-1 were enabled. ProjectVic hashset lookup will be disabled.");
            enabled = false;
            return;
        }

        String[] cacheSuffixes = { ".md5", ".sha-1", ".pdna" };

        long jsonDate = vicJsonFile.lastModified();
        for (int i = 0; i < cacheSuffixes.length; i++) {
            File cacheFile = new File(CACHE_PATH + cacheSuffixes[i]);
            if (cacheFile.exists() && cacheFile.lastModified() == jsonDate) {
                if (i == 0) {
                    md5Set = loadCache(cacheFile);
                } else if (i == 1) {
                    sha1Set = loadCache(cacheFile);
                } else if (photoDNAEnabled && i == 2) {
                    photoDnaSet = loadCache(cacheFile);
                }
            } else {
                Files.deleteIfExists(cacheFile.toPath());
            }
        }

        if (md5Set != null && sha1Set != null && (!photoDNAEnabled || photoDnaSet != null)) {
            printStats();
            installLookupForParsers();
            return;
        }

        md5Set = new VicSet(16);
        sha1Set = new VicSet(20);
        if (photoDNAEnabled) {
            photoDnaSet = new VicSet(144);
        }

        // must be in same order than cacheSufixes array
        VicSet[] sets = { md5Set, sha1Set, photoDnaSet };

        List<VicEntry> vicHashList = new ArrayList<>();
        logger.info("Loading ProjectVic json " + vicJsonFile.getAbsolutePath());

        int md5Count = 0, sha1Count = 0, photoDnaCount = 0;
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
                            } else if ("MD5".equals(jp.currentName())) {
                                ve.setHash(jp.nextTextValue().trim());
                                md5Count++;
                            } else if ("SHA1".equals(jp.currentName())) {
                                ve.sha1 = new HashValue(jp.nextTextValue().trim()).getBytes();
                                sha1Count++;
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
                                if (ve.getBytes() != null || ve.sha1 != null) {
                                    vicHashList.add(ve);
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

        for (Entry<String, Integer> e : seriesMap.entrySet()) {
            md5Set.seriesMap.put(e.getValue(), e.getKey());
            sha1Set.seriesMap.put(e.getValue(), e.getKey());
            if(photoDnaSet != null) {
                photoDnaSet.seriesMap.put(e.getValue(), e.getKey());
            }
        }

        logger.debug("Number of ProjectVic invalid photoDNA hashes: " + invalidPdna);

        logger.info("Compressing ProjectVic hashes.");
        
        int suffixIdx = 0;
        for(VicSet set : sets) {
            if (set == null) {
                continue;
            } else if (set != photoDnaSet) {
                logger.info("Sorting ProjectVic hashes.");
                if (set == md5Set) {
                    Collections.sort(vicHashList);
                } else if (set == sha1Set) {
                    Collections.sort(vicHashList, new VicEntrySha1Comparator());
                }
            }
            boolean isMd5 = set == md5Set;
            boolean isPDNA = set == photoDnaSet;
            int hashSize = set.hashSize;
            int recordSize = set.getRecordSize();
            int numHashes = isPDNA ? photoDnaCount : isMd5 ? md5Count : sha1Count;
            set.compressedHashArray = new byte[numHashes * recordSize];
            int k = 0;
            for (int i = 0; i < vicHashList.size(); i++) {
                VicEntry entry = vicHashList.get(i);
                byte[] hash = isPDNA ? entry.photoDNA : isMd5 ? entry.getBytes() : entry.sha1;
                if(hash == null || hash.length == 0) {
                    continue;
                }
                System.arraycopy(hash, 0, set.compressedHashArray, k * recordSize, hashSize);
                byte flags = 0;
                if (entry.victimIdentified)
                    flags |= 1;
                if (entry.ofenderIdentified)
                    flags |= 1 << 1;
                if (entry.isDistributed)
                    flags |= 1 << 2;
                set.compressedHashArray[(k + 1) * recordSize - 4] = entry.category;
                set.compressedHashArray[(k + 1) * recordSize - 3] = (byte) (entry.seriesId >> 8);
                set.compressedHashArray[(k + 1) * recordSize - 2] = (byte) (entry.seriesId & 0xFF);
                set.compressedHashArray[(k + 1) * recordSize - 1] = flags;
                k++;
            }
            
            File cacheFile = new File(CACHE_PATH + cacheSuffixes[suffixIdx++]);
            cacheFile.getParentFile().mkdirs();
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
                    ObjectOutputStream oos = new ObjectOutputStream(bos)){
                oos.writeObject(set);
            }
            cacheFile.setLastModified(jsonDate);
        }

        printStats();
        installLookupForParsers();

    }

    private void printStats() {
        logger.info("Number of ProjectVic md5 hashes loaded: " + md5Set.getNumRecords());
        logger.info("Number of ProjectVic sha1 hashes loaded: " + sha1Set.getNumRecords());
        logger.info("Number of ProjectVic photoDNA hashes loaded: "
                + (photoDnaSet != null ? photoDnaSet.getNumRecords() : 0));
        logger.info("Number of ProjectVic series loaded: " + md5Set.seriesMap.size());
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
    
    private void installLookupForParsers() {
        ChildPornHashLookup.addLookupProvider(new LookupProvider() {
            @Override
            public String lookupHash(String algorithm, String hashString) {
                if (hashString != null && !hashString.isEmpty()) {
                    if ("md5".equals(algorithm) || "sha-1".equals(algorithm)) {
                        byte[] hash = new HashValue(hashString).getBytes();
                        VicEntry ve = lookupVicEntry(hash);
                        if (ve != null) {
                            if (ve.category == 1) {
                                return "ProjectVic_Cat1";
                            } else if (ve.category == 2) {
                                return "ProjectVic_Cat2";
                            }
                        }
                    }
                }
                return null;
            }
        });
    }

    @Override
    protected void process(IItem item) throws Exception {
        
        if(item.getHash() == null || item.getHash().isEmpty())
            return;
        
        String hash = (String) item.getExtraAttribute(HashTask.HASH.MD5.toString());
        if (hash == null)
            hash = (String) item.getExtraAttribute(HashTask.HASH.SHA1.toString());
        if(hash == null)
            return;

        VicEntry ve = lookupVicEntry(new HashValue(hash).getBytes());
        if (ve != null) {
            storeProjectVicEntryInfo(item, ve);
            if (ve.category == 1 || ve.category == 2) {
                item.setExtraAttribute(KFFTask.KFF_STATUS, "pedo");
                item.setExtraAttribute(KFFTask.KFF_GROUP, "projectvic");
            }
        }

    }

    private VicEntry lookupVicEntry(byte[] hash) {
        VicSet set = hash.length == 16 ? md5Set : sha1Set;
        int idx = set.binarySearch(hash);
        if (idx >= 0) {
            return set.getEntry(idx);
        }
        return null;
    }

    public static void storeProjectVicEntryInfo(IItem item, VicEntry ve) {
        item.setExtraAttribute("projectvic_category", ve.category);
        item.setExtraAttribute("projectvic_victimIdentified", ve.victimIdentified);
        item.setExtraAttribute("projectvic_ofenderIdentified", ve.ofenderIdentified);
        item.setExtraAttribute("projectvic_isDistributed", ve.isDistributed);
        String series = md5Set.seriesMap.get(ve.seriesId);
        if (series != null) {
            item.setExtraAttribute("projectvic_Series", series);
        }
    }

}
