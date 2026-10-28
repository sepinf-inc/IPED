package iped.engine.task.index;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.IndexTaskConfig;
import iped.engine.config.ParsingTaskConfig;
import iped.engine.config.RAGConfig;
import iped.engine.data.Item;
import iped.engine.io.FragmentingReader;
import iped.engine.io.ParsingReader;
import iped.engine.rag.LocalEmbeddingProvider;
import iped.engine.rag.RAGService;
import iped.engine.rag.RAGTextSanitizer;
import iped.engine.task.AbstractTask;
import iped.engine.task.ParsingTask;
import iped.engine.task.SkipCommitedTask;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.util.TextCache;
import iped.exception.IPEDException;
import iped.parsers.standard.StandardParser;
import iped.properties.ExtraProperties;

/**
 * Generates the RAG embeddings of the items selected in RAGConfig.txt. The text of each item is cut into
 * fragments (chunkSize/chunkOverlap) and sanitized; the fragments of several items are sent to the embedding
 * service in one request (up to maxBatchChars), which saves round trips to a remote GPU. The fragments and
 * their vectors go to IndexTask in a temporary attribute of the item, and IndexTask writes them as child
 * documents of the item.
 */
public class EmbeddingTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingTask.class);

    /** Temporary item attribute with the {@link Fragments} of the item, read by IndexTask. */
    public static final String FRAGMENTS_ATTR = "ragFragments";

    private static final AtomicBoolean finished = new AtomicBoolean();
    private static final AtomicInteger embeddedItems = new AtomicInteger();
    private static final AtomicInteger embeddedFragments = new AtomicInteger();
    private static final AtomicInteger failedItems = new AtomicInteger();
    private static final AtomicInteger failedFragments = new AtomicInteger();
    private static final AtomicInteger truncatedItems = new AtomicInteger();

    private RAGConfig ragConfig;
    private IndexTaskConfig indexConfig;
    private ParsingTaskConfig parsingConfig;
    private RAGService ragService;
    private StandardParser autoParser;

    // items waiting for the embedding request of the current batch, in arrival order
    private final List<IItem> pending = new ArrayList<>();
    private final Set<IItem> pendingSet = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<IItem> embedded = new ArrayList<>();
    private long pendingChars;

    /** Fragments of one item: original text (keyword search), sanitized text (model) and vector. */
    public static class Fragments {

        private final List<String> texts = new ArrayList<>();
        private final List<String> sanitizedTexts = new ArrayList<>();
        private final List<float[]> vectors = new ArrayList<>();
        private final String model;
        private boolean truncated;

        Fragments(String model) {
            this.model = model;
        }

        void add(String text, String sanitizedText) {
            texts.add(text);
            sanitizedTexts.add(sanitizedText);
            vectors.add(null);
        }

        public int size() {
            return texts.size();
        }

        public String getText(int i) {
            return texts.get(i);
        }

        public String getSanitizedText(int i) {
            return sanitizedTexts.get(i);
        }

        public float[] getVector(int i) {
            return vectors.get(i);
        }

        void setVector(int i, float[] vector) {
            vectors.set(i, vector);
        }

        public String getModel() {
            return model;
        }

        long chars() {
            return sanitizedTexts.stream().mapToLong(String::length).sum();
        }
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new RAGConfig());
    }

    @Override
    public boolean isEnabled() {
        return ragConfig != null && ragConfig.isEnabled();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        ragConfig = configurationManager.findObject(RAGConfig.class);
        indexConfig = configurationManager.findObject(IndexTaskConfig.class);
        parsingConfig = configurationManager.findObject(ParsingTaskConfig.class);
        if (!isEnabled()) {
            return;
        }
        try {
            RAGService.initialize(output, ragConfig);
        } catch (Exception e) {
            LOGGER.error("RAG: Failed to initialise RAGService", e);
        }
        ragService = RAGService.getInstance();
        if ((ragService == null || !ragService.isAvailable()) && ragConfig.isAbortIfEmbeddingUnavailable()) {
            // Fail before processing: otherwise the whole case would be indexed without embeddings.
            String reason = ragService != null ? ragService.getUnavailableReason() : "service not initialized";
            throw new IPEDException("RAG is enabled but the embedding service is unavailable (" + reason
                    + "). Check embeddingEndpoint and embeddingModel in RAGConfig.txt, disable RAG,"
                    + " or set abortIfEmbeddingUnavailable = false to process without embeddings.");
        }
        autoParser = new StandardParser();
    }

    @Override
    protected boolean processQueueEnd() {
        return true;
    }

    @Override
    protected void process(IItem item) throws Exception {
        if (!pending.isEmpty() && (pendingChars >= ragConfig.getMaxBatchChars() || item.isQueueEnd())) {
            embedPending();
        }
        if (item.isQueueEnd() || !isToEmbed(item)) {
            return;
        }
        Fragments fragments = readFragments(item);
        if (fragments.size() == 0) {
            return;
        }
        item.setTempAttribute(FRAGMENTS_ATTR, fragments);
        pending.add(item);
        pendingSet.add(item);
        pendingChars += fragments.chars();
    }

    @Override
    protected void sendToNextTask(IItem item) throws Exception {
        if (!isEnabled()) {
            super.sendToNextTask(item);
            return;
        }
        List<IItem> ready = new ArrayList<>(embedded);
        embedded.clear();
        for (IItem it : ready) {
            super.sendToNextTask(it);
        }
        // items in the current batch go on after its embedding request
        if (!pendingSet.contains(item) || item.isQueueEnd()) {
            super.sendToNextTask(item);
        }
    }

    private boolean isToEmbed(IItem item) {
        return ragService != null && ragService.isAvailable() && item.isToAddToCase()
                && !SkipCommitedTask.isAlreadyCommited(item) && indexConfig.isIndexFileContents()
                && (indexConfig.isIndexUnallocated() || !BaseCarveTask.UNALLOCATED_MIMETYPE.equals(item.getMediaType()))
                && ragConfig.shouldEmbed(item);
    }

    /**
     * Cuts the text in fragments as IndexTask cuts the regular text, up to maxEmbeddingCharsPerItem. Empty
     * fragments are left out.
     */
    private Fragments readFragments(IItem item) throws IOException {
        Fragments fragments = new Fragments(RAGService.normalizeModelName(ragConfig.getEmbeddingModel()));
        Reader textReader = item.getTextReader();
        if (textReader == null) {
            textReader = parseToTextCache(item);
        }
        if (textReader == null) {
            return fragments;
        }
        int maxChars = ragConfig.getMaxEmbeddingCharsPerItem();
        try (FragmentingReader fragReader = new FragmentingReader(textReader, ragConfig.getChunkSize(),
                ragConfig.getChunkOverlap())) {
            long captured = 0;
            while (true) {
                String text = readFragment(fragReader);
                if (!text.isEmpty()) {
                    fragments.add(text, RAGTextSanitizer.sanitize(text));
                }
                captured += text.length();
                if (!fragReader.nextFragment()) {
                    break;
                }
                if (maxChars > 0 && captured >= maxChars) {
                    // the rest of the text is indexed for regular search, but not embedded
                    fragments.truncated = true;
                    break;
                }
            }
        }
        return fragments;
    }

    private static String readFragment(FragmentingReader reader) throws IOException {
        StringBuilder sb = new StringBuilder(4096);
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf, 0, buf.length)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    /**
     * Items of 100MB or more are not parsed by ParsingTask, but by IndexTask while it reads them. Their text is
     * parsed here once and kept in the item text cache (on disk when large), which IndexTask then reads.
     */
    private Reader parseToTextCache(IItem item) throws IOException {
        if (!(item instanceof Item)) {
            return null;
        }
        TextCache textCache = new TextCache();
        textCache.setEnableDiskCache(parsingConfig == null || parsingConfig.isStoreTextCacheOnDisk());
        try (TikaInputStream tis = item.getTikaStream()) {
            Metadata metadata = new Metadata();
            ParsingTask.fillMetadata(item, metadata);
            ParsingTask parsingTask = new ParsingTask(item, autoParser);
            parsingTask.setWorker(worker);
            parsingTask.init(ConfigurationManager.get());
            ParseContext context = parsingTask.getTikaContext();
            // this is to not create new items here
            parsingTask.setExtractEmbedded(false);
            try (ParsingReader reader = new ParsingReader(autoParser, tis, metadata, context)) {
                reader.startBackgroundParsing();
                char[] cbuf = new char[128 * 1024];
                int len;
                while ((len = reader.read(cbuf)) != -1 && !Thread.currentThread().isInterrupted()) {
                    textCache.write(cbuf, 0, len);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("RAG: error parsing {} for embedding: {}", item.getPath(), e.toString());
            textCache.close();
            return null;
        }
        ((Item) item).setParsedTextCache(textCache);
        return item.getTextReader();
    }

    /** One embedding request for the fragments of all pending items. */
    private void embedPending() {
        List<String> texts = new ArrayList<>();
        for (IItem item : pending) {
            Fragments fragments = (Fragments) item.getTempAttribute(FRAGMENTS_ATTR);
            for (int i = 0; i < fragments.size(); i++) {
                texts.add(fragments.getSanitizedText(i));
            }
        }
        List<float[]> vectors = null;
        try {
            vectors = ragService.getEmbeddings(texts);
        } catch (Exception e) {
            LOGGER.error("RAG: error generating embeddings for {} items", pending.size(), e);
        }
        int next = 0;
        for (IItem item : pending) {
            Fragments fragments = (Fragments) item.getTempAttribute(FRAGMENTS_ATTR);
            List<float[]> itemVectors = new ArrayList<>();
            for (int i = 0; i < fragments.size(); i++, next++) {
                float[] vector = vectors != null && next < vectors.size() ? vectors.get(next) : null;
                fragments.setVector(i, vector);
                itemVectors.add(vector);
            }
            setItemAttributes(item, fragments, itemVectors);
        }
        embedded.addAll(pending);
        pending.clear();
        pendingSet.clear();
        pendingChars = 0;
    }

    private void setItemAttributes(IItem item, Fragments fragments, List<float[]> vectors) {
        long valid = vectors.stream().filter(v -> v != null && v.length > 0).count();
        if (valid > 0) {
            // allows filtering the items with embeddings in the UI
            item.setExtraAttribute(ExtraProperties.HAS_EMBEDDING, Boolean.TRUE.toString());
            embeddedItems.incrementAndGet();
            embeddedFragments.addAndGet((int) valid);
        }
        int failed = countMissingEmbeddings(fragments.sanitizedTexts, vectors);
        if (failed > 0) {
            item.setExtraAttribute(ExtraProperties.EMBEDDING_FAILED, Boolean.TRUE.toString());
            failedItems.incrementAndGet();
            failedFragments.addAndGet(failed);
            LOGGER.warn("RAG: {} fragment(s) of {} were not embedded and are not retrievable by the assistant.",
                    failed, item.getPath());
        }
        if (fragments.truncated) {
            item.setExtraAttribute(ExtraProperties.EMBEDDING_TRUNCATED, Boolean.TRUE.toString());
            truncatedItems.incrementAndGet();
            LOGGER.warn("RAG: text of {} exceeds maxEmbeddingCharsPerItem; the excess is searchable but not embedded.",
                    item.getPath());
        }
    }

    /** Fragments with useful content that got no vector (provider error, timeout or service down). */
    static int countMissingEmbeddings(List<String> fragments, List<float[]> vectors) {
        int missing = 0;
        for (int i = 0; i < fragments.size(); i++) {
            if (!RAGTextSanitizer.hasUsefulContent(fragments.get(i))) {
                continue;
            }
            float[] vector = vectors != null && i < vectors.size() ? vectors.get(i) : null;
            if (vector == null || vector.length == 0) {
                missing++;
            }
        }
        return missing;
    }

    @Override
    public void finish() throws Exception {
        if (!isEnabled() || finished.getAndSet(true)) {
            return;
        }
        LOGGER.info("RAG Embeddings: {} vectors generated in {} documents (Model: {}).", embeddedFragments.get(),
                embeddedItems.get(), ragConfig.getEmbeddingModel());
        int batches = LocalEmbeddingProvider.totalHttpBatches.get();
        if (batches > 0) {
            LOGGER.info("RAG HTTP Batching: {} requests sent, avg batch payload size: {} chars.", batches,
                    LocalEmbeddingProvider.totalBatchChars.get() / batches);
        }
        if (failedItems.get() > 0) {
            LOGGER.warn("RAG: {} items have {} fragments without embedding, not retrievable by the AI assistant."
                    + " Filter by '{}' to list them.", failedItems.get(), failedFragments.get(),
                    ExtraProperties.EMBEDDING_FAILED);
        }
        int cutTexts = LocalEmbeddingProvider.totalTruncatedTexts.get();
        if (cutTexts > 0) {
            LOGGER.warn("RAG: {} fragments exceeded the context of the embedding model: their vector covers only"
                    + " the start of the fragment. Reduce chunkSize in RAGConfig.txt.", cutTexts);
        }
        if (truncatedItems.get() > 0) {
            LOGGER.warn("RAG: {} items exceed maxEmbeddingCharsPerItem; the excess text is searchable but not embedded."
                    + " Filter by '{}' to list them.", truncatedItems.get(), ExtraProperties.EMBEDDING_TRUNCATED);
        }
    }
}
