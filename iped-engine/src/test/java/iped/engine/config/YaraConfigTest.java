package iped.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.utils.UTF8Properties;

/**
 * Unit tests for {@link YaraConfig}.
 */
public class YaraConfigTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private YaraConfig loadFrom(String body) throws Exception {
        Path file = tmp.newFile("YaraConfig.txt").toPath();
        Files.write(file, body.getBytes(StandardCharsets.UTF_8));
        UTF8Properties props = new UTF8Properties();
        props.load(file.toFile());
        YaraConfig cfg = new YaraConfig();
        cfg.setConfiguration(props);
        cfg.processProperties(props);
        return cfg;
    }

    @Test
    public void defaults_apply_when_keys_omitted() throws Exception {
        YaraConfig cfg = loadFrom("");
        assertEquals(YaraConfig.DEFAULT_MAX_FILE_SIZE_BYTES, cfg.getMaxFileSizeBytes());
        assertEquals(YaraConfig.DEFAULT_PER_ITEM_TIMEOUT_MS, cfg.getPerItemTimeoutMs());
        assertEquals(YaraConfig.DEFAULT_MATCH_HEX_MAX_BYTES, cfg.getMatchHexMaxBytes());
        assertFalse(cfg.isScanAllItems());
        assertTrue(cfg.getRuleDirectories().isEmpty());
        assertEquals("", cfg.getEngineLibraryHint());
    }

    @Test
    public void parses_size_with_M_suffix() throws Exception {
        YaraConfig cfg = loadFrom("maxFileSizeBytes = 250M");
        assertEquals(250L * 1024L * 1024L, cfg.getMaxFileSizeBytes());
    }

    @Test
    public void parses_size_with_G_suffix() throws Exception {
        YaraConfig cfg = loadFrom("maxFileSizeBytes = 1G");
        assertEquals(1024L * 1024L * 1024L, cfg.getMaxFileSizeBytes());
    }

    @Test
    public void parses_size_with_K_suffix_lowercase() throws Exception {
        YaraConfig cfg = loadFrom("maxFileSizeBytes = 64k");
        assertEquals(64L * 1024L, cfg.getMaxFileSizeBytes());
    }

    @Test
    public void parses_plain_integer_size() throws Exception {
        YaraConfig cfg = loadFrom("maxFileSizeBytes = 65536");
        assertEquals(65536L, cfg.getMaxFileSizeBytes());
    }

    @Test
    public void invalid_size_keeps_default() throws Exception {
        YaraConfig cfg = loadFrom("maxFileSizeBytes = NaN-not-a-number");
        assertEquals(YaraConfig.DEFAULT_MAX_FILE_SIZE_BYTES, cfg.getMaxFileSizeBytes());
    }

    @Test
    public void timeout_under_floor_keeps_default() throws Exception {
        YaraConfig cfg = loadFrom("perItemTimeoutMs = 50");
        assertEquals(YaraConfig.DEFAULT_PER_ITEM_TIMEOUT_MS, cfg.getPerItemTimeoutMs());
    }

    @Test
    public void timeout_valid_is_applied() throws Exception {
        YaraConfig cfg = loadFrom("perItemTimeoutMs = 60000");
        assertEquals(60000, cfg.getPerItemTimeoutMs());
    }

    @Test
    public void scanAllItems_true_is_parsed() throws Exception {
        YaraConfig cfg = loadFrom("scanAllItems = true");
        assertTrue(cfg.isScanAllItems());
    }

    @Test
    public void matchHexMaxBytes_within_range_applied() throws Exception {
        YaraConfig cfg = loadFrom("matchHexMaxBytes = 1024");
        assertEquals(1024, cfg.getMatchHexMaxBytes());
    }

    @Test
    public void matchHexMaxBytes_above_absolute_max_keeps_default() throws Exception {
        YaraConfig cfg = loadFrom("matchHexMaxBytes = 99999999");
        assertEquals(YaraConfig.DEFAULT_MATCH_HEX_MAX_BYTES, cfg.getMatchHexMaxBytes());
    }

    @Test
    public void matchHexMaxBytes_non_numeric_keeps_default() throws Exception {
        YaraConfig cfg = loadFrom("matchHexMaxBytes = oops");
        assertEquals(YaraConfig.DEFAULT_MATCH_HEX_MAX_BYTES, cfg.getMatchHexMaxBytes());
    }

    @Test
    public void ruleDirectories_split_by_semicolon() throws Exception {
        YaraConfig cfg = loadFrom("ruleDirectories = /a/path;/b/path");
        assertEquals(2, cfg.getRuleDirectories().size());
        // Use File.toString() round-trip so the assertion holds on both Windows (\) and Linux (/).
        assertEquals(new java.io.File("/a/path").getPath(), cfg.getRuleDirectories().get(0).getPath());
        assertEquals(new java.io.File("/b/path").getPath(), cfg.getRuleDirectories().get(1).getPath());
    }

    @Test
    public void engineLibraryHint_is_trimmed() throws Exception {
        YaraConfig cfg = loadFrom("engineLibraryHint =   /usr/local/lib/libyara.so  ");
        assertEquals("/usr/local/lib/libyara.so", cfg.getEngineLibraryHint());
    }

    @Test
    public void taskEnableProperty_returns_enableYara() {
        YaraConfig cfg = new YaraConfig();
        assertEquals("enableYara", cfg.getTaskEnableProperty());
    }

    @Test
    public void taskConfigFileName_returns_YaraConfig_txt() {
        YaraConfig cfg = new YaraConfig();
        assertEquals("YaraConfig.txt", cfg.getTaskConfigFileName());
    }

    @Test
    public void parseSizeWithSuffix_directly() {
        assertEquals(1024L, YaraConfig.parseSizeWithSuffix("1k"));
        assertEquals(1024L * 1024L, YaraConfig.parseSizeWithSuffix("1M"));
        assertEquals(2L * 1024L * 1024L * 1024L, YaraConfig.parseSizeWithSuffix("2G"));
        assertEquals(42L, YaraConfig.parseSizeWithSuffix("42"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSizeWithSuffix_rejects_zero() {
        YaraConfig.parseSizeWithSuffix("0M");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSizeWithSuffix_rejects_negative() {
        YaraConfig.parseSizeWithSuffix("-1");
    }

    /* ${IPED_HOME} expansion -------------------------------------------- */

    @Test
    public void resolveRuleDir_without_iped_home_token_passes_through() {
        // Bare path: no expansion, just wraps in File.
        java.io.File f = YaraConfig.resolveRuleDir("yara-rules");
        assertEquals("yara-rules", f.getPath());
    }

    @Test
    public void resolveRuleDir_absolute_path_passes_through() {
        String abs = new java.io.File(tmp.getRoot(), "rules").getAbsolutePath();
        java.io.File f = YaraConfig.resolveRuleDir(abs);
        assertEquals(abs, f.getAbsolutePath());
    }

    @Test
    public void resolveRuleDir_iped_home_token_left_when_unresolved() {
        // In a unit-test JVM, YaraInstallPaths.getIpedRoot() returns null
        // (no release layout). The token should be left as-is so the caller
        // sees a clear "directory does not exist" warning.
        java.io.File f = YaraConfig.resolveRuleDir("${IPED_HOME}/yara-rules");
        assertTrue("path should still contain the token when not running from a release: " + f.getPath(),
                f.getPath().contains("${IPED_HOME}"));
    }
}
