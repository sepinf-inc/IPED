package iped.engine.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import iped.utils.HashValue;

public class HashIndexTest {

    private static final String MD5 = "0123456789abcdef0123456789abcdef";
    private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";

    @Test
    public void addReportsNewAndDuplicate() {
        HashIndex index = new HashIndex();
        assertTrue(index.add(new HashValue(MD5)));
        assertFalse(index.add(new HashValue(MD5)));
        assertEquals(1, index.size());
        assertTrue(index.contains(new HashValue(MD5)));
        assertFalse(index.contains(new HashValue("fedcba9876543210fedcba9876543210")));
    }

    @Test
    public void longerHashesAreIdentifiedByFirst16Bytes() {
        HashIndex index = new HashIndex();
        assertTrue(index.add(new HashValue(SHA1)));
        assertFalse(index.add(new HashValue(SHA1)));
        assertTrue(index.add(new HashValue("0123456789abcdef0123456789abcdee01234567")));
        assertEquals(2, index.size());
    }

    @Test
    public void allZeroHashIsSupported() {
        HashIndex index = new HashIndex();
        byte[] zero = new byte[16];
        assertFalse(index.contains(zero));
        assertTrue(index.add(zero));
        assertFalse(index.add(zero));
        assertTrue(index.contains(zero));
        assertEquals(1, index.size());
        index.clear();
        assertEquals(0, index.size());
        assertFalse(index.contains(zero));
    }

    @Test
    public void growsAndKeepsAllValues() {
        HashIndex index = new HashIndex();
        Random random = new Random(42);
        Set<String> expected = new HashSet<>();
        byte[][] values = new byte[200000][];
        for (int i = 0; i < values.length; i++) {
            byte[] b = new byte[16];
            random.nextBytes(b);
            values[i] = b;
            boolean isNew = expected.add(new HashValue(b).toString());
            assertEquals(isNew, index.add(b));
        }
        assertEquals(expected.size(), index.size());
        for (byte[] b : values) {
            assertTrue(index.contains(b));
            assertFalse(index.add(b));
        }
        byte[] missing = new byte[16];
        missing[0] = 1;
        assertFalse(index.contains(missing));
    }

    @Test
    public void sameHashGetsSameLock() {
        HashIndex index = new HashIndex();
        assertSame(index.lockFor(new HashValue(MD5)), index.lockFor(new HashValue(MD5)));
        index.add(new HashValue(MD5));
        assertSame(index.lockFor(new HashValue(MD5)), index.lockFor(new HashValue(MD5)));
        index.clear();
        assertSame(index.lockFor(new HashValue(MD5)), index.lockFor(new HashValue(MD5)));
    }

    @Test
    public void concurrentAddsCountEachHashOnce() throws Exception {
        HashIndex index = new HashIndex();
        int threads = 4;
        int values = 50000;
        Thread[] ts = new Thread[threads];
        int[] added = new int[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                for (int i = 0; i < values; i++) {
                    byte[] b = new byte[16];
                    b[0] = (byte) (i >>> 24);
                    b[1] = (byte) (i >>> 16);
                    b[2] = (byte) (i >>> 8);
                    b[3] = (byte) i;
                    b[15] = 1;
                    if (index.add(b)) {
                        added[id]++;
                    }
                }
            });
            ts[t].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        int total = 0;
        for (int a : added) {
            total += a;
        }
        assertEquals(values, total);
        assertEquals(values, index.size());
    }
}
