package iped.app.ai;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.AIAssistantConfig;
import iped.engine.config.ConfigurationManager;

/**
 * Main AI service interface for IPED
 * Handles initialization and delegates to local AI service
 */
public class AIAssistantService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AIAssistantService.class);
    
    private LocalAIAssistant localAIAssistant;
    private AIAssistantConfig config;
    private boolean initialized = false;
    
    public AIAssistantService() {
        try {
            config = ConfigurationManager.get().findObject(AIAssistantConfig.class);
                        
            if (config == null) {
                LOGGER.warn("AIAssistantConfig not found in ConfigurationManager, using defaults");
                config = new AIAssistantConfig();
            }
            
            initialize();
            
        } catch (Exception e) {
            LOGGER.error("Failed to load AI Assistant configuration", e);
            // Create default config as fallback
            config = new AIAssistantConfig();
        }
    }
    
    public AIAssistantService(AIAssistantConfig config) {
        this.config = config;
        initialize();
    }
    
    /**
     * Initializes the AI service with current configuration
     */
    private void initialize() {
        if (!config.isEnabled()) {
            LOGGER.info("AI Assistant is disabled in configuration");
            initialized = false;
            return;
        }
        
        try {
            localAIAssistant = new LocalAIAssistant(config);
            initialized = true;
            LOGGER.info("AI Assistant initialized successfully");
            LOGGER.info("Provider: {}, Model: {}, Endpoint: {}", 
                       config.getProvider(), 
                       config.getModelName(),
                       config.getApiUrl());
        } catch (SecurityException e) {
            LOGGER.error("SECURITY ERROR: {}", e.getMessage());
            initialized = false;
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize AI Assistant", e);
            initialized = false;
        }
    }
    
    /**
     * Checks if the service is properly initialized and ready
     */
    public boolean isAvailable() {
        return initialized && config.isEnabled();
    }
    
    /**
     * Gets the current configuration
     */
    public AIAssistantConfig getConfig() {
        return config;
    }
    
    /**
     * Sends a message to the AI and returns the response
     */
    public String sendMessage(String message, AIAssistantContext context) throws IOException {
        if (!isAvailable()) {
            throw new IOException("AI Assistant is not available. Check configuration and logs.");
        }
        
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        
        if (context == null) {
            context = new AIAssistantContext();
        }
        
        try {
            LOGGER.debug("Sending message to AI: {} chars, {} selected items", 
                        message.length(), context.getSelectedItems().size());
            
            String response = localAIAssistant.sendMessage(message, context);
            
            LOGGER.debug("Received AI response: {} chars", response.length());
            
            return response;
            
        } catch (IOException e) {
            LOGGER.error("Error communicating with AI service", e);
            throw new IOException("AI communication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Identifies the subject of chat conversations
     */
    public String identifyChatSubject(AIAssistantContext context) throws IOException {
        if (!isAvailable()) {
            throw new IOException("AI Assistant is not available");
        }
        
        if (context == null || context.getSelectedItems().isEmpty()) {
            return "No chat items selected for analysis.";
        }
        
        try {
            LOGGER.info("Analyzing chat subjects for {} items", context.getSelectedItems().size());
            return localAIAssistant.identifyChatSubject(context);
        } catch (IOException e) {
            LOGGER.error("Error analyzing chat subjects", e);
            throw new IOException("Chat analysis failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Summarizes documents
     */
    public String summarizeDocument(AIAssistantContext context) throws IOException {
        if (!isAvailable()) {
            throw new IOException("AI Assistant is not available");
        }
        
        if (context == null || context.getSelectedItems().isEmpty()) {
            return "No documents selected for summarization.";
        }
        
        try {
            LOGGER.info("Summarizing {} documents", context.getSelectedItems().size());
            return localAIAssistant.summarizeDocument(context);
        } catch (IOException e) {
            LOGGER.error("Error summarizing documents", e);
            throw new IOException("Summarization failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Finds patterns in data
     */
    public String findPatterns(AIAssistantContext context) throws IOException {
        if (!isAvailable()) {
            throw new IOException("AI Assistant is not available");
        }
        
        if (context == null || context.getSelectedItems().isEmpty()) {
            return "No items selected for pattern analysis.";
        }
        
        try {
            LOGGER.info("Finding patterns in {} items", context.getSelectedItems().size());
            return localAIAssistant.findPatterns(context);
        } catch (IOException e) {
            LOGGER.error("Error finding patterns", e);
            throw new IOException("Pattern analysis failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Classifies content
     */
    public String classifyContent(AIAssistantContext context) throws IOException {
        if (!isAvailable()) {
            throw new IOException("AI Assistant is not available");
        }
        
        if (context == null || context.getSelectedItems().isEmpty()) {
            return "No items selected for classification.";
        }
        
        try {
            LOGGER.info("Classifying {} items", context.getSelectedItems().size());
            return localAIAssistant.classifyContent(context);
        } catch (IOException e) {
            LOGGER.error("Error classifying content", e);
            throw new IOException("Classification failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Tests connectivity to the AI service
     * @return true if connection successful, false otherwise
     */
    public boolean testConnection() {
        if (!isAvailable()) {
            LOGGER.warn("Cannot test connection - service not available");
            return false;
        }
        
        try {
            AIAssistantContext context = new AIAssistantContext();
            String response = sendMessage("Hello, respond with 'OK' if you can read this.", context);
            LOGGER.info("Connection test successful. Response: {}", 
                       response.substring(0, Math.min(50, response.length())));
            return true;
        } catch (Exception e) {
            LOGGER.error("Connection test failed", e);
            return false;
        }
    }
    
    /**
     * Gets service status information
     */
    public String getStatusInfo() {
        StringBuilder status = new StringBuilder();
        status.append("AI Assistant Status:\n");
        status.append("- Enabled: ").append(config.isEnabled()).append("\n");
        status.append("- Initialized: ").append(initialized).append("\n");
        
        if (initialized) {
            status.append("- Provider: ").append(config.getProvider()).append("\n");
            status.append("- Model: ").append(config.getModelName()).append("\n");
            status.append("- Endpoint: ").append(config.getApiUrl()).append("\n");
            status.append("- Max Tokens: ").append(config.getMaxTokens()).append("\n");
            status.append("- Temperature: ").append(config.getTemperature()).append("\n");
        }
        
        return status.toString();
    }
}