package iped.engine.preview;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.engine.data.Item;

public class PreviewRepositoryManagerTest {

    private static final String DB_FILE = "previews.mv.db";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testReadOnlyWithoutDatabase() throws Exception {
        // cases without any preview do not have the database file
        File folder = tempFolder.newFolder();
        PreviewKey key = new PreviewKey(new byte[] { 1, 2, 3, 4 });

        PreviewRepositoryManager.configureReadOnly(folder);
        try {
            PreviewRepository repo = PreviewRepositoryManager.get(folder);
            assertFalse(repo.previewExists(key));
            assertFalse(repo.consumePreview(key, is -> {
            }));
            assertFalse(new File(folder, DB_FILE).exists());
        } finally {
            PreviewRepositoryManager.close(folder);
        }
    }

    @Test
    public void testReadOnlyWithDatabase() throws Exception {
        File folder = tempFolder.newFolder();
        Item item = new Item();
        item.setId(7);
        PreviewKey key = PreviewKey.create(item);
        PreviewKey otherKey = new PreviewKey(new byte[] { 5, 6, 7, 8 });
        byte[] data = "preview".getBytes("UTF-8");

        PreviewRepositoryManager.configureWritable(folder);
        try {
            PreviewRepositoryManager.get(folder).storeRawPreview(item, new ByteArrayInputStream(data));
        } finally {
            PreviewRepositoryManager.close(folder);
        }
        assertTrue(new File(folder, DB_FILE).exists());

        PreviewRepositoryManager.configureReadOnly(folder);
        try {
            PreviewRepository repo = PreviewRepositoryManager.get(folder);
            assertTrue(repo.previewExists(key));
            assertFalse(repo.previewExists(otherKey));
            AtomicReference<String> read = new AtomicReference<>();
            assertTrue(repo.consumePreview(key, is -> read.set(new String(is.readAllBytes(), "UTF-8"))));
            assertTrue("preview".equals(read.get()));
        } finally {
            PreviewRepositoryManager.close(folder);
        }
    }
}
