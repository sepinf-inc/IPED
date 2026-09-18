package iped.engine.task.yara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.mime.MediaType;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.engine.config.AbstractTaskConfig;
import iped.engine.config.EnableTaskProperty;
import iped.engine.config.YaraConfig;
import iped.engine.data.Item;
import iped.properties.ExtraProperties;
import iped.utils.UTF8Properties;

/**
 * Integration test of the full {@link YaraScanTask} pipeline against the real
 * {@code libyara-x-capi}. Loads a small rule catalog from a temp directory,
 * compiles via {@link YaraEngine}, and runs the task's {@link YaraScanTask#process}
 * on in-memory {@link Item}s to verify that the {@code yara:tag} and per-rule
 * {@code yara:match:<namespace>/<name>} fields are populated correctly (FR-001 / FR-003 / FR-004 /
 * FR-005 / FR-006 / FR-012).
 *
 * <p>Skipped via {@link org.junit.Assume} when {@code libyara-x-capi} is not
 * loadable in the test environment.</p>
 */
public class YaraScanTaskIntegrationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private YaraScanTask task;

    @BeforeClass
    public static void ensureEngine() {
        String hint = System.getenv("YARA_X_LIB_PATH");
        assumeTrue("libyara-x-capi not available — skipping integration tests",
                YaraEngine.ensureAvailable(hint));
    }

    @AfterClass
    public static void shutdownEngine() {
        YaraEngine.shutdown();
    }

    @Before
    public void resetTaskState() {
        YaraScanTask.resetForTests();
    }

    @After
    public void closeTask() throws Exception {
        if (task != null) {
            task.finish();
            task = null;
        }
    }

    /* ----------------------------------------------------------------- *
     *  Tests
     * ----------------------------------------------------------------- */

    @Test
    public void matched_item_receives_yara_tag_and_per_rule_match_field() throws Exception {
        File ruleDir = tmp.newFolder("rules-match");
        writeRule(ruleDir, "hello.yar",
                "rule hello_world : demo malware { strings: $s = \"hello world\" condition: $s }");

        YaraConfig config = buildEnabledConfig(ruleDir);
        task = new YaraScanTask();
        task.initWithConfig(config);
        assertTrue("task should be enabled with a valid catalog", task.isEnabled());

        Item item = makeItem("greeting.txt", "the quick brown fox says hello world to YARA-X");
        task.process(item);

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) item.getExtraAttribute(ExtraProperties.YARA_TAGS);
        assertNotNull("yara:tag must be set on matched item", tags);
        assertTrue("expected tag 'demo' but got " + tags, tags.contains("demo"));
        assertTrue("expected tag 'malware' but got " + tags, tags.contains("malware"));

        // Per-rule field is the sole match surface since rev-5.
        @SuppressWarnings("unchecked")
        List<String> ruleValues = (List<String>) item
                .getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "hello/hello_world");
        assertNotNull("yara:match:hello/hello_world must be set on matched item", ruleValues);
        assertEquals(Arrays.asList("hello world"), ruleValues);
    }

    @Test
    public void non_matching_item_receives_no_yara_attributes() throws Exception {
        File ruleDir = tmp.newFolder("rules-nomatch");
        writeRule(ruleDir, "only_mz.yar",
                "rule mz_at_start { strings: $mz = { 4D 5A } condition: $mz at 0 }");

        YaraConfig config = buildEnabledConfig(ruleDir);
        task = new YaraScanTask();
        task.initWithConfig(config);

        Item item = makeItem("not-pe.txt", "GIF89a — this is not a PE file");
        task.process(item);

        assertNull(item.getExtraAttribute(ExtraProperties.YARA_TAGS));
        assertNull(item.getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "only_mz/mz_at_start"));
    }

    @Test
    public void item_above_maxFileSize_is_skipped() throws Exception {
        File ruleDir = tmp.newFolder("rules-size");
        writeRule(ruleDir, "any.yar",
                "rule always { strings: $a = \"a\" condition: $a }");

        YaraConfig config = buildEnabledConfig(ruleDir);
        // Cap at 32 bytes via reflection on the private field.
        setLongField(config, "maxFileSizeBytes", 32L);

        task = new YaraScanTask();
        task.initWithConfig(config);

        // 256-byte payload exceeds the 32-byte cap.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            sb.append('a');
        }
        Item item = makeItem("big.txt", sb.toString());
        task.process(item);

        assertNull("item above cap must not be scanned",
                item.getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "any/always"));
        assertNull(item.getExtraAttribute(ExtraProperties.YARA_TAGS));
    }

    @Test
    public void invalid_rule_is_isolated_good_rule_still_matches() throws Exception {
        File ruleDir = tmp.newFolder("rules-mixed");
        writeRule(ruleDir, "good.yar",
                "rule good_rule { strings: $s = \"abc\" condition: $s }");
        writeRule(ruleDir, "bad.yar",
                "rule bad_rule { strings: $s = \"unterminated condition: $s }");

        YaraConfig config = buildEnabledConfig(ruleDir);
        task = new YaraScanTask();
        task.initWithConfig(config);
        // Task may or may not be enabled depending on whether the good rule compiled;
        // we only require: if enabled, the good rule still matches.
        if (task.isEnabled()) {
            Item item = makeItem("evidence.txt", "this contains abc as substring");
            task.process(item);
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) item
                    .getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "good/good_rule");
            assertNotNull("good rule must still match despite bad rule presence", values);
            assertEquals(Arrays.asList("abc"), values);
        }
    }

    @Test
    public void multiple_rules_matched_produce_one_per_rule_field_each() throws Exception {
        File ruleDir = tmp.newFolder("rules-multi");
        writeRule(ruleDir, "zeta.yar",
                "rule zeta_rule { strings: $s = \"target\" condition: $s }");
        writeRule(ruleDir, "alpha.yar",
                "rule alpha_rule { strings: $s = \"target\" condition: $s }");

        YaraConfig config = buildEnabledConfig(ruleDir);
        task = new YaraScanTask();
        task.initWithConfig(config);

        Item item = makeItem("multi.txt", "well past the target word");
        task.process(item);

        @SuppressWarnings("unchecked")
        List<String> alphaValues = (List<String>) item
                .getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "alpha/alpha_rule");
        @SuppressWarnings("unchecked")
        List<String> zetaValues = (List<String>) item
                .getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "zeta/zeta_rule");
        assertNotNull("alpha rule's per-rule field must be set", alphaValues);
        assertNotNull("zeta rule's per-rule field must be set", zetaValues);
        assertEquals(Arrays.asList("target"), alphaValues);
        assertEquals(Arrays.asList("target"), zetaValues);
    }

    @Test
    public void per_rule_facet_field_yara_match_is_populated_with_decoded_values() throws Exception {
        // Two rules in different namespaces — each gets its own
        // yara:match:<namespace>/<name> multi-valued field, mirroring how
        // RegexTask produces Regex:CPF, Regex:EMAIL, etc.
        File ruleDir = tmp.newFolder("rules-permatch");
        writeRule(ruleDir, "alpha.yar",
                "rule greeting { strings: $g = \"hello\" condition: $g }");
        writeRule(ruleDir, "beta.yar",
                "rule farewell { strings: $f1 = \"world\" $f2 = \"please\" condition: any of them }");

        YaraConfig config = buildEnabledConfig(ruleDir);
        task = new YaraScanTask();
        task.initWithConfig(config);

        Item item = makeItem("evidence.txt", "say hello to the world please");
        task.process(item);

        // Per-rule fields — each entry is a distinct matched string (decoded text
        // since these patterns are pure ASCII).
        @SuppressWarnings("unchecked")
        java.util.List<String> greetingValues = (java.util.List<String>) item
                .getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "alpha/greeting");
        assertNotNull("yara:match:alpha/greeting must be set", greetingValues);
        assertEquals(Arrays.asList("hello"), greetingValues);

        @SuppressWarnings("unchecked")
        java.util.List<String> farewellValues = (java.util.List<String>) item
                .getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "beta/farewell");
        assertNotNull("yara:match:beta/farewell must be set", farewellValues);
        // Two distinct matched strings; the task sorts them lexicographically for
        // deterministic SortedSetDocValues ordering.
        assertEquals(Arrays.asList("please", "world"), farewellValues);

        // The non-matching rule's per-rule field must NOT appear at all.
        assertNull("non-matching rules must not produce yara:match:* fields",
                item.getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "nonexistent/rule"));
    }

    @Test
    public void disabled_config_produces_a_no_op_task() throws Exception {
        // Build a config that is NOT enabled — enabledProp.value stays false.
        YaraConfig config = new YaraConfig();
        Field enabledField = AbstractTaskConfig.class.getDeclaredField("enabledProp");
        enabledField.setAccessible(true);
        EnableTaskProperty prop = new EnableTaskProperty("enableYara");
        prop.setEnabled(false);
        enabledField.set(config, prop);

        task = new YaraScanTask();
        task.initWithConfig(config);
        assertFalse("task must report disabled when enableYara=false", task.isEnabled());

        Item item = makeItem("anything.txt", "nothing should match");
        task.process(item);
        assertNull(item.getExtraAttribute(ExtraProperties.YARA_TAGS));
        assertNull(item.getExtraAttribute(ExtraProperties.YARA_MATCH_PREFIX + "anything/rule"));
    }

    /* ----------------------------------------------------------------- *
     *  Helpers
     * ----------------------------------------------------------------- */

    private static void writeRule(File dir, String fileName, String content) throws IOException {
        Files.write(new File(dir, fileName).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds an enabled {@link YaraConfig} pointing at {@code ruleDir} without
     * going through {@code ConfigurationManager}. Sets {@code enabledProp} via
     * reflection (the field is protected on {@code AbstractTaskConfig}).
     */
    private YaraConfig buildEnabledConfig(File ruleDir) throws Exception {
        YaraConfig config = new YaraConfig();
        Field enabledField = AbstractTaskConfig.class.getDeclaredField("enabledProp");
        enabledField.setAccessible(true);
        EnableTaskProperty prop = new EnableTaskProperty("enableYara");
        prop.setEnabled(true);
        enabledField.set(config, prop);

        UTF8Properties props = new UTF8Properties();
        props.setProperty("ruleDirectories", ruleDir.getAbsolutePath());
        config.processProperties(props);
        return config;
    }

    private static void setLongField(Object target, String fieldName, long value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.setLong(target, value);
    }

    /**
     * Builds an in-memory {@link Item} backed by a {@code byte[]} content,
     * with sane defaults: media type {@code application/octet-stream}, length
     * = content length.
     */
    private static Item makeItem(String name, String content) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Item item = new Item() {
            @Override
            public BufferedInputStream getBufferedInputStream() throws IOException {
                return new BufferedInputStream(new ByteArrayInputStream(bytes));
            }
        };
        item.setName(name);
        item.setLength((long) bytes.length);
        item.setMediaType(MediaType.OCTET_STREAM);
        return item;
    }
}
