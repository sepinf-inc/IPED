package iped.engine.util;

import iped.data.IHashValue;

/**
 * Compact set of hash values, used to track the hashes already seen while
 * processing a case. It replaces a HashMap of HashValue objects, which needs
 * about 100 bytes per hash, with an open addressing table of 16 bytes per slot.
 *
 * Each hash is represented by its first 16 bytes (shorter hashes are padded
 * with zeros). That is the full value of an MD5, and for longer hashes (SHA-1,
 * SHA-256) a 128 bit prefix, where an accidental match is not realistic.
 *
 * It also hands out a fixed set of lock objects, chosen from the hash value, so
 * the same hash always gets the same lock without keeping one object per hash.
 */
public class HashIndex {

    private static final int INITIAL_SLOTS = 1 << 10;
    private static final int NUM_LOCKS = 1 << 12;

    private final Object[] locks = new Object[NUM_LOCKS];

    // two longs (high, low) per slot, an all zero slot is empty
    private long[] table = new long[2 * INITIAL_SLOTS];
    private int mask = INITIAL_SLOTS - 1;
    private int size = 0;
    private boolean hasZeroKey = false;

    public HashIndex() {
        for (int i = 0; i < NUM_LOCKS; i++) {
            locks[i] = new Object();
        }
    }

    private static long word(byte[] bytes, int start) {
        long v = 0;
        for (int i = start; i < start + 8; i++) {
            v = (v << 8) | (i < bytes.length ? (bytes[i] & 0xFFL) : 0);
        }
        return v;
    }

    private static int slotOf(long hi, long lo) {
        long h = (hi ^ (lo * 0xC2B2AE3D27D4EB4FL)) * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 32);
    }

    /**
     * @return true if the hash was not present and was added, false if it was
     *         already there.
     */
    public synchronized boolean add(byte[] bytes) {
        long hi = word(bytes, 0);
        long lo = word(bytes, 8);
        if (hi == 0 && lo == 0) {
            boolean added = !hasZeroKey;
            hasZeroKey = true;
            return added;
        }
        int i = slotOf(hi, lo) & mask;
        while (true) {
            long h = table[2 * i];
            long l = table[2 * i + 1];
            if (h == 0 && l == 0) {
                break;
            }
            if (h == hi && l == lo) {
                return false;
            }
            i = (i + 1) & mask;
        }
        table[2 * i] = hi;
        table[2 * i + 1] = lo;
        size++;
        if ((long) size * 8 > (long) (mask + 1) * 5) {
            grow();
        }
        return true;
    }

    public boolean add(IHashValue hash) {
        return add(hash.getBytes());
    }

    public synchronized boolean contains(byte[] bytes) {
        long hi = word(bytes, 0);
        long lo = word(bytes, 8);
        if (hi == 0 && lo == 0) {
            return hasZeroKey;
        }
        int i = slotOf(hi, lo) & mask;
        while (true) {
            long h = table[2 * i];
            long l = table[2 * i + 1];
            if (h == 0 && l == 0) {
                return false;
            }
            if (h == hi && l == lo) {
                return true;
            }
            i = (i + 1) & mask;
        }
    }

    public boolean contains(IHashValue hash) {
        return contains(hash.getBytes());
    }

    private void grow() {
        long[] old = table;
        int newSlots = (mask + 1) << 1;
        table = new long[2 * newSlots];
        mask = newSlots - 1;
        for (int j = 0; j < old.length; j += 2) {
            long h = old[j];
            long l = old[j + 1];
            if (h != 0 || l != 0) {
                int i = slotOf(h, l) & mask;
                while (table[2 * i] != 0 || table[2 * i + 1] != 0) {
                    i = (i + 1) & mask;
                }
                table[2 * i] = h;
                table[2 * i + 1] = l;
            }
        }
    }

    /**
     * Number of distinct hashes stored.
     */
    public synchronized int size() {
        return size + (hasZeroKey ? 1 : 0);
    }

    public synchronized void clear() {
        table = new long[2 * INITIAL_SLOTS];
        mask = INITIAL_SLOTS - 1;
        size = 0;
        hasZeroKey = false;
    }

    /**
     * Returns the lock object for a hash. The same hash always gets the same lock,
     * different hashes may share it.
     */
    public Object lockFor(byte[] bytes) {
        return locks[slotOf(word(bytes, 0), word(bytes, 8)) & (NUM_LOCKS - 1)];
    }

    public Object lockFor(IHashValue hash) {
        return lockFor(hash.getBytes());
    }

    @Override
    public synchronized String toString() {
        return getClass().getSimpleName() + "[size=" + size() + ", slots=" + (mask + 1) + "]";
    }
}
