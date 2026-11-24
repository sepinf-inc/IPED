package iped.engine.task.leapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tika.metadata.Metadata;
import org.junit.Test;

import iped.data.IItemReader;
import iped.search.IItemSearcher;

public class FileSeekerTest {

    private static final String ROOT = "phone.ufdr/EXTRACTION_FFS.zip";
    private static final String CACHE_FOLDER = "data/data/com.android.chrome/cache/Cache/Cache_Data";
    private static final String CACHE = CACHE_FOLDER + "/";

    private static class ItemSpec {
        String path;
        boolean dir, deleted, carved, ufedEmbedded;

        ItemSpec(String relativePath) {
            this.path = ROOT + "/" + relativePath;
        }

        ItemSpec dir() {
            dir = true;
            return this;
        }

        ItemSpec deleted() {
            deleted = true;
            return this;
        }

        ItemSpec ufedEmbedded() {
            ufedEmbedded = true;
            return this;
        }

        IItemReader build() {
            Metadata metadata = new Metadata();
            if (ufedEmbedded) {
                metadata.set("ufed:embedded", "true");
            }
            return (IItemReader) Proxy.newProxyInstance(IItemReader.class.getClassLoader(), new Class<?>[] { IItemReader.class },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                        case "getPath":
                            return path;
                        case "isDir":
                            return dir;
                        case "isDeleted":
                            return deleted;
                        case "isCarved":
                            return carved;
                        case "getMetadata":
                            return metadata;
                        case "toString":
                            return (dir ? "dir:" : "file:") + path;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }

    private static ItemSpec item(String relativePath) {
        return new ItemSpec(relativePath);
    }

    /** Runs a search over a case holding the given items, returning what was found as "file:path"/"dir:path". */
    private static List<String> search(String glob, ItemSpec... specs) {
        List<IItemReader> items = Arrays.stream(specs).map(ItemSpec::build).collect(Collectors.toList());
        IItemSearcher searcher = (IItemSearcher) Proxy.newProxyInstance(IItemSearcher.class.getClassLoader(),
                new Class<?>[] { IItemSearcher.class }, (proxy, method, args) -> {
                    if ("search".equals(method.getName())) {
                        return items;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        FileSeeker seeker = new FileSeeker(ROOT, Paths.get("unused"), searcher);
        return seeker.searchItems(List.of(glob)).stream()
                .map(item -> item.toString().replace(ROOT + "/", ""))
                .collect(Collectors.toList());
    }

    @Test
    public void testUfedEmbeddedFilesAreIgnored() {
        // UFED lists the entries it extracted from a cache file below the path of that file, and UfedXmlReader
        // creates a virtual folder with the path of the file to hold them
        List<String> found = search("*/com.android.chrome/cache/Cache/Cache_Data/*",
                item(CACHE + "54e7_0").dir(),
                item(CACHE + "54e7_0/%7B%22_tl%22%3A%22aps-tag%22%7D").ufedEmbedded(),
                item(CACHE + "54e7_0"),
                item(CACHE + "index"));

        assertEquals(Arrays.asList("file:" + CACHE + "54e7_0", "file:" + CACHE + "index"), found);
    }

    @Test
    public void testItemsBelowAFileAreIgnored() {
        List<String> found = search("*/Cache_Data/*",
                item(CACHE + "54e7_0/entry"),
                item(CACHE + "54e7_0/sub/entry"),
                item(CACHE + "54e7_0"));

        assertEquals(Arrays.asList("file:" + CACHE + "54e7_0"), found);
    }

    @Test
    public void testItemsBelowAFolderAreKept() {
        List<String> found = search("*/Cache_Data*",
                item(CACHE_FOLDER).dir(),
                item(CACHE + "index"));

        assertEquals(Arrays.asList("dir:" + CACHE_FOLDER, "file:" + CACHE + "index"), found);
    }

    @Test
    public void testFileIsPreferredOverFolderOfSamePath() {
        assertEquals(Arrays.asList("file:" + CACHE + "54e7_0"),
                search("*/Cache_Data/*", item(CACHE + "54e7_0").dir(), item(CACHE + "54e7_0").deleted()));
    }

    @Test
    public void testContainerSubitemsAreIgnored() {
        assertFalse(search("*/Cache_Data/*", item(CACHE + "index>>entry")).contains("file:" + CACHE + "index>>entry"));
    }
}
