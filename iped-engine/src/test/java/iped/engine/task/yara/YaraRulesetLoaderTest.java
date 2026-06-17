package iped.engine.task.yara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link YaraRulesetLoader} — directory discovery of YARA
 * source files.
 */
public class YaraRulesetLoaderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void empty_input_returns_empty_list() {
        assertEquals(Collections.emptyList(), YaraRulesetLoader.discover(null));
        assertEquals(Collections.emptyList(), YaraRulesetLoader.discover(Collections.emptyList()));
    }

    @Test
    public void picks_up_yar_and_yara_extensions() throws Exception {
        File dir = tmp.newFolder("rules");
        File yar = new File(dir, "a.yar");
        File yara = new File(dir, "b.yara");
        File notes = new File(dir, "readme.txt");
        Files.write(yar.toPath(), "rule a { condition: true }".getBytes(StandardCharsets.UTF_8));
        Files.write(yara.toPath(), "rule b { condition: true }".getBytes(StandardCharsets.UTF_8));
        Files.write(notes.toPath(), "not a rule".getBytes(StandardCharsets.UTF_8));

        List<File> found = YaraRulesetLoader.discover(Arrays.asList(dir));

        assertEquals(2, found.size());
        // Deterministic order by absolute path.
        assertEquals("a.yar", found.get(0).getName());
        assertEquals("b.yara", found.get(1).getName());
    }

    @Test
    public void ignores_yarc_and_other_extensions() throws Exception {
        File dir = tmp.newFolder("rules");
        Files.write(new File(dir, "good.yar").toPath(),
                "rule g { condition: true }".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "skipped.yarc").toPath(), new byte[] { 0, 1, 2, 3 });
        Files.write(new File(dir, "also-skipped.txt").toPath(), "text".getBytes(StandardCharsets.UTF_8));

        List<File> found = YaraRulesetLoader.discover(Arrays.asList(dir));

        assertEquals(1, found.size());
        assertEquals("good.yar", found.get(0).getName());
    }

    @Test
    public void walks_recursively_into_subdirectories() throws Exception {
        File root = tmp.newFolder("catalog");
        File sub = new File(root, "vendor");
        sub.mkdirs();
        File subsub = new File(sub, "apt");
        subsub.mkdirs();
        Files.write(new File(root, "top.yar").toPath(),
                "rule t { condition: true }".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(sub, "middle.yar").toPath(),
                "rule m { condition: true }".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(subsub, "deep.yara").toPath(),
                "rule d { condition: true }".getBytes(StandardCharsets.UTF_8));

        List<File> found = YaraRulesetLoader.discover(Arrays.asList(root));

        assertEquals(3, found.size());
        List<String> names = new ArrayList<>();
        for (File f : found) {
            names.add(f.getName());
        }
        // By absolute path: deep < middle < top (vendor/apt/deep, vendor/middle, top).
        assertTrue("expected deep.yara to appear: " + names, names.contains("deep.yara"));
        assertTrue("expected middle.yar to appear: " + names, names.contains("middle.yar"));
        assertTrue("expected top.yar to appear: " + names, names.contains("top.yar"));
    }

    @Test
    public void is_case_insensitive_on_extension() throws Exception {
        File dir = tmp.newFolder("rules");
        Files.write(new File(dir, "upper.YAR").toPath(),
                "rule u { condition: true }".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "mixed.YaRa").toPath(),
                "rule m { condition: true }".getBytes(StandardCharsets.UTF_8));

        List<File> found = YaraRulesetLoader.discover(Arrays.asList(dir));

        assertEquals(2, found.size());
    }

    @Test
    public void missing_directory_is_skipped_without_throwing() {
        File missing = new File(tmp.getRoot(), "does-not-exist");
        List<File> found = YaraRulesetLoader.discover(Arrays.asList(missing));
        assertEquals(Collections.emptyList(), found);
    }

    @Test
    public void non_directory_path_is_skipped_without_throwing() throws Exception {
        File regular = tmp.newFile("not-a-dir.txt");
        Files.write(regular.toPath(), "text".getBytes(StandardCharsets.UTF_8));
        List<File> found = YaraRulesetLoader.discover(Arrays.asList(regular));
        assertEquals(Collections.emptyList(), found);
    }

    @Test
    public void multiple_directories_are_merged() throws Exception {
        File a = tmp.newFolder("a");
        File b = tmp.newFolder("b");
        Files.write(new File(a, "rule-a.yar").toPath(),
                "rule a { condition: true }".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(b, "rule-b.yara").toPath(),
                "rule b { condition: true }".getBytes(StandardCharsets.UTF_8));

        List<File> found = YaraRulesetLoader.discover(Arrays.asList(a, b));

        assertEquals(2, found.size());
    }

    @Test
    public void null_entry_in_input_list_is_skipped() throws Exception {
        File dir = tmp.newFolder("rules");
        Files.write(new File(dir, "only.yar").toPath(),
                "rule only { condition: true }".getBytes(StandardCharsets.UTF_8));

        List<File> input = new ArrayList<>();
        input.add(null);
        input.add(dir);
        List<File> found = YaraRulesetLoader.discover(input);
        assertEquals(1, found.size());
    }
}
