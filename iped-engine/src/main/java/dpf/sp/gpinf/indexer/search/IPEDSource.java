/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃƒÂªncias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.IndexTask;
import dpf.sp.gpinf.indexer.util.ConfiguredFSDirectory;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.SelectImagePathWithDialog;
import dpf.sp.gpinf.indexer.util.SlowCompositeReaderWrapper;
import dpf.sp.gpinf.indexer.util.TouchSleuthkitImages;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.Item;
import iped3.IIPEDSource;
import iped3.IItem;
import iped3.IItemId;
import iped3.search.IMarcadores;
import iped3.search.IMultiMarcadores;
import iped3.util.BasicProps;

public class IPEDSource implements Closeable, IIPEDSource {

    private static Logger LOGGER = LoggerFactory.getLogger(IPEDSource.class);

    public static final String INDEX_DIR = "index"; //$NON-NLS-1$
    public static final String MODULE_DIR = "indexador"; //$NON-NLS-1$
    public static final String SLEUTH_DB = "sleuth.db"; //$NON-NLS-1$
    public static final String PREV_TEMP_INFO_PATH = "data/prevTempDir.txt"; //$NON-NLS-1$

    /**
     * workaround para JVM não coletar objeto, nesse caso Sleuthkit perde referencia
     * para FS_INFO
     */
    private static List<SleuthkitCase> tskCaseList = new ArrayList<SleuthkitCase>();

    private File casePath;
    private File moduleDir;
    private File index;

    SleuthkitCase sleuthCase;
    IndexReader reader;
    LeafReader atomicReader;
    IndexWriter iw;
    IndexSearcher searcher;
    Analyzer analyzer;

    private ExecutorService searchExecutorService;

    protected ArrayList<String> categories = new ArrayList<String>();

    private IMarcadores marcadores;
    IMultiMarcadores globalMarcadores;

    private int[] ids, docs;
    private long[] textSizes;

    protected int sourceId = -1;

    int totalItens = 0;

    private int lastId = 0;

    BitSet splitedIds = new BitSet();

    LinkedHashSet<String> keywords = new LinkedHashSet<String>();

    Set<String> extraAttributes = new HashSet<String>();

    Set<String> evidenceUUIDs = new HashSet<String>();

    boolean isFTKReport = false, isReport = false;

    public static File getTempDirInfoFile(File moduleDir) {
        return new File(moduleDir, IPEDSource.PREV_TEMP_INFO_PATH);
    }

    public static File getTempIndexDir(File moduleDir) throws IOException {
        File prevTempInfoFile = getTempDirInfoFile(moduleDir);
        String prevTemp = new String(Files.readAllBytes(prevTempInfoFile.toPath()), "UTF-8");
        return new File(prevTemp, INDEX_DIR);
    }

    public IPEDSource(File casePath) {
        this(casePath, null);
    }

