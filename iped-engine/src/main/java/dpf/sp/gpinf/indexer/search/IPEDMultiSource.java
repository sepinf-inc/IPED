package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.sleuthkit.datamodel.TskCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.util.IPEDException;
import iped3.IIPEDSource;
import iped3.IItem;
import iped3.IItemId;
import iped3.search.LuceneSearchResult;

public class IPEDMultiSource extends IPEDSource {

    private static Logger LOGGER = LoggerFactory.getLogger(IPEDMultiSource.class);

    private static ArrayList<Integer> baseDocCache = new ArrayList<Integer>();

    List<IPEDSource> cases = new ArrayList<>();

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
                File path = new File(pathStr.trim());
                if (!new File(path, MODULE_DIR).exists())
                    continue;
                files.add(path);
            }

        } catch (Exception e) {
            e.printStackTrace();
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
                    if (new File(file, MODULE_DIR).exists())
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

        for (IPEDSource iCase : cases)
            for (String category : iCase.categories)
                if (!categories.contains(category))
                    categories.add(category);

        for (IPEDSource iCase : cases)
            for (String keyword : iCase.keywords)
                if (!keywords.contains(keyword))
                    keywords.add(keyword);

        // marcadores = new Marcadores(this, this.getCaseDir());
        this.globalMarcadores = new MultiMarcadores(cases);

        analyzer = AppAnalyzer.get();

        for (IPEDSource iCase : cases)
            if (iCase.isFTKReport)
                isFTKReport = true;

        LOGGER.info("Loaded " + cases.size() + " cases."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void openIndex() throws IOException {
        int i = 0;
        IndexReader[] readers = new IndexReader[cases.size()];
        for (IPEDSource iCase : cases)
            readers[i++] = iCase.reader;

        LOGGER.info("Opening MultiReader..."); //$NON-NLS-1$

        reader = new MultiReader(readers, false);

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

    final public IIPEDSource getAtomicSourceBySourceId(int sourceId) {
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

    @Override
    boolean isSplited(int id) {
        throw new RuntimeException("Forbidden call from " + this.getClass().getSimpleName()); //$NON-NLS-1$
    }

    @Override
    public long getTextSize(int id) {
        throw new RuntimeException("Forbidden call from " + this.getClass().getSimpleName()); //$NON-NLS-1$
    }

    @Override
    public int getLastId() {
        throw new RuntimeException("Forbidden call from " + this.getClass().getSimpleName()); //$NON-NLS-1$
    }

}
