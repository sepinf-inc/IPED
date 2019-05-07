package macee.collection;

import java.util.Iterator;
import java.util.LinkedList;

public class BitSetCollectionIterator implements Iterator<Integer> {

    private final LinkedList<Iterator<Integer>> iterators;
    private Iterator<Integer> currentIterator;

    public BitSetCollectionIterator(BitSetCollection collection) {
        iterators = new LinkedList<>();
        collection.getBitSets().keySet().forEach((String bs) -> {
            iterators.add(collection.iterator(bs));
        });
    }

    @Override
    public boolean hasNext() {
        if (currentIterator.hasNext()) {
            return true;
        } else {
            if (iterators.isEmpty()) {
                return false;
            }
            currentIterator = iterators.removeFirst();
        }
        return currentIterator.hasNext();
    }

    @Override
    public Integer next() {
        return currentIterator.next();
    }
}
