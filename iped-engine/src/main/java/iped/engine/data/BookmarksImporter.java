package iped.engine.data;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import javax.swing.KeyStroke;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemId;
import iped.engine.lucene.ConfiguredFSDirectory;
import iped.engine.lucene.DocValuesUtil;
import iped.engine.lucene.SlowCompositeReaderWrapper;
import iped.engine.task.index.IndexItem;
import iped.engine.util.SaveStateThread;
import iped.engine.util.Util;
import iped.properties.BasicProps;

/**
 * Maps the bookmarks saved in a state file of another case to the items of the
 * current case, using the trackId as the matching key.
 * <p>
 * The source case is accessed in a lightweight way: only its index doc values
 * are read. The source case is not opened as an {@link IPEDSource}, so its
 * configuration, sleuthkit database and images are not touched, and nothing is
 * written to it.
 * <p>
 * This class does not change the current case bookmarks, it just computes what
 * should be imported, so the caller can apply the result atomically.
 */
public class BookmarksImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookmarksImporter.class);

    /**
     * A bookmark of the source case and its mapping to the current case.
     */
    public static class ImportedBookmark {

        private final String name;
        private String comment;
        private Color color;
        private KeyStroke keyStroke;
        private boolean inReport;

        private int sourceItems;
        private int sourceItemsWithoutTrackId;
        private int foundSourceItems;
        private final Set<IItemId> targetIds = new HashSet<>();
        private final List<NotFoundItem> notFoundItems = new ArrayList<>();

        private ImportedBookmark(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getComment() {
            return comment;
        }

        public Color getColor() {
            return color;
        }

        public KeyStroke getKeyStroke() {
            return keyStroke;
        }

        public boolean isInReport() {
            return inReport;
        }

        /**
         * @return number of bookmarked items in the source case
         */
        public int getSourceItems() {
            return sourceItems;
        }

        /**
         * @return number of bookmarked items in the source case without trackId
         *         (e.g. cases processed by old versions), so they can't be matched
         */
        public int getSourceItemsWithoutTrackId() {
            return sourceItemsWithoutTrackId;
        }

        /**
         * @return number of bookmarked items of the source case found in the
         *         current case
         */
        public int getFoundSourceItems() {
            return foundSourceItems;
        }

        public int getNotFoundSourceItems() {
            return sourceItems - foundSourceItems;
        }

        /**
         * @return items of the current case to be added to this bookmark
         */
        public Set<IItemId> getTargetIds() {
            return targetIds;
        }

        /**
         * @return bookmarked items of the source case not found in the current case
         */
        public List<NotFoundItem> getNotFoundItems() {
            return notFoundItems;
        }
    }

    /**
     * A bookmarked item of the source case not found in the current case.
     */
    public static class NotFoundItem {

        private final File sourceCase;
        private final int id;
        private final String path;
        private final String hash;
        private final boolean withoutTrackId;

        private NotFoundItem(File sourceCase, int id, String path, String hash, boolean withoutTrackId) {
            this.sourceCase = sourceCase;
            this.id = id;
            this.path = path;
            this.hash = hash;
            this.withoutTrackId = withoutTrackId;
        }

        public File getSourceCase() {
            return sourceCase;
        }

        /**
         * @return the item id in the source case
         */
        public int getId() {
            return id;
        }

        public String getPath() {
            return path;
        }

        public String getHash() {
            return hash;
        }

        public boolean isWithoutTrackId() {
            return withoutTrackId;
        }
    }

    private final File stateFile;
    private final IPEDMultiSource targetCase;
    private final BooleanSupplier isCanceled;

    /**
     * @param stateFile
     *            the bookmarks file to import: the default state file of a case,
     *            one of its backups or a file saved by the "Save bookmarks" menu
     * @param targetCase
     *            the current case
     * @param isCanceled
     *            checked periodically to abort the import
     */
    public BookmarksImporter(File stateFile, IPEDMultiSource targetCase, BooleanSupplier isCanceled) {
        this.stateFile = stateFile;
        this.targetCase = targetCase;
        this.isCanceled = isCanceled;
    }

    /**
     * @return the source case folder if the file is in its module folder or in
     *         its backup folder, otherwise null
     */
    public static File getCaseFolderFromStateFile(File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && SaveStateThread.BKP_DIR.equals(parent.getName())) {
            parent = parent.getParentFile();
        }
        if (parent != null && IPEDSource.MODULE_DIR.equals(parent.getName())) {
            File caseFolder = parent.getParentFile();
            if (caseFolder != null && IPEDSource.checkIfIsCaseFolder(caseFolder)) {
                return caseFolder;
            }
        }
        return null;
    }

    /**
     * Reads the state file, maps its items to the current case and returns the
     * bookmarks to be imported, in the source order.
     *
     * @throws UnsupportedStateFileException
     *             if the file is not a supported bookmarks file
     * @throws SourceCaseNotFoundException
     *             if a source case folder referenced by the file can't be found
     * @throws CancellationException
     *             if canceled
     */
    public List<ImportedBookmark> computeImport() throws IOException {

        List<BitmapBookmarks> sourceStates = readStateFile();

        // resolve all source cases before processing, to fail fast
        Map<BitmapBookmarks, File> sourceCaseFolders = new LinkedHashMap<>();
        for (BitmapBookmarks state : sourceStates) {
            sourceCaseFolders.put(state, resolveSourceCaseFolder(state, sourceStates.size() == 1));
        }

        // bookmarks with the same name in different source cases are merged
        Map<String, ImportedBookmark> result = new LinkedHashMap<>();
        Map<String, List<SourceBitmap>> pendingItems = new HashMap<>();
        List<SourceIndexInfo> sourceInfos = new ArrayList<>();
        Set<String> neededTrackIds = new HashSet<>();

        for (Map.Entry<BitmapBookmarks, File> entry : sourceCaseFolders.entrySet()) {
            BitmapBookmarks state = entry.getKey();

            RoaringBitmap allBookmarked = new RoaringBitmap();
            Map<String, RoaringBitmap> bitmaps = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> bookmark : state.getBookmarkMap().entrySet()) {
                int bookmarkId = bookmark.getKey();
                String name = bookmark.getValue();
                RoaringBitmap bitmap = state.getBookmarksUnion(Collections.singleton(name));
                bitmaps.put(name, bitmap);
                allBookmarked.or(bitmap);

                ImportedBookmark imported = result.computeIfAbsent(name, ImportedBookmark::new);
                if (imported.comment == null) {
                    imported.comment = state.getBookmarkComment(bookmarkId);
                }
                if (imported.color == null) {
                    imported.color = state.getBookmarkColor(bookmarkId);
                }
                if (imported.keyStroke == null) {
                    imported.keyStroke = state.getBookmarkKeyStroke(bookmarkId);
                }
                imported.inReport |= state.isInReport(bookmarkId);
            }

            SourceIndexInfo info = readSourceTrackIds(entry.getValue(), allBookmarked);
            sourceInfos.add(info);

            for (Map.Entry<String, RoaringBitmap> bitmap : bitmaps.entrySet()) {
                pendingItems.computeIfAbsent(bitmap.getKey(), k -> new ArrayList<>())
                        .add(new SourceBitmap(bitmap.getValue(), info));
            }
            info.idToTrackIds.values().forEach(neededTrackIds::addAll);
        }

        Map<String, List<IItemId>> trackIdToTargetIds = findTargetItems(neededTrackIds);

        for (ImportedBookmark imported : result.values()) {
            for (SourceBitmap ref : pendingItems.getOrDefault(imported.name, Collections.emptyList())) {
                for (int sourceId : ref.bitmap) {
                    if (!ref.info.existingIds.contains(sourceId)) {
                        // id not present in the source index, ignore it
                        continue;
                    }
                    imported.sourceItems++;
                    List<String> trackIds = ref.info.idToTrackIds.get(sourceId);
                    if (trackIds == null) {
                        imported.sourceItemsWithoutTrackId++;
                        ref.info.notFoundIds.add(sourceId);
                        continue;
                    }
                    boolean found = false;
                    for (String trackId : trackIds) {
                        List<IItemId> targets = trackIdToTargetIds.get(trackId);
                        if (targets != null) {
                            imported.targetIds.addAll(targets);
                            found = true;
                        }
                    }
                    if (found) {
                        imported.foundSourceItems++;
                    } else {
                        ref.info.notFoundIds.add(sourceId);
                    }
                }
            }
        }

        // load path and hash of not found items to report them
        for (SourceIndexInfo info : sourceInfos) {
            readStoredFields(info);
        }
        for (ImportedBookmark imported : result.values()) {
            for (SourceBitmap ref : pendingItems.getOrDefault(imported.name, Collections.emptyList())) {
                for (int sourceId : ref.bitmap) {
                    if (ref.info.notFoundIds.contains(sourceId)) {
                        String[] fields = ref.info.storedFields.getOrDefault(sourceId, new String[2]);
                        imported.notFoundItems.add(new NotFoundItem(ref.info.caseFolder, sourceId, fields[0], fields[1],
                                !ref.info.idToTrackIds.containsKey(sourceId)));
                    }
                }
            }
        }

        return new ArrayList<>(result.values());
    }

    /**
     * Writes a text report with the bookmarked items not found in the current case.
     *
     * @return number of items written
     */
    public static int writeNotFoundReport(List<ImportedBookmark> imported, File stateFile, File reportFile)
            throws IOException {
        int count = 0;
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportFile.toPath(), StandardCharsets.UTF_8))) {
            writer.println("Bookmarks import - items not found in the current case");
            writer.println("Date: " + new Date());
            writer.println("Imported file: " + stateFile.getAbsolutePath());
            writer.println();
            for (ImportedBookmark bookmark : imported) {
                if (bookmark.notFoundItems.isEmpty()) {
                    continue;
                }
                writer.println("Bookmark: " + bookmark.name + " (" + bookmark.notFoundItems.size() + " of "
                        + bookmark.sourceItems + " items not found)");
                writer.println("id\tpath\thash\treason\tsource case");
                for (NotFoundItem item : bookmark.notFoundItems) {
                    writer.println(item.id + "\t" + nonNull(item.path) + "\t" + nonNull(item.hash) + "\t"
                            + (item.withoutTrackId ? "item without trackId" : "trackId not found") + "\t"
                            + item.sourceCase.getAbsolutePath());
                    count++;
                }
                writer.println();
            }
        }
        return count;
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }

    /**
     * Bookmarked items of one source case.
     */
    private static class SourceBitmap {
        private final RoaringBitmap bitmap;
        private final SourceIndexInfo info;

        private SourceBitmap(RoaringBitmap bitmap, SourceIndexInfo info) {
            this.bitmap = bitmap;
            this.info = info;
        }
    }

    /**
     * Information read from the index of one source case.
     */
    private static class SourceIndexInfo {
        private final File caseFolder;
        private final File indexDir;
        private final RoaringBitmap existingIds = new RoaringBitmap();
        private final Map<Integer, List<String>> idToTrackIds = new HashMap<>();
        private final RoaringBitmap notFoundIds = new RoaringBitmap();
        private final Map<Integer, String[]> storedFields = new HashMap<>();

        private SourceIndexInfo(File caseFolder, File indexDir) {
            this.caseFolder = caseFolder;
            this.indexDir = indexDir;
        }
    }

    private List<BitmapBookmarks> readStateFile() throws IOException {
        Object obj;
        try {
            obj = Util.readObject(stateFile.getAbsolutePath());
        } catch (ClassNotFoundException | ObjectStreamException e) {
            // not a serialized bookmarks file or incompatible class
            throw new UnsupportedStateFileException(stateFile, e);
        }
        if (obj instanceof BitmapBookmarks) {
            // default state file of a case or one of its backups
            return Collections.singletonList((BitmapBookmarks) obj);
        }
        if (obj instanceof MultiBitmapBookmarks) {
            // file saved by the "Save bookmarks" menu, maybe from a multicase
            List<BitmapBookmarks> list = new ArrayList<>();
            for (Object single : ((MultiBitmapBookmarks) obj).map.values()) {
                if (!(single instanceof BitmapBookmarks)) {
                    throw new UnsupportedStateFileException(stateFile, null);
                }
                list.add((BitmapBookmarks) single);
            }
            return list;
        }
        // old formats (Bookmarks / MultiBookmarks) are not supported
        throw new UnsupportedStateFileException(stateFile, null);
    }

    private File resolveSourceCaseFolder(BitmapBookmarks state, boolean singleState) {
        // If the file is inside a case folder, that case is the source, even if the
        // case was moved after the state was saved
        if (singleState) {
            File caseFolder = getCaseFolderFromStateFile(stateFile);
            if (caseFolder != null) {
                return caseFolder;
            }
        }
        // otherwise, use the case path stored in the state file
        File indexDir = state.getIndexDir();
        File caseFolder = indexDir != null && indexDir.getParentFile() != null
                ? indexDir.getParentFile().getParentFile()
                : null;
        if (caseFolder != null && IPEDSource.checkIfIsCaseFolder(caseFolder)) {
            return caseFolder;
        }
        throw new SourceCaseNotFoundException(caseFolder);
    }

    /**
     * Reads the trackIds of the given item ids from the source case index. Keeps a
     * list per id because an item can be split in many documents (text
     * fragments), each one with its own trackId.
     */
    private SourceIndexInfo readSourceTrackIds(File caseFolder, RoaringBitmap ids) throws IOException {
        File moduleDir = new File(caseFolder, IPEDSource.MODULE_DIR);
        File indexDir = new File(moduleDir, IPEDSource.INDEX_DIR);
        if (!indexDir.exists()) {
            indexDir = IPEDSource.getTempIndexDir(moduleDir);
        }
        SourceIndexInfo info = new SourceIndexInfo(caseFolder, indexDir);
        if (ids.isEmpty()) {
            return info;
        }
        LOGGER.info("Reading trackIds of {} bookmarked items from {}", ids.getCardinality(), indexDir);

        try (DirectoryReader reader = DirectoryReader.open(ConfiguredFSDirectory.open(indexDir))) {
            LeafReader leafReader = SlowCompositeReaderWrapper.wrap(reader);
            NumericDocValues idValues = leafReader.getNumericDocValues(IndexItem.ID);
            if (idValues == null) {
                return info;
            }
            // may be null in old cases without trackId
            SortedDocValues trackIdValues = leafReader.getSortedDocValues(IndexItem.TRACK_ID);
            Bits liveDocs = leafReader.getLiveDocs();
            for (int doc = 0; doc < leafReader.maxDoc(); doc++) {
                checkCanceled(doc);
                if (liveDocs != null && !liveDocs.get(doc)) {
                    continue;
                }
                Long id = DocValuesUtil.get(idValues, doc);
                if (id == null || !ids.contains(id.intValue())) {
                    continue;
                }
                info.existingIds.add(id.intValue());
                String trackId = trackIdValues != null ? DocValuesUtil.getVal(trackIdValues, doc) : null;
                if (trackId != null && !trackId.isEmpty()) {
                    info.idToTrackIds.computeIfAbsent(id.intValue(), k -> new ArrayList<>(1)).add(trackId);
                }
            }
        }
        return info;
    }

    /**
     * Loads the path and hash of the not found items from the source case index.
     */
    private void readStoredFields(SourceIndexInfo info) throws IOException {
        if (info.notFoundIds.isEmpty()) {
            return;
        }
        Set<String> fieldsToLoad = new HashSet<>(Arrays.asList(BasicProps.PATH, BasicProps.HASH));
        try (DirectoryReader reader = DirectoryReader.open(ConfiguredFSDirectory.open(info.indexDir))) {
            LeafReader leafReader = SlowCompositeReaderWrapper.wrap(reader);
            NumericDocValues idValues = leafReader.getNumericDocValues(IndexItem.ID);
            Bits liveDocs = leafReader.getLiveDocs();
            for (int doc = 0; doc < leafReader.maxDoc(); doc++) {
                checkCanceled(doc);
                if (liveDocs != null && !liveDocs.get(doc)) {
                    continue;
                }
                Long id = DocValuesUtil.get(idValues, doc);
                if (id == null || !info.notFoundIds.contains(id.intValue())
                        || info.storedFields.containsKey(id.intValue())) {
                    continue;
                }
                Document document = leafReader.document(doc, fieldsToLoad);
                info.storedFields.put(id.intValue(),
                        new String[] { document.get(BasicProps.PATH), document.get(BasicProps.HASH) });
            }
        }
    }

    /**
     * Finds the items of the current case with the given trackIds.
     */
    private Map<String, List<IItemId>> findTargetItems(Collection<String> trackIds) throws IOException {
        Map<String, List<IItemId>> result = new HashMap<>();
        if (trackIds.isEmpty()) {
            return result;
        }
        for (IPEDSource source : targetCase.getAtomicSources()) {
            LeafReader leafReader = source.getLeafReader();
            NumericDocValues idValues = leafReader.getNumericDocValues(IndexItem.ID);
            SortedDocValues trackIdLookup = leafReader.getSortedDocValues(IndexItem.TRACK_ID);
            if (idValues == null || trackIdLookup == null) {
                continue;
            }
            // map the needed trackIds to the ordinals of this index
            Map<Integer, String> ordToTrackId = new HashMap<>();
            for (String trackId : trackIds) {
                int ord = trackIdLookup.lookupTerm(new BytesRef(trackId));
                if (ord >= 0) {
                    ordToTrackId.put(ord, trackId);
                }
            }
            if (ordToTrackId.isEmpty()) {
                continue;
            }
            // use a new instance to iterate, the lookup one may be positioned
            SortedDocValues trackIdValues = leafReader.getSortedDocValues(IndexItem.TRACK_ID);
            Bits liveDocs = leafReader.getLiveDocs();
            for (int doc = 0; doc < leafReader.maxDoc(); doc++) {
                checkCanceled(doc);
                if (liveDocs != null && !liveDocs.get(doc)) {
                    continue;
                }
                int ord = DocValuesUtil.getOrd(trackIdValues, doc);
                String trackId = ord >= 0 ? ordToTrackId.get(ord) : null;
                if (trackId == null) {
                    continue;
                }
                Long id = DocValuesUtil.get(idValues, doc);
                if (id != null) {
                    result.computeIfAbsent(trackId, k -> new ArrayList<>(1))
                            .add(new ItemId(source.getSourceId(), id.intValue()));
                }
            }
        }
        return result;
    }

    private void checkCanceled(int doc) {
        if ((doc & 0xFFF) == 0 && isCanceled.getAsBoolean()) {
            throw new CancellationException();
        }
    }

    public static class UnsupportedStateFileException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UnsupportedStateFileException(File file, Throwable cause) {
            super("Unsupported bookmarks file: " + file.getAbsolutePath(), cause);
        }
    }

    public static class SourceCaseNotFoundException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final File caseFolder;

        public SourceCaseNotFoundException(File caseFolder) {
            super("Source case not found: " + caseFolder);
            this.caseFolder = caseFolder;
        }

        public File getCaseFolder() {
            return caseFolder;
        }
    }
}
