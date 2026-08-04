package iped.engine.task.index;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexOptions;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.IndexTaskConfig;
import iped.engine.config.RAGConfig;
import iped.engine.core.Worker.STATE;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.io.CloseFilterReader;
import iped.engine.io.FragmentingReader;
import iped.engine.io.ParsingReader;
import iped.engine.rag.RAGService;
import iped.engine.task.AbstractTask;
import iped.engine.task.ParsingTask;
import iped.engine.task.SkipCommitedTask;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.util.Util;
import iped.engine.util.UIPropertyListenerProvider;
import iped.exception.IPEDException;
import iped.parsers.standard.StandardParser;
import iped.properties.ExtraProperties;
import iped.utils.IOUtil;

/**
 * Tarefa de indexação dos itens. Indexa apenas as propriedades, caso a
 * indexação do conteúdo esteja desabilitada. Reaproveita o texto dos itens caso
 * tenha sido extraído por tarefas anteriores.
 *
 * Indexa itens grandes dividindo-os em fragmentos, pois a lib de indexação
 * consome mta memória com documentos grandes.
 *
 */
public class IndexTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(IndexTask.class);

    public static final String TEXT_SIZE = "textSize"; //$NON-NLS-1$
    public static final String TEXT_SPLITTED = "textSplitted";
    public static final String FRAG_NUM = "fragNum";
    public static final String FRAG_PARENT_ID = "fragParentId";
    public static final String extraAttrFilename = "extraAttributes.dat"; //$NON-NLS-1$

    /** Stored field: unique track-ID of a fragment document (used to locate it). */
    public static final String FRAG_TRACK_ID = "fragTrackId";

    /** KNN vector field: stores the text embedding of a fragment. */
    public static final String CONTENT_EMBEDDING = "content_embedding";

    /** Stored field: stores the original fragment text so RAGService can retrieve it during search. */
    public static final String CONTENT_STORED = "content_stored";

    /** Marker field: set to "1" on fragment child documents that have an embedding vector. */
    public static final String HAS_EMBEDDING_FRAG = "hasEmbeddingFrag";

    private static final AtomicBoolean finished = new AtomicBoolean();
    private static final AtomicBoolean lastIDLoaded = new AtomicBoolean();

    /** Global RAG statistics counters for final Statistics summary report. */
    public static final java.util.concurrent.atomic.AtomicInteger totalEmbeddedDocs =
            new java.util.concurrent.atomic.AtomicInteger(0);
    public static final java.util.concurrent.atomic.AtomicInteger totalEmbeddedVectors =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private static FieldType contentField;

    public static final FieldType getContentFieldType() {
        if (contentField == null) {
            FieldType field = new FieldType();
            field.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            field.setOmitNorms(true);
            IndexTaskConfig indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);
            field.setStoreTermVectors(indexConfig.isStoreTermVectors());
            field.freeze();
            contentField = field;
        }
        return contentField;
    }

    private StandardParser autoParser;

    private IndexTaskConfig indexConfig;

    public static boolean isTreeNodeOnly(IItem item) {
        return (!item.isToAddToCase() && (item.isDir() || item.isRoot() || item.hasChildren()))
                || item.getExtraAttribute(IndexItem.TREENODE) != null;
    }

    public static void configureTreeNodeAttributes(IItem item) {
        if (item.isSubItem() && item instanceof Item) {
            ((Item) item).dispose(false);
        }
        item.setIdInDataSource(null);
        item.setInputStreamFactory(null);
        item.setExtraAttribute(IndexItem.TREENODE, "true"); //$NON-NLS-1$
        item.getCategorySet().clear();
    }

    public void process(IItem evidence) throws IOException {
        if (evidence.isQueueEnd()) {
            return;
        }

        if (SkipCommitedTask.isAlreadyCommited(evidence)) {
            evidence.setToIgnore(true);
            return;
        }

        Reader textReader = null;

        if (!evidence.isToAddToCase()) {
            if (isTreeNodeOnly(evidence)) {
                configureTreeNodeAttributes(evidence);
                textReader = new StringReader("");
            } else
                return;
        }

        stats.updateLastId(evidence.getId());

        RAGConfig ragConfig = ConfigurationManager.get().findObject(RAGConfig.class);
        boolean luceneVectorMode = ragConfig != null
                && ragConfig.isEnabled()
                && "lucene".equalsIgnoreCase(ragConfig.getVectorStoreMode());

        if (textReader == null) {
            if (indexConfig.isIndexFileContents() && (indexConfig.isIndexUnallocated()
                    || !BaseCarveTask.UNALLOCATED_MIMETYPE.equals(evidence.getMediaType()))) {
                textReader = evidence.getTextReader();
                if (textReader == null) {
                    LOGGER.warn("Null Text reader, creating a new one for {}", evidence.getPath()); //$NON-NLS-1$
                    try {
                        TikaInputStream tis = evidence.getTikaStream();
                        Metadata metadata = getMetadata(evidence);
                        final ParseContext context = getTikaContext(evidence);
                        textReader = new ParsingReader(this.autoParser, tis, metadata, context);
                        ((ParsingReader) textReader).startBackgroundParsing();

                    } catch (IOException e) {
                        LOGGER.warn("{} Error opening: {} {}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                                e.toString());
                    }
                }
            }
        }

        if (textReader == null)
            textReader = new StringReader(""); //$NON-NLS-1$

        int splitSize = indexConfig.getTextSplitSize();
        int overlapSize = indexConfig.getTextOverlapSize();
        boolean shouldEmbed = false;
        if (ragConfig != null && ragConfig.isEnabled()) {
            shouldEmbed = ragConfig.shouldEmbed(evidence);
            if (shouldEmbed) {
                splitSize = ragConfig.getChunkSize();
                overlapSize = ragConfig.getChunkOverlap();
            }
        }
        FragmentingReader fragReader = new FragmentingReader(textReader, splitSize, overlapSize);
        try {
            worker.writer.addDocuments(new DocumentsIterable(evidence, fragReader, luceneVectorMode, ragConfig, shouldEmbed));

        } catch (IOException e) {
            if (IOUtil.isDiskFull(e))
                throw new IPEDException(
                        "Not enough space for the index on " + worker.manager.getIndexTemp().getAbsolutePath()); //$NON-NLS-1$
            else
                throw e;
        } finally {
            fragReader.close();
        }

    }

    private class DocumentsIterable implements Iterable<Document> {

        private IItem item;
        private java.util.List<String> fragmentTexts;
        private java.util.List<float[]> vectors;
        private boolean parentIndexed = false;
        private int numFrags = 0;
        private final boolean luceneVectorMode;
        private final boolean shouldEmbed;
        private final int embDimension;
        private final String globalId;
        private FragmentingReader lazyReader; // for non-vector mode

        private DocumentsIterable(IItem item, FragmentingReader fragReader,
                boolean luceneVectorMode, RAGConfig ragConfig, boolean shouldEmbed) {
            this.item = item;
            this.luceneVectorMode = luceneVectorMode;
            this.shouldEmbed = shouldEmbed;

            // Resolve embedding dimensions and global ID if in vector mode
            int dim = 0;
            String gid = null;
            if (luceneVectorMode && ragConfig != null && shouldEmbed) {
                dim = ragConfig.getEmbeddingDimensions();
                gid = (String) item.getExtraAttribute(ExtraProperties.GLOBAL_ID);
            }
            this.embDimension = dim;
            this.globalId = gid;

            if (luceneVectorMode && shouldEmbed) {
                // Read all fragments eagerly ONLY for RAG-whitelisted items
                java.util.List<String> frags = new java.util.ArrayList<>();
                try {
                    String t = captureText(fragReader);
                    if (t != null && !t.trim().isEmpty()) {
                        frags.add(t);
                    }
                    while (fragReader.nextFragment()) {
                        t = captureText(fragReader);
                        if (t != null && !t.trim().isEmpty()) {
                            frags.add(t);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                this.fragmentTexts = frags;

                // Batch generate embeddings synchronously ONLY if there are non-empty text fragments
                RAGService ragService = RAGService.getInstance();
                if (!frags.isEmpty() && ragService != null && ragService.isAvailable()) {
                    try {
                        this.vectors = ragService.getEmbeddings(frags);
                        if (this.vectors != null && !this.vectors.isEmpty()) {
                            totalEmbeddedDocs.incrementAndGet();
                            totalEmbeddedVectors.addAndGet(this.vectors.size());
                        }
                        LOGGER.debug("RAG: generated {} embeddings for {}", this.vectors != null ? this.vectors.size() : 0, item.getPath());
                    } catch (Exception e) {
                        LOGGER.error("RAG: Error generating embeddings for {}", item.getPath(), e);
                    }
                }
            } else {
                this.lazyReader = fragReader;
            }
        }

        public java.util.Iterator<Document> iterator() {
            return new java.util.Iterator<Document>() {
                private int index = 0;
                private boolean hasMoreContentFrags = false;

                public boolean hasNext() {
                    try {
                        while (worker.state != STATE.RUNNING) {
                            synchronized (worker) {
                                if (worker.state == STATE.PAUSING) {
                                    worker.state = STATE.PAUSED;
                                }
                            }
                            Thread.sleep(1000);
                        }
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }
                        if (luceneVectorMode && shouldEmbed) {
                            return index < fragmentTexts.size() || !parentIndexed;
                        } else {
                            hasMoreContentFrags = (numFrags == 0 || lazyReader.nextFragment());
                            return hasMoreContentFrags || !parentIndexed;
                        }
                    } catch (InterruptedException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                public Document next() {
                    if (luceneVectorMode && shouldEmbed) {
                        if (index < fragmentTexts.size()) {
                            String fragText = fragmentTexts.get(index);
                            numFrags = index + 1;
                            if (numFrags > 1) {
                                stats.incSplits();
                                if (shouldEmbed) {
                                    LOGGER.debug("{} Splitting RAG text of {}", Thread.currentThread().getName(), item.getPath()); //$NON-NLS-1$
                                } else {
                                    LOGGER.info("{} Splitting text of {}", Thread.currentThread().getName(), item.getPath()); //$NON-NLS-1$
                                }
                            }

                            // child (content) document
                            Document doc = new Document();
                            doc.add(new IntPoint(FRAG_NUM, numFrags));
                            doc.add(new IntPoint(FRAG_PARENT_ID, item.getId()));

                            if (shouldEmbed && globalId != null) {
                                String fragTrackId = globalId + "#" + numFrags;

                                // StoredField so the text can be retrieved at search time
                                doc.add(new StoredField(CONTENT_STORED, fragText));

                                // StringField (stored) so it can be uniquely identified
                                doc.add(new StringField(FRAG_TRACK_ID, fragTrackId, Field.Store.YES));

                                // StringField (stored) containing the numeric parent ID
                                doc.add(new StringField("fragParentNumericId", String.valueOf(item.getId()), Field.Store.YES));

                                // Add pre-generated vector if available
                                if (vectors != null && index < vectors.size()) {
                                    float[] vector = vectors.get(index);
                                    if (vector != null && vector.length > 0) {
                                        doc.add(new org.apache.lucene.document.KnnVectorField(
                                                CONTENT_EMBEDDING, vector,
                                                org.apache.lucene.index.VectorSimilarityFunction.COSINE));
                                        doc.add(new StringField(HAS_EMBEDDING_FRAG, "1", Field.Store.YES));
                                    }
                                }
                            }

                            // Index the text for BM25 lexical search
                            doc.add(new Field(IndexItem.CONTENT, new StringReader(fragText),
                                    getContentFieldType()));

                            index++;
                            return doc;
                        } else {
                            if (numFrags > 1) {
                                item.setExtraAttribute(TEXT_SPLITTED, Boolean.TRUE.toString());
                            }
                            // Mark the parent item as having embeddings so it can be
                            // filtered in the IPED UI (follows the textSplitted pattern).
                            // Only set if at least one non-null vector was actually generated
                            // (mirrors the same guard used when adding KnnVectorField to children).
                            if (shouldEmbed && vectors != null
                                    && vectors.stream().anyMatch(v -> v != null && v.length > 0)) {
                                item.setExtraAttribute(ExtraProperties.HAS_EMBEDDING, Boolean.TRUE.toString());
                            }
                            long totalSize = 0;
                            for (String s : fragmentTexts) {
                                totalSize += s.length();
                            }
                            item.setExtraAttribute(TEXT_SIZE, totalSize);
                            // parent (metadata) document
                            Document doc = IndexItem.Document(item, output);
                            parentIndexed = true;
                            return doc;
                        }
                    } else {
                        // Standard lazy mode
                        if (hasMoreContentFrags) {
                            if (++numFrags > 1) {
                                stats.incSplits();
                                LOGGER.info("{} Splitting text of {}", Thread.currentThread().getName(), item.getPath()); //$NON-NLS-1$
                            }
                            Document doc = new Document();
                            doc.add(new IntPoint(FRAG_NUM, numFrags));
                            doc.add(new IntPoint(FRAG_PARENT_ID, item.getId()));
                            doc.add(new Field(IndexItem.CONTENT, new CloseFilterReader(lazyReader),
                                    getContentFieldType()));
                            return doc;
                        } else {
                            if (numFrags > 1) {
                                item.setExtraAttribute(TEXT_SPLITTED, Boolean.TRUE.toString());
                            }
                            item.setExtraAttribute(TEXT_SIZE, lazyReader.getTotalTextSize());
                            // parent (metadata) document
                            Document doc = IndexItem.Document(item, output);
                            parentIndexed = true;
                            return doc;
                        }
                    }
                }

            };
        }

        /**
         * Reads the current fragment from the FragmentingReader into a String.
         * The String is then used both as a StoredField and as the text for the
         * BM25 Field (via a StringReader), avoiding any double-read of the stream.
         */
        private String captureText(FragmentingReader reader) {
            StringBuilder sb = new StringBuilder(4096);
            char[] buf = new char[4096];
            int n;
            try {
                while ((n = reader.read(buf, 0, buf.length)) != -1) {
                    sb.append(buf, 0, n);
                }
            } catch (IOException e) {
                LOGGER.warn("Error reading fragment text for embedding.", e);
            }
            return sb.toString();
        }

    }

    private Metadata getMetadata(IItem evidence) {
        // new metadata to prevent ConcurrentModificationException while indexing
        Metadata metadata = new Metadata();
        ParsingTask.fillMetadata(evidence, metadata);
        return metadata;
    }

    private ParseContext getTikaContext(IItem evidence) {
        ParsingTask pt = new ParsingTask(evidence, this.autoParser);
        pt.setWorker(worker);
        pt.init(ConfigurationManager.get());
        ParseContext context = pt.getTikaContext();
        // this is to not create new items while indexing
        pt.setExtractEmbedded(false);
        return context;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new IndexTaskConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        
        indexConfig = configurationManager.findObject(IndexTaskConfig.class);

        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        if ((args.isAppendIndex() || args.isContinue() || args.isRestart()) && !lastIDLoaded.getAndSet(true)) {
            try (IPEDSource ipedSrc = new IPEDSource(output.getParentFile(), worker.writer)) {
                stats.setLastId(ipedSrc.getLastId());
                Item.setStartID(ipedSrc.getLastId() + 1);
            }
        }

        IndexItem.loadMetadataTypes(new File(output, "conf")); //$NON-NLS-1$
        loadExtraAttributes();

        this.autoParser = new StandardParser();

        RAGConfig ragConfig = configurationManager.findObject(RAGConfig.class);
        if (ragConfig != null && ragConfig.isEnabled()) {
            try {
                RAGService.initialize(output, ragConfig);
            } catch (Exception e) {
                LOGGER.error("RAG: Failed to initialise RAGService", e);
            }
        }

    }

    @Override
    public void finish() throws Exception {

        if (!finished.getAndSet(true)) {
            saveExtraAttributes(output);
            IndexItem.saveMetadataTypes(new File(output, "conf")); //$NON-NLS-1$
        }
    }

    public static void saveExtraAttributes(File output) throws IOException {
        File extraAttributtesFile = new File(output, "data/" + extraAttrFilename); //$NON-NLS-1$
        Set<String> extraAttr = Item.getAllExtraAttributes();
        Util.writeObject(extraAttr, extraAttributtesFile.getAbsolutePath());
        Util.fsync(extraAttributtesFile.toPath());
    }

    private void loadExtraAttributes() throws ClassNotFoundException, IOException {

        File extraAttributtesFile = new File(output, "data/" + extraAttrFilename); //$NON-NLS-1$
        if (extraAttributtesFile.exists()) {
            Set<String> extraAttributes = (Set<String>) Util.readObject(extraAttributtesFile.getAbsolutePath());
            Item.getAllExtraAttributes().addAll(extraAttributes);
        }
    }

}
