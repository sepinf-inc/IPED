package iped.engine.task.index;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.VectorSimilarityFunction;
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
import iped.engine.core.Worker.STATE;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.io.CloseFilterReader;
import iped.engine.io.FragmentingReader;
import iped.engine.io.ParsingReader;
import iped.engine.task.AbstractTask;
import iped.engine.task.ParsingTask;
import iped.engine.task.SkipCommitedTask;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.util.Util;
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

    /** Stored field of a RAG fragment document: unique track-ID ("globalId#n") of the fragment. */
    public static final String FRAG_TRACK_ID = "fragTrackId";

    /** Stored field of a RAG fragment document: numeric ID of its item. */
    public static final String FRAG_PARENT_NUMERIC_ID = "fragParentNumericId";

    /** KNN vector field of a RAG fragment document: the text embedding of the fragment. */
    public static final String CONTENT_EMBEDDING = "content_embedding";

    /** Stored field of a RAG fragment document: the sanitized fragment text sent to the model. */
    public static final String CONTENT_STORED = "content_stored";

    /** Indexed field of a RAG fragment document: the original fragment text, for keyword search. */
    public static final String RAG_CONTENT = "ragContent";

    /** Marker field set to "1" on RAG fragment documents. */
    public static final String HAS_EMBEDDING_FRAG = "hasEmbeddingFrag";

    /** Indexed field: embedding model of the fragment vector, checked when the case is analyzed. */
    public static final String EMBEDDING_MODEL = "embeddingModel";

    /** Fields of RAG fragment documents, which are not item properties. */
    public static final Set<String> RAG_FRAGMENT_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            FRAG_TRACK_ID, FRAG_PARENT_NUMERIC_ID, CONTENT_EMBEDDING, CONTENT_STORED, RAG_CONTENT, HAS_EMBEDDING_FRAG,
            EMBEDDING_MODEL)));

    private static final AtomicBoolean finished = new AtomicBoolean();
    private static final AtomicBoolean lastIDLoaded = new AtomicBoolean();

    private static FieldType contentField;

    /** As the regular content, without term vectors: RAG keyword search does not use them. */
    private static final FieldType RAG_CONTENT_FIELD = ragContentFieldType();

    private static FieldType ragContentFieldType() {
        FieldType field = new FieldType();
        field.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        field.setOmitNorms(true);
        field.freeze();
        return field;
    }

    private static final FieldType getContentFieldType() {
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

        FragmentingReader fragReader = new FragmentingReader(textReader, indexConfig.getTextSplitSize(),
                indexConfig.getTextOverlapSize());
        try {
            Iterable<Document> documents = new DocumentsIterable(evidence, fragReader);
            List<Document> ragDocuments = ragDocuments(evidence,
                    (EmbeddingTask.Fragments) evidence.getTempAttribute(EmbeddingTask.FRAGMENTS_ATTR));
            if (!ragDocuments.isEmpty()) {
                stats.addRagFragments(ragDocuments.size());
                // children before the parent document, in the same block
                Iterable<Document> itemDocuments = documents;
                documents = () -> new ConcatIterator(ragDocuments.iterator(), itemDocuments.iterator());
            }
            worker.writer.addDocuments(documents);
            evidence.setTempAttribute(EmbeddingTask.FRAGMENTS_ATTR, null);

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
        private FragmentingReader fragReader;
        private boolean hasMoreContentFrags, parentIndexed = false;
        private int numFrags = 0;

        private DocumentsIterable(IItem item, FragmentingReader fragReader) {
            this.item = item;
            this.fragReader = fragReader;
        }

        public Iterator<Document> iterator() {
            return new Iterator<Document>() {

                public boolean hasNext() {
                    try {
                        while (worker.state != STATE.RUNNING) {
                            Thread.sleep(1000);
                        }
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }
                        hasMoreContentFrags = (numFrags == 0 || fragReader.nextFragment());
                        return hasMoreContentFrags || !parentIndexed;

                    } catch (InterruptedException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                public Document next() {
                    if (hasMoreContentFrags) {
                        if (++numFrags > 1) {
                            stats.incSplits();
                            LOGGER.info("{} Splitting text of {}", Thread.currentThread().getName(), item.getPath()); //$NON-NLS-1$
                        }
                        // child (content) document
                        Document doc = new Document();
                        doc.add(new IntPoint(FRAG_NUM, numFrags));
                        doc.add(new IntPoint(FRAG_PARENT_ID, item.getId()));
                        doc.add(new Field(IndexItem.CONTENT, new CloseFilterReader(fragReader), getContentFieldType()));
                        return doc;
                    } else {
                        if (numFrags > 1) {
                            item.setExtraAttribute(TEXT_SPLITTED, Boolean.TRUE.toString());
                        }
                        item.setExtraAttribute(TEXT_SIZE, fragReader.getTotalTextSize());
                        // parent (metadata) document
                        Document doc = IndexItem.Document(item, output);
                        parentIndexed = true;
                        return doc;
                    }
                }
                
            };
        }

    }

    /**
     * One child document per embedded fragment, with the vector, the sanitized text sent to the model
     * and the original text for keyword search. They have no regular content field, so the regular
     * searches do not see them.
     */
    static List<Document> ragDocuments(IItem item, EmbeddingTask.Fragments fragments) {
        String globalId = (String) item.getExtraAttribute(ExtraProperties.GLOBAL_ID);
        if (fragments == null || globalId == null) {
            return Collections.emptyList();
        }
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            float[] vector = fragments.getVector(i);
            if (vector == null || vector.length == 0) {
                continue;
            }
            Document doc = new Document();
            doc.add(new StringField(FRAG_TRACK_ID, globalId + "#" + (i + 1), Field.Store.YES));
            doc.add(new StringField(FRAG_PARENT_NUMERIC_ID, String.valueOf(item.getId()), Field.Store.YES));
            doc.add(new StoredField(CONTENT_STORED, fragments.getSanitizedText(i)));
            doc.add(new Field(RAG_CONTENT, new StringReader(fragments.getText(i)), RAG_CONTENT_FIELD));
            doc.add(new KnnVectorField(CONTENT_EMBEDDING, vector, VectorSimilarityFunction.COSINE));
            doc.add(new StringField(HAS_EMBEDDING_FRAG, "1", Field.Store.YES));
            doc.add(new StringField(EMBEDDING_MODEL, fragments.getModel(), Field.Store.NO));
            docs.add(doc);
        }
        return docs;
    }

    private static class ConcatIterator implements Iterator<Document> {

        private final Iterator<Document> first, second;

        private ConcatIterator(Iterator<Document> first, Iterator<Document> second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean hasNext() {
            return first.hasNext() || second.hasNext();
        }

        @Override
        public Document next() {
            return first.hasNext() ? first.next() : second.next();
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
