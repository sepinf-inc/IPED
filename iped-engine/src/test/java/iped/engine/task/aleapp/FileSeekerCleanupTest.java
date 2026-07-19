package iped.engine.task.aleapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import iped.data.IItemReader;
import iped.search.IItemSearcher;

public class FileSeekerCleanupTest {

    private static final String TEST_TRANSLATED_KEY = "/tmp/sqlite_tmp_fake/test.db";

    @After
    public void tearDown() {
        // never leave state behind in the process-wide static map
        AleappTask.getState().getTranslatedPaths().remove(TEST_TRANSLATED_KEY);
    }

    /**
     * Minimal dynamic stub: returns the mapped value for invoked method names,
     * otherwise a primitive-safe default. Avoids a mockito dependency in this module.
     */
    private static <T> T stub(Class<T> iface, Map<String, Object> values) {
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface },
                (proxy, method, args) -> {
                    if (values.containsKey(method.getName())) {
                        return values.get(method.getName());
                    }
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return Boolean.FALSE;
                    if (r == char.class) return Character.valueOf((char) 0);
                    if (r == byte.class) return Byte.valueOf((byte) 0);
                    if (r == short.class) return Short.valueOf((short) 0);
                    if (r == int.class) return Integer.valueOf(0);
                    if (r == long.class) return Long.valueOf(0L);
                    if (r == float.class) return Float.valueOf(0f);
                    if (r == double.class) return Double.valueOf(0d);
                    return null;
                }));
    }

    private static IItemSearcher searcherReturning(List<IItemReader> items) {
        Map<String, Object> values = new HashMap<>();
        values.put("search", items);
        return stub(IItemSearcher.class, values);
    }

    @SuppressWarnings("unchecked")
    private List<Path> getTempDirs(FileSeeker seeker) throws Exception {
        Field f = FileSeeker.class.getDeclaredField("tempDirs");
        f.setAccessible(true);
        return (List<Path>) f.get(seeker);
    }

    @SuppressWarnings("unchecked")
    private List<String> getTranslatedKeys(FileSeeker seeker) throws Exception {
        Field f = FileSeeker.class.getDeclaredField("translatedKeys");
        f.setAccessible(true);
        return (List<String>) f.get(seeker);
    }

    @Test
    public void testCleanupDeletesTempDirs() throws Exception {
        Map<String, Object> itemValues = new HashMap<>();
        itemValues.put("getPath", "/root/data/com.android.vending/databases/test.db");
        itemValues.put("getName", "test.db");
        itemValues.put("getType", "sqlite");
        itemValues.put("getBufferedInputStream", new ByteArrayInputStream(new byte[0]));
        IItemReader item = stub(IItemReader.class, itemValues);

        IItemSearcher searcher = searcherReturning(Collections.singletonList(item));

        FileSeeker seeker = new FileSeeker("/root", searcher);

        try {
            try {
                seeker.search(Arrays.asList("*/com.android.vending/databases/*.db"));
            } catch (Exception ignored) {
                // SQLite export may fail with empty bytes — temp dir was still created
            }

            // Capture the exact paths tracked before cleanup
            List<Path> trackedDirs = new ArrayList<>(getTempDirs(seeker));
            assertFalse("seeker must have tracked at least one temp dir", trackedDirs.isEmpty());

            seeker.cleanup();

            for (Path dir : trackedDirs) {
                assertFalse("cleanup() must delete tracked dir: " + dir, Files.exists(dir));
            }
            assertTrue("cleanup() must clear the tempDirs list", getTempDirs(seeker).isEmpty());
        } finally {
            // cleanup() is idempotent: make sure no temp dir survives a failed assert
            seeker.cleanup();
        }
    }

    @Test
    public void testCleanupIsIdempotent() {
        IItemSearcher searcher = searcherReturning(Collections.emptyList());

        FileSeeker seeker = new FileSeeker("/root", searcher);

        // cleanup() on empty seeker must not throw, and must be safely repeatable
        seeker.cleanup();
        seeker.cleanup();
    }

    @Test
    public void testCleanupDeletesInjectedDir() throws Exception {
        IItemSearcher searcher = searcherReturning(Collections.emptyList());

        FileSeeker seeker = new FileSeeker("/root", searcher);

        // Inject a real temp dir directly into the seeker's tracked list
        Path tempDir = Files.createTempDirectory("sqlite_tmp_test");
        Path tempFile = tempDir.resolve("dummy.db");
        Files.write(tempFile, new byte[]{1, 2, 3});

        getTempDirs(seeker).add(tempDir);

        assertTrue(Files.exists(tempDir));
        assertTrue(Files.exists(tempFile));

        seeker.cleanup();

        assertFalse("cleanup() must delete the injected temp dir", Files.exists(tempDir));
        assertFalse("cleanup() must delete files inside the temp dir", Files.exists(tempFile));
        assertTrue("cleanup() must clear the tempDirs list", getTempDirs(seeker).isEmpty());
    }

    @Test
    public void testCleanupRemovesTranslatedPathEntries() throws Exception {
        IItemSearcher searcher = searcherReturning(Collections.emptyList());
        FileSeeker seeker = new FileSeeker("/root", searcher);

        // Simulate an exported sqlite: a translatedPaths entry tracked by this seeker
        AleappTask.getState().getTranslatedPaths().put(TEST_TRANSLATED_KEY, "/root/data/test.db");
        getTranslatedKeys(seeker).add(TEST_TRANSLATED_KEY);

        seeker.cleanup();

        assertFalse("cleanup() must remove translatedPaths entries added by this seeker",
                AleappTask.getState().getTranslatedPaths().containsKey(TEST_TRANSLATED_KEY));
        assertTrue("cleanup() must clear the translatedKeys list", getTranslatedKeys(seeker).isEmpty());
    }
}
