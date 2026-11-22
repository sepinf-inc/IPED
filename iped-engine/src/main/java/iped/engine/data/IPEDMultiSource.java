package iped.engine.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.sleuthkit.datamodel.TskCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IIPEDSource;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.lucene.SlowCompositeReaderWrapper;
import iped.engine.lucene.analysis.AppAnalyzer;
import iped.engine.search.LuceneSearchResult;
import iped.exception.IPEDException;

public class IPEDMultiSource extends IPEDSource {

    private static Logger LOGGER = LoggerFactory.getLogger(IPEDMultiSource.class);

    private static ArrayList<Integer> baseDocCache = new ArrayList<Integer>();

    List<IPEDSource> cases = new ArrayList<>();

    public IPEDMultiSource(IIPEDSource singleCase) {
        super(singleCase.getCaseDir());
        this.cases.add((IPEDSource) singleCase);
        init();
    }

    public IPEDMultiSource(List<IIPEDSource> sources) {
        super(null);
        for (IIPEDSource src : sources)
            this.cases.add((IPEDSource) src);
        init();
    }

    public IPEDMultiSource(File file) {

        super(null);

        List<File> files;
        if (file.isDirectory())
            files = searchCasesinFolder(file);
        else
            files = loadCasesFromTxtFile(file);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        ArrayList<Future<IPEDSource>> futures = new ArrayList<>();
        for (final File src : files) {
            Callable<IPEDSource> openCase = new Callable<IPEDSource>() {
                public IPEDSource call() {
                    LOGGER.info("Loading " + src.getAbsolutePath()); //$NON-NLS-1$
                    return new IPEDSource(src);
                }
            };
            futures.add(executor.submit(openCase));
        }
        for (Future<IPEDSource> f : futures)
            try {
                cases.add(f.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        executor.shutdown();

        init();

    }

    private List<File> loadCasesFromTxtFile(File file) {

        ArrayList<File> files = new ArrayList<File>();
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            // BOM test
            if (bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF)
                bytes[0] = bytes[1] = bytes[2] = 0;

            String content = new String(bytes, "UTF-8"); //$NON-NLS-1$
            for (String pathStr : content.split("\n")) { //$NON-NLS-1$
                pathStr = pathStr.trim();
                if (pathStr.isEmpty() || pathStr.startsWith("#")) {
                    continue;
                }
                File path = new File(pathStr);
                if (!checkIfIsCaseFolder(path)) {
                    throw new IllegalArgumentException("Invalid case path: " + path.getAbsolutePath());
                }
                files.add(path);
            }

        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
        return files;
    }

    private List<File> searchCasesinFolder(File folder) {
        LOGGER.info("Searching cases in " + folder.getPath()); //$NON-NLS-1$
        ArrayList<File> files = new ArrayList<File>();
        File[] subFiles = folder.listFiles();
        if (subFiles != null)
            for (File file : subFiles) {
                if (file.isDirectory()) {
                    if (checkIfIsCaseFolder(file))
                        files.add(file);
                    else
                        files.addAll(searchCasesinFolder(file));
                }
            }
        return files;
    }

    public void init() {

        int i = 0;
        for (IPEDSource iCase : cases)
            iCase.sourceId = i++;

        try {
            openIndex();

        } catch (IOException e) {
            e.printStackTrace();
        }

        for (IPEDSource iCase : cases)
            totalItens += iCase.totalItens;

        for (IIPEDSource iCase : cases)
            baseDocCache.add(getBaseLuceneId(iCase));

        loadCategories();

        for (IPEDSource iCase : cases)
            for (String keyword : iCase.keywords)
                if (!keywords.contains(keyword))
                    keywords.add(keyword);

        // marcadores = new Bookmarks(this, this.getCaseDir());
        this.multiBookmarks = new MultiBitmapBookmarks(cases);

        analyzer = AppAnalyzer.get();

        LOGGER.info("Loaded " + cases.size() + " cases."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void loadCategories() {
        for (IPEDSource iCase : cases) {
            for (String category : iCase.leafCategories) {
                if (!leafCategories.contains(category)) {
                    leafCategories.add(category);
                }
            }
            for (String ancestral : iCase.descendantsCategories.keySet()) {
                Set<String> caseDescendants = iCase.descendantsCategories.get(ancestral);
                Set<String> multiDescendants = descendantsCategories.get(ancestral);
                if (multiDescendants != null) {
                    multiDescendants.addAll(caseDescendants);
                } else {
                    descendantsCategories.put(ancestral, new HashSet<String>(caseDescendants));
                }
            }
        }
        loadCategoryTree();
    }

    private void openIndex() throws IOException {
        int i = 0;
        IndexReader[] readers = new IndexReader[cases.size()];
        for (IPEDSource iCase : cases)
            readers[i++] = iCase.reader;

        LOGGER.info("Opening MultiReader..."); //$NON-NLS-1$

        reader = new MultiReader(readers, false);

        // TODO get rid of deprecated SlowCompositeReaderWrapper
        atomicReader = SlowCompositeReaderWrapper.wrap(reader);

        LOGGER.info("MultiReader opened"); //$NON-NLS-1$

        openSearcher();

    }

    @Override
    public void reopen() throws IOException {
        reader.close();
        openIndex();
    }

    @Override
    public void close() {
        super.close();

        for (IIPEDSource iCase : cases)
            iCase.close();
    }

    @Override
    public void checkImagePaths() throws IPEDException, TskCoreException {
        for (IPEDSource iCase : cases)
            iCase.checkImagePaths();
    }

    final public IIPEDSource getAtomicSource(int luceneId) {
        int maxDoc = 0;
        for (IPEDSource iCase : cases) {
            maxDoc += iCase.reader.maxDoc();
            if (luceneId < maxDoc)
                return iCase;
        }
        return null;
    }

    final public IPEDSource getAtomicSourceBySourceId(int sourceId) {
        return cases.get(sourceId);
    }

    final public List<IPEDSource> getAtomicSources() {
        return this.cases;
    }

    public final int getBaseLuceneId(IIPEDSource atomicCase) {
        int maxDoc = 0;
        for (IPEDSource iCase : cases) {
            if (atomicCase == iCase)
                return maxDoc;
            maxDoc += iCase.reader.maxDoc();
        }
        return maxDoc;
    }

    private LuceneSearchResult rebaseLuceneIds(LuceneSearchResult resultsFromAtomicCase, IIPEDSource atomicCase) {
        LuceneSearchResult result = resultsFromAtomicCase.clone();
        int baseDoc = getBaseLuceneId(atomicCase);
        int[] docs = result.getLuceneIds();
        for (int i = 0; i < docs.length; i++)
            docs[i] += baseDoc;
        return result;
    }

    @Override
    final public IItem getItemByID(int id) {
        throw new RuntimeException("Use getItemByItemId() from " + this.getClass().getSimpleName()); //$NON-NLS-1$
    }

    final public IItem getItemByItemId(IItemId item) {
        return getAtomicSourceBySourceId(item.getSourceId()).getItemByID(item.getId());
    }

    @Override
    final public IItem getItemByLuceneID(int luceneId) {
        IIPEDSource atomicCase = getAtomicSource(luceneId);
        luceneId -= getBaseLuceneId(atomicCase);
        return atomicCase.getItemByLuceneID(luceneId);
    }

    @Override
    final public int getId(int luceneId) {
        throw new RuntimeException("Use getItemId() from " + this.getClass().getSimpleName()); //$NON-NLS-1$
    }

    final public IItemId getItemId(int luceneId) {
        IIPEDSource atomicSource = getAtomicSource(luceneId);
        int sourceId = atomicSource.getSourceId();
        int baseDoc = getBaseLuceneId(atomicSource);
        int id = atomicSource.getId(luceneId - baseDoc);
        return new ItemId(sourceId, id);
    }

    final public int getLuceneId(int id) {
        throw new RuntimeException("Use getLuceneId(ItemId) from " + this.getClass().getSimpleName()); //$NON-NLS-1$
    }

    final public int getLuceneId(IItemId id) {
        int sourceid = id.getSourceId();
        IIPEDSource atomicCase = getAtomicSourceBySourceId(sourceid);
        int baseDoc = baseDocCache.get(sourceid);
        return atomicCase.getLuceneId(id.getId()) + baseDoc;
    }
    
    @SuppressWarnings("resource")
    @Override
    public IntStream getLuceneIdStream() {
        IntStream is = null;
        for (int i = 0; i < cases.size(); i++) {
            IntStream next = cases.get(i).getLuceneIdStream();
            int baseId = getBaseLuceneId(cases.get(i));
            next = next.map(docId -> docId + baseId);
            if (is == null) {
                is = next;
            } else {
                is = IntStream.concat(is, next);
            }
        }
        return is;
    }

    @Override
    public int getLastId() {
        throw new RuntimeException("Forbidden call from " + this.getClass().getSimpleName()); //$NON-NLS-1$
    }

    @Override
    public Set<String> getEvidenceUUIDs() {
        if (evidenceUUIDs.size() <= 0) {
            for (Iterator iterator = cases.iterator(); iterator.hasNext();) {
                IPEDSource curcase = (IPEDSource) iterator.next();
                evidenceUUIDs.addAll(curcase.getEvidenceUUIDs());
            }
        }
        return super.getEvidenceUUIDs();
    }

}
