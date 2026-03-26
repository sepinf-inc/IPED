package iped.parsers.ufed.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Parties implements Iterable<Party>, Collection<Party> {

    private static final Map<Party, Party> allParties = new ConcurrentHashMap<>();

    private ArrayList<Party> list = new ArrayList<>();

    public static Party getPrevInstance(Party party) {
        if (party == null) {
            return null;
        }
        Party prev = allParties.putIfAbsent(party, party);
        return prev != null ? prev : party;
    }

    @Override
    public Iterator<Party> iterator() {
        return list.iterator();
    }

    @Override
    public boolean add(Party party) {
        return list.add(getPrevInstance(party));
    }

    public Party get(int index) {
        return list.get(index);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return list.contains(o);
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return list.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return list.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return list.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Party> c) {
        boolean added = false;
        for (Party p : c) {
            if (this.add(p)) {
                added = true;
            }
        }
        return added;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return list.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return list.retainAll(c);
    }

    @Override
    public void clear() {
        list.clear();
    }

}
