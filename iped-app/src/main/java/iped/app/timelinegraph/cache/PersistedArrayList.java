package iped.app.timelinegraph.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    Map<Long,CacheTimePeriodEntry> inMemoryEntries = new TreeMap<Long, CacheTimePeriodEntry>();
    String timePeriod;
    private int flushCount = 0;
    ArrayList<Future> flushes = new ArrayList<Future>();
    boolean isFlushing = false;
    
    AtomicInteger size = new AtomicInteger(0);
    File indexDirectory;
    private Serializer cacheTimePeriodEntrySerializer = new CacheTimePeriodEntrySerializer();
    ArrayList<BPlusTree<Long, CacheTimePeriodEntry>> trees = new ArrayList<BPlusTree<Long, CacheTimePeriodEntry>>();

    private AtomicInteger flushSize = new AtomicInteger(0);
    private int flushMaxSize = 10000;

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
        if(inMemoryEntries.containsKey(ctpe.date)) {
            return true;
        }
        return false;
    }

    @Override
    public Iterator<CacheTimePeriodEntry> iterator() {
        if(trees.size()<=0) {
            return inMemoryEntries.values().iterator();
        }else {
            if(isFlushing) {
                try {
                    waitPendingFlushes();
                } catch (InterruptedException | ExecutionException ex) {
                    ex.printStackTrace();
                }
            }
            
            Iterator<CacheTimePeriodEntry>[] iterators = new Iterator[1+trees.size()];
            iterators[0] = inMemoryEntries.values().iterator();
            int i=1;
            for (Iterator iterator = trees.iterator(); iterator.hasNext();) {
                BPlusTree<Long, CacheTimePeriodEntry> tree = (BPlusTree<Long, CacheTimePeriodEntry>) iterator.next();
                iterators[i] = tree.find(Long.MIN_VALUE, Long.MAX_VALUE).iterator();
                i++;
            }
            return new CombinedIterators(iterators);
        }
    }

    @Override
    public Object[] toArray() {
        throw new RuntimeException("Remove not implemented");
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }

    @Override
    public boolean add(CacheTimePeriodEntry e) {        
        if(flushSize.get() == Math.floorDiv(flushMaxSize,2) && isFlushing) {
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
        inMemoryEntries.put(e.date, e);
        return true;
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
        return inMemoryEntries.get(time);
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
        isFlushing=true;
        final Collection<CacheTimePeriodEntry> setToFlush = inMemoryEntries.values();
        inMemoryEntries = new HashMap<Long, CacheTimePeriodEntry>();
        
        final int lflushCount = flushCount++;
        
        CachePersistance cp = CachePersistance.getInstance();
        Future f = cp.cachePersistanceExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    indexDirectory = cp.getBaseDir();
                    indexDirectory = new File(indexDirectory,"cacheflushes");
                    indexDirectory = new File(indexDirectory, timePeriod);
                    indexDirectory = new File(indexDirectory,Integer.toString(lflushCount));
                    indexDirectory.mkdirs();
                    BPlusTree tree = BPlusTree 
                              .file()
                              .directory(indexDirectory)
                              .maxLeafKeys(32)
                              .maxNonLeafKeys(8)
                              .segmentSizeMB(1)
                              .keySerializer(Serializer.LONG)
                              .valueSerializer(cacheTimePeriodEntrySerializer)
                              .naturalOrder();
                    trees.add(tree);
                    for(CacheTimePeriodEntry ctpe: setToFlush) {
                        tree.insert(ctpe.date, ctpe);
                    }
                }catch(Exception e) {
                  e.printStackTrace();  
                } finally {
                    isFlushing=false;
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
