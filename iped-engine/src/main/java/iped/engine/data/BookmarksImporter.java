package iped.engine.data;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import javax.swing.KeyStroke;

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
        Map<String, List<IntBitmapRef>> pendingItems = new HashMap<>();
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

            Map<Integer, List<String>> idToTrackIds = readSourceTrackIds(entry.getValue(), allBookmarked);

            for (Map.Entry<String, RoaringBitmap> bitmap : bitmaps.entrySet()) {
                pendingItems.computeIfAbsent(bitmap.getKey(), k -> new ArrayList<>())
                        .add(new IntBitmapRef(bitmap.getValue(), idToTrackIds));
            }
            idToTrackIds.values().forEach(neededTrackIds::addAll);
        }

        Map<String, List<IItemId>> trackIdToTargetIds = findTargetItems(neededTrackIds);

        for (ImportedBookmark imported : result.values()) {
            for (IntBitmapRef ref : pendingItems.getOrDefault(imported.name, Collections.emptyList())) {
                for (int sourceId : ref.bitmap) {
                    imported.sourceItems++;
                    List<String> trackIds = ref.idToTrackIds.get(sourceId);
                    if (trackIds == null) {
                        imported.sourceItemsWithoutTrackId++;
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
                    }
                }
            }
        }

        return new ArrayList<>(result.values());
    }

    private static class IntBitmapRef {
        private final RoaringBitmap bitmap;
        private final Map<Integer, List<String>> idToTrackIds;

        private IntBitmapRef(RoaringBitmap bitmap, Map<Integer, List<String>> idToTrackIds) {
            this.bitmap = bitmap;
            this.idToTrackIds = idToTrackIds;
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
     * Reads the trackIds of the given item ids from the source case index.
     * Returns a list per id because an item can be split in many documents (text
     * fragments), each one with its own trackId.
     */
    private Map<Integer, List<String>> readSourceTrackIds(File caseFolder, RoaringBitmap ids) throws IOException {
        Map<Integer, List<String>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        File moduleDir = new File(caseFolder, IPEDSource.MODULE_DIR);
        File indexDir = new File(moduleDir, IPEDSource.INDEX_DIR);
        if (!indexDir.exists()) {
            indexDir = IPEDSource.getTempIndexDir(moduleDir);
        }
        LOGGER.info("Reading trackIds of {} bookmarked items from {}", ids.getCardinality(), indexDir);

        try (DirectoryReader reader = DirectoryReader.open(ConfiguredFSDirectory.open(indexDir))) {
            LeafReader leafReader = SlowCompositeReaderWrapper.wrap(reader);
            NumericDocValues idValues = leafReader.getNumericDocValues(IndexItem.ID);
            SortedDocValues trackIdValues = leafReader.getSortedDocValues(IndexItem.TRACK_ID);
            if (idValues == null || trackIdValues == null) {
                // old case without trackId
                return result;
            }
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
                String trackId = DocValuesUtil.getVal(trackIdValues, doc);
                if (trackId != null && !trackId.isEmpty()) {
                    result.computeIfAbsent(id.intValue(), k -> new ArrayList<>(1)).add(trackId);
                }
            }
        }
        return result;
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
