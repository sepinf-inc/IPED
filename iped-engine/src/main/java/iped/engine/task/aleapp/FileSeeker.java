package iped.engine.task.aleapp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.sqlite.SQLite3DBParser;
import iped.search.IItemSearcher;

public class FileSeeker {

    protected static final Logger logger = LoggerFactory.getLogger(FileSeeker.class);

    public static final String IPED_PATH_PREFIX = "iped://";

    private String rootPath;
    private IItemSearcher searcher;

    public String data_folder = "";

    public FileSeeker(String rootPath, IItemSearcher searcher) {
        this.rootPath = rootPath;
        this.searcher = searcher;
    }

    public Object search(String filePatternToSearch) {
        return search(filePatternToSearch, false, false);
    }

    public Object search(String filePatternToSearch, boolean returnOnFirstHit) {
        return search(filePatternToSearch, returnOnFirstHit, false);
    }

    // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/scripts/search_files.py#L23
    public Object search(String filePatternToSearch, boolean returnOnFirstHit, boolean force) {

        String query = AleappUtils.globToLuceneQuery(rootPath, filePatternToSearch);

        logger.debug("query=[{}], pattern=[{}]", query, filePatternToSearch);

        Stream<String> stream = searcher
                .search(query) //
                .stream() //
                .filter(item -> item.getPath().startsWith(rootPath)) //
                .map(item -> {
                    if ("sqlite".equals(item.getType())) {
                        try {
                            // export db file
                            Path tempDir = Files.createTempDirectory("sqlite_tmp");
                            Path tempDB = tempDir.resolve(item.getName());
                            Files.copy(item.getBufferedInputStream(), tempDB);
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

                            return tempDB.toString();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        return IPED_PATH_PREFIX + item.getPath();
                    }
                });

        if (returnOnFirstHit) {
            return stream.findFirst().orElse(null);
        } else {
            return stream.distinct().collect(Collectors.toList());
        }
    }

    // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/scripts/search_files.py#L27
    public void cleanup() {
    }
}
