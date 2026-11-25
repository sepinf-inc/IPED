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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;
import iped.utils.IOUtil;
import jep.PyMethod;

public class FileSeeker {

    protected static final Logger logger = LoggerFactory.getLogger(FileSeeker.class);

    // "embedded" attribute of the file entries of a ufdr report (see UfedXmlReader)
    private static final String UFED_EMBEDDED_META = ExtraProperties.UFED_META_PREFIX + "embedded";

    private String pathRoot;
    private IItemSearcher searcher;
    private Path exportFolder;
    
    private Map<String, IItemReader> exportedFiles = new HashMap<>();

    // the same items, by their path relative to the extraction root
    private Map<String, IItemReader> exportedByRelativePath = new HashMap<>();

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

    public FileSeeker(String pathRoot, Path exportFolder, IItemSearcher searcher) {
        this.pathRoot = pathRoot;
        this.searcher = searcher;
        this.exportFolder = exportFolder.toAbsolutePath().normalize();
        this.data_folder = this.exportFolder.toString();
    }

    /**
     * Python-facing API, mirroring ALEAPP's FileSeekerBase:
     *
     * def search(self, filepattern, return_on_first_hit=False, force=False): '''Returns a list of paths for
     * files/folders that matched'''
     *
     * Unlike ALEAPP seekers, the matched files live inside the IPED case, so each hit is exported to the local export
     * folder and the local paths are returned. For sqlite hits, -wal and -journal companion files are exported too, so
     * the databases can be opened consistently.
     *
     * Plugins call it positionally (seeker.search(pattern, True)) or with keyword arguments
     * (seeker.search(pattern, return_on_first_hit=True)). Jep only maps Python keyword arguments onto Java methods
     * annotated with {@link PyMethod}, and dispatches by the Python call shape (positional-only, keyword-only or both),
     * hence the three overloads below. "force" (re-extract a file already staged) is accepted and ignored: exported
     * files are always checked against the case item.
     *
     * https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/scripts/search_files.py#L372
     */
    @PyMethod(varargs = true, kwargs = true)
    public Object search(Object[] args, Map<String, Object> kwargs) throws IOException {

        Object filepattern = getArgument("filepattern", 0, args, kwargs);
        if (filepattern == null) {
            throw new IllegalArgumentException("search() missing required argument: 'filepattern'");
        }
        boolean returnOnFirstHit = isTrue(getArgument("return_on_first_hit", 1, args, kwargs));

        List<String> paths = searchAndExport(filepattern.toString(), returnOnFirstHit);

        // mimics ALEAPP semantics: with return_on_first_hit the path itself is returned (a str, not a list)
        // when there is a hit, and an empty list when there is none
        if (returnOnFirstHit && !paths.isEmpty()) {
            return paths.get(0);
        }
        return paths;
    }

    @PyMethod(varargs = true, kwargs = false)
    public Object search(Object... args) throws IOException {
        return search(args, null);
    }

    @PyMethod(varargs = false, kwargs = true)
    public Object search(Object filepattern, Map<String, Object> kwargs) throws IOException {
        return search(new Object[] { filepattern }, kwargs);
    }

    private static Object getArgument(String key, int index, Object[] args, Map<String, Object> kwargs) {
        return MapUtils.getObject(kwargs, key, ArrayUtils.get(args, index));
    }

    /** The truth value of what a plugin can reasonably pass as a flag: a bool or a number. */
    private static boolean isTrue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() != 0
                : BooleanUtils.toBoolean(Objects.toString(value, null));
    }

    /**
     * Java-facing counterpart of {@link #search(Object[], Map)}: searches the case and exports the hits (only the first
     * one when returnOnFirstHit is set), returning their local paths.
     */
    public List<String> searchAndExport(String filepattern, boolean returnOnFirstHit) throws IOException {

        List<IItemReader> items = searchItems(List.of(filepattern));

        if (returnOnFirstHit && !items.isEmpty()) {
            // only the first hit is exported
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
                .filter(item -> item.getPath().startsWith(pathRoot + "/")) //
                .filter(item -> {
                    String relativePath = item.getPath().substring(pathRoot.length() + 1);
                    // subitems of containers are not part of the device file system
                    if (relativePath.contains(">>") || isUfedEmbeddedFile(item)) {
                        return false;
                    }
                    for (String glob : globPatterns) {
                        if (LeappUtils.matchesGlob(relativePath, glob)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());

        // Two or more items can share the same path (e.g. an active file plus a carved
        // copy or one recovered from deletion), which would collide in the export
        // folder. Keep only the most significant item per path: active > deleted >
        // carved > folder; ties keep the first hit.
        Map<String, IItemReader> bestItemPerPath = new LinkedHashMap<>();
        for (IItemReader item : hits) {
            bestItemPerPath.merge(item.getPath(), item,
                    (current, candidate) -> significanceRank(candidate) < significanceRank(current) ? candidate : current);
        }

        // An item below a file cannot be staged: its path needs the file to be a folder. The known cases are
        // filtered above, this is a safety net for any other source of items nested in a file.
        Set<String> filePaths = bestItemPerPath.values().stream()
                .filter(item -> !item.isDir())
                .map(IItemReader::getPath)
                .collect(Collectors.toSet());

        return bestItemPerPath.values().stream()
                .filter(item -> !hasAncestorIn(item.getPath(), filePaths))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Whether the item is a file UFED extracted from inside another file (e.g. the entries of a browser cache file).
     * In a ufdr they are listed below the path of the file holding them, as if it was a folder, but they do not exist
     * in the device file system.
     */
    private static boolean isUfedEmbeddedFile(IItemReader item) {
        return BooleanUtils.toBoolean(item.getMetadata().get(UFED_EMBEDDED_META));
    }

    /** Whether any ancestor of the path, up to the extraction root, is one of the given paths. */
    private boolean hasAncestorIn(String path, Set<String> paths) {
        if (paths.isEmpty()) {
            return false;
        }
        String ancestor = StringUtils.substringBeforeLast(path, "/");
        while (ancestor.length() > pathRoot.length()) {
            if (paths.contains(ancestor)) {
                return true;
            }
            ancestor = StringUtils.substringBeforeLast(ancestor, "/");
        }
        return false;
    }

    /**
     * Lower is better: an active file is preferred over one recovered from deletion, which is preferred over a carved
     * one. A folder comes last: when it shares the path of a file, it is a virtual one, created to hold items found
     * inside that file (as UfedXmlReader does for the files UFED extracted from it).
     */
    private static int significanceRank(IItemReader item) {
        if (item.isDir()) {
            return 3;
        }
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
        exportedByRelativePath.put(StringUtils.removeStart(item.getPath(), pathRoot + "/"), item);

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

    // https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/scripts/search_files.py#L382
    public void cleanup() {
        try {
            if (Files.exists(exportFolder)) {
                PathUtils.deleteDirectory(exportFolder);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete export folder: {}", exportFolder, e);
        }
        exportedFiles.clear();
        exportedByRelativePath.clear();
        file_infos.clear();
    }
    
    /**
     * Returns the case item of a file staged by this seeker, given the path a plugin wrote in its results: either the
     * local path of the staged file or, as plugins using Context.get_relative_path() do, its path relative to the
     * extraction root. Returns null when the value is not the path of a staged file.
     */
    public IItemReader findExported(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        IItemReader item = exportedFiles.get(path);
        if (item == null && StringUtils.containsAny(path, '/', '\\')) {
            // a lone name is not taken as a relative path: it could be any text
            item = exportedByRelativePath.get(StringUtils.stripStart(path.replace('\\', '/'), "/"));
        }
        return item;
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
