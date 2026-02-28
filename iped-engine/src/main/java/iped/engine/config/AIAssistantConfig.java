package iped.engine.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import iped.utils.UTF8Properties;

/**
 * Configuration for AI Assistant integration
 * Focused on local/intranet deployment only for data security
 */
public class AIAssistantConfig extends AbstractPropertiesConfigurable {
    
    private static final long serialVersionUID = 1L;
    
    public static final String CONFIG_FILE = "AIAssistantConfig.txt";
    
    // AI Provider options - LOCAL ONLY
    public enum AIProvider {
        OLLAMA,          // Default: http://localhost:11434
        LM_STUDIO,       // Default: http://localhost:1234
        VLLM,            // Default: http://localhost:8000
        TEXT_GEN_WEBUI,  // Default: http://localhost:5000
        CUSTOM_LOCAL     // Custom local endpoint
    }
    
    private boolean enabled = false;
    private AIProvider provider = AIProvider.OLLAMA;
    
    // API Configuration
    private String apiUrl = "";
    private String modelName = "llama3.2";
    
    // Behavior settings
    private int maxTokens = 2048;
    private double temperature = 0.7;
    private int maxContextItems = 10;
    private int maxTextLength = 10000;
    private int connectionTimeoutMs = 5000;
    private int readTimeoutMs = 120000;
    
    // UI Settings
    private int panelWidth = 450;
    private boolean autoHideOnFocusLost = false;
    
    // Security Settings
    private boolean enforceLocalOnly = true;
    private boolean allowPrivateNetworks = true;
    
    // Advanced Settings
    private boolean enableChatHistory = true;
    private int maxChatHistorySize = 50;
    private boolean streamResponse = false;
    private String systemPrompt = "";

    /**
     * Filter to locate the configuration file, following AnalysisConfig pattern
     */
    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    void processProperties(UTF8Properties properties) {
        
        String value = properties.getProperty("enabled");
        if (value != null) {
            enabled = Boolean.parseBoolean(value.trim());
        }
        
        value = properties.getProperty("provider");
        if (value != null && !value.trim().isEmpty()) {
            provider = AIProvider.valueOf(value.trim().toUpperCase());
        }
        
        value = properties.getProperty("apiUrl");
        if (value != null && !value.trim().isEmpty()) {
            apiUrl = value.trim();
        } else {
            apiUrl = getDefaultApiUrl(provider);
        }
        
        value = properties.getProperty("modelName");
        if (value != null && !value.trim().isEmpty()) {
            modelName = value.trim();
        }
        
        value = properties.getProperty("maxTokens");
        if (value != null && !value.trim().isEmpty()) {
            maxTokens = Integer.parseInt(value.trim());
        }
        
        value = properties.getProperty("temperature");
        if (value != null && !value.trim().isEmpty()) {
            temperature = Double.parseDouble(value.trim());
        }
        
        value = properties.getProperty("maxContextItems");
        if (value != null && !value.trim().isEmpty()) {
            maxContextItems = Integer.parseInt(value.trim());
        }
        
        value = properties.getProperty("maxTextLength");
        if (value != null && !value.trim().isEmpty()) {
            maxTextLength = Integer.parseInt(value.trim());
        }
        
        value = properties.getProperty("connectionTimeoutMs");
        if (value != null && !value.trim().isEmpty()) {
            connectionTimeoutMs = Integer.parseInt(value.trim());
        }
        
        value = properties.getProperty("readTimeoutMs");
        if (value != null && !value.trim().isEmpty()) {
            readTimeoutMs = Integer.parseInt(value.trim());
        }
        
        value = properties.getProperty("panelWidth");
        if (value != null && !value.trim().isEmpty()) {
            panelWidth = Integer.parseInt(value.trim());
        }
        
        value = properties.getProperty("autoHideOnFocusLost");
        if (value != null) {
            autoHideOnFocusLost = Boolean.parseBoolean(value.trim());
        }
        
        value = properties.getProperty("enforceLocalOnly");
        if (value != null) {
            enforceLocalOnly = Boolean.parseBoolean(value.trim());
        }
        
        value = properties.getProperty("allowPrivateNetworks");
        if (value != null) {
            allowPrivateNetworks = Boolean.parseBoolean(value.trim());
        }
        
        value = properties.getProperty("enableChatHistory");
        if (value != null) {
            enableChatHistory = Boolean.parseBoolean(value.trim());
        }
        
        value = properties.getProperty("maxChatHistorySize");
        if (value != null && !value.trim().isEmpty()) {
            maxChatHistorySize = Integer.parseInt(value.trim());
        }
        
        value = properties.getProperty("streamResponse");
        if (value != null) {
            streamResponse = Boolean.parseBoolean(value.trim());
        }
        
        value = properties.getProperty("systemPrompt");
        if (value != null) {
            systemPrompt = value.trim();
        }
    }
    
    private String getDefaultApiUrl(AIProvider provider) {
        switch (provider) {
            case OLLAMA:
                return "http://localhost:11434/api/generate";
            case LM_STUDIO:
                return "http://localhost:1234/v1/chat/completions";
            case VLLM:
                return "http://localhost:8000/v1/chat/completions";
            case TEXT_GEN_WEBUI:
                return "http://localhost:5000/api/v1/generate";
            case CUSTOM_LOCAL:
            default:
                return "http://localhost:11434/api/generate";
        }
    }
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public AIProvider getProvider() { return provider; }
    public String getApiUrl() { return apiUrl; }
    public String getModelName() { return modelName; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }
    public int getMaxContextItems() { return maxContextItems; }
    public int getMaxTextLength() { return maxTextLength; }
    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public int getPanelWidth() { return panelWidth; }
    public boolean isAutoHideOnFocusLost() { return autoHideOnFocusLost; }
    public boolean isEnforceLocalOnly() { return enforceLocalOnly; }
    public boolean isAllowPrivateNetworks() { return allowPrivateNetworks; }
    public boolean isEnableChatHistory() { return enableChatHistory; }
    public int getMaxChatHistorySize() { return maxChatHistorySize; }
    public boolean isStreamResponse() { return streamResponse; }
    public String getSystemPrompt() { return systemPrompt; }
}