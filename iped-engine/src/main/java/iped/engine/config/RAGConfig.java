package iped.engine.config;

import iped.utils.UTF8Properties;

/**
 * Configuration class for the RAG (Retrieval-Augmented Generation) engine.
 * Reads and exposes all parameters defined in RAGConfig.txt.
 *
 * @author Rui Sant'Ana Junior
 */
public class RAGConfig extends AbstractTaskPropertiesConfig {

    private static final long serialVersionUID = 1L;

    private static final String CONF_FILE_NAME = "RAGConfig.txt";
    private static final String ENABLED_KEY = "enableRAG";

    private String embeddingProvider = "local";
    private String embeddingModel = "bge-m3";
    private String embeddingEndpoint = "http://localhost:11434/api/embeddings";
    private int embeddingDimensions = 1024;
    private int chunkSize = 3500;
    private int chunkOverlap = 700;
    private boolean enableLLMQueries = true;
    private float chunkSimilarityThreshold = 0.62f;
    private int maxRetrievedChunks = 5;
    private float vectorSearchWeight = 0.4f;
    private float lexicalSearchWeight = 0.6f;
    private String llmProvider = "local";
    private String llmModel = "qwen3:4b";
    private String llmEndpoint = "http://localhost:11434/v1";
    private String llmApiKey = "";
    private int llmContextWindow = 9216;
    private String vectorStoreMode = "lucene"; // "lucene" | "opensearch"
    private String embeddingFilterMode = "blacklist";
    private String embeddingCategoryBlacklist = "^(Programs and Libraries|Compressed Archives|Image Disks|Unallocated|File Slacks|Main Registry Files|Other Registry Files|Fonts|Geographic Data|Databases|XML Files|Other)$";
    private String embeddingMimeTypeBlacklist = "^(application/octet-stream" +
            "|application/x-dosexec|application/x-msdownload|application/x-ms-installer|application/x-msi" +
            "|application/x-sharedlib" +        // .so, .dex, .odex (bin??rios Android/Linux)
            "|application/x-dex" +              // Dalvik Executable (.dex)
            "|application/vnd\\.android\\.dex" + // variante de MIME para .dex
            "|application/vnd\\.android\\.package-archive" + // pacotes APK
            "|application/java-archive|application/x-java-archive" + // arquivos JAR/WAR/EAR
            "|application/x-object" +           // arquivos objeto compilados (.o)
            "|application/x-archive" +          // bibliotecas est??ticas (.a)
            "|application/x-executable" +       // execut??veis ELF gen??ricos
            "|application/x-windows-registry-main|application/x-windows-registry|application/x-sqlite3|application/x-edb|application/x-msaccess" +
            "|text/xml|application/xml" +       // Dumps XML / minireport.xml
            "|font/.*" +
            "|application/zip|application/x-7z-compressed|application/x-rar-compressed|application/x-tar|application/x-gzip|application/x-bzip2" +
            "|application/x-ms-pdb|application/x-win-lnk" +
            ")$";
    private String embeddingCategoryWhitelist = "^(Documents|Emails and Mailboxes|Chats|SMS Messages|Instant Messages Artifacts|Notes|Plain Texts|HTML and Web pages|PDF Documents|Office Documents|Audio)$";
    private String embeddingMimeTypeWhitelist = "^(text/plain|text/html|application/xhtml\\+xml|text/csv|text/tab-separated-values|message/rfc822|application/pdf|application/msword|application/vnd\\.openxmlformats-officedocument\\..*|application/vnd\\.ms-excel|application/vnd\\.ms-powerpoint|application/vnd\\.oasis\\.opendocument\\..*|application/rtf|application/json|audio/.*)$";
    private transient java.util.regex.Pattern embeddingCategoryBlacklistPattern;
    private transient java.util.regex.Pattern embeddingMimeTypeBlacklistPattern;
    private transient java.util.regex.Pattern embeddingCategoryWhitelistPattern;
    private transient java.util.regex.Pattern embeddingMimeTypeWhitelistPattern;

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getEmbeddingEndpoint() {
        return embeddingEndpoint;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public boolean isEnableLLMQueries() {
        return enableLLMQueries;
    }

    public float getChunkSimilarityThreshold() {
        return chunkSimilarityThreshold;
    }

    public int getMaxRetrievedChunks() {
        return maxRetrievedChunks;
    }

    /**
     * Weight applied to the vector (semantic) side of the RRF fusion score.
     * Must be in [0.0, 1.0]. The lexical weight is automatically 1 - vectorWeight.
     * Default: 0.4 (40/60 split between vector and lexical).
     * For forensic use with many entity queries (names, CPFs, codes), consider
     * shifting towards lexical, e.g. vectorSearchWeight = 0.4.
     */
    public float getVectorSearchWeight() {
        return vectorSearchWeight;
    }

    /**
     * Weight applied to the lexical (BM25) side of the RRF fusion score.
     * Automatically derived as 1.0 - vectorSearchWeight.
     */
    public float getLexicalSearchWeight() {
        return lexicalSearchWeight;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getLlmEndpoint() {
        return llmEndpoint;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public int getLlmContextWindow() {
        return llmContextWindow;
    }

    /**
     * Returns the vector store backend mode.
     * "lucene"     : vectors are stored in the case's own Lucene index (portable, default).
     * "opensearch" : vectors are stored in an external OpenSearch cluster (corporate/legacy).
     */
    public String getVectorStoreMode() {
        return vectorStoreMode;
    }

    public void setVectorStoreMode(String vectorStoreMode) {
        this.vectorStoreMode = vectorStoreMode;
    }

    public String getEmbeddingFilterMode() {
        return embeddingFilterMode;
    }

    public String getEmbeddingCategoryBlacklist() {
        return embeddingCategoryBlacklist;
    }

    public String getEmbeddingMimeTypeBlacklist() {
        return embeddingMimeTypeBlacklist;
    }

    public String getEmbeddingCategoryWhitelist() {
        return embeddingCategoryWhitelist;
    }

    public String getEmbeddingMimeTypeWhitelist() {
        return embeddingMimeTypeWhitelist;
    }

    public java.util.regex.Pattern getEmbeddingCategoryBlacklistPattern() {
        if (embeddingCategoryBlacklistPattern == null) {
            synchronized (this) {
                if (embeddingCategoryBlacklistPattern == null) {
                    String patternStr = embeddingCategoryBlacklist != null ? embeddingCategoryBlacklist.trim() : "";
                    if (patternStr.isEmpty()) {
                        patternStr = "$^";
                    }
                    embeddingCategoryBlacklistPattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
                }
            }
        }
        return embeddingCategoryBlacklistPattern;
    }

    public java.util.regex.Pattern getEmbeddingMimeTypeBlacklistPattern() {
        if (embeddingMimeTypeBlacklistPattern == null) {
            synchronized (this) {
                if (embeddingMimeTypeBlacklistPattern == null) {
                    String patternStr = embeddingMimeTypeBlacklist != null ? embeddingMimeTypeBlacklist.trim() : "";
                    if (patternStr.isEmpty()) {
                        patternStr = "$^";
                    }
                    embeddingMimeTypeBlacklistPattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
                }
            }
        }
        return embeddingMimeTypeBlacklistPattern;
    }

    public java.util.regex.Pattern getEmbeddingCategoryWhitelistPattern() {
        if (embeddingCategoryWhitelistPattern == null) {
            synchronized (this) {
                if (embeddingCategoryWhitelistPattern == null) {
                    String patternStr = embeddingCategoryWhitelist != null ? embeddingCategoryWhitelist.trim() : "";
                    if (patternStr.isEmpty()) {
                        patternStr = "$^";
                    }
                    embeddingCategoryWhitelistPattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
                }
            }
        }
        return embeddingCategoryWhitelistPattern;
    }

    public java.util.regex.Pattern getEmbeddingMimeTypeWhitelistPattern() {
        if (embeddingMimeTypeWhitelistPattern == null) {
            synchronized (this) {
                if (embeddingMimeTypeWhitelistPattern == null) {
                    String patternStr = embeddingMimeTypeWhitelist != null ? embeddingMimeTypeWhitelist.trim() : "";
                    if (patternStr.isEmpty()) {
                        patternStr = "$^";
                    }
                    embeddingMimeTypeWhitelistPattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
                }
            }
        }
        return embeddingMimeTypeWhitelistPattern;
    }

    /**
     * Decides whether an item with the given categories and MIME type should have an
     * embedding generated. Returns {@code true} if ANY category/MIME combination
     * passes the configured blacklist/whitelist filter.
     */
    public boolean shouldEmbed(java.util.Collection<String> categories, String mimeType) {
        // In whitelist mode, at least one category OR the MIME type must match.
        // In blacklist mode, no category AND no MIME type must be blocked.
        if (categories == null || categories.isEmpty()) {
            // No categories available ??? decide purely by MIME type using null category
            return shouldEmbed((String) null, mimeType);
        }
        if ("whitelist".equalsIgnoreCase(embeddingFilterMode)) {
            for (String category : categories) {
                if (shouldEmbed(category, mimeType)) {
                    return true;
                }
            }
            return false;
        } else {
            for (String category : categories) {
                if (!shouldEmbed(category, mimeType)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Decides whether an item with a single category and MIME type should have an
     * embedding generated. This is the canonical implementation used by both overloads.
     */
    public boolean shouldEmbed(String category, String mimeType) {
        if ("whitelist".equalsIgnoreCase(embeddingFilterMode)) {
            boolean categoryMatched = embeddingCategoryWhitelist != null
                    && !embeddingCategoryWhitelist.trim().isEmpty()
                    && category != null
                    && getEmbeddingCategoryWhitelistPattern().matcher(category).matches();
            boolean mimeTypeMatched = embeddingMimeTypeWhitelist != null
                    && !embeddingMimeTypeWhitelist.trim().isEmpty()
                    && mimeType != null
                    && getEmbeddingMimeTypeWhitelistPattern().matcher(mimeType).matches();
            return categoryMatched || mimeTypeMatched;
        } else {
            if (embeddingCategoryBlacklist != null && !embeddingCategoryBlacklist.trim().isEmpty()
                    && category != null
                    && getEmbeddingCategoryBlacklistPattern().matcher(category).matches()) {
                return false;
            }
            if (embeddingMimeTypeBlacklist != null && !embeddingMimeTypeBlacklist.trim().isEmpty()
                    && mimeType != null
                    && getEmbeddingMimeTypeBlacklistPattern().matcher(mimeType).matches()) {
                return false;
            }
            return true;
        }
    }



    @Override
    public String getTaskEnableProperty() {
        return ENABLED_KEY;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONF_FILE_NAME;
    }

    @Override
    public void processProperties(UTF8Properties props) {
        String val;

        val = props.getProperty("embeddingProvider");
        if (val != null) embeddingProvider = val.trim();

        val = props.getProperty("embeddingModel");
        if (val != null) embeddingModel = val.trim();

        val = props.getProperty("embeddingEndpoint");
        if (val != null) embeddingEndpoint = val.trim();

        val = props.getProperty("embeddingDimensions");
        if (val != null) embeddingDimensions = Integer.parseInt(val.trim());

        val = props.getProperty("chunkSize");
        if (val != null) chunkSize = Integer.parseInt(val.trim());

        val = props.getProperty("chunkOverlap");
        if (val != null) chunkOverlap = Integer.parseInt(val.trim());

        val = props.getProperty("enableLLMQueries");
        if (val != null) enableLLMQueries = Boolean.parseBoolean(val.trim());

        val = props.getProperty("chunkSimilarityThreshold");
        if (val != null) chunkSimilarityThreshold = Float.parseFloat(val.trim());

        val = props.getProperty("maxRetrievedChunks");
        if (val != null) maxRetrievedChunks = Integer.parseInt(val.trim());

        val = props.getProperty("vectorSearchWeight");
        if (val != null) {
            float v = Float.parseFloat(val.trim());
            if (v < 0.0f || v > 1.0f)
                throw new IllegalArgumentException("vectorSearchWeight must be between 0.0 and 1.0, got: " + v);
            vectorSearchWeight  = v;
            lexicalSearchWeight = 1.0f - v;
        }

        val = props.getProperty("llmProvider");
        if (val != null) llmProvider = val.trim();

        val = props.getProperty("llmModel");
        if (val != null) llmModel = val.trim();

        val = props.getProperty("llmEndpoint");
        if (val != null) llmEndpoint = val.trim();

        val = props.getProperty("llmApiKey");
        if (val != null) llmApiKey = val.trim();

        val = props.getProperty("llmContextWindow");
        if (val != null) llmContextWindow = Integer.parseInt(val.trim());

        val = props.getProperty("vectorStoreMode");
        if (val != null) vectorStoreMode = val.trim().toLowerCase();

        val = props.getProperty("embeddingFilterMode");
        if (val != null) embeddingFilterMode = val.trim();

        val = props.getProperty("embeddingCategoryBlacklist");
        if (val != null) {
            embeddingCategoryBlacklist = val.trim();
            embeddingCategoryBlacklistPattern = null;
        }

        val = props.getProperty("embeddingMimeTypeBlacklist");
        if (val != null) {
            embeddingMimeTypeBlacklist = val.trim();
            embeddingMimeTypeBlacklistPattern = null;
        }

        val = props.getProperty("embeddingCategoryWhitelist");
        if (val != null) {
            embeddingCategoryWhitelist = val.trim();
            embeddingCategoryWhitelistPattern = null;
        }

        val = props.getProperty("embeddingMimeTypeWhitelist");
        if (val != null) {
            embeddingMimeTypeWhitelist = val.trim();
            embeddingMimeTypeWhitelistPattern = null;
        }
    }
}
