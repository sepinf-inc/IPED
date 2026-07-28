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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import iped.engine.util.SSLFix;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.ElasticSearchTaskConfig;
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
    private final boolean available;
    private Connection dbConnection;
    private RestHighLevelClient openSearchClient;
    private String indexName;

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

        // Initialize Embedding Provider
        String embProv = config.getEmbeddingProvider();
        if ("local".equalsIgnoreCase(embProv)) {
            this.embeddingProvider = new LocalEmbeddingProvider(config.getEmbeddingEndpoint(), config.getEmbeddingModel());
        } else if ("gemini".equalsIgnoreCase(embProv)) {
            this.embeddingProvider = new GeminiEmbeddingProvider(config.getEmbeddingEndpoint(), config.getEmbeddingModel(), config.getLlmApiKey(), config.getEmbeddingDimensions());
        } else {
            this.embeddingProvider = new RemoteEmbeddingProvider(config.getEmbeddingEndpoint(), config.getEmbeddingModel(), config.getLlmApiKey());
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

        // Resolve Index Name
        if (caseDir != null) {
            this.indexName = caseDir.getName();
        }

        // Perform health check on embedding provider and vector store at startup
        boolean embOk = false;
        if (config != null && config.isEnabled()) {
            try {
                embOk = checkEmbeddingHealth(config);
            } catch (Exception e) {
                embOk = false;
            }
        }

        if (embOk) {
            if ("opensearch".equalsIgnoreCase(config.getVectorStoreMode())) {
                boolean osOk = checkOpenSearchHealth(config);
                if (!osOk) {
                    String msg = "RAG: OpenSearch cluster unreachable. Falling back to local Lucene vector store.";
                    if (LOG4J_LOGGER != null && MSG != null) {
                        LOG4J_LOGGER.log(MSG, msg);
                    } else {
                        LOGGER.warn(msg);
                    }
                    config.setVectorStoreMode("lucene");
                }
            }
            this.available = true;
            LOGGER.info("RAG embedding generation enabled.");
        } else {
            this.available = false;
            if (config != null && config.isEnabled()) {
                String msg = "RAG: Embedding service unreachable. Skipping embeddings.";
                if (LOG4J_LOGGER != null && MSG != null) {
                    LOG4J_LOGGER.log(MSG, msg);
                } else {
                    LOGGER.warn(msg);
                }
            }
        }
    }

    private boolean checkOpenSearchHealth(RAGConfig config) {
        if (!"opensearch".equalsIgnoreCase(config.getVectorStoreMode())) {
            return true;
        }
        try {
            RestHighLevelClient client = getOpenSearchClient();
            if (client != null) {
                return client.ping(RequestOptions.DEFAULT);
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public boolean isAvailable() {
        return available;
    }

    private static boolean checkEmbeddingHealth(RAGConfig config) {
        String endpoint = config.getEmbeddingEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return false;
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(2))
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
                    .timeout(java.time.Duration.ofSeconds(2))
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
     * the SearchApp after the case index is opened, when vectorStoreMode=lucene.
     *
     * @param searcher the IndexSearcher from IPEDSource.getSearcher()
     */
    public void setLuceneSearcher(IndexSearcher searcher) {
        if (searcher != null) {
            searcher.setSimilarity(new org.apache.lucene.search.similarities.BM25Similarity());
        }
        this.luceneSearcher = searcher;
    }

    public void setLuceneAnalyzer(org.apache.lucene.analysis.Analyzer analyzer) {
        this.luceneAnalyzer = analyzer;
    }

    private File resolveDbFile(File baseDir) {
        if (baseDir == null) return new File("iped/data/embedding_cache.db");

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
            dbFile.getParentFile().mkdirs();
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
            }
        }
        return dbConnection;
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
                List<RAGSourceDoc> sources = gson.fromJson(sourcesJson, type);
                list.add(new HistoryEntryRecord(id, sessionId, question, answerHtml, sources));
            }
        } catch (SQLException e) {
            // If table layout doesn't match (missing session_id on legacy tables), recreate it
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS chat_history");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS chat_history ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "timestamp INTEGER, "
                        + "session_id TEXT, "
                        + "question TEXT, "
                        + "answer_html TEXT, "
                        + "sources_json TEXT)");
            } catch (SQLException ex) {
                org.slf4j.LoggerFactory.getLogger(RAGService.class).error("Error recreating chat_history table", ex);
            }
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
        String modelName = config.getEmbeddingModel();
        float[] vector = getCachedEmbedding(text, modelName);
        if (vector == null && isAvailable()) {
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

    private synchronized RestHighLevelClient getOpenSearchClient() throws IOException {
        if (openSearchClient == null) {
            ElasticSearchTaskConfig esConfig = ConfigurationManager.get().findObject(ElasticSearchTaskConfig.class);
            if (esConfig == null) {
                throw new IOException("ElasticSearchConfig.txt is not loaded.");
            }

            String protocol = esConfig.getProtocol();
            String hostProp = esConfig.getHost();
            int port = esConfig.getPort();

            String[] hosts = hostProp.split("[,;\\s]+");
            HttpHost[] httpHosts = new HttpHost[hosts.length];
            for (int i = 0; i < hosts.length; i++) {
                httpHosts[i] = new HttpHost(hosts[i].trim(), port, protocol);
            }

            RestClientBuilder clientBuilder = RestClient.builder(httpHosts)
                    .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                             .setConnectTimeout(esConfig.getConnect_timeout())
                             .setSocketTimeout(esConfig.getTimeout_millis()));

            String user = System.getProperty("elastic.user");
            String password = System.getProperty("elastic.password");

            if (user == null || password == null) {
                String cmdFields = System.getenv("elastic");
                if (cmdFields == null) {
                    cmdFields = System.getProperty("elastic");
                }
                if (cmdFields != null) {
                    String[] entries = cmdFields.split(";");
                    for (String entry : entries) {
                        String[] pair = entry.split(":", 2);
                        if (pair.length == 2) {
                            if ("user".equals(pair[0])) user = pair[1];
                            else if ("password".equals(pair[0])) password = pair[1];
                            else if ("indexName".equals(pair[0])) this.indexName = pair[1];
                        }
                    }
                }
            }

            if (user == null) {
                user = esConfig.getUsername();
            }
            if (password == null) {
                password = esConfig.getPassword();
            }

            final String finalUser = user;
            final String finalPassword = password;
            clientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {
                if (finalUser != null && finalPassword != null) {
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(finalUser, finalPassword));
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }
                if (!esConfig.getValidateSSL()) {
                    httpClientBuilder.setSSLContext(SSLFix.getUnsecureSSLContext()).setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                }
                return httpClientBuilder;
            });

            openSearchClient = new RestHighLevelClient(clientBuilder);
        }
        return openSearchClient;
    }

    public List<RAGSourceDoc> performVectorSearch(float[] queryVector, int k) throws IOException {
        return performVectorSearch(queryVector, k, null);
    }

    public List<RAGSourceDoc> performVectorSearch(float[] queryVector, int k, java.util.Collection<String> allowedItemIds) throws IOException {
        if ("lucene".equalsIgnoreCase(config.getVectorStoreMode())) {
            return performVectorSearchLucene(queryVector, k, allowedItemIds);
        }
        return performVectorSearchOpenSearch(queryVector, k, allowedItemIds);
    }

    // --- Lucene KNN backend ---

    private List<RAGSourceDoc> performVectorSearchLucene(float[] queryVector, int k,
            java.util.Collection<String> allowedItemIds) throws IOException {
        IndexSearcher searcher = luceneSearcher;
        if (searcher == null) {
            throw new IOException("Lucene IndexSearcher not initialised. " +
                    "Call RAGService.setLuceneSearcher() after opening the case.");
        }

        // Retrieve k*5 candidates then re-rank (consistent with OpenSearch behaviour)
        int candidates = k * 5;
        Query knnQuery = new KnnVectorQuery(
                iped.engine.task.index.IndexTask.CONTENT_EMBEDDING, queryVector, candidates);

        Query finalQuery = knnQuery;
        if (allowedItemIds != null && !allowedItemIds.isEmpty()) {
            BooleanQuery.Builder b = new BooleanQuery.Builder();
            b.add(knnQuery, BooleanClause.Occur.MUST);
            // Filter: fragment's parent ID must match one of the allowed item IDs.
            BooleanQuery.Builder idFilter = new BooleanQuery.Builder();
            for (String id : allowedItemIds) {
                idFilter.add(new TermQuery(
                        new Term("fragParentNumericId", id)),
                        BooleanClause.Occur.SHOULD);
            }
            b.add(idFilter.build(), BooleanClause.Occur.MUST);
            finalQuery = b.build();
        }

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

            String parentNumericId = doc.get("fragParentNumericId");

            RAGSourceDoc ragDoc = new RAGSourceDoc();
            ragDoc.itemId = parentNumericId != null ? parentNumericId : parentId;
            ragDoc.content = content;
            ragDoc.contenttrackID = fragTrackId;
            ragDoc.score = scoreDoc.score;
            hits.add(ragDoc);
        }
        return hits;
    }

    // --- OpenSearch backend (legacy) ---

    private List<RAGSourceDoc> performVectorSearchOpenSearch(float[] queryVector, int k,
            java.util.Collection<String> allowedItemIds) throws IOException {
        RestHighLevelClient client = getOpenSearchClient();
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        StringBuilder vecJson = new StringBuilder("[");
        for (int i = 0; i < queryVector.length; i++) {
            vecJson.append(queryVector[i]);
            if (i < queryVector.length - 1) vecJson.append(",");
        }
        vecJson.append("]");

        String knnJson = "{\"knn\": {\"content_embedding\": {\"vector\": " + vecJson.toString() + ", \"k\": " + k + "}}}";
        QueryBuilder knnQuery = QueryBuilders.wrapperQuery(knnJson);
        QueryBuilder queryBuilder = knnQuery;

        if (allowedItemIds != null && !allowedItemIds.isEmpty()) {
            queryBuilder = QueryBuilders.boolQuery()
                    .must(knnQuery)
                    .filter(QueryBuilders.termsQuery("id", allowedItemIds));
        }

        sourceBuilder.query(queryBuilder);
        sourceBuilder.size(k);
        sourceBuilder.fetchSource(new String[]{"id", "content", "contenttrackID"}, null);
        searchRequest.source(sourceBuilder);

        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        List<RAGSourceDoc> hits = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            if (source != null) {
                RAGSourceDoc doc = new RAGSourceDoc();
                doc.itemId = source.get("id") != null ? String.valueOf(source.get("id")) : null;
                doc.content = source.get("content") != null ? String.valueOf(source.get("content")) : null;
                doc.contenttrackID = source.get("contenttrackID") != null ? String.valueOf(source.get("contenttrackID")) : null;
                doc.score = hit.getScore();
                hits.add(doc);
            }
        }
        return hits;
    }

    public List<RAGSourceDoc> performLexicalSearch(String question, int k) throws IOException {
        return performLexicalSearch(question, k, null);
    }

    public List<RAGSourceDoc> performLexicalSearch(String question, int k, java.util.Collection<String> allowedItemIds) throws IOException {
        if ("lucene".equalsIgnoreCase(config.getVectorStoreMode())) {
            return performLexicalSearchLucene(question, k, allowedItemIds);
        }
        return performLexicalSearchOpenSearch(question, k, allowedItemIds);
    }

    // --- Lucene BM25 backend ---

    private List<RAGSourceDoc> performLexicalSearchLucene(String question, int k,
            java.util.Collection<String> allowedItemIds) throws IOException {
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
            cleanedQuestion = cleanedQuestion.replaceAll("[\\+\\-\\&\\|\\!\\(\\)\\{\\}\\[\\]\\^\\\"\\~\\*\\?\\:\\\\\\/]", " ");
            cleanedQuestion = cleanedQuestion.trim().replaceAll("\\s+", " ");
        }

        Query wordsQuery;
        try {
            org.apache.lucene.analysis.Analyzer analyzer = luceneAnalyzer;
            if (analyzer == null) {
                analyzer = new org.apache.lucene.analysis.standard.StandardAnalyzer();
            }
            org.apache.lucene.queryparser.flexible.standard.StandardQueryParser parser =
                    new org.apache.lucene.queryparser.flexible.standard.StandardQueryParser(analyzer);
            parser.setMultiFields(new String[]{ iped.properties.BasicProps.CONTENT });
            wordsQuery = parser.parse(cleanedQuestion, iped.properties.BasicProps.CONTENT);
            
            if (wordsQuery instanceof BooleanQuery) {
                BooleanQuery bq = (BooleanQuery) wordsQuery;
                int clauseCount = bq.clauses().size();
                int minMatch = Math.max(1, (int) (clauseCount * 0.4));
                
                BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();
                for (BooleanClause clause : bq.clauses()) {
                    bqBuilder.add(clause);
                }
                bqBuilder.setMinimumNumberShouldMatch(minMatch);
                wordsQuery = bqBuilder.build();
            }
        } catch (org.apache.lucene.queryparser.flexible.core.QueryNodeException e) {
            throw new IOException("Failed to parse lexical query: " + question, e);
        }

        BooleanQuery.Builder boolBuilder = new BooleanQuery.Builder();
        boolBuilder.add(hasEmbeddingFilter, BooleanClause.Occur.MUST);
        boolBuilder.add(wordsQuery, BooleanClause.Occur.MUST);

        if (allowedItemIds != null && !allowedItemIds.isEmpty()) {
            BooleanQuery.Builder idFilter = new BooleanQuery.Builder();
            for (String id : allowedItemIds) {
                idFilter.add(new TermQuery(
                        new Term("fragParentNumericId", id)),
                        BooleanClause.Occur.SHOULD);
            }
            boolBuilder.add(idFilter.build(), BooleanClause.Occur.MUST);
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

            String parentNumericId = doc.get("fragParentNumericId");

            RAGSourceDoc ragDoc = new RAGSourceDoc();
            ragDoc.itemId = parentNumericId != null ? parentNumericId : parentId;
            ragDoc.content = content;
            ragDoc.contenttrackID = fragTrackId;
            ragDoc.score = scoreDoc.score;
            hits.add(ragDoc);
        }
        return hits;
    }

    // --- OpenSearch BM25 backend (legacy) ---

    private List<RAGSourceDoc> performLexicalSearchOpenSearch(String question, int k,
            java.util.Collection<String> allowedItemIds) throws IOException {
        RestHighLevelClient client = getOpenSearchClient();
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        QueryBuilder matchQuery = QueryBuilders.matchQuery("content", question)
                .minimumShouldMatch("40%");
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(matchQuery)
                .filter(QueryBuilders.termQuery(iped.engine.task.index.IndexTask.HAS_EMBEDDING_FRAG, "1"));

        if (allowedItemIds != null && !allowedItemIds.isEmpty()) {
            boolQuery.filter(QueryBuilders.termsQuery("id", allowedItemIds));
        }

        sourceBuilder.query(boolQuery);
        sourceBuilder.size(k);
        sourceBuilder.fetchSource(new String[]{"id", "content", "contenttrackID"}, null);
        searchRequest.source(sourceBuilder);

        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        List<RAGSourceDoc> hits = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            if (source != null) {
                RAGSourceDoc doc = new RAGSourceDoc();
                doc.itemId = source.get("id") != null ? String.valueOf(source.get("id")) : null;
                doc.content = source.get("content") != null ? String.valueOf(source.get("content")) : null;
                doc.contenttrackID = source.get("contenttrackID") != null ? String.valueOf(source.get("contenttrackID")) : null;
                doc.score = hit.getScore();
                hits.add(doc);
            }
        }
        return hits;
    }

    public List<RAGSourceDoc> performHybridSearch(String question, float[] queryVector) throws IOException {
        return performHybridSearch(question, queryVector, null);
    }

    public List<RAGSourceDoc> performHybridSearch(String question, float[] queryVector, java.util.Collection<String> allowedItemIds) throws IOException {
        int maxChunks = config.getMaxRetrievedChunks();
        List<RAGSourceDoc> vectorHits = performVectorSearch(queryVector, maxChunks * 5, allowedItemIds);
        List<RAGSourceDoc> lexicalHits = performLexicalSearch(question, maxChunks * 5, allowedItemIds);

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
        for (RAGSourceDoc doc : vectorHits) {
            if (threshold > 0.0f && doc.score < threshold) {
                continue; // Apply threshold before RRF
            }
            rrfScores.put(doc.contenttrackID, rrfScores.getOrDefault(doc.contenttrackID, 0.0) + vectorWeight * (1.0 / (60.0 + rank)));
            docMap.put(doc.contenttrackID, doc);
            rank++;
        }

        // Rank lexical hits - Weighted by lexicalSearchWeight
        rank = 1;
        for (RAGSourceDoc doc : lexicalHits) {
            rrfScores.put(doc.contenttrackID, rrfScores.getOrDefault(doc.contenttrackID, 0.0) + lexicalWeight * (1.0 / (60.0 + rank)));
            if (!docMap.containsKey(doc.contenttrackID)) {
                docMap.put(doc.contenttrackID, doc);
            }
            rank++;
        }

        // Sort by RRF score descending
        List<String> sortedIds = new ArrayList<>(rrfScores.keySet());
        sortedIds.sort((id1, id2) -> Double.compare(rrfScores.get(id2), rrfScores.get(id1)));

        // Collect top-N results.
        // Scores are normalized so the best document = 1.0 and others are proportional
        // for displaying relative scores in the UI.
        List<RAGSourceDoc> result = new ArrayList<>();
        int limit = Math.min(sortedIds.size(), maxChunks);
        double topRrfScore = limit > 0 ? rrfScores.get(sortedIds.get(0)) : 1.0;
        for (int i = 0; i < limit; i++) {
            String id = sortedIds.get(i);
            float normalizedScore = (float) (rrfScores.get(id) / topRrfScore); // [0.0, 1.0]
            RAGSourceDoc doc = docMap.get(id);
            doc.score = normalizedScore;
            result.add(doc);
        }
        return result;
    }


    public float[] getAverageEmbeddingForItem(String parentNumericId) throws IOException {
        if ("lucene".equalsIgnoreCase(config.getVectorStoreMode())) {
            return getAverageEmbeddingForItemLucene(parentNumericId);
        }
        return getAverageEmbeddingForItemOpenSearch(parentNumericId);
    }

    private float[] getAverageEmbeddingForItemLucene(String parentNumericId) throws IOException {
        IndexSearcher searcher = luceneSearcher;
        if (searcher == null) {
            throw new IOException("Lucene IndexSearcher not initialised.");
        }
        Query filter = new TermQuery(new Term("fragParentNumericId", parentNumericId));
        TopDocs topDocs = searcher.search(filter, 200);
        List<float[]> embeddings = new java.util.ArrayList<>();
        for (ScoreDoc sd : topDocs.scoreDocs) {
            Document doc = searcher.doc(sd.doc);
            // KnnVectorField values are not stored as retrievable bytes in Lucene 9;
            // we can only check presence via HAS_EMBEDDING_FRAG marker.
            // Return null to signal that the caller must regenerate the embedding.
            String hasEmb = doc.get(iped.engine.task.index.IndexTask.HAS_EMBEDDING_FRAG);
            if ("1".equals(hasEmb)) {
                // Embedding is present but cannot be retrieved from Lucene stored fields.
                // Callers should use getEmbedding(text) on the stored content instead.
                return null;
            }
        }
        return null;
    }

    private float[] getAverageEmbeddingForItemOpenSearch(String parentId) throws IOException {
        RestHighLevelClient client = getOpenSearchClient();
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("document_content.parent", parentId));
        sourceBuilder.size(100);
        sourceBuilder.fetchSource(new String[]{"content_embedding"}, null);
        searchRequest.source(sourceBuilder);

        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        List<float[]> embeddings = new java.util.ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            if (source != null && source.containsKey("content_embedding")) {
                Object embObj = source.get("content_embedding");
                if (embObj instanceof List) {
                    List<?> list = (List<?>) embObj;
                    float[] emb = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        emb[i] = ((Number) list.get(i)).floatValue();
                    }
                    embeddings.add(emb);
                }
            }
        }

        if (embeddings.isEmpty()) {
            return null;
        }

        int dim = embeddings.get(0).length;
        float[] avg = new float[dim];
        for (float[] emb : embeddings) {
            for (int i = 0; i < dim; i++) {
                avg[i] += emb[i];
            }
        }
        for (int i = 0; i < dim; i++) {
            avg[i] /= embeddings.size();
        }
        return avg;
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
        try {
            if (openSearchClient != null) {
                openSearchClient.close();
            }
        } catch (IOException ignored) {}
    }

    public static class RAGSourceDoc {
        public String itemId;
        public String content;
        public String contenttrackID;
        public float score;
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
