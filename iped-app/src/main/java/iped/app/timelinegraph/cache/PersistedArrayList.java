package iped.app.timelinegraph.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.jfree.data.time.TimePeriod;
import org.roaringbitmap.RoaringBitmap;

import com.github.davidmoten.bplustree.BPlusTree;
import com.github.davidmoten.bplustree.LargeByteBuffer;
import com.github.davidmoten.bplustree.Serializer;

import iped.app.timelinegraph.cache.persistance.CachePersistance;
import iped.utils.SeekableFileInputStream;

public class PersistedArrayList implements Set<CacheTimePeriodEntry> {
    int docCount = 0;
    HashSet<CacheTimePeriodEntry> mostFreqUsed = new HashSet<CacheTimePeriodEntry>();
    String timePeriod;
    private int flushCount = 0;
    ArrayList<Future> flushes = new ArrayList<Future>(); 
    
    AtomicInteger size = new AtomicInteger(0);
    File indexDirectory;
    private Serializer cacheTimePeriodEntrySerializer = new CacheTimePeriodEntrySerializer();
    BPlusTree<Long, CacheTimePeriodEntry> tree;

    private AtomicInteger flushSize = new AtomicInteger(0);
    private int flushMaxSize = 100000;

    public PersistedArrayList(Class<? extends TimePeriod> timePeriodClass) {
        this.timePeriod = timePeriodClass.getSimpleName();
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public boolean isEmpty() {
        return size.get()==0;
    }

    @Override
    public boolean contains(Object o) {
        if(o instanceof CacheTimePeriodEntry) {
            return false;
        }
        CacheTimePeriodEntry ctpe = (CacheTimePeriodEntry) o;
        if(mostFreqUsed.contains(ctpe.date)) {
            return true;
        }
        return tree.findFirst(ctpe.date)!=null;
    }

    @Override
    public Iterator<CacheTimePeriodEntry> iterator() {
        return tree.findAll().iterator();
    }

    @Override
    public Object[] toArray() {
        Object[] o = new Object[size.get()];
        Iterator<CacheTimePeriodEntry> it = tree.findAll().iterator();
        for (int i = 0; i < o.length; i++) {
            o[i]=it.next();
        }
        return o;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }

    @Override
    public boolean add(CacheTimePeriodEntry e) {
        
        if(flushSize.get() == Math.floorDiv(flushMaxSize,2)) {
            try {
                waitPendingFlushes();
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        }
        if(flushSize.get()>=flushMaxSize) {
            flush();
            flushSize.set(0);
        }
        
        size.incrementAndGet();
        flushSize.incrementAndGet();
        return mostFreqUsed.add(e);
    }

    @Override
    public boolean remove(Object o) {
        throw new RuntimeException("Remove not implemented");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new RuntimeException("Remove not implemented");
    }

    @Override
    public boolean addAll(Collection<? extends CacheTimePeriodEntry> c) {
        throw new RuntimeException("Remove not implemented");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new RuntimeException("Remove not implemented");
    }

    @Override        
    public boolean retainAll(Collection<?> c) {
        throw new RuntimeException("Remove not implemented");
    }

    @Override
    public void clear() {
        throw new RuntimeException("Remove not implemented");
    }

    public CacheTimePeriodEntry get(long time) {
        return tree.findFirst(time);
    }

    public RoaringBitmap createRoaringBitmap() {
        return new MonitoredRoaringBitmap(this);
    }

    public void notifyAdd() {
    }

    private void waitPendingFlushes() throws InterruptedException, ExecutionException {
        for (Iterator iterator = flushes.iterator(); iterator.hasNext();) {
            Future future = (Future) iterator.next();
            future.get();
        }
    }

    private void flush() {
        final HashSet<CacheTimePeriodEntry> setToFlush = mostFreqUsed;
        mostFreqUsed = new HashSet<CacheTimePeriodEntry>();
        
        final int lflushCount = flushCount++;
        
        CachePersistance cp = CachePersistance.getInstance();
        Future f = cp.cachePersistanceExecutor.submit(new Runnable() {
            @Override
            public void run() {
                if(tree==null) {
                    indexDirectory = cp.getBaseDir();
                    indexDirectory = new File(indexDirectory,"cacheflushes");
                    indexDirectory = new File(timePeriod,"cacheflush");
                    indexDirectory = new File(Integer.toString(lflushCount),"cacheflush");
                    tree = BPlusTree 
                              .file()
                              .directory(indexDirectory)
                              .maxLeafKeys(32)
                              .maxNonLeafKeys(8)
                              .segmentSizeMB(1)
                              .keySerializer(Serializer.LONG)
                              .valueSerializer(cacheTimePeriodEntrySerializer)
                              .naturalOrder();
                }
                
                for(CacheTimePeriodEntry ctpe: setToFlush) {
                    tree.insert(ctpe.date, ctpe);
                }
            }
        });
        flushes.add(f);
    }
    
    class CacheTimePeriodEntrySerializer implements Serializer<CacheTimePeriodEntry>{
        @Override
        public CacheTimePeriodEntry read(LargeByteBuffer bb) {
            CacheTimePeriodEntry ct = new CacheTimePeriodEntry();
            ct.events = new ArrayList<CacheEventEntry>();
            ct.date=bb.getLong();
            String eventName = bb.getString();
            while (!eventName.equals("!!")) {
                CacheEventEntry ce = new CacheEventEntry();
                ce.event = eventName;
                int size = bb.getInt();
                byte b[] = new byte[size];
                bb.get(b);
                ce.docIds = new RoaringBitmap();
                try {
                    ce.docIds.deserialize(new DataInputStream(new ByteArrayInputStream(b)));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                
                ct.events.add(ce);
                try {
                    eventName = bb.getString();
                }catch(Exception e) {
                    e.printStackTrace();
                }
            }
            return ct;
        }

        @Override
        public void write(LargeByteBuffer bb, CacheTimePeriodEntry ct) {
            bb.putLong(ct.date);
            for(CacheEventEntry ce: ct.events) {
               bb.putString(ce.event);
               ByteArrayOutputStream bos = new ByteArrayOutputStream();
               DataOutputStream dos = new DataOutputStream(bos);
               try {
                   ce.docIds.serialize(dos);
               } catch (IOException e) {
                   e.printStackTrace();
               }
               byte b[] = bos.toByteArray();
               bb.putInt(b.length);
               bb.put(b);
            }
            bb.putString("!!");
        }

        @Override
        public int maxSize() {
            return 0;
        }
        
    }
}
