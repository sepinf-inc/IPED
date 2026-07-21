package iped.engine.task.aleapp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.sqlite.SQLite3DBParser;
import iped.search.IItemSearcher;
import iped.utils.IOUtil;

public class FileSeeker {

    protected static final Logger logger = LoggerFactory.getLogger(FileSeeker.class);

    private static final String IPED_PATH_PREFIX = "iped-" + RandomStringUtils.randomAlphanumeric(15) + ":";

    private String rootPath;
    private IItemSearcher searcher;
    private final List<Path> tempDirs = new ArrayList<>();

    public String data_folder = "";

    // properties used by plugins
    public HashMap<String, FileInfo> file_infos = new HashMap<>();

    public static class FileInfo {
        public String source_path;
        public Date creation_date;
        public Date modification_date;

        public FileInfo(String source_path, Date creation_date, Date modification_date) {
            this.source_path = source_path;
            this.creation_date = creation_date;
            this.modification_date = modification_date;
        }
    }

    public FileSeeker(String rootPath, IItemSearcher searcher) {
        this.rootPath = rootPath;
        this.searcher = searcher;
    }

    // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/search_files.py#L57
    public List<IItemReader> search(List<String> filePatternsToSearch) {

        String query = "("
                + filePatternsToSearch.stream()
                    .map(regex -> AleappUtils.globToLuceneQuery(rootPath, regex))
                    .collect(Collectors.joining(") OR ("))
                + ")";

        logger.debug("query=[{}], patterns=[{}]", query, filePatternsToSearch);

        return searcher
                .search(query) //
                .stream() //
                .filter(item -> item.getPath().startsWith(rootPath)) //
                .filter(item -> {
                    for (String filePattern : filePatternsToSearch) {
                        if (AleappUtils.checkLucenePath(item, filePattern)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    public String convertItemPathToPlugin(IItemReader item) {
        String ipedPath;
        if ("sqlite".equals(item.getType())) {
            try {
                // export db file
                Path tempDir = Files.createTempDirectory("sqlite_tmp");
                tempDirs.add(tempDir);
                Path tempDB = tempDir.resolve(item.getName());
                try (InputStream is = item.getBufferedInputStream()) {
                    Files.copy(is, tempDB);
                }
                tempDB.toFile().deleteOnExit();

                // export .db-wal and .db-journal files
                TemporaryResources tmp = new TemporaryResources();
                ParseContext context = new ParseContext();
                context.set(IItemSearcher.class, searcher);
                context.set(IItemReader.class, item);
                File walLogFile = SQLite3DBParser.exportWalLog(tempDB.toFile(), context, tmp);
                if (walLogFile != null) {
                    walLogFile.deleteOnExit();
                }
                File journalFile = SQLite3DBParser.exportRollbackJournal(tempDB.toFile(), context, tmp);
                if (journalFile != null) {
                    journalFile.deleteOnExit();
                }

                LeappContext.get().getTranslatedPaths().put(tempDB.toString(), item.getPath());

                ipedPath = tempDB.toString();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            ipedPath = toIPEDPath(item);
        }

        // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/search_files.py#L130
        file_infos.put(ipedPath, new FileInfo(item.getPath(), item.getCreationDate(), item.getModDate()));

        return ipedPath;
    }

    // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/scripts/search_files.py#L27
    public void cleanup() {
        for (Path dir : tempDirs) {
            try {
                IOUtil.deleteDirectory(dir.toFile(), false);
            } catch (IOException e) {
                logger.warn("Failed to clean up temp dir: {}", dir, e);
            }
        }
        tempDirs.clear();
    }

    public static boolean isIPEDPath(String path) {
        return path.startsWith(IPED_PATH_PREFIX);
    }

    public static String toIPEDPath(IItemReader item) {
        return IPED_PATH_PREFIX + item.getPath();
    }

    public static String getItemPath(String ipedPath) {
        return StringUtils.removeStart(ipedPath, IPED_PATH_PREFIX);
    }

}
