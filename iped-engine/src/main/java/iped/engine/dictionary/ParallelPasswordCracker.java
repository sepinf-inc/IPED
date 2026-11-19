package iped.engine.dictionary;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.dictionary.PasswordCrackAttempt;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.PasswordCrackingConfig;

/**
 * Runs password cracking attempts in background, parallelized across several
 * threads, following a producer/consumer model.
 *
 * A single producer thread reads candidate passwords from the given
 * {@code Iterable<String>} and feeds them into a bounded
 * {@link LinkedBlockingQueue} (so a huge dictionary never materializes fully in
 * memory: the producer blocks while the queue is full and resumes as consumers
 * drain it). Several consumer threads take passwords from the queue and invoke
 * the supplied {@link PasswordCrackAttempt} on each one, until one succeeds, the
 * dictionary is exhausted, or the configured time limit is reached.
 *
 * The level of parallelism and the timeout are read from
 * {@code PasswordCrackingConfig.txt} (parallelism defaulting to half of the
 * available processors), but can also be provided explicitly.
 */
public class ParallelPasswordCracker {

    private static final Logger logger = LoggerFactory.getLogger(ParallelPasswordCracker.class);

    /** Sentinel that tells a consumer no more passwords will arrive. */
    private static final String POISON = new String("POISON"); // compared by identity (==)

    private final int parallelism;
    private final long timeoutMillis;

    /**
     * Builds a cracker using the parallelism and timeout from
     * {@link PasswordCrackingConfig} (or sensible defaults if the configuration
     * is not available).
     */
    public ParallelPasswordCracker() {
        this(getConfig());
    }

    public ParallelPasswordCracker(PasswordCrackingConfig config) {
        this(config != null ? config.getCrackingThreads() : Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                config != null ? config.getCrackTimeoutMillis() : 0);
    }

    /**
     * @param parallelism
     *            number of consumer threads used to try candidate passwords
     *            (values below 1 are clamped to 1).
     * @param timeoutMillis
     *            maximum time to spend cracking, in milliseconds. Zero or a
     *            negative value means no time limit.
     */
    public ParallelPasswordCracker(int parallelism, long timeoutMillis) {
        this.parallelism = Math.max(1, parallelism);
        this.timeoutMillis = timeoutMillis;
    }

    public int getParallelism() {
        return parallelism;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Tries the given candidate passwords in parallel, invoking {@code attempt}
     * once per password.
     *
     * @param passwords
     *            candidate passwords, consumed lazily by a single producer
     *            thread.
     * @param attempt
     *            the single-try logic; returns a non-null result on success.
     * @return the result of the first successful attempt, or {@code null} if none
     *         matched or the timeout was reached.
     */
    public <T> T crack(Iterable<String> passwords, PasswordCrackAttempt<T> attempt) {
        // Cap the queue so a huge dictionary does not pile up in memory.
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(Math.max(1024, parallelism * 256));
        // The result doubles as the stop signal: once set, producer and consumers wind down.
        AtomicReference<T> result = new AtomicReference<>();

        Thread producer = new Thread(() -> produce(passwords, queue, result), "password-cracker-producer");
        producer.setDaemon(true);

        ThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("password-cracker-%d-" + Thread.currentThread().getName())
                .daemon(true)
                .build();
        ExecutorService consumers = Executors.newFixedThreadPool(parallelism, factory);
        CountDownLatch doneLatch = new CountDownLatch(parallelism);

        producer.start();
        for (int i = 0; i < parallelism; i++) {
            consumers.execute(() -> {
                try {
                    consume(queue, attempt, result);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        try {
            if (timeoutMillis > 0) {
                if (!doneLatch.await(timeoutMillis, TimeUnit.MILLISECONDS) && result.get() == null) {
                    logger.error("Password cracking timeout reached ({} ms); stopping.", timeoutMillis);
                }
            } else {
                doneLatch.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            producer.interrupt();
            consumers.shutdownNow();
        }

        return result.get();
    }

    /** Producer: reads passwords into the queue, then signals the end with poison pills. */
    private <T> void produce(Iterable<String> passwords, BlockingQueue<String> queue, AtomicReference<T> result) {
        try {
            for (String password : passwords) {
                if (result.get() != null) {
                    break;
                }
                queue.put(password); // blocks while the queue is full
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            logger.warn("Error reading candidate passwords", e);
        } finally {
            // Always release the consumers, even on early stop or read errors.
            for (int i = 0; i < parallelism; i++) {
                try {
                    queue.put(POISON);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** Consumer: tries passwords from the queue until poison, success, or interruption. */
    private <T> void consume(BlockingQueue<String> queue, PasswordCrackAttempt<T> attempt, AtomicReference<T> result) {
        try {
            String password;
            while ((password = queue.take()) != POISON && result.get() == null) {
                try {
                    T attemptResult = attempt.tryPassword(password);
                    if (attemptResult != null) {
                        result.compareAndSet(null, attemptResult);
                        break;
                    }
                } catch (Exception e) {
                    logger.debug("Crack attempt failed for a candidate password", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static PasswordCrackingConfig getConfig() {
        ConfigurationManager cm = ConfigurationManager.get();
        return cm != null ? cm.findObject(PasswordCrackingConfig.class) : null;
    }
}
