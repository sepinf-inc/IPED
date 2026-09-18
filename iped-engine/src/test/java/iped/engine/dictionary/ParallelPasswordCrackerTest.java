package iped.engine.dictionary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import iped.dictionary.PasswordCrackAttempt;

public class ParallelPasswordCrackerTest {

    private static List<String> passwords(int count) {
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add("p" + i);
        }
        return list;
    }

    /** An attempt that "cracks" only the given target password. */
    private static PasswordCrackAttempt<String> matches(String target) {
        return candidate -> candidate.equals(target) ? candidate : null;
    }

    @Test
    public void testFindsMatchingPassword() {
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(4, 0);
        String result = cracker.crack(passwords(100), matches("p42"));
        assertEquals("p42", result);
    }

    @Test
    public void testFindsMatchWithSingleThread() {
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(1, 0);
        assertEquals("p7", cracker.crack(passwords(20), matches("p7")));
    }

    @Test
    public void testNoMatchReturnsNull() {
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(4, 0);
        assertNull(cracker.crack(passwords(100), matches("not-there")));
    }

    @Test
    public void testEmptyDictionaryReturnsNull() {
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(4, 0);
        assertNull(cracker.crack(Collections.emptyList(), matches("anything")));
    }

    @Test
    public void testAllCandidatesTriedWhenNoMatch() {
        int count = 1000;
        AtomicInteger attempts = new AtomicInteger();
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(4, 0);

        String result = cracker.crack(passwords(count), candidate -> {
            attempts.incrementAndGet();
            return null; // never matches
        });

        assertNull(result);
        // With no time limit and no match, every candidate must be tried exactly once.
        assertEquals(count, attempts.get());
    }

    @Test
    public void testFindsMatchAtEndOfLargeDictionary() {
        int count = 20000;
        String target = "p" + (count - 1);
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(4, 0);
        assertEquals(target, cracker.crack(passwords(count), matches(target)));
    }

    @Test
    public void testExceptionInAttemptIsTreatedAsFailure() {
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(4, 0);

        // Attempt throws for every candidate but the target, which still matches.
        String result = cracker.crack(passwords(50), candidate -> {
            if (candidate.equals("p30")) {
                return candidate;
            }
            throw new RuntimeException("boom for " + candidate);
        });

        assertEquals("p30", result);
    }

    @Test
    public void testAllAttemptsThrowingReturnsNull() {
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(4, 0);
        String result = cracker.crack(passwords(50), candidate -> {
            throw new RuntimeException("always fails");
        });
        assertNull(result);
    }

    @Test
    public void testGenericReturnType() {
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(4, 0);
        // The result type is inferred from the attempt: here an Integer (the password length).
        Integer result = cracker.crack(passwords(100),
                candidate -> candidate.equals("p13") ? candidate.length() : null);
        assertEquals(Integer.valueOf(3), result);
    }

    @Test
    public void testTimeoutReturnsNullPromptly() {
        int count = 40;
        long perAttemptMillis = 500;
        long timeoutMillis = 300;
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(2, timeoutMillis);

        long start = System.currentTimeMillis();
        String result = cracker.crack(passwords(count), candidate -> {
            try {
                Thread.sleep(perAttemptMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null; // never matches
        });
        long elapsed = System.currentTimeMillis() - start;

        assertNull(result);
        // Trying everything sequentially would take count * perAttempt / threads = 10s;
        // the timeout must make crack() return far sooner.
        assertTrue("crack() should return shortly after the timeout, but took " + elapsed + " ms",
                elapsed < 3000);
    }

    @Test
    public void testParallelismClampedAndGetters() {
        ParallelPasswordCracker clamped = new ParallelPasswordCracker(0, 1234);
        assertEquals(1, clamped.getParallelism());
        assertEquals(1234, clamped.getTimeoutMillis());

        ParallelPasswordCracker negative = new ParallelPasswordCracker(-5, 0);
        assertEquals(1, negative.getParallelism());
    }

    @Test
    public void testRunsAttemptsConcurrently() {
        int threads = 3;
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(threads, 0);

        // Each attempt only completes once all `threads` attempts are running at the
        // same time; if they ran sequentially, the latch would never reach zero and
        // await() would time out, yielding a null result.
        CountDownLatch allStarted = new CountDownLatch(threads);

        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            candidates.add("c" + i);
        }
        String target = "c1";

        String result = cracker.crack(candidates, candidate -> {
            allStarted.countDown();
            boolean concurrent = allStarted.await(10, TimeUnit.SECONDS);
            return (concurrent && candidate.equals(target)) ? candidate : null;
        });

        assertEquals(target, result);
    }
}
