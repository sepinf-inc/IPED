package iped.app.timelinegraph.cache.persistance;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.core.util.ExecutorServices;
import org.jfree.data.time.TimePeriod;
import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.cache.CacheEventEntry;
import iped.app.timelinegraph.cache.CacheTimePeriodEntry;
import iped.app.timelinegraph.cache.TimeIndexedMap;
import iped.app.timelinegraph.cache.TimeIndexedMap.CacheDataInputStream;
import iped.app.timelinegraph.cache.TimeStampCache;
import iped.app.ui.App;
import iped.utils.IOUtil;
import iped.utils.SeekableFileInputStream;

/*
 * Class implementing method for timeline chart cache persistance
 */

public class CachePersistance {
    File baseDir;

    HashMap<String, String> pathsToCheck = new HashMap<String, String>();

    static Thread t;
    
    boolean bitstreamSerialize = false;

    private File bitstreamSerializeFile;

    static private boolean bitstreamSerializeAsDefault = false;


    static CachePersistance singleton = new CachePersistance();
    public static CachePersistance getInstance() {
        return singleton;
    }
    
    static public ExecutorService cachePersistanceExecutor = Executors.newFixedThreadPool(1);

    public CachePersistance() {
        File startDir;
        startDir = new File(App.get().casesPathFile, "iped");
        startDir = new File(startDir, "data");
        
        bitstreamSerializeFile = new File(startDir, "bitstreamSerialize");
        bitstreamSerialize = bitstreamSerializeFile.exists();

        startDir = new File(startDir, "timecache");
        startDir.mkdirs();


        // if case cache folder is not writable, use user.home for caches
        if (!IOUtil.canWrite(startDir)) {
            startDir = new File(System.getProperty("user.home"), ".iped");
            startDir = new File(startDir, "timecache");
            getTempBaseDir(startDir);
        } else {
            getTempBaseDir(startDir);
        }
    }

