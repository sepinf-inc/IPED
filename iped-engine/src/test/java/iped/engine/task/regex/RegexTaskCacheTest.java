package iped.engine.task.regex;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.Configuration;
import iped.engine.config.ExportByKeywordsConfig;
import iped.engine.config.RegexTaskConfig;
import iped.engine.data.Item;
import iped.engine.task.AbstractTask;

public class RegexTaskCacheTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File cache;
    private ConfigurationManager manager;
    private RegexTaskConfig config;
    private ExportByKeywordsConfig exportConfig;
    private final Map<Field, Object> savedState = new LinkedHashMap<>();

    @Before
    public void setUp() throws Exception {
        // Preserve the process-wide state used by other task tests.
        for (String name : new String[] { "regexList", "regexFull", "regexValidator" }) {
            saveAndClear(RegexTask.class, name);
        }
        saveAndClear(ConfigurationManager.class, "singleton");
        manager = ConfigurationManager.createInstance(null);
        config = new RegexTaskConfig();
        File enabled = temporaryFolder.newFile(Configuration.CONFIG_FILE);
        Files.write(enabled.toPath(), "enableRegexSearch = true".getBytes(StandardCharsets.UTF_8));
        config.processConfig(enabled.toPath());
        setPattern("foo[0-9]{3}");
        exportConfig = new ExportByKeywordsConfig();
        manager.addObject(config);
        manager.addObject(exportConfig);
        cache = new File(temporaryFolder.newFolder(), "regexAutomata.cache");
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<Field, Object> entry : savedState.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    public void testEmptyCacheIsRebuilt() throws Exception {
        Files.createFile(cache.toPath());
        RegexTask task = init(new RegexTask(cache));
        assertHit(task, "foo123");
        assertTrue(cache.length() > 16);
    }

    @Test
    public void testTruncatedCacheIsRebuiltWithoutPublishingPartialState() throws Exception {
        init(new RegexTask(cache));
        byte[] complete = Files.readAllBytes(cache.toPath());
        new RegexTask(cache).finish();
        Files.write(cache.toPath(), Arrays.copyOf(complete, complete.length - 1));
        Method load = RegexTask.class.getDeclaredMethod("loadCache", RegexTaskConfig.class,
                ExportByKeywordsConfig.class);
        load.setAccessible(true);
        assertEquals(false, load.invoke(new RegexTask(cache), config, exportConfig));
        assertNull(staticField("regexList").get(null));
        assertNull(staticField("regexFull").get(null));
        assertHit(init(new RegexTask(cache)), "foo123");
        assertEquals(complete.length, cache.length());
    }

    @Test
    public void testInvalidCacheLengthsAreCacheMisses() throws Exception {
        init(new RegexTask(cache));
        byte[] complete = Files.readAllBytes(cache.toPath());
        for (int length : new int[] { -1, Integer.MAX_VALUE }) {
            new RegexTask(cache).finish();
            byte[] corrupt = complete.clone();
            ByteBuffer.wrap(corrupt).putInt(16, length);
            Files.write(cache.toPath(), corrupt);
            assertHit(init(new RegexTask(cache)), "foo123");
        }
    }

    @Test
    public void testInvalidSerializedPayloadIsRebuilt() throws Exception {
        init(new RegexTask(cache));
        byte[] corrupt = Files.readAllBytes(cache.toPath());
        new RegexTask(cache).finish();
        int listLength = ByteBuffer.wrap(corrupt).getInt(16);
        Arrays.fill(corrupt, 20, 20 + listLength, (byte) 0xff);
        Files.write(cache.toPath(), corrupt);
        assertHit(init(new RegexTask(cache)), "foo123");
    }

    @Test
    public void testUnwritableCacheLocationDoesNotAbortMatching() throws Exception {
        // A regular file used as parent fails on every OS, including privileged CI users.
        File parent = temporaryFolder.newFile();
        RegexTask task = init(new RegexTask(new File(parent, "cache")));
        assertHit(task, "foo123");
        assertTrue(parent.isFile());
    }

    @Test
    public void testSerializationStackOverflowPreservesPreviousCache() throws Exception {
        init(new RegexTask(cache));
        byte[] previous = Files.readAllBytes(cache.toPath());
        new RegexTask(cache).finish();
        setPattern("bar[0-9]{3}");
        RegexTask task = init(new RegexTask(cache) {
            private int calls;

            @Override
            byte[] serializeCache(Object value) {
                if (++calls == 2) {
                    throw new StackOverflowError("Injected cache serialization overflow");
                }
                return super.serializeCache(value);
            }
        });
        assertHit(task, "bar123");
        assertArrayEquals(previous, Files.readAllBytes(cache.toPath()));
        assertArrayEquals(new String[] { cache.getName() }, cache.getParentFile().list());
        task.finish();
        assertHit(init(new RegexTask(cache)), "bar123");
    }

    @Test
    public void testValidCacheIsLoadedWithoutSerialization() throws Exception {
        init(new RegexTask(cache));
        byte[] previous = Files.readAllBytes(cache.toPath());
        new RegexTask(cache).finish();
        RegexTask task = init(new RegexTask(cache) {
            @Override
            byte[] serializeCache(Object value) {
                throw new AssertionError("Valid cache should be loaded, not rebuilt");
            }
        });
        assertHit(task, "foo123");
        assertArrayEquals(previous, Files.readAllBytes(cache.toPath()));
    }

    @Test
    public void testChangedConfigurationRebuildsCache() throws Exception {
        init(new RegexTask(cache));
        new RegexTask(cache).finish();
        setPattern("bar[0-9]{3}");
        assertHit(init(new RegexTask(cache)), "bar123");
    }

    @Test
    public void testOtherFatalErrorsStillPropagate() throws Exception {
        OutOfMemoryError error = new OutOfMemoryError("Injected fatal failure");
        try {
            init(new RegexTask(cache) {
                @Override
                byte[] serializeCache(Object value) {
                    throw error;
                }
            });
            fail("OutOfMemoryError must not be swallowed");
        } catch (OutOfMemoryError expected) {
            assertSame(error, expected);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPatternStillFailsInitialization() throws Exception {
        setPattern("(");
        init(new RegexTask(cache));
    }

    private RegexTask init(RegexTask task) throws Exception {
        Field output = AbstractTask.class.getDeclaredField("output");
        output.setAccessible(true);
        output.set(task, temporaryFolder.getRoot());
        task.init(manager);
        return task;
    }

    private void setPattern(String pattern) throws Exception {
        File file = temporaryFolder.newFile();
        Files.write(file.toPath(), ("TEST, false = " + pattern).getBytes(StandardCharsets.UTF_8));
        config.processTaskConfig(file.toPath());
    }

    @SuppressWarnings("unchecked")
    private void assertHit(RegexTask task, String text) throws Exception {
        Item item = new Item();
        item.setName("sample");
        item.setParsedTextCache(text);
        try {
            task.process(item);
            Collection<RegexHits> hits = (Collection<RegexHits>) item.getExtraAttribute("Regex:TEST");
            assertNotNull(hits);
            assertEquals(1, hits.size());
            RegexHits hit = hits.iterator().next();
            assertEquals(text, hit.getHit());
            assertArrayEquals(new long[] { 0 }, hit.getOffsets());
        } finally {
            item.getTextCache().close();
        }
    }

    private static Field staticField(String name) throws Exception {
        Field field = RegexTask.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void saveAndClear(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        savedState.put(field, field.get(null));
        field.set(null, null);
    }
}
