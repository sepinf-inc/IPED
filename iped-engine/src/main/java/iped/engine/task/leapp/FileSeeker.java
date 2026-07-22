package iped.engine.task.leapp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.file.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;
import iped.utils.IOUtil;

public class FileSeeker {

    protected static final Logger logger = LoggerFactory.getLogger(FileSeeker.class);

    private String pathRoot;
    private IItemSearcher searcher;
    private Path exportFolder;
    
    private Map<String, IItemReader> exportedFiles = new HashMap<>();

    // Public snake_case fields: LEAPP plugins access these directly on the Python
    // side as attributes of the "seeker" object (e.g. seeker.data_folder), so the
    // names MUST match FileSeekerBase attributes. Do not rename.
    public String data_folder;
    public HashMap<String, FileInfo> file_infos = new HashMap<>();

    public static class FileInfo {
        public String source_path;
        // epoch SECONDS, mirroring Python's FileInfo which stores os.stat()
        // st_ctime/st_mtime floats (0 when unknown, like FileSeekerTar does).
        // Plugins compare/convert these as numbers, so java.util.Date must not
        // be used here.
        public double creation_date;
        public double modification_date;

        public FileInfo(String source_path, Date creationDate, Date modificationDate) {
            this.source_path = source_path;
            this.creation_date = creationDate == null ? 0 : creationDate.getTime() / 1000.0;
            this.modification_date = modificationDate == null ? 0 : modificationDate.getTime() / 1000.0;
        }
    }

    public FileSeeker(String pathRoot, Path outputFolder, IItemSearcher searcher) {
        this.pathRoot = pathRoot;
        this.searcher = searcher;
        this.exportFolder = outputFolder.toAbsolutePath().normalize().resolve(DigestUtils.md5Hex(pathRoot.getBytes()));
        this.data_folder = exportFolder.toString();
    }

    /**
     * Python-facing API, mirroring ALEAPP's FileSeekerBase:
     *
     * def search(self, filepattern, return_on_first_hit=False): '''Returns a list of paths for files/folders that
     * matched'''
     *
     * Unlike ALEAPP seekers, the matched files live inside the IPED case, so each hit is exported to the local export
     * folder and the local paths are returned. For sqlite hits, -wal and -journal companion files are exported too, so
     * the databases can be opened consistently.
     *
     * NOTE: Jep cannot map Python keyword arguments onto Java methods, so plugins must call seeker.search(pattern, True)
     * positionally; the kwarg form return_on_first_hit=True is not supported.
     *
     * https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/search_files.py#L57
     */
    public List<String> search(String filepattern) throws IOException {
        return search(filepattern, false);
    }

    public List<String> search(String filepattern, boolean returnOnFirstHit) throws IOException {

        List<IItemReader> items = searchItems(List.of(filepattern));

        if (returnOnFirstHit && !items.isEmpty()) {
            // mimics ALEAPP semantics: a list containing only the first hit
            items = items.subList(0, 1);
        }

        return exportItems(items);
    }

    /**
     * Exports the given case items to the local export folder and returns their local paths, in the same order. For
     * sqlite items, -wal and -journal companion files are exported too, so the databases can be opened consistently.
     */
    public ArrayList<String> exportItems(List<IItemReader> items) throws IOException {

        ArrayList<String> paths = new ArrayList<>();

        // ids of the items themselves: avoids exporting a -wal/-journal companion a
        // second time when the plugin's search pattern also matched it directly
        Set<Integer> itemIds = new HashSet<>();
        for (IItemReader item : items) {
            itemIds.add(item.getId());
        }

        for (IItemReader item : items) {

            paths.add(exportItemToFile(item).toString());

            if ("sqlite".equals(item.getType())) {
                for (IItemReader walOrJournal : getJournalAndWalFiles(item)) {
                    if (!itemIds.contains(walOrJournal.getId())) {
                        exportItemToFile(walOrJournal);
                    }
                }
            }
        }

        return paths;
    }

