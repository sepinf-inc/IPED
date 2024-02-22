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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
import org.jfree.data.time.TimePeriod;
import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedChartsPanel;
import iped.app.timelinegraph.TimeEventGroup;
import iped.app.timelinegraph.cache.CacheEventEntry;
import iped.app.timelinegraph.cache.CacheTimePeriodEntry;
import iped.app.timelinegraph.cache.PersistedArrayList;
import iped.app.timelinegraph.cache.TimeIndexedMap;
import iped.app.timelinegraph.cache.TimeStampCache;
import iped.app.timelinegraph.cache.TimelineCache;
import iped.app.ui.App;
import iped.utils.IOUtil;

/*
 * Class implementing method for timeline chart cache persistance
 */

public class CachePersistance {
    File baseDir;

    HashMap<String, String> pathsToCheck = new HashMap<String, String>();

    static Thread t;

    boolean bitstreamSerialize = false;

    private File bitstreamSerializeFile;

    static private boolean bitstreamSerializeAsDefault = true;

    static CachePersistance singleton = new CachePersistance();

    public static CachePersistance getInstance() {
        return singleton;
    }

    static public ExecutorService cachePersistanceExecutor = Executors.newFixedThreadPool(1);

    public CachePersistance() {
        File startDir;
        if (App.get().appCase.getAtomicSources().size() == 1) {
            // single case
            startDir = new File(App.get().casesPathFile, "iped/data");
        } else {
            // multicase: use the same base dir as multicase graphs
            startDir = new File(App.get().casesPathFile.getParentFile(), "iped-multicases");
        }

        startDir = new File(startDir, "timecache");
        startDir.mkdirs();

        bitstreamSerializeFile = new File(startDir, "bitstreamSerialize");
        bitstreamSerialize = bitstreamSerializeFile.exists();

        getTempBaseDir(startDir);
    }

