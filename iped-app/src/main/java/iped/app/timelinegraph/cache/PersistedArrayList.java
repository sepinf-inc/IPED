package iped.app.timelinegraph.cache;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.jfree.data.time.TimePeriod;
import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.cache.persistance.CachePersistance;
import iped.app.timelinegraph.cache.persistance.CachePersistance.CacheFileIterator;

public class PersistedArrayList implements Set<CacheTimePeriodEntry> {
    int docCount = 0;
    Map<Long,CacheTimePeriodEntry> inMemoryEntries = new TreeMap<Long, CacheTimePeriodEntry>();
    String timePeriod;
    private int flushCount = 0;
    List<Future> flushes = new ArrayList<Future>();
    boolean isFlushing = false;
    
    AtomicInteger size = new AtomicInteger(0);
    File indexDirectory;

    ArrayList<File> flushFiles = new ArrayList<File>();

    private AtomicInteger flushSize = new AtomicInteger(0);
    private int flushMaxSize = 100000;
    private int minAvailableMemoryNotToFlush = 40000000;//~40MB

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
        if(flushFiles.size()<=0) {
            return inMemoryEntries.values().iterator();
        }else {
            try {
                waitPendingFlushes();
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            Iterator<CacheTimePeriodEntry>[] iterators = new Iterator[1+flushFiles.size()];
            iterators[0] = inMemoryEntries.values().iterator();
            int i=1;
            for (Iterator iterator = flushFiles.iterator(); iterator.hasNext();) {
                File f = (File) iterator.next();
                iterators[i] = new CacheFileIterator(f);
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
        if(isFlushing) {
            try {
                waitPendingFlushes();
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        }
        if(Runtime.getRuntime().freeMemory()<minAvailableMemoryNotToFlush) {//if available memory less than 40MB
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
        synchronized (flushes) {
            for (Iterator iterator = flushes.iterator(); iterator.hasNext();) {
                Future future = (Future) iterator.next();
                future.get();
            }
        }
    }

    private void flush() {
        isFlushing=true;
        final Collection<CacheTimePeriodEntry> setToFlush = inMemoryEntries.values();
        inMemoryEntries = new TreeMap<Long, CacheTimePeriodEntry>();
        
        final int lflushCount = flushCount++;
        
        CachePersistance cp = CachePersistance.getInstance();
        Future f = cp.cachePersistanceExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    indexDirectory = cp.getBaseDir();
                    indexDirectory = new File(indexDirectory,"cacheflushes");
                    indexDirectory = new File(indexDirectory, timePeriod);
                    indexDirectory.mkdirs();
                    File flushFile = new File(indexDirectory,Integer.toString(lflushCount));
                    
                    flushFiles.add(flushFile);
                    cp.saveIntermediaryCacheSet(setToFlush, flushFile);
                }catch(Exception e) {
                  e.printStackTrace();  
                } finally {
                    setToFlush.clear();
                    isFlushing=false;
                }
            }
        });
        Future fend = cp.cachePersistanceExecutor.submit(new Runnable() { //removes from unfinished flush list at the end 
            @Override
            public void run() {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }finally {
                    synchronized (flushes) {
                        flushes.remove(f);
                    }
                }
            }
            
        });
        flushes.add(f);
    }
}
