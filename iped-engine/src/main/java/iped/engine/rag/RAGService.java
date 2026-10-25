package iped.engine.rag;

/**
 * Core engine service for native IPED RAG (Retrieval-Augmented Generation),
 * hybrid search (BM25 + KNN RRF), embedding caching, and multi-turn chat management.
 *
 * @author Rui Sant'Ana Junior
 */
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;

import iped.engine.config.RAGConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RAGService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RAGService.class);
    private static final org.apache.logging.log4j.Level MSG = org.apache.logging.log4j.Level.getLevel("MSG");
    private static final org.apache.logging.log4j.Logger LOG4J_LOGGER = org.apache.logging.log4j.LogManager.getLogger(RAGService.class);
    private static RAGService instance;

    private final File caseDir;
    private final RAGConfig config;
    private final EmbeddingProvider embeddingProvider;
    private final LLMProvider llmProvider;
    private volatile boolean available;
    /** embeddingDimensions set in RAGConfig.txt, instead of detected from the model. */
    private final boolean dimensionsConfigured;
    /** Set when the case index was built with another embedding model; not cleared by a retry. */
    private volatile String modelMismatch;
    private long lastHealthCheckMillis;
    /** Minimum interval between checks of an embedding service that was down. */
    static final long HEALTH_RETRY_MILLIS = 10_000;
    private String unavailableReason;

    /**
     * Portuguese and English stop words plus question words, accent-free and lower case. They are
     * removed from the lexical query: otherwise words like "qual", "de" or "the" count towards the
     * required share of terms, match almost any fragment and push relevant ones out of the context.
     * Words naming the kind of item asked for ("há mensagem sobre...", "any email about...") are
     * removed too: they say where to look, not what, and match the labels IPED writes in rendered
     * chats and emails ("Mensagem apagada recuperada") instead of the subject.
     */
    private static final java.util.Set<String> LEXICAL_STOP_WORDS = buildLexicalStopWords();

    /**
     * Share of the lexical query terms, without stop words, a fragment must contain. A question that
     * compares two documents has about half of its terms in each one, so 40% left one of them out.
     */
    static final double LEXICAL_MIN_SHOULD_MATCH = 0.3;

    private static java.util.Set<String> buildLexicalStopWords() {
        java.util.Set<String> words = new java.util.HashSet<>();
        for (Object w : org.apache.lucene.analysis.pt.PortugueseAnalyzer.getDefaultStopSet()) {
            words.add(foldAccents(new String((char[]) w)));
        }
        for (Object w : org.apache.lucene.analysis.en.EnglishAnalyzer.ENGLISH_STOP_WORDS_SET) {
            words.add(foldAccents(new String((char[]) w)));
        }
        words.addAll(java.util.Arrays.asList("qual", "quais", "quem", "onde", "quando", "como", "quanto",
                "quanta", "quantos", "quantas", "porque", "sobre", "what", "which", "who", "whom", "whose", "when",
                "where", "why", "how", "does", "did", "do", "is", "are", "was", "were", "about"));
        words.addAll(java.util.Arrays.asList("mensagem", "mensagens", "conversa", "conversas", "chat", "chats",
                "email", "emails", "e-mail", "e-mails", "arquivo", "arquivos", "documento", "documentos", "message",
                "messages", "conversation", "conversations", "file", "files", "document", "documents", "existe",
                "existem", "houve", "algum", "alguma", "alguns", "algumas", "any"));
        return java.util.Collections.unmodifiableSet(words);
    }

    private static String foldAccents(String word) {
        return java.text.Normalizer.normalize(word.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }

    /** Lexical query terms without stop words, in the original spelling and order. */
    static String removeStopWords(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder terms = new StringBuilder();
        for (String token : text.trim().split("\\s+")) {
            if (!token.isEmpty() && !LEXICAL_STOP_WORDS.contains(foldAccents(token))) {
                terms.append(terms.length() > 0 ? " " : "").append(token);
            }
        }
        return terms.toString();
    }

    /** Fragments averaged to represent one item in the similarity search (~3.5M chars). */
    static final int MAX_AVERAGED_FRAGMENTS = 1000;
    private Connection dbConnection;

    /** Lucene IndexSearcher injected by the SearchApp when a case is opened (lucene mode only). */
    private volatile IndexSearcher luceneSearcher;

    /** Lucene Analyzer injected by the SearchApp when a case is opened. */
    private volatile org.apache.lucene.analysis.Analyzer luceneAnalyzer;

    public static synchronized void initialize(File caseDir, RAGConfig config) {
        if (instance == null || (caseDir != null && instance.caseDir != null && !instance.caseDir.equals(caseDir))) {
            instance = new RAGService(caseDir, config);
        }
    }

    public static synchronized RAGService getInstance() {
        return instance;
    }

    protected RAGService(File caseDir, RAGConfig config) {
        this.caseDir = caseDir;
        this.config = config;

        // Initialize Embedding Provider & Auto-Resolve Vector Dimensions
        String embProv = config.getEmbeddingProvider();
        if ("local".equalsIgnoreCase(embProv)) {
            this.embeddingProvider = new LocalEmbeddingProvider(config.getEmbeddingEndpoint(), config.getEmbeddingModel(), config.getMaxBatchChars());
        } else {
            this.embeddingProvider = new RemoteEmbeddingProvider(config.getEmbeddingEndpoint(), config.getEmbeddingModel(), config.getLlmApiKey(), config.getMaxBatchChars());
        }

        int userDims = config.getEmbeddingDimensions();
        this.dimensionsConfigured = userDims > 0;
        if (userDims > 0) {
            LOGGER.info("RAG: Using user-configured embeddingDimensions override: {} dims", userDims);
        } else {
            int resolvedDims = -1;
            if ("local".equalsIgnoreCase(embProv)) {
                resolvedDims = LocalEmbeddingProvider.fetchVectorDimensionsFromOllama(config.getEmbeddingEndpoint(), config.getEmbeddingModel());
            } else {
                resolvedDims = RemoteEmbeddingProvider.resolveRemoteVectorDimensions(config.getEmbeddingModel());
            }

            if (resolvedDims > 0) {
                LOGGER.info("RAG: Auto-resolved vector dimensions for model '{}': {} dims", config.getEmbeddingModel(), resolvedDims);
                config.setEmbeddingDimensions(resolvedDims);
            } else {
                config.setEmbeddingDimensions(1024); // Safe fallback default
            }
        }

        if (config.isLlmApiKeyFromConfigFile()) {
            LOGGER.warn("RAG: llmApiKey is set in RAGConfig.txt, which is copied to the case folder."
                    + " Prefer the {} environment variable.", RAGConfig.API_KEY_ENV);
        }

        // Initialize LLM Provider
        String provider = config.getLlmProvider();
        if ("local".equalsIgnoreCase(provider)) {
            this.llmProvider = new LocalLLMProvider(config.getLlmEndpoint(), config.getLlmModel());
        } else if ("gemini".equalsIgnoreCase(provider)) {
            this.llmProvider = new GeminiLLMProvider(config.getLlmEndpoint(), config.getLlmModel(), config.getLlmApiKey());
        } else if ("claude".equalsIgnoreCase(provider)) {
            this.llmProvider = new ClaudeLLMProvider(config.getLlmEndpoint(), config.getLlmModel(), config.getLlmApiKey());
        } else {
            this.llmProvider = new RemoteLLMProvider(config.getLlmEndpoint(), config.getLlmModel(), config.getLlmApiKey());
        }

        // Perform health check on embedding provider and vector store at startup
        boolean embOk = config != null && config.isEnabled() && checkHealth();
        lastHealthCheckMillis = System.currentTimeMillis();
        if (embOk) {
            this.available = true;
            LOGGER.info("RAG embedding generation enabled.");
        } else {
            this.available = false;
            if (config != null && config.isEnabled()) {
                String msg = "RAG: Embedding service unavailable (" + unavailableReason + "). Skipping embeddings.";
                if (LOG4J_LOGGER != null && MSG != null) {
                    LOG4J_LOGGER.log(MSG, msg);
                } else {
                    LOGGER.warn(msg);
                }
            }
        }
    }

    /** Embedding service and model checks; sets unavailableReason on failure. */
    private boolean checkHealth() {
        boolean embOk;
        try {
            embOk = checkEmbeddingHealth(config);
            if (!embOk) {
                unavailableReason = "endpoint " + config.getEmbeddingEndpoint() + " is unreachable";
            } else {
                embOk = checkEmbeddingModel();
            }
        } catch (Exception e) {
            embOk = false;
            unavailableReason = e.toString();
        }
        return embOk;
    }

    /**
     * Checks the embedding service again when it was down, at most once every
     * {@link #HEALTH_RETRY_MILLIS}, so the analysis recovers without reopening the case.
     * Processing does not call it: items keep the state checked when the task started.
     */
    public synchronized boolean retryIfUnavailable() {
        if (available || config == null || !config.isEnabled()) {
            return isAvailable();
        }
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckMillis < HEALTH_RETRY_MILLIS) {
            return false;
        }
        lastHealthCheckMillis = now;
        if (checkHealth()) {
            available = true;
            unavailableReason = null;
            LOGGER.info("RAG: embedding service is available again.");
        }
        return isAvailable();
    }

    public boolean isAvailable() {
        return available && modelMismatch == null;
    }

    /** Why the embedding service is unavailable, or null when it is available or RAG is disabled. */
    public String getUnavailableReason() {
        return modelMismatch != null ? modelMismatch : unavailableReason;
    }

    /** Model name as recorded in the index: Ollama resolves "name" and "name:latest" to the same model. */
    public static String normalizeModelName(String model) {
        if (model == null) {
            return "";
        }
        String name = model.trim().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(":latest") ? name.substring(0, name.length() - ":latest".length()) : name;
    }

    /**
     * Vectors of different models are not comparable: searching a case indexed with
     * another model returns unrelated fragments without any error. Returns why the
     * configured model cannot search this index, or null when it can. Cases indexed
     * before the model was recorded are not checked.
     */
    static String checkIndexedEmbeddingModel(IndexReader reader, String configuredModel) throws IOException {
        Terms terms = MultiTerms.getTerms(reader, iped.engine.task.index.IndexTask.EMBEDDING_MODEL);
        if (terms == null) {
            return null;
        }
        java.util.Set<String> models = new java.util.TreeSet<>();
        TermsEnum termsEnum = terms.iterator();
        for (BytesRef term = termsEnum.next(); term != null; term = termsEnum.next()) {
            models.add(term.utf8ToString());
        }
        String configured = normalizeModelName(configuredModel);
        if (models.isEmpty() || (models.size() == 1 && models.contains(configured))) {
            return null;
        }
        return "the case was indexed with embedding model " + String.join(", ", models)
                + " but embeddingModel is " + configured + "; vectors of different models are not comparable";
    }

    /**
     * Embeds a fixed synthetic text: a reachable server is not enough, the
     * configured model must exist and return a vector.
     */
    private boolean checkEmbeddingModel() throws IOException, InterruptedException {
        float[] vector = embeddingProvider.generateEmbedding("IPED RAG embedding health check");
        if (vector == null || vector.length == 0) {
            unavailableReason = "model " + config.getEmbeddingModel() + " returned no vector";
            return false;
        }
        if (vector.length != config.getEmbeddingDimensions()) {
            if (dimensionsConfigured) {
                LOGGER.warn("RAG: model '{}' returns {} dims but embeddingDimensions is {}.", config.getEmbeddingModel(),
                        vector.length, config.getEmbeddingDimensions());
            } else {
                // the model itself is the reference when the dimensions were not configured
                config.setEmbeddingDimensions(vector.length);
            }
        }
        return true;
    }

    private static boolean checkEmbeddingHealth(RAGConfig config) {
        String endpoint = config.getEmbeddingEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return false;
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();

            String pingUrl = endpoint;
            if (endpoint.contains("/api/embeddings")) {
                pingUrl = endpoint.substring(0, endpoint.indexOf("/api/embeddings"));
            } else if (endpoint.contains("/v1/embeddings")) {
                pingUrl = endpoint.substring(0, endpoint.indexOf("/v1/embeddings"));
            }
            if (pingUrl.trim().isEmpty()) {
                pingUrl = endpoint;
            }

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(pingUrl))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(3))
                    .build();

            java.net.http.HttpResponse<String> response = sendPrivilegedPing(client, request);
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private static java.net.http.HttpResponse<String> sendPrivilegedPing(java.net.http.HttpClient client, java.net.http.HttpRequest request) throws Exception {
        try {
            return java.security.AccessController.doPrivileged(
                    (java.security.PrivilegedExceptionAction<java.net.http.HttpResponse<String>>) () -> client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            );
        } catch (java.security.PrivilegedActionException e) {
            throw e.getException();
        }
    }

    public RAGConfig getConfig() {
        return config;
    }

    public EmbeddingProvider getEmbeddingProvider() {
        return embeddingProvider;
    }

    public LLMProvider getLlmProvider() {
        return llmProvider;
    }

    /**
     * Injects the Lucene IndexSearcher from the opened case. Must be called by
     * the SearchApp after the case index is opened.
     *
     * @param searcher the IndexSearcher from IPEDSource.getSearcher()
     */
    /**
     * Searcher of the RAG fragments, with BM25. Keyword search scores the fragment field with the term
     * statistics of the regular content, i.e. of the whole case, as when the fragments were part of the
     * regular content: within the embedded fragments alone, a name frequent in the chats weighs less than
     * common words of the question.
     */
    static class RAGSearcher extends IndexSearcher {

        RAGSearcher(IndexReader reader) {
            super(reader);
            setSimilarity(new org.apache.lucene.search.similarities.BM25Similarity());
        }

        @Override
        public CollectionStatistics collectionStatistics(String field) throws IOException {
            if (iped.engine.task.index.IndexTask.RAG_CONTENT.equals(field)) {
                CollectionStatistics content = super.collectionStatistics(iped.properties.BasicProps.CONTENT);
                if (content != null) {
                    return new CollectionStatistics(field, content.maxDoc(), content.docCount(),
                            content.sumTotalTermFreq(), content.sumDocFreq());
                }
            }
            return super.collectionStatistics(field);
        }

        @Override
        public TermStatistics termStatistics(Term term, int docFreq, long totalTermFreq) throws IOException {
            if (iped.engine.task.index.IndexTask.RAG_CONTENT.equals(term.field())) {
                Term content = new Term(iped.properties.BasicProps.CONTENT, term.bytes());
                int contentDocFreq = getIndexReader().docFreq(content);
                if (contentDocFreq > 0) {
                    return super.termStatistics(content, contentDocFreq, getIndexReader().totalTermFreq(content));
                }
            }
            return super.termStatistics(term, docFreq, totalTermFreq);
        }
    }

    public void setLuceneSearcher(IndexSearcher searcher) {
        IndexSearcher ragSearcher = null;
        if (searcher != null) {
            // Own searcher over the same reader: setting BM25 on the case searcher would
            // replace the IndexerSimilarity used by the regular IPED searches.
            ragSearcher = new RAGSearcher(searcher.getIndexReader());
        }
        this.luceneSearcher = ragSearcher;
        modelMismatch = null;
        if (searcher != null && config != null) {
            try {
                modelMismatch = checkIndexedEmbeddingModel(searcher.getIndexReader(), config.getEmbeddingModel());
            } catch (IOException e) {
                LOGGER.warn("RAG: could not read the embedding model recorded in the index.", e);
            }
            if (modelMismatch != null) {
                LOGGER.error("RAG: assistant disabled for this case: {}.", modelMismatch);
            }
        }
    }

    public void setLuceneAnalyzer(org.apache.lucene.analysis.Analyzer analyzer) {
        this.luceneAnalyzer = analyzer;
    }

    private File resolveDbFile(File baseDir) {
        if (baseDir == null) return new File("iped/data/embedding_cache.db");

        // Multicase lists are files, not case directories. Keep each list's history separate.
        if (baseDir.isFile()) {
            baseDir = new File(new File(baseDir.getAbsoluteFile().getParentFile(), "iped-multicases"),
                    baseDir.getName());
        }

        // Preserve legacy DB location if it already exists in an old case
        File oldDb = new File(baseDir, "storage/embedding_cache.db");
        if (oldDb.exists()) return oldDb;
        File oldIpedDb = new File(baseDir, "iped/storage/embedding_cache.db");
        if (oldIpedDb.exists()) return oldIpedDb;
        File oldIpedDataDb = new File(baseDir, "iped/data/embedding_cache.db");
        if (oldIpedDataDb.exists()) return oldIpedDataDb;

        // Always resolve into the internal iped/ folder
        File ipedDir = baseDir;
        if (!baseDir.getName().equalsIgnoreCase("iped") && new File(baseDir, "iped").isDirectory()) {
            ipedDir = new File(baseDir, "iped");
        }

        File dataDir = new File(ipedDir, "data");
        return new File(dataDir, "embedding_cache.db");
    }

    private synchronized Connection getConnection() throws SQLException {
        if (dbConnection == null || dbConnection.isClosed()) {
            File dbFile = resolveDbFile(caseDir);
            File parent = dbFile.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new SQLException("Could not create RAG database directory: " + parent);
            }
            org.sqlite.SQLiteConfig sqliteConfig = new org.sqlite.SQLiteConfig();
            sqliteConfig.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);
            dbConnection = sqliteConfig.createConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = dbConnection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS embedding_cache ("
                        + "text_hash TEXT, "
                        + "model_name TEXT, "
                        + "embedding BLOB, "
                        + "PRIMARY KEY(text_hash, model_name))");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS chat_history ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "timestamp INTEGER, "
                        + "session_id TEXT, "
                        + "question TEXT, "
                        + "answer_html TEXT, "
                        + "sources_json TEXT)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS chat_session_titles ("
                        + "session_id TEXT PRIMARY KEY, "
                        + "title TEXT)");
                // Legacy tables lack session_id: migrate in place so saved history is kept.
                if (!hasColumn(stmt, "chat_history", "session_id")) {
                    stmt.executeUpdate("ALTER TABLE chat_history ADD COLUMN session_id TEXT");
                }
            }
        }
        return dbConnection;
    }

    private static boolean hasColumn(Statement stmt, String table, String column) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized float[] getCachedEmbedding(String text, String modelName) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String hash = computeSHA256(text);
        String sql = "SELECT embedding FROM embedding_cache WHERE text_hash = ? AND model_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hash);
            pstmt.setString(2, modelName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    byte[] bytes = rs.getBytes(1);
                    if (bytes != null) {
                        return byteArrayToFloatArray(bytes);
                    }
                }
            }
        } catch (SQLException e) {
            // Log or ignore cache read errors
        }
        return null;
    }

    public synchronized void cacheEmbedding(String text, String modelName, float[] embedding) {
        String hash = computeSHA256(text);
        String sql = "INSERT OR REPLACE INTO embedding_cache (text_hash, model_name, embedding) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hash);
            pstmt.setString(2, modelName);
            pstmt.setBytes(3, floatArrayToByteArray(embedding));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            // Log or ignore cache write errors
        }
    }

    public synchronized long saveHistoryEntry(String sessionId, String question, String answerHtml, List<RAGSourceDoc> sources) {
        String sql = "INSERT INTO chat_history (timestamp, session_id, question, answer_html, sources_json) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.setString(2, sessionId);
            pstmt.setString(3, question);
            pstmt.setString(4, answerHtml);
            String json = new Gson().toJson(sources);
            pstmt.setString(5, json);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(RAGService.class).error("Error saving chat history entry", e);
        }
        return -1;
    }

    public synchronized List<HistoryEntryRecord> loadHistoryEntries() {
        List<HistoryEntryRecord> list = new ArrayList<>();
        String sql = "SELECT id, session_id, question, answer_html, sources_json FROM chat_history ORDER BY id ASC";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<RAGSourceDoc>>(){}.getType();
            while (rs.next()) {
                long id = rs.getLong(1);
                String sessionId = rs.getString(2);
                String question = rs.getString(3);
                String answerHtml = rs.getString(4);
                String sourcesJson = rs.getString(5);
                List<RAGSourceDoc> sources;
                try {
                    sources = gson.fromJson(sourcesJson, type);
                } catch (com.google.gson.JsonParseException e) {
                    LOGGER.warn("Ignoring unreadable sources of chat history entry {}", id);
                    sources = null;
                }
                list.add(new HistoryEntryRecord(id, sessionId, question, answerHtml, sources));
            }
        } catch (SQLException e) {
            // Never drop the table here: a locked or read-only database must not erase saved history.
            LOGGER.error("Error loading chat history", e);
        }
        return list;
    }

    public synchronized void deleteHistorySession(String sessionId) {
        String sql = "DELETE FROM chat_history WHERE session_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(RAGService.class).error("Error deleting chat history session", e);
        }
        String sqlTitle = "DELETE FROM chat_session_titles WHERE session_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlTitle)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(RAGService.class).error("Error deleting chat session title", e);
        }
    }

    public synchronized void updateSessionTitle(String sessionId, String title) {
        String sql = "INSERT OR REPLACE INTO chat_session_titles (session_id, title) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setString(2, title);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(RAGService.class).error("Error updating chat session title", e);
        }
    }

    public synchronized Map<String, String> loadSessionTitles() {
        Map<String, String> titles = new HashMap<>();
        String sql = "SELECT session_id, title FROM chat_session_titles";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                titles.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(RAGService.class).error("Error loading chat session titles", e);
        }
        return titles;
    }

    public static class HistoryEntryRecord {
        public final long id;
        public final String sessionId;
        public final String question;
        public final String answerHtml;
        public final List<RAGSourceDoc> displaySources;

        public HistoryEntryRecord(long id, String sessionId, String question, String answerHtml, List<RAGSourceDoc> displaySources) {
            this.id = id;
            this.sessionId = sessionId;
            this.question = question;
            this.answerHtml = answerHtml;
            this.displaySources = displaySources;
        }
    }

    public static class HistoryTurn {
        public final String question;
        public final String answer;

        public HistoryTurn(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }


    public float[] getEmbedding(String text) throws IOException, InterruptedException {
        if (modelMismatch != null) {
            return null; // a cached query vector would still search an incompatible index
        }
        String modelName = config.getEmbeddingModel();
        float[] vector = getCachedEmbedding(text, modelName);
        if (vector == null && (isAvailable() || retryIfUnavailable())) {
            vector = embeddingProvider.generateEmbedding(text);
            if (vector != null) {
                cacheEmbedding(text, modelName, vector);
            }
        }
        return vector;
    }

    public List<float[]> getEmbeddings(List<String> texts) throws IOException, InterruptedException {
        if (texts == null || texts.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String modelName = config.getEmbeddingModel();
        List<float[]> result = new ArrayList<>(texts.size());
        List<Integer> missingIndices = new ArrayList<>();
        List<String> missingTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String txt = texts.get(i);
            if (!RAGTextSanitizer.hasUsefulContent(txt)) {
                // Skip GPU embedding generation for empty/useless fragments.
                // Returning null leaves KnnVectorField omitted in IndexTask,
                // saving GPU inference, HTTP payload & disk while keeping Lucene
                // document counts 100% accurate for IPED Statistics.
                result.add(null);
                continue;
            }
            float[] vec = getCachedEmbedding(txt, modelName);
            result.add(vec);
            if (vec == null) {
                missingIndices.add(i);
                missingTexts.add(txt);
            }
        }

        if (!missingTexts.isEmpty() && isAvailable()) {
            List<float[]> newVecs = embeddingProvider.generateEmbeddings(missingTexts);
            if (newVecs != null && newVecs.size() == missingTexts.size()) {
                for (int k = 0; k < missingTexts.size(); k++) {
                    int idx = missingIndices.get(k);
                    float[] vec = newVecs.get(k);
                    result.set(idx, vec);
                    if (vec != null) {
                        cacheEmbedding(missingTexts.get(k), modelName, vec);
                    }
                }
            } else {
                for (int k = 0; k < missingTexts.size(); k++) {
                    int idx = missingIndices.get(k);
                    float[] vec = getEmbedding(missingTexts.get(k));
                    result.set(idx, vec);
                }
            }
        }
        return result;
    }

    public List<RAGSourceDoc> performVectorSearch(float[] queryVector, int k) throws IOException {
        return performVectorSearch(queryVector, k, null);
    }

    public List<RAGSourceDoc> performVectorSearch(float[] queryVector, int k, java.util.Collection<String> allowedItemIds) throws IOException {
        if (allowedItemIds != null && allowedItemIds.isEmpty()) {
            return Collections.emptyList();
        }
        return performVectorSearchLucene(queryVector, k, allowedItemIds, null);
    }

    // --- Lucene KNN backend ---

    private List<RAGSourceDoc> performVectorSearchLucene(float[] queryVector, int k,
            java.util.Collection<String> allowedItemIds, RAGSearchScope scope) throws IOException {
        IndexSearcher searcher = luceneSearcher;
        if (searcher == null) {
            throw new IOException("Lucene IndexSearcher not initialised. " +
                    "Call RAGService.setLuceneSearcher() after opening the case.");
        }

        // Retrieve k*5 candidates then re-rank
        int candidates = k * 5;
        Query filter = createLuceneScopeFilter(allowedItemIds, scope);
        Query finalQuery = new KnnVectorQuery(
                iped.engine.task.index.IndexTask.CONTENT_EMBEDDING, queryVector, candidates, filter);

        TopDocs topDocs = searcher.search(finalQuery, Math.min(k, 1000));

        List<RAGSourceDoc> hits = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String fragTrackId = doc.get(iped.engine.task.index.IndexTask.FRAG_TRACK_ID);
            String content = doc.get(iped.engine.task.index.IndexTask.CONTENT_STORED);
            if (fragTrackId == null || content == null) {
                continue;
            }
            // parentGlobalId is everything before the last '#'
            int sep = fragTrackId.lastIndexOf('#');
            String parentId = sep > 0 ? fragTrackId.substring(0, sep) : fragTrackId;

            String parentNumericId = doc.get(iped.engine.task.index.IndexTask.FRAG_PARENT_NUMERIC_ID);

            RAGSourceDoc ragDoc = new RAGSourceDoc();
            ragDoc.itemId = parentNumericId != null ? parentNumericId : parentId;
            ragDoc.content = content;
            ragDoc.contenttrackID = fragTrackId;
            ragDoc.score = scoreDoc.score;
            hits.add(ragDoc);
        }
        return hits;
    }

    public List<RAGSourceDoc> performLexicalSearch(String question, int k) throws IOException {
        return performLexicalSearch(question, k, null);
    }

    public List<RAGSourceDoc> performLexicalSearch(String question, int k, java.util.Collection<String> allowedItemIds) throws IOException {
        if (allowedItemIds != null && allowedItemIds.isEmpty()) {
            return Collections.emptyList();
        }
        return performLexicalSearchLucene(question, k, allowedItemIds, null);
    }

    // --- Lucene BM25 backend ---

    private List<RAGSourceDoc> performLexicalSearchLucene(String question, int k,
            java.util.Collection<String> allowedItemIds, RAGSearchScope scope) throws IOException {
        IndexSearcher searcher = luceneSearcher;
        if (searcher == null) {
            throw new IOException("Lucene IndexSearcher not initialised. " +
                    "Call RAGService.setLuceneSearcher() after opening the case.");
        }

        // Only search over fragment documents that have been embedded
        Query hasEmbeddingFilter = new TermQuery(
                new Term(iped.engine.task.index.IndexTask.HAS_EMBEDDING_FRAG, "1"));

        String cleanedQuestion = question;
        if (cleanedQuestion != null) {
            cleanedQuestion = cleanedQuestion.trim();
            while (cleanedQuestion.endsWith("?") || cleanedQuestion.endsWith(".") || cleanedQuestion.endsWith("!")) {
                cleanedQuestion = cleanedQuestion.substring(0, cleanedQuestion.length() - 1).trim();
            }
            cleanedQuestion = cleanedQuestion.replaceAll("[\\+\\-\\&\\|\\!\\(\\)\\{\\}\\[\\]\\^\\\"\\~\\*\\?\\:\\\\\\/@]", " ");
            cleanedQuestion = cleanedQuestion.trim().replaceAll("\\s+", " ");
            cleanedQuestion = removeStopWords(cleanedQuestion);
        }
        if (cleanedQuestion == null || cleanedQuestion.isEmpty()) {
            // Only stop words: the lexical side has nothing to match, the vector side still runs.
            return new ArrayList<>();
        }

        Query wordsQuery;
        try {
            org.apache.lucene.analysis.Analyzer analyzer = luceneAnalyzer;
            if (analyzer == null) {
                analyzer = new org.apache.lucene.analysis.standard.StandardAnalyzer();
            }
            org.apache.lucene.queryparser.flexible.standard.StandardQueryParser parser =
                    new org.apache.lucene.queryparser.flexible.standard.StandardQueryParser(analyzer);
            parser.setMultiFields(new String[]{ iped.engine.task.index.IndexTask.RAG_CONTENT });
            wordsQuery = parser.parse(cleanedQuestion, iped.engine.task.index.IndexTask.RAG_CONTENT);
            
            if (wordsQuery instanceof BooleanQuery) {
                BooleanQuery bq = (BooleanQuery) wordsQuery;
                int clauseCount = bq.clauses().size();
                int minMatch = Math.max(1, (int) (clauseCount * LEXICAL_MIN_SHOULD_MATCH));
                
                BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();
                for (BooleanClause clause : bq.clauses()) {
                    bqBuilder.add(clause);
                }
                bqBuilder.setMinimumNumberShouldMatch(minMatch);
                wordsQuery = bqBuilder.build();
            }
        } catch (org.apache.lucene.queryparser.flexible.core.QueryNodeException e) {
            // Graceful fallback: build a simple term-by-term query instead of failing.
            // This handles questions containing emails (user@domain.com), URLs, SQL, or
            // any other text with characters that the Lucene query parser cannot handle.
            // The question is not logged: it may contain sensitive case data.
            LOGGER.warn("Lucene query parser failed, falling back to simple term query: {}", e.getMessage());
            String[] tokens = cleanedQuestion.toLowerCase().split("\\s+");
            BooleanQuery.Builder fallbackBuilder = new BooleanQuery.Builder();
            int validTokens = 0;
            for (String token : tokens) {
                token = token.trim();
                if (!token.isEmpty() && token.length() >= 2) {
                    fallbackBuilder.add(
                            new TermQuery(new Term(iped.engine.task.index.IndexTask.RAG_CONTENT, token)),
                            BooleanClause.Occur.SHOULD);
                    validTokens++;
                }
            }
            if (validTokens > 0) {
                int minMatch = Math.max(1, (int) (validTokens * LEXICAL_MIN_SHOULD_MATCH));
                fallbackBuilder.setMinimumNumberShouldMatch(minMatch);
                wordsQuery = fallbackBuilder.build();
            } else {
                // Query had no usable tokens (e.g. single-char input): return nothing rather than everything.
                wordsQuery = new org.apache.lucene.search.MatchNoDocsQuery();
            }
        }

        BooleanQuery.Builder boolBuilder = new BooleanQuery.Builder();
        boolBuilder.add(hasEmbeddingFilter, BooleanClause.Occur.MUST);
        boolBuilder.add(wordsQuery, BooleanClause.Occur.MUST);

        Query filter = createLuceneScopeFilter(allowedItemIds, scope);
        if (filter != null) {
            boolBuilder.add(filter, BooleanClause.Occur.FILTER);
        }

        TopDocs topDocs = searcher.search(boolBuilder.build(), Math.min(k, 1000));

        List<RAGSourceDoc> hits = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String fragTrackId = doc.get(iped.engine.task.index.IndexTask.FRAG_TRACK_ID);
            String content = doc.get(iped.engine.task.index.IndexTask.CONTENT_STORED);
            if (fragTrackId == null || content == null) {
                continue;
            }
            int sep = fragTrackId.lastIndexOf('#');
            String parentId = sep > 0 ? fragTrackId.substring(0, sep) : fragTrackId;

            String parentNumericId = doc.get(iped.engine.task.index.IndexTask.FRAG_PARENT_NUMERIC_ID);

            RAGSourceDoc ragDoc = new RAGSourceDoc();
            ragDoc.itemId = parentNumericId != null ? parentNumericId : parentId;
            ragDoc.content = content;
            ragDoc.contenttrackID = fragTrackId;
            ragDoc.score = scoreDoc.score;
            hits.add(ragDoc);
        }
        return hits;
    }

    public List<RAGSourceDoc> performHybridSearch(String question, float[] queryVector) throws IOException {
        return performHybridSearch(question, queryVector, null);
    }

    public List<RAGSourceDoc> performHybridSearch(String question, float[] queryVector, java.util.Collection<String> allowedItemIds) throws IOException {
        if (allowedItemIds != null && allowedItemIds.isEmpty()) {
            return Collections.emptyList();
        }
        int maxChunks = config.getMaxRetrievedChunks();
        List<RAGSourceDoc> vectorHits = performVectorSearch(queryVector, maxChunks * 5, allowedItemIds);
        List<RAGSourceDoc> lexicalHits = performLexicalSearch(question, maxChunks * 5, allowedItemIds);
        return mergeHybridHits(vectorHits, lexicalHits);
    }

    public List<RAGSourceDoc> performHybridSearchInScope(String question, float[] queryVector, RAGSearchScope scope)
            throws IOException {
        if (scope == null) {
            return performHybridSearch(question, queryVector);
        }
        if (scope.isEmpty()) {
            return Collections.emptyList();
        }
        int k = config.getMaxRetrievedChunks() * 5;
        List<RAGSourceDoc> vectorHits = performVectorSearchLucene(queryVector, k, null, scope);
        List<RAGSourceDoc> lexicalHits = performLexicalSearchLucene(question, k, null, scope);
        return mergeHybridHits(vectorHits, lexicalHits);
    }

    private Query createLuceneScopeFilter(java.util.Collection<String> allowedItemIds, RAGSearchScope scope) {
        if (scope != null) {
            return scope.createLuceneFilter();
        }
        if (allowedItemIds == null) {
            return null;
        }
        List<BytesRef> ids = new ArrayList<>();
        for (String id : allowedItemIds) {
            ids.add(new BytesRef(id));
        }
        return new TermInSetQuery(iped.engine.task.index.IndexTask.FRAG_PARENT_NUMERIC_ID, ids);
    }

    /**
     * Rank of a hit in its list. Fragments with the same text (copies of a file, chat databases kept in several
     * backups) share the rank of the first one: otherwise the copies push the other fragments down the list.
     */
    private static int rankOf(RAGSourceDoc doc, Map<String, Integer> rankByText, int nextRank) {
        if (doc.content == null) {
            return nextRank;
        }
        return rankByText.computeIfAbsent(doc.content.trim(), text -> nextRank);
    }

    /** Words in a row compared to find copies; long enough that common phrases do not match. */
    static final int SHINGLE_WORDS = 5;

    /**
     * Share of the smaller fragment's word sequences found in the other for it to be a copy. Measured on chats
     * kept in the main database and in a backup: copies 0.86 to 0.95; fragments of the same chat cut at other
     * points up to 0.27; different chats up to 0.05.
     */
    static final double NEAR_COPY_MIN_OVERLAP = 0.8;

    /** Sequences of {@link #SHINGLE_WORDS} words, case and punctuation ignored. */
    static java.util.Set<String> wordShingles(String text) {
        List<String> words = new ArrayList<>();
        for (String w : text.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        java.util.Set<String> shingles = new java.util.HashSet<>();
        for (int i = 0; i + SHINGLE_WORDS <= words.size(); i++) {
            shingles.add(String.join(" ", words.subList(i, i + SHINGLE_WORDS)));
        }
        return shingles;
    }

    /** Share of the smaller set found in the other one; 0 when either has no sequence. */
    static double overlap(java.util.Set<String> a, java.util.Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        java.util.Set<String> small = a.size() <= b.size() ? a : b;
        java.util.Set<String> large = small == a ? b : a;
        long common = small.stream().filter(large::contains).count();
        return (double) common / small.size();
    }

    private static RAGSourceDoc nearCopyOf(java.util.Set<String> shingles,
            Map<RAGSourceDoc, java.util.Set<String>> selectedShingles) {
        for (Map.Entry<RAGSourceDoc, java.util.Set<String>> e : selectedShingles.entrySet()) {
            if (e.getValue() != null && overlap(shingles, e.getValue()) >= NEAR_COPY_MIN_OVERLAP) {
                return e.getKey();
            }
        }
        return null;
    }

    private List<RAGSourceDoc> mergeHybridHits(List<RAGSourceDoc> vectorHits, List<RAGSourceDoc> lexicalHits) {
        int maxChunks = config.getMaxRetrievedChunks();

        // Raw similarity fallback: if there are no lexical matches and the best vector
        // match has a raw similarity score below the threshold, treat the whole query as noise.
        // This prevents RRF normalization from boosting weak/spurious vector hits to 1.0.
        float rawThreshold = config.getChunkSimilarityThreshold();
        if (lexicalHits.isEmpty() && !vectorHits.isEmpty() && rawThreshold > 0.0f) {
            double topRawScore = vectorHits.get(0).score;
            if (topRawScore < rawThreshold) {
                return new ArrayList<>();
            }
        }

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, RAGSourceDoc> docMap = new HashMap<>();

        // Rank vector hits - Weighted by vectorSearchWeight.
        // Apply raw threshold before RRF: only vector hits >= threshold participate.
        double vectorWeight  = config.getVectorSearchWeight();
        double lexicalWeight = config.getLexicalSearchWeight();
        float threshold = config.getChunkSimilarityThreshold();
        int rank = 1;
        Map<String, Integer> rankByText = new HashMap<>();
        for (RAGSourceDoc doc : vectorHits) {
            if (threshold > 0.0f && doc.score < threshold) {
                continue; // Apply threshold before RRF
            }
            int docRank = rankOf(doc, rankByText, rank);
            rrfScores.put(doc.contenttrackID, rrfScores.getOrDefault(doc.contenttrackID, 0.0) + vectorWeight * (1.0 / (60.0 + docRank)));
            docMap.put(doc.contenttrackID, doc);
            if (docRank == rank) {
                rank++;
            }
        }

        // Rank lexical hits - Weighted by lexicalSearchWeight
        rank = 1;
        rankByText.clear();
        for (RAGSourceDoc doc : lexicalHits) {
            int docRank = rankOf(doc, rankByText, rank);
            rrfScores.put(doc.contenttrackID, rrfScores.getOrDefault(doc.contenttrackID, 0.0) + lexicalWeight * (1.0 / (60.0 + docRank)));
            if (!docMap.containsKey(doc.contenttrackID)) {
                docMap.put(doc.contenttrackID, doc);
            }
            if (docRank == rank) {
                rank++;
            }
        }

        // Sort by RRF score descending
        List<String> sortedIds = new ArrayList<>(rrfScores.keySet());
        sortedIds.sort((id1, id2) -> Double.compare(rrfScores.get(id2), rrfScores.get(id1)));

        // Collect top-N results.
        // Scores are normalized so the best document = 1.0 and others are proportional
        // for displaying relative scores in the UI.
        // A fragment with the same text as one already selected (copies of a file, chat
        // databases kept in several backups, the same file in two cases) does not take a
        // context slot: it is listed as a copy of the selected one, so its source is kept.
        // Copies need not be identical: a chat in the main database names the contact where
        // its backup has only the number, so a fragment mostly contained in a selected one is
        // a copy too.
        List<RAGSourceDoc> result = new ArrayList<>();
        Map<String, RAGSourceDoc> selectedByText = new HashMap<>();
        Map<RAGSourceDoc, java.util.Set<String>> selectedShingles = new java.util.IdentityHashMap<>();
        double topRrfScore = sortedIds.isEmpty() ? 1.0 : rrfScores.get(sortedIds.get(0));
        for (int i = 0; i < sortedIds.size() && result.size() < maxChunks; i++) {
            String id = sortedIds.get(i);
            RAGSourceDoc doc = docMap.get(id);
            String text = doc.content != null ? doc.content.trim() : null;
            RAGSourceDoc selected = text != null ? selectedByText.get(text) : null;
            java.util.Set<String> shingles = null;
            if (selected == null && text != null) {
                shingles = wordShingles(text);
                selected = nearCopyOf(shingles, selectedShingles);
            }
            if (selected != null) {
                selected.identicalCopies.add(doc);
                continue;
            }
            if (text != null) {
                selectedByText.put(text, doc);
                selectedShingles.put(doc, shingles);
            }
            doc.identicalCopies.clear();
            doc.score = (float) (rrfScores.get(id) / topRrfScore); // [0.0, 1.0]
            result.add(doc);
        }
        return result;
    }


    /**
     * Average embedding of the fragments of the item(s) in the scope, or null when
     * they have no embeddings. The scope keeps the item's case in multicase.
     */
    public float[] getAverageEmbedding(RAGSearchScope scope) throws IOException {
        if (scope == null || scope.isEmpty()) {
            return null;
        }
        return getAverageEmbeddingLucene(scope);
    }

    /** Reads the vectors already stored in the case index: no embedding request is made. */
    private float[] getAverageEmbeddingLucene(RAGSearchScope scope) throws IOException {
        IndexSearcher searcher = luceneSearcher;
        if (searcher == null) {
            throw new IOException("Lucene IndexSearcher not initialised.");
        }
        BooleanQuery query = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(iped.engine.task.index.IndexTask.HAS_EMBEDDING_FRAG, "1")),
                        BooleanClause.Occur.MUST)
                .add(scope.createLuceneFilter(), BooleanClause.Occur.FILTER)
                .build();
        TopDocs topDocs = searcher.search(query, MAX_AVERAGED_FRAGMENTS);
        int[] docs = new int[topDocs.scoreDocs.length];
        for (int i = 0; i < docs.length; i++) {
            docs[i] = topDocs.scoreDocs[i].doc;
        }
        // Vector values are forward-only iterators: visit docs in index order.
        java.util.Arrays.sort(docs);
        List<org.apache.lucene.index.LeafReaderContext> leaves = searcher.getIndexReader().leaves();
        org.apache.lucene.index.LeafReaderContext leaf = null;
        org.apache.lucene.index.VectorValues values = null;
        float[] sum = null;
        int count = 0;
        for (int doc : docs) {
            org.apache.lucene.index.LeafReaderContext docLeaf = leaves
                    .get(org.apache.lucene.index.ReaderUtil.subIndex(doc, leaves));
            if (docLeaf != leaf) {
                leaf = docLeaf;
                values = leaf.reader().getVectorValues(iped.engine.task.index.IndexTask.CONTENT_EMBEDDING);
            }
            int local = doc - leaf.docBase;
            if (values == null || values.docID() > local || values.advance(local) != local) {
                continue;
            }
            float[] vector = values.vectorValue();
            if (sum == null) {
                sum = new float[vector.length];
            }
            for (int i = 0; i < sum.length; i++) {
                sum[i] += vector[i];
            }
            count++;
        }
        if (sum == null) {
            return null;
        }
        for (int i = 0; i < sum.length; i++) {
            sum[i] /= count;
        }
        return sum;
    }

    /**
     * Items most similar to the vector, one hit per item (its best fragment), best first,
     * with score = cosine similarity, keeping only scores >= minCosine. Items are told
     * apart by the global ID in contenttrackID, so equal numeric IDs of different cases
     * are kept separately.
     */
    public List<RAGSourceDoc> findSimilarDocuments(float[] queryVector, int maxResults, float minCosine)
            throws IOException {
        List<RAGSourceDoc> fragmentHits = performVectorSearch(queryVector, maxResults * 3);
        Map<String, RAGSourceDoc> parents = new LinkedHashMap<>();
        for (RAGSourceDoc hit : fragmentHits) {
            // Lucene scores cosine vectors as (1 + cos) / 2.
            hit.score = 2 * hit.score - 1;
            if (hit.score < minCosine) {
                continue;
            }
            String key = hit.itemId;
            if (hit.contenttrackID != null && hit.contenttrackID.lastIndexOf('#') > 0) {
                key = hit.contenttrackID.substring(0, hit.contenttrackID.lastIndexOf('#'));
            }
            if (key != null && !parents.containsKey(key)) {
                parents.put(key, hit);
            }
        }
        List<RAGSourceDoc> result = new ArrayList<>(parents.values());
        return result.size() > maxResults ? result.subList(0, maxResults) : result;
    }

    public List<String> findSimilarDocumentIds(float[] queryVector, int maxResults) throws IOException {
        List<RAGSourceDoc> fragmentHits = performVectorSearch(queryVector, maxResults * 3);
        Map<String, Float> parentScores = new LinkedHashMap<>();
        for (RAGSourceDoc hit : fragmentHits) {
            if (hit.itemId != null && !parentScores.containsKey(hit.itemId)) {
                parentScores.put(hit.itemId, hit.score);
            }
        }
        List<String> sortedParentIds = new ArrayList<>(parentScores.keySet());
        if (sortedParentIds.size() > maxResults) {
            return sortedParentIds.subList(0, maxResults);
        }
        return sortedParentIds;
    }

    public void close() {
        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
            }
        } catch (SQLException ignored) {}
    }

    public static class RAGSourceDoc {
        public String itemId;
        public String content;
        public String contenttrackID;
        public float score;
        /** Other retrieved fragments with the same or mostly the same text, left out of the context but kept as sources. */
        public final List<RAGSourceDoc> identicalCopies = new ArrayList<>();
    }

    private static String computeSHA256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static byte[] floatArrayToByteArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private static float[] byteArrayToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }
}