    public void getTempBaseDir(File startDir) {
        try {
            Set<String> uuids = App.get().appCase.getEvidenceUUIDs();
            MessageDigest md = DigestUtils.getMd5Digest();
            for (Iterator iterator = uuids.iterator(); iterator.hasNext();) {
                String string = (String) iterator.next();
                md.update(string.getBytes());
            }

            String uuid = DatatypeConverter.printHexBinary(md.digest());

            baseDir = new File(startDir, uuid);
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                // cache doesn't exist and folder is not writable, use user.home for caches
                startDir = new File(System.getProperty("user.home"), ".iped/timecache");
                baseDir = new File(startDir, uuid);
                baseDir.mkdirs();
            }

            final File finalStartDir = startDir;

            // thread to clean caches with no correspondent original data
            if (t == null) {
                // if it is null means it wasn't executed yet
                t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (finalStartDir.isDirectory()) {
                                for (File f : finalStartDir.listFiles()) {
                                    if (f.isDirectory() && !f.getName().equals(uuid)) {
                                        IOUtil.deleteDirectory(f);
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

    public TimeIndexedMap loadNewCache(TimeEventGroup teGroup, Class<? extends TimePeriod> className)
            throws IOException {
        TimeIndexedMap newCache = null;

        File[] files = baseDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().equals(teGroup.getName())) {
                    File[] files2 = f.listFiles();
                    if (files2 != null) {
                        for (File f2 : files2) {
                            if (f2.getName().equals(className.getSimpleName())) {
                                newCache = new TimeIndexedMap();
                                newCache.setIndexFile(teGroup, className.getSimpleName(), baseDir);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return newCache;
    }

    public void saveNewCache(TimeStampCache timeStampCache) {
        if (bitstreamSerializeAsDefault) {
            try {
                if (bitstreamSerializeFile.getParentFile().exists()) {
                    bitstreamSerializeFile.createNewFile();// mark this cache as containing bitstreams serialized
                    bitstreamSerialize = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // set the start dir to persist the cache based on TimeEventGroup
        TimeEventGroup teGroup = timeStampCache.getTimeEventGroup();
        File startDir;
        if (!teGroup.equals(TimeEventGroup.ALL_EVENTS)) {
            startDir = new File(baseDir, teGroup.getName());
            startDir.mkdir();
        } else {
            startDir = baseDir;
        }

        TimeIndexedMap newCache = (TimeIndexedMap) timeStampCache.getNewCache();

        for (Entry<String, Set<CacheTimePeriodEntry>> entry : newCache.entrySet()) {
            savePeriodNewCache(teGroup, timeStampCache, entry.getValue(), new File(startDir, entry.getKey()));
        }
    }

    public void saveUpperPeriodIndex(HashMap<String, TreeMap<Date, Long>> upperPeriodIndex, Map<Long, Integer> positionsIndexes, String timePeriodName) {
        Map<Date, Long> dates = (TreeMap<Date, Long>) upperPeriodIndex.get(timePeriodName);

        baseDir.mkdirs();
        File upperPeriodFile = new File(new File(baseDir, timePeriodName), "1");

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(upperPeriodFile.toPath())))) {
            for (Iterator iterator2 = dates.entrySet().iterator(); iterator2.hasNext();) {
                Entry dateEntry = (Entry) iterator2.next();
                dos.writeLong(((Date) dateEntry.getKey()).getTime());
                dos.writeLong(((Long) dateEntry.getValue()));
                dos.writeInt(positionsIndexes.get(((Long) dateEntry.getValue())));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveIntermediaryCacheSet(Collection<CacheTimePeriodEntry> entry, File file) {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file.toPath())))) {
            for (CacheTimePeriodEntry ct : entry) {
                dos.writeLong(ct.date);
                CacheEventEntry[] events = ct.getEvents();
                for (int j = 0; j < events.length; j++) {
                    CacheEventEntry ce = events[j];
                    dos.writeInt(ce.getEventOrd());
                    ce.docIds.serialize(dos);
                }
                dos.writeInt(-1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class PositionOutputStream extends BufferedOutputStream {

        public PositionOutputStream(OutputStream out) {
            super(out);
        }

        long position = 0;

        @Override
        public synchronized void write(int b) throws IOException {
            position += 1;
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            position += len;
            super.write(b, off, len);
        }

        @Override
        public void write(byte[] b) throws IOException {
            position += b.length;
            super.write(b);
        }

        public long getPosition() {
            return position;
        }

    }

    private void savePeriodNewCache(TimeEventGroup teGroup, TimeStampCache timeStampCache,
            Set<CacheTimePeriodEntry> entry, File file) {
        file.mkdirs();
        File indexFile = new File(file, "0");
        File upperPeriodIndexFile = new File(file, "1");

        boolean commit = false;
        try (PositionOutputStream pos = new PositionOutputStream(Files.newOutputStream(indexFile.toPath()));
                DataOutputStream dos = new DataOutputStream(pos);
                DataOutputStream upperPeriodIndexDos = new DataOutputStream(new PositionOutputStream(Files.newOutputStream(upperPeriodIndexFile.toPath())))) {
            dos.writeShort(0);
            dos.writeUTF(timeStampCache.getCacheTimeZone().getID());
            dos.writeInt(entry.size());

            Date lastUpperPeriod = null;
            Calendar c = (Calendar) Calendar.getInstance().clone();
            int internalCount = 0;
            long lastPos = pos.position;

            int ctIndex = 0;
            CacheTimePeriodEntry[] cache = null;
            if (file.getName().contains("Day")) {
                cache = new CacheTimePeriodEntry[entry.size()];
                TimelineCache.get(teGroup).getCaches().put("Day", cache);
            }
            for (CacheTimePeriodEntry ct : entry) {
                dos.writeLong(ct.date);
                CacheEventEntry[] events = ct.getEvents();
                for (int j = 0; j < events.length; j++) {
                    CacheEventEntry ce = events[j];
                    dos.writeInt(ce.getEventOrd());
                    if (bitstreamSerialize) {
                        ce.docIds.serialize(dos);
                    } else {
                        for (int docId : ce.docIds) {
                            dos.writeInt(docId);
                        }
                        dos.writeInt(-1);
                    }
                }
                dos.writeInt(-1);

                c.clear();
                c.set(Calendar.YEAR, 1900 + ct.getDate().getYear());
                c.set(Calendar.MONTH, ct.getDate().getMonth());
                c.set(Calendar.DAY_OF_MONTH, ct.getDate().getDate());
                if (file.getName().contains("Minute") || file.getName().contains("Second")) {
                    c.set(Calendar.HOUR_OF_DAY, ct.getDate().getHours());
                }

                internalCount += events.length;
                Date upperPeriod = c.getTime();
                if (!upperPeriod.equals(lastUpperPeriod) || internalCount > 4000) {
                    lastUpperPeriod = upperPeriod;
                    internalCount = 0;

                    upperPeriodIndexDos.writeLong(upperPeriod.getTime());
                    upperPeriodIndexDos.writeLong(lastPos);
                    upperPeriodIndexDos.writeInt(ctIndex);
                }

                if (cache != null) {
                    cache[ctIndex] = ct;// keep in cache as it will be used
                }

                ctIndex++;

                lastPos = pos.position;
            }

            commit = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (commit) {
            try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(indexFile.toPath(), StandardOpenOption.WRITE))) {
                dos.writeShort(1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // removes intermediary flush files if exists
        ((PersistedArrayList) entry).removeFlushes();
    }

    public File getBaseDir() {
        return baseDir;
    }

    public void loadUpperPeriodIndex(TimeEventGroup timeEventGroup, String periodName, TreeMap<Long, Long> datesPos,
            Map<Long, Integer> positionsIndexes) {
        File upperPeriodFile = new File(new File(new File(baseDir,timeEventGroup.getName()), periodName), "1");
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(upperPeriodFile)))) {
            try {
                while (true) {
                    long d = dis.readLong();
                    long pos = dis.readLong();
                    int index = dis.readInt();
                    datesPos.put(d, pos);
                    positionsIndexes.put(pos, index);
                }
            } catch (EOFException e) {
                // ignores
            }
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

    static public CacheTimePeriodEntry loadNextEntry(DataInputStream dis, boolean bitstreamSerialize) throws IOException {
        CacheTimePeriodEntry ct = new CacheTimePeriodEntry();
        ct.date = dis.readLong();
        int eventOrd = dis.readInt();
        while (eventOrd != -1) {
            CacheEventEntry ce = new CacheEventEntry(eventOrd);
            ce.event = IpedChartsPanel.getEventName(eventOrd);

            if (bitstreamSerialize) {
                ce.docIds = new RoaringBitmap();
                ce.docIds.deserialize(dis);
            } else {
                ce.docIds = new RoaringBitmap();
                int docId = dis.readInt();
                while (docId != -1) {
                    ce.docIds.add(docId);
                    docId = dis.readInt();
                }
            }

            ct.addEventEntry(ce);
            try {
                eventOrd = dis.readInt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ct;
    }

    static public CacheTimePeriodEntry loadIntermediaryNextEntry(DataInputStream dis) throws IOException {
        CacheTimePeriodEntry ct = new CacheTimePeriodEntry();
        ct.date = dis.readLong();
        int evCode = dis.readInt();
        while (evCode != -1) {
            CacheEventEntry ce = new CacheEventEntry(evCode);
            ce.docIds = new RoaringBitmap();
            ce.docIds.deserialize(dis);
            ct.addEventEntry(ce);
            try {
                evCode = dis.readInt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ct;
    }

    static public class CacheFileIterator implements Iterator<CacheTimePeriodEntry> {
        File f;
        DataInputStream dis;
        CacheTimePeriodEntry currentCt = new CacheTimePeriodEntry();

        public CacheFileIterator(File f) {
            this.f = f;
            try {
                dis = new DataInputStream(new RandomAccessBufferedFileInputStream(f));
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        public boolean finish() {
            try {
                dis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return false;
        }

        @Override
        public boolean hasNext() {
            try {
                currentCt = loadIntermediaryNextEntry(dis);
                if (currentCt == null) {
                    return finish();
                }
            } catch (IOException e) {
                return finish();
            }
            return true;
        }

        @Override
        public CacheTimePeriodEntry next() {
            return currentCt;
        }

    }

}
