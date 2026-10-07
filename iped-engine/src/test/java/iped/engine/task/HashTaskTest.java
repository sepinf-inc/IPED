package iped.engine.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.data.ICaseData;
import iped.engine.config.AbstractTaskConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.config.HashTaskConfig;
import iped.engine.core.Statistics;
import iped.engine.data.CaseData;
import iped.engine.data.Item;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.utils.UTF8Properties;

public class HashTaskTest {

    @BeforeClass
    public static void setupSecurityProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @After
    public void tearDown() throws Exception {
        Field singletonField = ConfigurationManager.class.getDeclaredField("singleton");
        singletonField.setAccessible(true);
        singletonField.set(null, null);

        System.clearProperty(WhatsAppParser.SHA256_ENABLED_SYSPROP);
        System.clearProperty(WhatsAppParser.HASH_TASK_ENABLED_SYSPROP);
    }

    private HashTask createHashTask(boolean enabled, String... algorithms) throws Exception {
        HashTaskConfig config = new HashTaskConfig();
        UTF8Properties props = new UTF8Properties();
        if (algorithms != null && algorithms.length > 0) {
            props.setProperty("hashes", String.join("; ", algorithms));
        }
        config.processProperties(props);

        Field enabledPropField = AbstractTaskConfig.class.getDeclaredField("enabledProp");
        enabledPropField.setAccessible(true);
        EnableTaskProperty enableProp = new EnableTaskProperty(config.getTaskEnableProperty());
        enableProp.setEnabled(enabled);
        enabledPropField.set(config, enableProp);

        IConfigurationDirectory dummyDir = new IConfigurationDirectory() {
            @Override
            public void addPath(Path path) {}
            @Override
            public List<Path> getResourceLookupFolders() { return Collections.emptyList(); }
            @Override
            public List<Path> lookUpResource(Predicate<Path> predicate) { return Collections.emptyList(); }
            @Override
            public List<Path> lookUpResource(Configurable<?> configurable) { return Collections.emptyList(); }
        };

        ConfigurationManager cm = ConfigurationManager.createInstance(dummyDir);
        cm.addObject(config);

        HashTask task = new HashTask();
        Constructor<Statistics> statsConstructor = Statistics.class.getDeclaredConstructor(ICaseData.class, java.io.File.class);
        statsConstructor.setAccessible(true);
        task.stats = statsConstructor.newInstance(new CaseData(), new java.io.File("target/test-index"));
        task.init(cm);
        return task;
    }

    private static class TestItem extends Item {
        private final byte[] content;
        private final boolean throwIoException;

        public TestItem(byte[] content) {
            this(content, false);
        }

        public TestItem(byte[] content, boolean throwIoException) {
            this.content = content;
            this.throwIoException = throwIoException;
            if (content != null) {
                setLength((long) content.length);
            }
        }

        @Override
        public BufferedInputStream getBufferedInputStream() throws IOException {
            if (throwIoException) {
                throw new IOException("Simulated test I/O failure");
            }
            if (content == null) {
                return null;
            }
            return new BufferedInputStream(new ByteArrayInputStream(content));
        }
    }

    @Test
    public void testEmptyStreamKnownVectors() throws Exception {
        HashTask task = createHashTask(true, "md5", "sha-1", "sha-256", "sha-512", "edonkey");
        TestItem item = new TestItem(new byte[0]);

        task.process(item);

        assertEquals("D41D8CD98F00B204E9800998ECF8427E", item.getExtraAttribute("md5"));
        assertEquals("DA39A3EE5E6B4B0D3255BFEF95601890AFD80709", item.getExtraAttribute("sha-1"));
        assertEquals("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", item.getExtraAttribute("sha-256"));
        assertEquals("CF83E1357EEFB8BDF1542850D66D8007D620E4050B5715DC83F4A921D36CE9CE47D0D13C5D85F2B0FF8318D2877EEC2F63B931BD47417A81A538327AF927DA3E", item.getExtraAttribute("sha-512"));
        assertEquals("31D6CFE0D16AE931B73C59D7E0C089C0", item.getExtraAttribute("edonkey"));

        // Default hash should match the first algorithm (md5)
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", item.getHash());
    }

    @Test
    public void testStandardTextVector() throws Exception {
        HashTask task = createHashTask(true, "sha-256", "md5", "sha-1", "sha-512", "edonkey");
        byte[] data = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        TestItem item = new TestItem(data);

        task.process(item);

        assertEquals("D7A8FBB307D7809469CA9ABCB0082E4F8D5651E46D3CDB762D02D0BF37C9E592", item.getExtraAttribute("sha-256"));
        assertEquals("9E107D9D372BB6826BD81D3542A419D6", item.getExtraAttribute("md5"));
        assertEquals("2FD4E1C67A2D28FCED849EE1BB76E7391B93EB12", item.getExtraAttribute("sha-1"));
        assertEquals("07E547D9586F6A73F73FBAC0435ED76951218FB7D0C8D788A309D785436BBB642E93A252A954F23912547D1E8A3B5ED6E1BFD7097821233FA0538F3DB854FEE6", item.getExtraAttribute("sha-512"));
        assertEquals("1BEE69A46BA811185C194762ABAEAE90", item.getExtraAttribute("edonkey"));

        // Default hash should be sha-256 since it was configured first
        assertEquals("D7A8FBB307D7809469CA9ABCB0082E4F8D5651E46D3CDB762D02D0BF37C9E592", item.getHash());
    }

