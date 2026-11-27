package iped.parsers.whatsapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class MessageThumbDataTest {

    private static final byte[] THUMB = new byte[] { 1, 2, 3, 4, 5 };

    /**
     * An interrupted thread must give up writing the thumb, instead of reopening
     * the shared channel and being interrupted again in an endless recursion
     * (#2685).
     */
    @Test
    public void testInterruptedThreadGivesUp() {
        Message m = new Message();
        Thread.currentThread().interrupt();
        try {
            m.setThumbData(THUMB);
            fail("RuntimeException expected");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof ClosedByInterruptException);
        } catch (StackOverflowError e) {
            fail("endless recursion when the thread is interrupted");
        } finally {
            // clear the interrupt flag
            Thread.interrupted();
        }

        // the channel closed by the interrupt is reopened by a not interrupted thread
        Message m2 = new Message();
        m2.setThumbData(THUMB);
        assertArrayEquals(THUMB, m2.getThumbData());
    }

    /**
     * Other threads must reopen the shared channel closed by the interrupt of a
     * thread using it, and access the thumbs (#1679).
     */
    @Test
    public void testOtherThreadsReopenChannel() throws Exception {
        Message m = new Message();
        m.setThumbData(THUMB);

        // closes the shared channel by interrupting a thread while using it
        Thread t = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                new Message().setThumbData(THUMB);
            } catch (RuntimeException e) {
                // expected
            }
        });
        t.start();
        t.join();

        AtomicReference<byte[]> read = new AtomicReference<>();
        Thread reader = new Thread(() -> read.set(m.getThumbData()));
        reader.start();
        reader.join();
        assertArrayEquals(THUMB, read.get());

        Message m2 = new Message();
        m2.setThumbData(THUMB);
        assertArrayEquals(THUMB, m2.getThumbData());
    }
}
