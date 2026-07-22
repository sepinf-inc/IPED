package iped.engine.task.aleapp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    
    // properties used by plugins
    public String data_folder;
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

    public FileSeeker(String pathRoot, Path outputFolder, IItemSearcher searcher) {
        this.pathRoot = pathRoot;
        this.searcher = searcher;
        this.exportFolder = outputFolder.toAbsolutePath().normalize().resolve(DigestUtils.md5Hex(pathRoot.getBytes()));
        this.data_folder = exportFolder.toString();
    }

    // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/search_files.py#L57
    public List<IItemReader> search(List<String> filePatternsToSearch) {

        String query = "("
                + filePatternsToSearch.stream()
                    .map(regex -> AleappUtils.globToLuceneQuery(pathRoot, regex))
                    .collect(Collectors.joining(") OR ("))
                + ")";

        logger.debug("query=[{}], patterns=[{}]", query, filePatternsToSearch);

        // search for files in the case data using the constructed query
        // filter results to ensure they start with the rootPath and match at least one of the file patterns
        return searcher
                .search(query) //
                .stream() //
                .filter(item -> item.getPath().startsWith(pathRoot)) //
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

    public Path exportItemToFile(IItemReader item) throws IOException {

        Path filePath;

        if (IOUtil.hasFile(item)) {

            filePath = IOUtil.getFile(item).toPath();

        } else {

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
                        Files.copy(is, filePath);
                    }
                }
            }

            DateUtil.updatePathTimes(filePath, item);
        }

        filePath = filePath.toAbsolutePath().normalize();

        exportedFiles.put(filePath.toString(), item);

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

    // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/scripts/search_files.py#L27
    public void cleanup() {
        try {
            PathUtils.deleteDirectory(exportFolder);
        } catch (IOException e) {
            logger.warn("Failed to delete export folder: {}", exportFolder, e);
        }
        exportedFiles.clear();
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