    @Test
    public void testMultiBufferStreaming() throws Exception {
        // HASH_BUFFER_LEN is 1 MB (1048576 bytes). Use 2.5 MB to exercise buffer swapping loop
        int size = 2 * 1024 * 1024 + 512 * 1024;
        byte[] largeData = new byte[size];
        for (int i = 0; i < size; i++) {
            largeData[i] = (byte) (i % 251);
        }

        MessageDigest md5ExpectedDigest = MessageDigest.getInstance("MD5");
        byte[] expectedMd5Bytes = md5ExpectedDigest.digest(largeData);
        String expectedMd5 = HashTask.getHashString(expectedMd5Bytes);

        MessageDigest sha256ExpectedDigest = MessageDigest.getInstance("SHA-256");
        byte[] expectedSha256Bytes = sha256ExpectedDigest.digest(largeData);
        String expectedSha256 = HashTask.getHashString(expectedSha256Bytes);

        HashTask task = createHashTask(true, "md5", "sha-256");
        TestItem item = new TestItem(largeData);

        task.process(item);

        assertEquals(expectedMd5, item.getExtraAttribute("md5"));
        assertEquals(expectedSha256, item.getExtraAttribute("sha-256"));
        assertEquals(expectedMd5, item.getHash());
    }

    @Test
    public void testEdonkeyMultiChunkRollover() throws Exception {
        // CHUNK_SIZE for edonkey is 9500 KB = 9,728,000 bytes.
        // Test with 10,000,000 bytes to force multi-chunk rollover
        int chunkSize = 9500 * 1024;
        int totalSize = 10_000_000;
        byte[] data = new byte[totalSize];
        for (int i = 0; i < totalSize; i++) {
            data[i] = (byte) (i & 0x7F);
        }

        // Independently calculate expected ED2K hash
        MessageDigest md4 = MessageDigest.getInstance("MD4");
        md4.update(data, 0, chunkSize);
        byte[] chunk1Hash = md4.digest();

        md4.update(data, chunkSize, totalSize - chunkSize);
        byte[] chunk2Hash = md4.digest();

        md4.update(chunk1Hash);
        md4.update(chunk2Hash);
        byte[] expectedEd2k = md4.digest();
        String expectedEd2kStr = HashTask.getHashString(expectedEd2k);

        HashTask task = createHashTask(true, "edonkey");
        TestItem item = new TestItem(data);

        task.process(item);

        assertEquals(expectedEd2kStr, item.getExtraAttribute("edonkey"));
        assertEquals(expectedEd2kStr, item.getHash());
    }

    @Test
    public void testGetHashStringFormatting() {
        assertEquals("", HashTask.getHashString(new byte[0]));

        byte[] zerosAndNibbles = new byte[] { 0x00, 0x05, 0x0A, 0x0F, 0x10 };
        assertEquals("00050A0F10", HashTask.getHashString(zerosAndNibbles));

        byte[] negativeBytes = new byte[] { (byte) 0xFF, (byte) 0x80, (byte) 0xFE };
        assertEquals("FF80FE", HashTask.getHashString(negativeBytes));
    }

    @Test
    public void testNullLengthItem() throws Exception {
        HashTask task = createHashTask(true, "md5");
        TestItem item = new TestItem(new byte[] { 1, 2, 3 });
        item.setLength(null);

        task.process(item);

        assertEquals("", item.getHash());
        assertNull(item.getExtraAttribute("md5"));
    }

    @Test
    public void testQueueEndItemSkipped() throws Exception {
        HashTask task = createHashTask(true, "md5");
        TestItem item = new TestItem(new byte[] { 1, 2, 3 });
        item.setQueueEnd(true);

        task.process(item);

        assertNull(item.getHash());
        assertNull(item.getExtraAttribute("md5"));
    }

    @Test
    public void testAlreadyHashedItemSkipped() throws Exception {
        HashTask task = createHashTask(true, "md5");
        TestItem item = new TestItem(new byte[] { 1, 2, 3 });
        item.setHash("PREVIOUS_HASH");

        task.process(item);

        assertEquals("PREVIOUS_HASH", item.getHash());
        assertNull(item.getExtraAttribute("md5"));
    }

    @Test
    public void testIgnoreHardlinkSkipped() throws Exception {
        HashTask task = createHashTask(true, "md5");
        TestItem item = new TestItem(new byte[] { 1, 2, 3 });
        item.setExtraAttribute(IgnoreHardLinkTask.IGNORE_HARDLINK_ATTR, true);

        task.process(item);

        assertNull(item.getHash());
        assertNull(item.getExtraAttribute("md5"));
    }

    @Test
    public void testIoErrorHandling() throws Exception {
        HashTask task = createHashTask(true, "md5");
        TestItem item = new TestItem(new byte[10], true);

        task.process(item);

        assertEquals("true", item.getExtraAttribute("ioError"));
        assertEquals(1, task.stats.getIoErrors());
        assertNull(item.getHash());
    }

    @Test
    public void testWhatsAppSystemPropertiesSet() throws Exception {
        createHashTask(true, "sha-256", "md5");

        assertEquals("true", System.getProperty(WhatsAppParser.SHA256_ENABLED_SYSPROP));
        assertEquals("true", System.getProperty(WhatsAppParser.HASH_TASK_ENABLED_SYSPROP));
    }
}