    public void getTempBaseDir(File startDir) {
        try {
            baseDir = startDir;
            baseDir.mkdirs();
            final File tempCasesDir = baseDir;

            boolean found = false;

            Set<String> uuids = App.get().appCase.getEvidenceUUIDs();
            MessageDigest md = DigestUtils.getMd5Digest();
            for (Iterator iterator = uuids.iterator(); iterator.hasNext();) {
                String string = (String) iterator.next();
                md.update(string.getBytes());
            }

            String uuid = DatatypeConverter.printHexBinary(md.digest());

            File tempDirCase = new File(tempCasesDir, uuid);
            if (tempDirCase.exists()) {
                found = true;
                baseDir = tempDirCase;
            }

            if (!found) {
                baseDir = new File(tempCasesDir, uuid);
                baseDir.mkdirs();
                RandomAccessFile ras = new RandomAccessFile(new File(baseDir, "case.txt"), "rw");
                ras.writeUTF(App.get().casesPathFile.getAbsolutePath().toString());
                ras.close();
            }

            // thread to clean caches with no correspondent original data
            if (t == null) {
                // if it is null means it wasn't executed yet
                t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (tempCasesDir.listFiles() != null) {
                                for (File f : tempCasesDir.listFiles()) {
                                    if (!f.getName().equals(uuid)) {
                                        try {
                                            RandomAccessFile ras = new RandomAccessFile(new File(f, "case.txt"), "r");
                                            String casePath = ras.readUTF();
                                            ras.close();
                                            if (casePath != null) {
                                                File caseDir = new File(casePath);
                                                if (!caseDir.exists()) {
                                                    FileUtils.forceDelete(f);
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
            }

        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public TimeIndexedMap loadNewCache(Class<? extends TimePeriod> className) throws IOException {
        TimeIndexedMap newCache = null;

        for (File f : baseDir.listFiles()) {
            if (f.getName().equals(className.getSimpleName())) {
                newCache = new TimeIndexedMap();
                newCache.setIndexFile(className.getSimpleName(),baseDir);
                break;
            }
        }

        return newCache;
    }

    private boolean loadEventNewCache(ArrayList<CacheTimePeriodEntry> times, File f) {
        File cache = new File(f, "0");
        if (!cache.exists() || cache.length() == 0) {
            return false;
        }
        try (TimeIndexedMap.CacheDataInputStream dis = new TimeIndexedMap.CacheDataInputStream(new BufferedInputStream(Files.newInputStream(cache.toPath())))) {
            int committed = dis.readShort();
            if (committed != 1) {
                return false;
            }
            String timezoneID = dis.readUTF();
            int entries = dis.readInt2();
            while (times.size() < entries) {
                CacheTimePeriodEntry ct = new CacheTimePeriodEntry();
                ct.events = new ArrayList<CacheEventEntry>();
                ct.date=dis.readLong();
                String eventName = dis.readUTF();
                while (!eventName.equals("!!")) {
                    CacheEventEntry ce = new CacheEventEntry();
                    ce.event = eventName;
                    if(bitstreamSerialize) {
                        try {
                            ce.docIds = new RoaringBitmap();
                            ce.docIds.deserialize(dis);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }else {
                        ce.docIds = new RoaringBitmap();
                        int docId = dis.readInt2();
                        while (docId != -1) {
                            ce.docIds.add(docId);
                            docId = dis.readInt2();
                        }
                    }
                    ct.events.add(ce);
                    eventName = dis.readUTF();
                }
                times.add(ct);
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void saveNewCache(TimeStampCache timeStampCache) {
        if(bitstreamSerializeAsDefault ) {
            try {
                bitstreamSerializeFile.createNewFile();// mark this cache as containing bitstreams serialized
                bitstreamSerialize=true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        TimeIndexedMap newCache = (TimeIndexedMap) timeStampCache.getNewCache();

        for (Entry<String, Set<CacheTimePeriodEntry>> entry : newCache.entrySet()) {
            savePeriodNewCache(timeStampCache, entry.getValue(), new File(baseDir, entry.getKey()));
        }
    }

    public void saveMonthIndex(HashMap<String, TreeMap<Date, Long>> monthIndex, Map<Long, Integer> positionsIndexes, String timePeriodName) {
        Map<Date, Long> dates = (TreeMap<Date, Long>) monthIndex.get(timePeriodName);
        
        baseDir.mkdirs();
        File monthFile = new File(new File(baseDir,timePeriodName), "1");

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(monthFile.toPath())))) {
            for (Iterator iterator2 = dates.entrySet().iterator(); iterator2.hasNext();) {
                Entry dateEntry = (Entry) iterator2.next();
                dos.writeLong(((Date)dateEntry.getKey()).getTime());
                dos.writeLong(((Long)dateEntry.getValue()));
                dos.writeInt(positionsIndexes.get(((Long)dateEntry.getValue())));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void savePeriodNewCache(TimeStampCache timeStampCache, Set<CacheTimePeriodEntry> entry, File file) {
        file.mkdirs();
        File eventFile = new File(file, "0");

        //Collections.sort(entry);

        boolean commit = false;
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(eventFile.toPath())))) {
            dos.writeShort(0);
            dos.writeUTF(timeStampCache.getCacheTimeZone().getID());
            dos.writeInt(entry.size());
            for (CacheTimePeriodEntry ct:entry) {
                dos.writeLong(ct.date);
                for (int j = 0; j < ct.events.size(); j++) {
                    CacheEventEntry ce = ct.events.get(j);
                    dos.writeUTF(ce.event);
                    if(bitstreamSerialize) {
                        ce.docIds.serialize(dos);
                    }else {
                        for (int docId : ce.docIds) {
                            dos.writeInt(docId);
                        }
                        dos.writeInt(-1);
                    }
                }
                dos.writeUTF("!!");
            }
            commit = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (commit) {
            try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(eventFile.toPath(), StandardOpenOption.WRITE))) {
                dos.writeShort(1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public File getBaseDir() {
        return baseDir;
    }

    public void loadMonthIndex(String ev, TreeMap<Date, Long> datesPos, Map<Long, Integer> positionsIndexes) {
        File monthFile = new File(new File(baseDir,ev), "1");
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(monthFile)))) {
            try {
                while(true) {
                    Date d = new Date(dis.readLong());
                    long pos = dis.readLong();
                    int index = dis.readInt();
                    datesPos.put(d, pos);
                    positionsIndexes.put(pos, index);
                }
            }catch (EOFException e) {
                // ignores
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isBitstreamSerialize() {
        return bitstreamSerialize;
    }

    public void setBitstreamSerialize(boolean bitstreamSerialize) {
        this.bitstreamSerialize = bitstreamSerialize;
    }

    public CacheTimePeriodEntry loadNextEntry(CacheDataInputStream dis) throws IOException {
        CacheTimePeriodEntry ct = new CacheTimePeriodEntry();
        ct.events = new ArrayList<CacheEventEntry>();
        ct.date=dis.readLong();
        String eventName = dis.readUTF();
        while (!eventName.equals("!!")) {
            CacheEventEntry ce = new CacheEventEntry();
            ce.event = eventName;
            
            if(this.bitstreamSerialize) {
                ce.docIds = new RoaringBitmap();
                ce.docIds.deserialize(dis);
            }else {
                ce.docIds = new RoaringBitmap();
                int docId = dis.readInt2();
                while (docId != -1) {
                    ce.docIds.add(docId);
                    docId = dis.readInt2();
                }
            }
            
            ct.events.add(ce);
            try {
                eventName = dis.readUTF();
            }catch(Exception e) {
                e.printStackTrace();
                long pos = ((SeekableFileInputStream)dis.wrapped).position();
            }
        }
        return ct;
    }

}