    public IPEDSource(File casePath, IndexWriter iw) {

        this.casePath = casePath;
        moduleDir = new File(casePath, MODULE_DIR);
        index = new File(moduleDir, INDEX_DIR);
        this.iw = iw;

        // return if multicase
        if (casePath == null)
            return;

        if (!index.exists() && iw == null) {
            File defaultIndex = index;
            try {
                index = getTempIndexDir(moduleDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (!index.exists()) {
                throw new RuntimeException("Index not found: " + defaultIndex.getAbsolutePath()); //$NON-NLS-1$
            }
        }

        // sourceId = nextId.getAndIncrement();

        try {
            Configuration.getInstance().loadConfigurables(moduleDir.getAbsolutePath(), true);

            isFTKReport = new File(moduleDir, "data/containsFTKReport.flag").exists(); //$NON-NLS-1$
            isReport = new File(moduleDir, "data/containsReport.flag").exists(); //$NON-NLS-1$

            File sleuthFile = new File(casePath, SLEUTH_DB);
            if (sleuthFile.exists()) {
                if (SleuthkitReader.sleuthCase != null)
                    // workaroud para demora ao abrir o caso enquanto tsk_loaddb não termina
                    sleuthCase = SleuthkitReader.sleuthCase;
                else
                    sleuthCase = SleuthkitCase.openCase(sleuthFile.getAbsolutePath());

                if (!isReport)
                    updateImagePathsToAbsolute(casePath, sleuthFile);

                tskCaseList.add(sleuthCase);
            }

            AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                    .findObjects(AdvancedIPEDConfig.class).iterator().next();
            if (advancedConfig.isPreOpenImagesOnSleuth() && iw == null) {
                TouchSleuthkitImages.preOpenImagesOnSleuth(sleuthCase, advancedConfig.isOpenImagesCacheWarmUpEnabled(),
                        advancedConfig.getOpenImagesCacheWarmUpThreads());
            }

            openIndex(index, iw);

            BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
            analyzer = AppAnalyzer.get();

            populateLuceneIdToIdMap();
            invertIdToLuceneIdArray();
            populateEvidenceUUIDs();
            splitedIds = getSplitedIds();
            countTotalItems();

            File textSizesFile = new File(moduleDir, "data/texts.size"); //$NON-NLS-1$
            if (textSizesFile.exists()) {
                Object array = Util.readObject(textSizesFile.getAbsolutePath());
                if (array instanceof long[])
                    textSizes = (long[]) array;
                else if (array instanceof int[]) {
                    int i = 0;
                    textSizes = new long[((int[]) array).length];
                    for (int size : (int[]) array)
                        textSizes[i++] = size * 1000L;
                }
            } else
                textSizes = new long[lastId + 1];

            loadCategories();

            loadKeywords();

            IndexItem.loadMetadataTypes(new File(moduleDir, "conf")); //$NON-NLS-1$

            File extraAttrFile = new File(moduleDir, "data/" + IndexTask.extraAttrFilename); //$NON-NLS-1$
            if (extraAttrFile.exists()) {
                extraAttributes = (Set<String>) Util.readObject(extraAttrFile.getAbsolutePath());
                Item.getAllExtraAttributes().addAll(extraAttributes);
            }

            marcadores = new Marcadores(this, moduleDir);
            marcadores.loadState();
            globalMarcadores = new MultiMarcadores(Collections.singletonList(this));

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void populateLuceneIdToIdMap() throws IOException {

        LOGGER.info("Creating LuceneId to ID mapping..."); //$NON-NLS-1$
        ids = new int[reader.maxDoc()];

        NumericDocValues ndv = atomicReader.getNumericDocValues(IndexItem.ID);

        for (int i = 0; i < reader.maxDoc(); i++) {
            ids[i] = (int) ndv.get(i);
            if (ids[i] > lastId)
                lastId = ids[i];
        }
    }

    protected void invertIdToLuceneIdArray() {
        docs = new int[lastId + 1];
        for (int i = ids.length - 1; i >= 0; i--)
            docs[ids[i]] = i;
    }

    private void populateEvidenceUUIDs() throws IOException {
        SortedDocValues sdv = atomicReader.getSortedDocValues(BasicProps.EVIDENCE_UUID);
        if (sdv == null)
            return;
        for (int i = 0; i < sdv.getValueCount(); i++) {
            evidenceUUIDs.add(sdv.lookupOrd(i).utf8ToString());
        }
    }

    public Set<String> getEvidenceUUIDs() {
        return evidenceUUIDs;
    }

    private BitSet getSplitedIds() {
        int[] sortedIds = Arrays.copyOf(this.ids, this.ids.length);
        Arrays.sort(sortedIds);
        BitSet splitedIds = new BitSet();
        for (int i = 0; i < sortedIds.length - 1; i++)
            if (sortedIds[i] == sortedIds[i + 1])
                splitedIds.set(sortedIds[i]);

        return splitedIds;
    }

    private void countTotalItems() {
        // Não ignora tree nodes em reports
        /*
         * Bits liveDocs = MultiFields.getLiveDocs(reader); for(int i = 0; i <
         * docs.length; i++) if(docs[i] > 0 && (liveDocs == null ||
         * liveDocs.get(docs[i]))) totalItens++;
         * 
         * //inclui docId = 0 na contagem se nao for deletado if(liveDocs == null ||
         * liveDocs.get(0)) totalItens++;
         */

        // ignora tree nodes
        IPEDSearcher pesquisa = new IPEDSearcher(this, ""); //$NON-NLS-1$
        pesquisa.setNoScoring(true);
        try {
            totalItens = pesquisa.search().getLength();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCategories() throws IOException {
        Fields fields = atomicReader.fields();
        if (fields == null)
            return;
        Terms terms = fields.terms(IndexItem.CATEGORY);
        if (terms == null)
            return;
        TermsEnum termsEnum = terms.iterator();
        while (termsEnum.next() != null) {
            String cat = termsEnum.term().utf8ToString();
            categories.add(cat);
        }
    }

    private void loadKeywords() {
        ArrayList<String> words;
        try {
            words = Util.loadKeywords(moduleDir.getAbsolutePath() + "/palavras-chave.txt", "UTF-8"); //$NON-NLS-1$//$NON-NLS-2$
        } catch (IOException e) {
            words = new ArrayList<String>();
        }
        for (String word : words)
            keywords.add(word);
    }

    private void openIndex(File index, IndexWriter iw) throws IOException {
        LOGGER.info("Opening index " + index.getAbsolutePath()); //$NON-NLS-1$

        if (iw == null) {
            Directory directory = ConfiguredFSDirectory.open(index);
            reader = DirectoryReader.open(directory);
        } else {
            reader = DirectoryReader.open(iw, true, false);
        }

        // TODO get rid of deprecated SlowCompositeReaderWrapper
        atomicReader = SlowCompositeReaderWrapper.wrap(reader);

        openSearcher();

        LOGGER.info("Index opened"); //$NON-NLS-1$
    }

    protected void openSearcher() {
        AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                .findObjects(AdvancedIPEDConfig.class).iterator().next();
        if (advancedConfig.getSearchThreads() > 1) {
            searchExecutorService = Executors.newFixedThreadPool(advancedConfig.getSearchThreads());
            searcher = new IndexSearcher(reader, searchExecutorService);
        } else
            searcher = new IndexSearcher(reader);

        searcher.setSimilarity(new IndexerSimilarity());
    }

    @Override
    public void close() {
        try {
            IOUtil.closeQuietly(reader);

            if (searchExecutorService != null)
                searchExecutorService.shutdown();

            // if(sleuthCase != null)
            // sleuthCase.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public IItem getItemByLuceneID(int docID) {
        try {
            Document doc = searcher.doc(docID);
            IItem item = IndexItem.getItem(doc, moduleDir, sleuthCase, false);
            return item;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public IItem getItemByID(int id) {
        return getItemByLuceneID(docs[id]);
    }

    public void reopen() throws IOException {
        close();
        openIndex(index, iw);
    }

    public void checkImagePaths() throws IPEDException, TskCoreException {
        if (sleuthCase == null || isReport)
            return;
        Map<Long, List<String>> imgPaths = sleuthCase.getImagePaths();
        for (Long id : imgPaths.keySet()) {
            List<String> paths = imgPaths.get(id);
            for (String path : paths) {
                if (!new File(path).exists() && !path.toLowerCase().contains("physicaldrive")) //$NON-NLS-1$
                    throw new IPEDException(
                            Messages.getString("IPEDSource.ImageNotFound") + new File(path).getAbsolutePath()); //$NON-NLS-1$
            }
        }
    }

    /**
     * Substitui caminhos absolutos para imagens por relativos
     * 
     */
    public void updateImagePathsToRelative() {
        if (sleuthCase == null)
            return;
        try {
            File sleuthFile = new File(sleuthCase.getDbDirPath() + "/" + SLEUTH_DB); //$NON-NLS-1$
            Map<Long, List<String>> imgPaths = sleuthCase.getImagePaths();
            for (Long id : imgPaths.keySet()) {
                List<String> paths = imgPaths.get(id);
                ArrayList<String> newPaths = new ArrayList<String>();
                for (String path : paths) {
                    File file = new File(path);
                    if (!file.isAbsolute())
                        break;
                    String relPath = Util.getRelativePath(sleuthFile, file);
                    file = new File(relPath);
                    if (file.isAbsolute() || !new File(sleuthFile.getParentFile(), relPath).exists())
                        break;
                    else
                        newPaths.add(relPath);
                }
                if (newPaths.size() > 0)
                    sleuthCase.setImagePaths(id, newPaths);
            }
        } catch (Exception e) {
            LOGGER.error("Error converting image references to relative paths"); //$NON-NLS-1$
        }
    }

    private void updateImagePathsToAbsolute(File casePath, File sleuthFile) throws Exception {
        char letter = casePath.getAbsolutePath().charAt(0);
        boolean isWindowsNetworkShare = casePath.getAbsolutePath().startsWith("\\\\");
        Map<Long, List<String>> imgPaths = sleuthCase.getImagePaths();
        for (Long id : imgPaths.keySet()) {
            List<String> paths = imgPaths.get(id);
            ArrayList<String> newPaths = new ArrayList<String>();
            for (String path : paths) {
                if (isWindowsNetworkShare && !path.startsWith("\\") && !path.startsWith("/") && path.length() > 1
                        && path.charAt(1) != ':') {
                    String newPath = new File(casePath.getAbsolutePath() + File.separator + path).getCanonicalPath();
                    if (new File(newPath).exists())
                        newPaths.add(newPath);
                } else if ((new File(path).exists() && path.contains(File.separator))
                        || (System.getProperty("os.name").startsWith("Windows")
                                && path.toLowerCase().contains("physicaldrive"))) {
                    newPaths = null;
                    break;
                } else {
                    path = path.replace("/", File.separator).replace("\\", File.separator);
                    String newPath = letter + path.substring(1);
                    if (new File(newPath).exists())
                        newPaths.add(newPath);
                    else {
                        File baseFile = sleuthFile;
                        while ((baseFile = baseFile.getParentFile()) != null) {
                            File file = new File(path);
                            String relPath = ""; //$NON-NLS-1$
                            do {
                                relPath = File.separator + file.getName() + relPath;
                                newPath = baseFile.getAbsolutePath() + relPath;
                                file = file.getParentFile();

                            } while (file != null && !new File(newPath).exists());

                            if (new File(newPath).exists()) {
                                newPaths.add(newPath);
                                break;
                            }
                        }
                    }
                }
            }
            if (newPaths != null)
                if (newPaths.size() > 0) {
                    testCanWriteToCase(sleuthFile);
                    sleuthCase.setImagePaths(id, newPaths);
                } else if (iw == null)
                    askNewImagePath(id, paths, sleuthFile);
        }
    }

    File tmpCaseFile = null;

    private void testCanWriteToCase(File sleuthFile) throws TskCoreException, IOException {
        if (tmpCaseFile == null && (!sleuthFile.canWrite() || !IOUtil.canCreateFile(sleuthFile.getParentFile()))) {
            tmpCaseFile = File.createTempFile("sleuthkit-", ".db"); //$NON-NLS-1$ //$NON-NLS-2$
            tmpCaseFile.deleteOnExit();
            // causes "case is closed" error in some cases
            // sleuthCase.close();
            IOUtil.copiaArquivo(sleuthFile, tmpCaseFile);
            sleuthCase = SleuthkitCase.openCase(tmpCaseFile.getAbsolutePath());
            tskCaseList.add(sleuthCase);
        }
    }

    private void askNewImagePath(long imgId, List<String> paths, File sleuthFile) throws TskCoreException, IOException {
        SelectImagePathWithDialog sip = new SelectImagePathWithDialog(new File(paths.get(0)));
        File newImage = sip.askImagePathInGUI();

        ArrayList<String> newPaths = new ArrayList<String>();
        if (paths.size() == 1) {
            newPaths.add(newImage.getAbsolutePath());
        } else
            for (String path : paths) {
                String ext = path.substring(path.lastIndexOf('.'));
                String basePath = newImage.getAbsolutePath().substring(0, newImage.getAbsolutePath().lastIndexOf('.'));
                if (!new File(basePath + ext).exists())
                    throw new IOException(Messages.getString("IPEDSource.ImgFragNotFound") + basePath + ext); //$NON-NLS-1$
                newPaths.add(basePath + ext);
            }
        testCanWriteToCase(sleuthFile);
        sleuthCase.setImagePaths(imgId, newPaths);
    }

    public int getSourceId() {
        return sourceId;
    }

    public File getIndex() {
        return index;
    }

    public File getModuleDir() {
        return moduleDir;
    }

    public File getCaseDir() {
        return casePath;
    }

    public int getId(int luceneId) {
        return ids[luceneId];
    }

    public int getLuceneId(IItemId itemId) {
        return docs[itemId.getId()];
    }

    public int getLuceneId(int id) {
        return docs[id];
    }

    public int getParentId(int id) {
        try {
            Set<String> field = Collections.singleton(BasicProps.PARENTID);
            Document doc = searcher.doc(getLuceneId(id), field);
            String parent = doc.get(BasicProps.PARENTID);
            if (parent != null && !parent.isEmpty()) {
                return Integer.valueOf(parent);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public long getTextSize(int id) {
        if (id < textSizes.length)
            return textSizes[id];
        else
            // we currently save text size at the end of processing, --append enters here
            return 0;
    }

    boolean isSplited(int id) {
        return splitedIds.get(id);
    }

    public List<String> getCategories() {
        return categories;
    }

    public Set<String> getKeywords() {
        return keywords;
    }

    public Set<String> getExtraAttributes() {
        return this.extraAttributes;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public SleuthkitCase getSleuthCase() {
        return sleuthCase;
    }

    public IndexReader getReader() {
        return reader;
    }

    public LeafReader getAtomicReader() {
        return this.atomicReader;
    }

    public LeafReader getLeafReader() {
        return this.atomicReader;
    }

    public IndexSearcher getSearcher() {
        return searcher;
    }

    public IMarcadores getMarcadores() {
        return marcadores;
    }

    public IMultiMarcadores getMultiMarcadores() {
        return this.globalMarcadores;
    }

    public int getTotalItens() {
        return totalItens;
    }

    public int getLastId() {
        return lastId;
    }

    public boolean isFTKReport() {
        return isFTKReport;
    }

}