    /**
     * Java-facing search used by the task itself: returns the matched case items without exporting them.
     */
    public List<IItemReader> searchItems(List<String> globPatterns) {

        String query = "("
                + globPatterns.stream()
                    .map(glob -> LeappUtils.buildFileSearchQuery(pathRoot, glob))
                    .collect(Collectors.joining(") OR ("))
                + ")";

        logger.debug("query=[{}], patterns=[{}]", query, globPatterns);

        // The Lucene query is a high-recall pre-filter and may return extra hits (see
        // LeappUtils.globToLuceneQuery), so re-check each hit strictly: it must start
        // with the evidence root path and fnmatch at least one of the glob patterns.
        List<IItemReader> hits = searcher
                .search(query) //
                .stream() //
                .filter(item -> item.getPath().startsWith(pathRoot)) //
                .filter(item -> !item.getPath().substring(pathRoot.length()).contains(">>")) //
                .filter(item -> {
                    for (String glob : globPatterns) {
                        if (LeappUtils.matchesGlob(item, glob)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());

        // Two or more items can share the same path (e.g. an active file plus a carved
        // copy or one recovered from deletion), which would collide in the export
        // folder. Keep only the most significant item per path: active > deleted >
        // carved; ties keep the first hit.
        Map<String, IItemReader> bestItemPerPath = new LinkedHashMap<>();
        for (IItemReader item : hits) {
            bestItemPerPath.merge(item.getPath(), item,
                    (current, candidate) -> significanceRank(candidate) < significanceRank(current) ? candidate : current);
        }

        return new ArrayList<>(bestItemPerPath.values());
    }

    /**
     * Lower is better: an active file is preferred over one recovered from deletion, which is preferred over a carved one
     */
    private static int significanceRank(IItemReader item) {
        if (item.isCarved()) {
            return 2;
        }
        if (item.isDeleted()) {
            return 1;
        }
        return 0;
    }

    public Path exportItemToFile(IItemReader item) throws IOException {

        Path filePath;

        if (IOUtil.hasFile(item)) {

            filePath = IOUtil.getFile(item).toPath();

        } else {

            // we are sure that the item path starts with the evidence root path 
            // because searchItems() already filtered it, so this substring is safe
            String fileRelativePathStr = item.getPath().substring(pathRoot.length() + 1);
            filePath = exportFolder.resolve(fileRelativePathStr);

            // create the folder for the item
            Files.createDirectories(filePath.getParent());

            if (item.isDir()) {
                Files.createDirectories(filePath);
            } else {
                if (!Files.exists(filePath) || item.getLength() == null || Files.size(filePath) != item.getLength()) {

                    // copy the item to the folder
                    try (InputStream is = item.getBufferedInputStream()) {
                        Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            DateUtil.updatePathTimes(filePath, item);
        }

        filePath = filePath.toAbsolutePath().normalize();

        exportedFiles.put(filePath.toString(), item);

        // keep file_infos in sync: some plugins read seeker.file_infos[path] to recover
        // the original source path and timestamps of an exported file
        file_infos.put(filePath.toString(), new FileInfo(item.getPath(), item.getCreationDate(), item.getModDate()));

        return filePath;
    }

    public List<IItemReader> getJournalAndWalFiles(IItemReader item) {
        if (!"sqlite".equals(item.getType())) {
            return Collections.emptyList();
        }

        String basePath = item.getPath();
        String walPath = basePath + "-wal";
        String journalPath = basePath + "-journal";

        return searcher.search(BasicProps.PATH + ":(\"" + walPath + "\" \"" + journalPath + "\")")
                .stream()
                .filter(i -> i.getPath().equals(walPath) || i.getPath().equals(journalPath))
                .collect(Collectors.toList());
    }

    // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/search_files.py#L61
    public void cleanup() {
        try {
            if (Files.exists(exportFolder)) {
                PathUtils.deleteDirectory(exportFolder);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete export folder: {}", exportFolder, e);
        }
        exportedFiles.clear();
        file_infos.clear();
    }
    
    public Map<String, IItemReader> getExportedFiles() {
        return exportedFiles;
    }
    
    public String getPathRoot() {
        return pathRoot;
    }
    
    public IItemSearcher getSearcher() {
        return searcher;
    }
}
