package iped.engine.datasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.ICaseData;
import iped.data.IItem;
import iped.engine.core.Manager;
import iped.engine.data.DataSource;
import iped.engine.data.Item;
import iped.engine.io.ZIPInputStreamFactory;
import iped.properties.ExtraProperties;
import iped.utils.SeekableInputStreamFactory;

/**
 * DataSourceReader that reads items directly from ZIP files without expanding
 * them in the file system. The archive entries are enumerated and the content
 * of each entry is read on demand through a {@link SeekableInputStreamFactory}.
 *
 * Analogous to {@link FolderTreeReader}, which walks real folders from the OS.
 */
public class ZipFileReader extends DataSourceReader {

    private static final Logger logger = LoggerFactory.getLogger(ZipFileReader.class);

    private static final String ZIP_METADATA_PREFIX = "zip:";

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("zip");
    private static final Set<String> SUPPORTED_TYPES = Set.of(ArchiveStreamFactory.ZIP);

    private String evidenceName;
    private SeekableInputStreamFactory inputStreamFactory;

    /** Maps normalized entry path -> directory item already created */
    private final Map<String, IItem> dirItems = new HashMap<>();

    private IItem rootItem;

    public ZipFileReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    @Override
    public boolean isSupported(File datasource) {

        if (!datasource.isFile()) {
            return false;
        }

        // detect by extension
        String ext = FilenameUtils.getExtension(datasource.getName()).toLowerCase();
        if (SUPPORTED_EXTENSIONS.contains(ext)) {
            return true;
        }

        // detection by file signature
        try (InputStream is = new FileInputStream(datasource)) {
            String detectedType = ArchiveStreamFactory.detect(is);
            if (SUPPORTED_TYPES.contains(detectedType)) {
                return true;
            }
        } catch (ArchiveException | IOException e) {
        }

        return false;
    }

    @Override
    public void read(File file) throws Exception {
        read(file, null);
    }

    @Override
    public void read(File file, Item parent) throws Exception {

        evidenceName = getEvidenceName(file);
        if (evidenceName == null) {
            evidenceName = file.getName();
        }

        inputStreamFactory = new ZIPInputStreamFactory(file.toPath());

        if (parent == null) {
            dataSource = new DataSource(file);
            dataSource.setName(evidenceName);
        }

        if (!listOnly) {
            // creates a root item representing the archive file itself
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            rootItem = createItem(evidenceName, evidenceName, true, null, attributes.creationTime(), attributes.lastModifiedTime(), attributes.lastAccessTime());
            rootItem.setExtraAttribute(ExtraProperties.DATASOURCE_READER, this.getClass().getSimpleName());
            rootItem.setIdInDataSource("");
            if (parent != null) {
                rootItem.setParent(parent);
            } else {
                rootItem.setRoot(true);
            }
            addToQueue(rootItem);
        }

        readZipEntries(file);
    }

    /**
     * Reads ZIP entries using ZipFile, which parses the central directory at the
     * end of the file. This supports data descriptors, gets correct sizes for all
     * entries and allows random access to entry contents.
     */
    private void readZipEntries(File file) throws IOException, InterruptedException {
        try (ZipFile zip = ZipFile.builder().setFile(file).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntriesInPhysicalOrder();
            while (entries.hasMoreElements()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                ZipArchiveEntry entry = entries.nextElement();
                processEntry(entry);
            }
        }
    }

    private void processEntry(ZipArchiveEntry entry)
            throws InterruptedException {

        String path = normalize(entry.getName());
        if (path.isEmpty() || path.contains("../")) {
            // protection against malicious (zip slip) or invalid entries
            logger.warn("Entry ignored (invalid path): {} in {}", entry.getName(), dataSource);
            return;
        }

        if (listOnly) {
            caseData.incDiscoveredEvidences(1);
            if (entry.getSize() > 0) {
                caseData.incDiscoveredVolume(entry.getSize());
            }
            return;
        }

        IItem parent = getOrCreateParent(path);

        String name = path.substring(path.lastIndexOf('/') + 1);
        String evidencePath = (parent != null ? parent.getPath() : evidenceName) + '/' + name;

        IItem item = createItem(name, evidencePath, entry.isDirectory(), entry.getSize(), entry.getCreationTime(), entry.getLastModifiedTime(), entry.getLastAccessTime());
        addMetadata(item, entry);
        item.setIdInDataSource(path);


        boolean isDir = entry.isDirectory();
        if (parent != null) {
            item.setParent(parent);
        }

        addToQueue(item);

        if (isDir) {
            dirItems.put(path, item);
        }
    }

    private void addMetadata(IItem item, ZipArchiveEntry entry) {
        item.getMetadata().set(ZIP_METADATA_PREFIX + "name", entry.getName());
        item.getMetadata().set(ZIP_METADATA_PREFIX + "comment", entry.getComment());
        item.getMetadata().set(ZIP_METADATA_PREFIX + "compressedSize", Long.toString(entry.getCompressedSize()));
        if (entry.getSize() > 0) {
            item.getMetadata().set(ZIP_METADATA_PREFIX + "compressedRatio", Double.toString((double) entry.getCompressedSize() / entry.getSize()));
        }
        item.getMetadata().set(ZIP_METADATA_PREFIX + "unixMode", Long.toOctalString(entry.getUnixMode()));
    }

    /**
     * Ensures the existence of the parent directory chain of an entry. Many
     * archives do not have explicit entries for directories, or the entries come
     * out of order, so missing directories are created here.
     */
    private IItem getOrCreateParent(String path) throws InterruptedException {
        int idx = path.lastIndexOf('/');
        if (idx == -1) {
            return rootItem;
        }
        String parentPath = path.substring(0, idx);
        IItem parent = dirItems.get(parentPath);
        if (parent != null) {
            return parent;
        }
        IItem grandParent = getOrCreateParent(parentPath);
        String name = parentPath.substring(parentPath.lastIndexOf('/') + 1);
        String evidencePath = (grandParent != null ? grandParent.getPath() : evidenceName) + '/' + name;

        parent = createItem(name, evidencePath, true, null, null, null, null);
        parent.setIdInDataSource(parentPath);
        if (grandParent != null) {
            parent.setParent(grandParent);
            if (grandParent == rootItem) {
                parent.setExtraAttribute(ExtraProperties.DATASOURCE_READER, this.getClass().getSimpleName());
            }
        }
        addToQueue(parent);
        dirItems.put(parentPath, parent);
        return parent;
    }

    private IItem createItem(String name, String evidencePath, boolean isDir, Long size, FileTime creationTime, FileTime modificationTime, FileTime accessTime) {
        IItem item = new Item();
        item.setDataSource(dataSource);
        item.setInputStreamFactory(inputStreamFactory);
        item.setName(name);
        item.setPath(evidencePath);
        item.setIsDir(isDir);
        if (!isDir) {
            item.setLength(size);
        }
        if (creationTime != null) {
            item.setCreationDate(new Date(creationTime.toMillis()));
        }
        if (modificationTime != null) {
            item.setModificationDate(new Date(modificationTime.toMillis()));
        }
        if (accessTime != null) {
            item.setAccessDate(new Date(accessTime.toMillis()));
        }
        return item;
    }

    private void addToQueue(IItem item) throws InterruptedException {
        Manager.getInstance().addItemToQueue(item);
    }

    public static String normalize(String entryName) {
        // 1. Standardize to Unix slashes
        // 2. Strip ALL leading and trailing slashes instantly
        return StringUtils.strip(entryName.replace('\\', '/'), "/");
    }

    @Override
    public void close() throws IOException {
        dirItems.clear();
    }
}