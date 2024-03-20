package iped.engine.data;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;

import org.roaringbitmap.RoaringBitmap;

import iped.data.IItemId;
import iped.data.IItemIdList;

public class ItemIdList extends AbstractSet<IItemId> implements IItemIdList {
    HashMap<Integer, RoaringBitmap> bitsets = new HashMap<Integer, RoaringBitmap>();
    int length = 0;

    public ItemIdList() {

    }

    @Override
    public Iterator<IItemId> iterator() {
        return new ItemIdBitSetIterator();
    }

    class ItemIdBitSetIterator implements Iterator<IItemId> {
        Integer currentSrcId = null;
        Iterator<Integer> srcIterator;
        RoaringBitmap currentBitset;
        int currentItemIdIdex = -1;
        private int currentLastId;

        ItemIdBitSetIterator() {
            srcIterator = bitsets.keySet().iterator();
        }

        @Override
        public boolean hasNext() {
            if (currentSrcId == null) {
                if (!nextBitSet()) {
                    return false;
                }
            }
            currentItemIdIdex = (int) currentBitset.nextValue(currentItemIdIdex + 1);
            if (currentItemIdIdex == -1) {
                if (!nextBitSet()) {
                    return false;
                }
            }
            return currentSrcId != null;
        }

        @Override
        public IItemId next() {
            return new ItemId(currentSrcId, currentItemIdIdex);
        }

        private boolean nextBitSet() {
            if (srcIterator.hasNext()) {
                currentSrcId = srcIterator.next();
                if (currentSrcId != null) {
                    currentBitset = bitsets.get(currentSrcId);
                    currentItemIdIdex = -1;
                    currentLastId = currentBitset.last();
                }
                return true;
            } else {
                return false;
            }
        }
    }

    public void put(int finalSourceId, RoaringBitmap ids) {
        RoaringBitmap oldlist = bitsets.put(finalSourceId, ids);
        if (oldlist == null) {
            length -= oldlist.getCardinality();
        }
        length += ids.getCardinality();
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public boolean add(IItemId e) {
        RoaringBitmap bitmap = bitsets.get(e.getSourceId());
        if (bitmap == null) {
            bitmap = new RoaringBitmap();
            bitsets.put(e.getSourceId(), bitmap);
        }
        length++;
        return bitmap.checkedAdd(e.getId());
    }

    @Override
    public void clear() {
        bitsets.clear();
    }

}
