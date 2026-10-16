package iped.engine.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ManagerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testDeletePortableCaseFilesKeepsOnlyViewFolder() throws IOException {
        File output = tmp.newFolder("iped");
        Files.createDirectories(new File(output, "index/sub").toPath());
        Files.createDirectories(new File(output, "data").toPath());
        Files.createDirectories(new File(output, "lib").toPath());
        Files.createFile(new File(output, "data/sleuth.db").toPath());
        Files.createFile(new File(output, "bookmarks.iped").toPath());
        Files.createDirectories(new File(output, "view/a/b").toPath());
        Files.createFile(new File(output, "view/a/b/abc.html").toPath());
        Manager.deletePortableCaseFiles(output);
        assertArrayEquals(new String[] { "view" }, output.list());
        assertTrue(new File(output, "view/a/b/abc.html").exists());
    }
}
