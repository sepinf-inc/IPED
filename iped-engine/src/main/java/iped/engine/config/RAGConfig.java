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
    /** 0: detected from the model; set only for endpoints that cannot report it. */
    private int embeddingDimensions = 0;
    private int maxBatchChars = 28000;
    private int maxEmbeddingCharsPerItem = 4000000;
    private boolean abortIfEmbeddingUnavailable = true;
    private float semanticSimilarityMinScore = 0.70f;
    private int chunkSize = 3500;
    private int chunkOverlap = 350;
    private boolean enableLLMQueries = true;
    private boolean debugMode = false;
    private float chunkSimilarityThreshold = 0.70f;
    private int maxRetrievedChunks = 5;
    private float vectorSearchWeight = 0.5f;
    private float lexicalSearchWeight = 0.5f;
    private String llmProvider = "local";
    private String llmModel = "qwen3:8b";
    private String llmEndpoint = "http://localhost:11434/v1";
    private String llmApiKey = "";
    private int llmContextWindow = 12288;
    private String embeddingFilterMode = "whitelist";
    private String embeddingCategoryBlacklist = "^(Multimedia|Texts in System Folders|Temporary Internet Texts|Images in System Folders|Other files|XML Files|Georeferenced Files|OLE files|Links|Apple Artifacts|Databases|Compressed Archives|Programs and Libraries|Unallocated|File Slacks|Image Disks|Main Registry Files|Other Registry Files|USN Journal|Cell Towers|SIM Data|Power Events|Mobile Cards|Activities Sensor|Device Connectivity|Device Events|Networks Usage|Recognized Devices|Journeys|Fuzzy Data)$";
    private String embeddingCategoryWhitelist = "^(Documents|Spreadsheets|Presentations|Emails|Appointments|Email Attachments|Chats|SMS Messages|MMS Messages|Notes|Calls|Contacts|User Accounts|Passwords|Autofill|User Dictionaries|Calendar|Searches|Locations|Notifications|Wireless Networks|IP Connections|Applications Usage|Device Information|Registry Full Reports|Registry Custom Reports|Event Transcript|User Activities|Web Bookmarks|Mozilla Firefox Saved Session|TorTCFragment|GDrive File Entries|Internal Revenue of Brazil|Open Financial Exchange|Credit Cards|Financial Accounts|Transfers of Funds|Social Media Activities|File Downloads|File Uploads|Recordings|Extraction Summary|Scanned Documents|Audios|Internet History)$";
    private String embeddingMimeTypeBlacklist = "^(application/octet-stream|application/x-sqlite3|application/x-chrome-sqlite|application/x-firefox-places|application/x-safari-sqlite|application/x-safari-plist|application/x-edge-web-cache)$";
    private String embeddingMimeTypeWhitelist = "^(text/csv|text/tab-separated-values)$";
    private transient java.util.regex.Pattern embeddingCategoryBlacklistPattern;
    private transient java.util.regex.Pattern embeddingMimeTypeBlacklistPattern;
    private transient java.util.regex.Pattern embeddingCategoryWhitelistPattern;
    private transient java.util.regex.Pattern embeddingMimeTypeWhitelistPattern;
    private String embeddingHtmlPathBlacklist = "Temporary Internet|/Microsoft/Windows/INetCache|/Microsoft/Windows/INetCookies|Chrome/User Data/|Mozilla/Firefox/Profiles|Apple Computer/Safari|/Library/Safari|/Library/Caches/|/Library/Cookies/|/Library/Preferences/com[.]apple[.]Safari|chromium/Default/Cache|cache/mozilla/firefox|/Library/Application Support/Firefox/|/Cookies/|/vol_vol[0-9]*/Windows/|/Windows[.]old|Program Files|Arquivos de Programas|System Volume Information";
    private transient volatile java.util.regex.Pattern embeddingHtmlPathBlacklistPattern;
    private static final java.util.regex.Pattern HTML_MIME = java.util.regex.Pattern
            .compile("text/html|application/xhtml\\+xml", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Pre-computed sets of all matching category names (including all subcategories)
     * for whitelist and blacklist modes. Built once at startup from CategoryConfig.
     * O(1) contains() per item — zero allocations during processing.
     */
    private transient volatile java.util.Set<String> expandedCategoryWhitelistSet;
    private transient volatile java.util.Set<String> expandedCategoryBlacklistSet;

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

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    /**
     * Maximum cumulative characters allowed per HTTP sub-batch request.
     * Prevents HTTP payloads from exceeding GPU/VRAM memory limits or triggering
     * server timeouts when embedding many large chunks.
     * Default: 24000 (~6000 tokens).
     */
    public int getMaxBatchChars() {
        return maxBatchChars;
    }

    /**
     * Maximum characters of one item read into memory and embedded. Text beyond it
     * is still indexed for regular search, without embeddings. 0 or less: no limit.
     */
    public int getMaxEmbeddingCharsPerItem() {
        return maxEmbeddingCharsPerItem;
    }

    /** Whether processing must stop at startup when the embedding service is unavailable. */
    public boolean isAbortIfEmbeddingUnavailable() {
        return abortIfEmbeddingUnavailable;
    }

    /** Minimum cosine similarity for "Find Similar Documents (AI)". */
    public float getSemanticSimilarityMinScore() {
        return semanticSimilarityMinScore;
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

    /** Diagnosis: the AI Assistant keeps the fragments and prompt of each answer and links to them. */
    public boolean isDebugMode() {
        return debugMode;
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
     * Default: 0.5 (equal weights, standard RRF). Below 0.5, a fragment found only by the
     * vector search can never outrank the lexical candidates, even when ranked first.
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

    /** Environment variable with the API key; it takes precedence over llmApiKey of RAGConfig.txt. */
    public static final String API_KEY_ENV = "IPED_RAG_API_KEY";

    /**
     * API key of remote providers: the IPED_RAG_API_KEY environment variable when set, otherwise
     * llmApiKey of RAGConfig.txt, which is copied to the case folder.
     */
    public String getLlmApiKey() {
        return resolveApiKey(System.getenv(API_KEY_ENV), llmApiKey);
    }

    /** Whether the API key in use comes from RAGConfig.txt (and so is stored with the case). */
    public boolean isLlmApiKeyFromConfigFile() {
        return isBlank(System.getenv(API_KEY_ENV)) && !isBlank(llmApiKey);
    }

    static String resolveApiKey(String fromEnv, String fromConfig) {
        return isBlank(fromEnv) ? fromConfig : fromEnv.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public int getLlmContextWindow() {
        return llmContextWindow;
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
     * Builds the expanded category set for a given regex pattern by walking the full
     * CategoryConfig tree once at startup. Any category whose name OR any ancestor name
     * matches the pattern is added to the returned set.
     * Called once per config load — O(n) where n = total categories (~150-200).
     */
    private java.util.Set<String> buildExpandedCategorySet(java.util.regex.Pattern pattern) {
        java.util.Set<String> result = new java.util.HashSet<>();
        try {
            CategoryConfig catConfig = iped.engine.config.ConfigurationManager.get().findObject(CategoryConfig.class);
            if (catConfig != null) {
                collectMatchingCategories(catConfig.getRootCategory(), pattern, false, result);
            }
        } catch (Throwable t) {
            // Fallback for isolated environments (unit tests) where CategoryConfig is not yet loaded
        }
        return result;
    }

    /**
     * Recursively walks the category tree. A category is added to the result if
     * it or any of its ancestors matches the pattern (parent match propagates down).
     */
    private void collectMatchingCategories(iped.engine.data.Category category, java.util.regex.Pattern pattern, boolean parentMatched, java.util.Set<String> result) {
        if (category == null) return;
        boolean matched = parentMatched || (category.getName() != null && pattern.matcher(category.getName()).matches());
        if (matched && category.getName() != null) {
            result.add(category.getName());
        }
        for (iped.engine.data.Category child : category.getChildren()) {
            collectMatchingCategories(child, pattern, matched, result);
        }
    }

    /**
     * Returns the pre-computed whitelist category set, building it on first access.
     * Thread-safe via double-checked locking.
     */
    private java.util.Set<String> getExpandedCategoryWhitelistSet() {
        if (expandedCategoryWhitelistSet == null) {
            synchronized (this) {
                if (expandedCategoryWhitelistSet == null) {
                    expandedCategoryWhitelistSet = buildExpandedCategorySet(getEmbeddingCategoryWhitelistPattern());
                }
            }
        }
        return expandedCategoryWhitelistSet;
    }

    /**
     * Returns the pre-computed blacklist category set, building it on first access.
     * Thread-safe via double-checked locking.
     */
    private java.util.Set<String> getExpandedCategoryBlacklistSet() {
        if (expandedCategoryBlacklistSet == null) {
            synchronized (this) {
                if (expandedCategoryBlacklistSet == null) {
                    expandedCategoryBlacklistSet = buildExpandedCategorySet(getEmbeddingCategoryBlacklistPattern());
                }
            }
        }
        return expandedCategoryBlacklistSet;
    }

    /**
     * Set of categories whose synthetic sub-items (individual message bubbles, log entries,
     * browser history records) are excluded from RAG embeddings to avoid duplication and noise,
     * since IPED already generates consolidated HTML reports covering the complete context.
     */
    private static final java.util.Set<String> SYNTHETIC_SUBITEM_CATEGORIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "Instant Messages",
            "Internet History Entries",
            "Event Records",
            "Event Transcript Records",
            "User Activities Entries",
            "Chat Activities",
            "Shared Contacts"
    ));

    /**
     * Decides whether an IPED Item should have an embedding generated.
     * Checks if the item is a synthetic sub-item of a category that already has
     * a consolidated HTML report (e.g. individual WhatsApp bubbles are blocked;
     * only the full consolidated HTML chat report is embedded).
     */
    public boolean shouldEmbed(iped.data.IItem item) {
        if (item == null) {
            return false;
        }
        // Never embed empty 0-byte items
        if (item.getLength() != null && item.getLength() == 0) {
            return false;
        }
        String mimeType = item.getMediaType() != null ? item.getMediaType().toString() : "";
        java.util.Set<String> categories = item.getCategorySet();

        // Block individual extracted records (e.g. chat bubbles, history entries) whose consolidated HTML report is already embedded
        if (item.isSubItem() && categories != null) {
            for (String cat : categories) {
                if (SYNTHETIC_SUBITEM_CATEGORIES.contains(cat)) {
                    return false;
                }
            }
        }

        if (isHtmlInExcludedPath(mimeType, item.getPath())) {
            return false;
        }

        return shouldEmbed(categories, mimeType);
    }

    /**
     * HTML pages of browser caches and of system or program folders (help pages, .pak
     * archives of Chrome/Electron) are application assets, not user documents.
     */
    boolean isHtmlInExcludedPath(String mimeType, String path) {
        if (path == null || mimeType == null || !HTML_MIME.matcher(mimeType).matches()) {
            return false;
        }
        java.util.regex.Pattern pattern = embeddingHtmlPathBlacklistPattern;
        if (pattern == null) {
            String regex = embeddingHtmlPathBlacklist != null ? embeddingHtmlPathBlacklist.trim() : "";
            pattern = java.util.regex.Pattern.compile(regex.isEmpty() ? "$^" : regex,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            embeddingHtmlPathBlacklistPattern = pattern;
        }
        return pattern.matcher(path).find();
    }

    /**
     * Decides whether an item with the given categories and MIME type should have an
     * embedding generated.
     *
     * Whitelist mode logic:
     *   1. Category whitelist check: O(1) pre-computed HashSet.contains() — zero allocations,
     *      scales to billions of items without GC pressure.
     *   2. MIME whitelist fast-path: specific types (csv, tsv) are always allowed if category didn't match.
     *
     * Blacklist mode logic:
     *   1. MIME blacklist veto (absolute): binary MIME types (e.g. application/octet-stream) are blocked.
     *   2. Category blacklist veto.
     *   3. Otherwise allow.
     */
    public boolean shouldEmbed(java.util.Collection<String> categories, String mimeType) {
        if (categories == null || categories.isEmpty()) {
            return shouldEmbed((String) null, mimeType);
        }

        // MIME blacklist is an ABSOLUTE VETO in BOTH modes: those types are never embedded.
        if (mimeType != null && embeddingMimeTypeBlacklist != null && !embeddingMimeTypeBlacklist.trim().isEmpty()
                && getEmbeddingMimeTypeBlacklistPattern().matcher(mimeType).matches()) {
            return false;
        }

        if ("whitelist".equalsIgnoreCase(embeddingFilterMode)) {
            // Category check: O(1) HashSet.contains() if expanded set available, otherwise regex matcher fallback (unit tests)
            boolean categoryMatched = false;
            java.util.Set<String> allowed = getExpandedCategoryWhitelistSet();
            java.util.regex.Pattern catPattern = getEmbeddingCategoryWhitelistPattern();
            for (String cat : categories) {
                if (cat != null) {
                    if (!allowed.isEmpty() ? allowed.contains(cat) : catPattern.matcher(cat).matches()) {
                        categoryMatched = true;
                        break;
                    }
                }
            }
            if (categoryMatched) {
                return true;
            }
            // MIME whitelist fast-path: surgical types (csv, tsv) always allowed if category didn't match
            if (mimeType != null && embeddingMimeTypeWhitelist != null && !embeddingMimeTypeWhitelist.trim().isEmpty()
                    && getEmbeddingMimeTypeWhitelistPattern().matcher(mimeType).matches()) {
                return true;
            }
            return false;
        } else {
            // Blacklist mode: block if any category is blacklisted
            java.util.Set<String> blocked = getExpandedCategoryBlacklistSet();
            java.util.regex.Pattern catPattern = getEmbeddingCategoryBlacklistPattern();
            for (String cat : categories) {
                if (cat != null) {
                    if (!blocked.isEmpty() ? blocked.contains(cat) : catPattern.matcher(cat).matches()) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /**
     * Decides whether an item with a single category and MIME type should have an
     * embedding generated. Fallback used when item has no category set.
     * Applies the same MIME blacklist absolute veto as the Collection overload.
     */
    public boolean shouldEmbed(String category, String mimeType) {
        // MIME blacklist is an ABSOLUTE VETO in BOTH modes.
        if (mimeType != null && embeddingMimeTypeBlacklist != null && !embeddingMimeTypeBlacklist.trim().isEmpty()
                && getEmbeddingMimeTypeBlacklistPattern().matcher(mimeType).matches()) {
            return false;
        }
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

        val = props.getProperty("maxBatchChars");
        if (val != null) maxBatchChars = Integer.parseInt(val.trim());

        val = props.getProperty("maxEmbeddingCharsPerItem");
        if (val != null) maxEmbeddingCharsPerItem = Integer.parseInt(val.trim());

        val = props.getProperty("abortIfEmbeddingUnavailable");
        if (val != null) abortIfEmbeddingUnavailable = Boolean.parseBoolean(val.trim());

        val = props.getProperty("semanticSimilarityMinScore");
        if (val != null) semanticSimilarityMinScore = Float.parseFloat(val.trim());

        val = props.getProperty("chunkSize");
        if (val != null) chunkSize = Integer.parseInt(val.trim());

        val = props.getProperty("chunkOverlap");
        if (val != null) chunkOverlap = Integer.parseInt(val.trim());

        val = props.getProperty("enableLLMQueries");
        if (val != null) enableLLMQueries = Boolean.parseBoolean(val.trim());

        val = props.getProperty("debugMode");
        if (val != null) debugMode = Boolean.parseBoolean(val.trim());

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

        val = props.getProperty("embeddingFilterMode");
        if (val != null) embeddingFilterMode = val.trim();

        val = props.getProperty("embeddingCategoryBlacklist");
        if (val != null) {
            embeddingCategoryBlacklist = val.trim();
            embeddingCategoryBlacklistPattern = null;
            expandedCategoryBlacklistSet = null; // invalidate pre-computed set on config reload
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
            expandedCategoryWhitelistSet = null; // invalidate pre-computed set on config reload
        }

        val = props.getProperty("embeddingMimeTypeWhitelist");
        if (val != null) {
            embeddingMimeTypeWhitelist = val.trim();
            embeddingMimeTypeWhitelistPattern = null;
        }

        val = props.getProperty("embeddingHtmlPathBlacklist");
        if (val != null) {
            embeddingHtmlPathBlacklist = val.trim();
            embeddingHtmlPathBlacklistPattern = null;
        }
    }
}
