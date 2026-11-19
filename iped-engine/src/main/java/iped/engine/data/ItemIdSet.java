package iped.engine.data;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.roaringbitmap.RoaringBitmap;

import iped.data.IItemId;

public class ItemIdSet extends AbstractSet<IItemId> {

    private HashMap<Integer, RoaringBitmap> bitsets = new HashMap<Integer, RoaringBitmap>();

    public ItemIdSet() {

    }

    @Override
    public Iterator<IItemId> iterator() {
        return new ItemIdBitSetIterator();
    }

    class ItemIdBitSetIterator implements Iterator<IItemId> {

        Iterator<Integer> srcIterator;
        Iterator<Integer> idIterator;
        IItemId nextItemId = null;
        int currentSrcId = -1;

        ItemIdBitSetIterator() {
            srcIterator = bitsets.keySet().iterator();
            advance();
        }

        @Override
        public boolean hasNext() {
            return nextItemId != null;
        }

        @Override
        public IItemId next() {
            if (nextItemId == null) {
                throw new NoSuchElementException();
            }
            IItemId itemId = nextItemId;
            advance();
            return itemId;
        }

        private void advance() {
            if (idIterator != null && idIterator.hasNext()) {
                int currentId = idIterator.next();
                nextItemId = new ItemId(currentSrcId, currentId);
            } else if (srcIterator.hasNext()) {
                currentSrcId = srcIterator.next();
                RoaringBitmap bitmap = bitsets.get(currentSrcId);
                idIterator = bitmap.iterator();
                advance();
            } else {
                nextItemId = null;
            }
        }

    }

    public void put(int finalSourceId, RoaringBitmap ids) {
        bitsets.put(finalSourceId, ids);
    }

    @Override
    public int size() {
        int size = 0;
        for (RoaringBitmap r : bitsets.values()) {
            size += r.getCardinality();
        }
        return size;
    }

    @Override
    public boolean add(IItemId e) {
        RoaringBitmap bitmap = bitsets.get(e.getSourceId());
        if (bitmap == null) {
            bitmap = new RoaringBitmap();
            bitsets.put(e.getSourceId(), bitmap);
        }
        return bitmap.checkedAdd(e.getId());
    }

    @Override
    public void clear() {
        bitsets.clear();
    }

}
