package iped.app.ai;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.config.AIAssistantConfig;
import iped.app.ui.App;

/**
 * LOCAL/INTRANET-ONLY AI Service for IPED
 * 
 * SECURITY GUARANTEES:
 * - Validates all endpoints are local/intranet before connection
 * - No internet connectivity - all data stays within organization
 * - Supports local model deployments (Ollama, LM Studio, vLLM, etc.)
 * - Can work completely offline
 * - No API keys or external authentication required
 * 
 * SUPPORTED PROVIDERS:
 * - Ollama (recommended): http://localhost:11434
 * - LM Studio: http://localhost:1234
 * - vLLM: http://localhost:8000
 * - Text Generation WebUI: http://localhost:5000
 * - Any OpenAI-compatible local API
 */
public class LocalAIAssistant {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalAIAssistant.class);
    
    private AIAssistantConfig config;
    private LocalLLMProvider provider;
    
    /**
     * Creates AI service with configuration
     */
    public LocalAIAssistant(AIAssistantConfig config) {
        this.config = config;
        this.provider = createProvider(config);
        LOGGER.info("AI Service initialized with provider: {}", config.getProvider());
    }
    
    /**
     * SECURITY: Validates that the API endpoint is local/intranet only
     */
    private boolean isLocalOrIntranet(String urlString) {
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            
            // Check for localhost by name
            if (host.equalsIgnoreCase("localhost") || 
                host.equals("127.0.0.1") || 
                host.equals("::1") ||
                host.equals("0:0:0:0:0:0:0:1")) {
                return true;
            }
            
            InetAddress addr;
            try {
                addr = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                LOGGER.error("Unable to resolve host: {}", host);
                return false;
            }
            
            // Check for loopback addresses
            if (addr.isLoopbackAddress()) {
                return true;
            }
            
            // If enforceLocalOnly is true, only allow loopback
            if (config.isEnforceLocalOnly() && !config.isAllowPrivateNetworks()) {
                LOGGER.warn("Private networks disabled, only localhost allowed");
                return false;
            }
            
            // Check for private IP ranges (intranet)
            if (config.isAllowPrivateNetworks()) {
                boolean isPrivate = addr.isSiteLocalAddress() ||  // 10.x.x.x, 172.16-31.x.x, 192.168.x.x
                                   addr.isLinkLocalAddress();    // 169.254.x.x
                if (isPrivate) {
                    return true;
                }
            }
            
            // All other addresses are rejected
            return false;
            
        } catch (Exception e) {
            LOGGER.error("Error validating URL locality: {}", urlString, e);
            return false;
        }
    }
    
    /**
     * Creates the appropriate local LLM provider based on configuration
     */
    private LocalLLMProvider createProvider(AIAssistantConfig config) {
        String apiUrl = config.getApiUrl();
        
        // CRITICAL SECURITY CHECK
        if (!isLocalOrIntranet(apiUrl)) {
            String message = String.format(
                "SECURITY VIOLATION: API URL is not local/intranet: %s\n" +
                "AI Assistant can only connect to local/intranet endpoints.\n" +
                "Allowed: localhost, 127.0.0.1, private IPs (if enabled)\n" +
                "Current settings: enforceLocalOnly=%s, allowPrivateNetworks=%s",
                apiUrl, config.isEnforceLocalOnly(), config.isAllowPrivateNetworks()
            );
            LOGGER.error(message);
            throw new SecurityException(message);
        }
        
        LOGGER.info("Validated local AI endpoint: {}", apiUrl);
        
        // Create provider based on type
        switch (config.getProvider()) {
            case OLLAMA:
                return new OllamaProvider(config);
            case LM_STUDIO:
                return new LMStudioProvider(config);
            case VLLM:
                return new VLLMProvider(config);
            case TEXT_GEN_WEBUI:
                return new TextGenWebUIProvider(config);
            case CUSTOM_LOCAL:
            default:
                return new GenericOpenAIProvider(config);
        }
    }
    
    /**
     * Sends a message to the local LLM with context
     */
    public String sendMessage(String message, AIAssistantContext context) throws IOException {
        // Build comprehensive context
        StringBuilder fullPrompt = new StringBuilder();
        
        // Add system prompt if configured
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isEmpty()) {
            fullPrompt.append("System: ").append(config.getSystemPrompt()).append("\n\n");
        }
        
        // Add context information
        fullPrompt.append("=== Context Information ===\n");
        fullPrompt.append("Selected items: ").append(context.getSelectedItems().size()).append("\n");
        
        if (context.getSearchQuery() != null && !context.getSearchQuery().isEmpty()) {
            fullPrompt.append("Current search query: ").append(context.getSearchQuery()).append("\n");
        }
        
        // Add viewer content if available
        if (context.hasViewerContent()) {
            fullPrompt.append("\n=== Currently Displayed Content ===\n");
            fullPrompt.append(context.getViewerContent()).append("\n");
        }
        
        // Add conversation history if enabled
        if (config.isEnableChatHistory() && context.getHistorySize() > 0) {
            fullPrompt.append("\n=== Conversation History ===\n");
            List<AIAssistantContext.ConversationMessage> history = context.getConversationHistory();
            // Limit history to avoid token overflow
            int startIdx = Math.max(0, history.size() - 10);
            for (int i = startIdx; i < history.size(); i++) {
                AIAssistantContext.ConversationMessage msg = history.get(i);
                fullPrompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        
        fullPrompt.append("\n=== User Question ===\n").append(message);
        
        // Generate response
        String response = provider.generate(fullPrompt.toString());
        
        // Update conversation history
        if (config.isEnableChatHistory()) {
            context.addToHistory("user", message);
            context.addToHistory("assistant", response);
            context.limitHistory(config.getMaxChatHistorySize());
        }
        
        return response;
    }
    
    /**
     * Analyzes chat conversations to identify subjects/topics
     */
    public String identifyChatSubject(AIAssistantContext context) throws IOException {
        List<IItemId> items = context.getSelectedItems();
        if (items.isEmpty()) {
            return "No items selected for analysis.";
        }
        
        int maxItems = Math.min(items.size(), config.getMaxContextItems());
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("=== FORENSICS TASK: Chat Subject Identification ===\n\n");
        prompt.append("You are a digital forensics analyst examining chat conversations.\n");
        prompt.append("Analyze the following ").append(maxItems).append(" messages and identify:\n");
        prompt.append("1. Main subjects/topics discussed\n");
        prompt.append("2. Key participants and their roles\n");
        prompt.append("3. Timeline of events (if relevant)\n");
        prompt.append("4. Any suspicious or noteworthy patterns\n");
        prompt.append("5. Potential investigative leads\n\n");
        
        prompt.append("=== CHAT MESSAGES ===\n\n");
        
        for (int i = 0; i < maxItems; i++) {
            try {
                IItemId itemId = items.get(i);
                IItem item = App.get().appCase.getItemByItemId(itemId);
                
                String text = extractTextContent(item);
                if (text != null && !text.isEmpty()) {
                    // Limit text length
                    if (text.length() > config.getMaxTextLength()) {
                        text = text.substring(0, config.getMaxTextLength()) + "\n[... truncated ...]";
                    }
                    
                    prompt.append("--- Message ").append(i + 1).append(" of ").append(maxItems).append(" ---\n");
                    prompt.append("File: ").append(item.getName()).append("\n");
                    
                    // Add available date information
                    if (item.getModDate() != null) {
                        prompt.append("Modified: ").append(item.getModDate()).append("\n");
                    } else if (item.getCreationDate() != null) {
                        prompt.append("Created: ").append(item.getCreationDate()).append("\n");
                    }
                    
                    prompt.append("Content:\n").append(text).append("\n\n");
                }
            } catch (Exception e) {
                LOGGER.error("Error processing item {}", i, e);
                prompt.append("--- Message ").append(i + 1).append(" ---\n");
                prompt.append("[Error reading message: ").append(e.getMessage()).append("]\n\n");
            }
        }
        
        prompt.append("=== ANALYSIS REQUEST ===\n");
        prompt.append("Provide a structured analysis of these chat conversations.\n");
        prompt.append("Focus on forensically relevant information.\n");
        
        return provider.generate(prompt.toString());
    }
    
    /**
     * Summarizes selected documents
     */
    public String summarizeDocument(AIAssistantContext context) throws IOException {
        List<IItemId> items = context.getSelectedItems();
        if (items.isEmpty()) {
            return "No documents selected for summarization.";
        }
        
        int maxItems = Math.min(items.size(), config.getMaxContextItems());
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("=== FORENSICS TASK: Document Summarization ===\n\n");
        prompt.append("You are a digital forensics analyst summarizing documents.\n");
        prompt.append("For each document, provide:\n");
        prompt.append("1. Brief summary of main content\n");
        prompt.append("2. Document type and purpose\n");
        prompt.append("3. Key dates, names, and entities\n");
        prompt.append("4. Forensically relevant information\n");
        prompt.append("5. Potential connections to investigation\n\n");
        
        prompt.append("=== DOCUMENTS ===\n\n");
        
        for (int i = 0; i < maxItems; i++) {
            try {
                IItemId itemId = items.get(i);
                IItem item = App.get().appCase.getItemByItemId(itemId);
                
                String text = extractTextContent(item);
                if (text != null && !text.isEmpty()) {
                    if (text.length() > config.getMaxTextLength()) {
                        text = text.substring(0, config.getMaxTextLength()) + "\n[... truncated ...]";
                    }
                    
                    prompt.append("--- Document ").append(i + 1).append(" of ").append(maxItems).append(" ---\n");
                    prompt.append("Name: ").append(item.getName()).append("\n");
                    prompt.append("Type: ").append(item.getType() != null ? item.getType().toString() : "Unknown").append("\n");
                    prompt.append("Size: ").append(formatSize(item.getLength())).append("\n");
                    
                    // Add available date information
                    if (item.getModDate() != null) {
                        prompt.append("Modified: ").append(item.getModDate()).append("\n");
                    }
                    if (item.getCreationDate() != null) {
                        prompt.append("Created: ").append(item.getCreationDate()).append("\n");
                    }
                    
                    prompt.append("\nContent:\n").append(text).append("\n\n");
                }
            } catch (Exception e) {
                LOGGER.error("Error processing document {}", i, e);
                prompt.append("--- Document ").append(i + 1).append(" ---\n");
                prompt.append("[Error reading document: ").append(e.getMessage()).append("]\n\n");
            }
        }
        
        prompt.append("=== ANALYSIS REQUEST ===\n");
        prompt.append("Provide concise summaries focusing on forensically relevant information.\n");
        
        return provider.generate(prompt.toString());
    }
    
    /**
     * Finds patterns across selected items
     */
    public String findPatterns(AIAssistantContext context) throws IOException {
        List<IItemId> items = context.getSelectedItems();
        if (items.isEmpty()) {
            return "No items selected for pattern analysis.";
        }
        
        int maxItems = Math.min(items.size(), config.getMaxContextItems());
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("=== FORENSICS TASK: Pattern Detection ===\n\n");
        prompt.append("You are a digital forensics analyst looking for patterns.\n");
        prompt.append("Analyze ").append(maxItems).append(" items and identify:\n");
        prompt.append("1. Recurring themes or topics\n");
        prompt.append("2. Temporal patterns (timing, frequency)\n");
        prompt.append("3. Communication patterns (who talks to whom)\n");
        prompt.append("4. Behavioral patterns\n");
        prompt.append("5. Anomalies or unusual activities\n");
        prompt.append("6. Connections between items\n\n");
        
        prompt.append("=== DATA ITEMS ===\n\n");
        
        for (int i = 0; i < maxItems; i++) {
            try {
                IItemId itemId = items.get(i);
                IItem item = App.get().appCase.getItemByItemId(itemId);
                
                prompt.append("--- Item ").append(i + 1).append(" ---\n");
                prompt.append("Name: ").append(item.getName()).append("\n");
                prompt.append("Type: ").append(item.getType() != null ? item.getType().toString() : "Unknown").append("\n");
                
                // Add available date information
                if (item.getModDate() != null) {
                    prompt.append("Modified: ").append(item.getModDate()).append("\n");
                }
                if (item.getCreationDate() != null) {
                    prompt.append("Created: ").append(item.getCreationDate()).append("\n");
                }
                if (item.getAccessDate() != null) {
                    prompt.append("Accessed: ").append(item.getAccessDate()).append("\n");
                }
                
                String text = extractTextContent(item);
                if (text != null && !text.isEmpty()) {
                    if (text.length() > config.getMaxTextLength()) {
                        text = text.substring(0, config.getMaxTextLength()) + "\n[... truncated ...]";
                    }
                    prompt.append("Content:\n").append(text).append("\n");
                }
                prompt.append("\n");
            } catch (Exception e) {
                LOGGER.error("Error processing item {}", i, e);
            }
        }
        
        prompt.append("=== ANALYSIS REQUEST ===\n");
        prompt.append("Identify significant patterns and their forensic implications.\n");
        
        return provider.generate(prompt.toString());
    }
    
    /**
     * Classifies content of selected items
     */
    public String classifyContent(AIAssistantContext context) throws IOException {
        List<IItemId> items = context.getSelectedItems();
        if (items.isEmpty()) {
            return "No items selected for classification.";
        }
        
        int maxItems = Math.min(items.size(), config.getMaxContextItems());
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("=== FORENSICS TASK: Content Classification ===\n\n");
        prompt.append("You are a digital forensics analyst classifying evidence.\n");
        prompt.append("For each item, determine:\n");
        prompt.append("1. Content category (personal, business, financial, etc.)\n");
        prompt.append("2. Sensitivity level (public, confidential, highly sensitive)\n");
        prompt.append("3. Relevance to investigation (high, medium, low)\n");
        prompt.append("4. Type of evidence (communication, document, media, etc.)\n");
        prompt.append("5. Recommended actions or tags\n\n");
        
        prompt.append("=== ITEMS TO CLASSIFY ===\n\n");
        
        for (int i = 0; i < maxItems; i++) {
            try {
                IItemId itemId = items.get(i);
                IItem item = App.get().appCase.getItemByItemId(itemId);
                
                prompt.append("--- Item ").append(i + 1).append(" ---\n");
                prompt.append("File: ").append(item.getName()).append("\n");
                prompt.append("Type: ").append(item.getType() != null ? item.getType().toString() : "Unknown").append("\n");
                
                String text = extractTextContent(item);
                if (text != null && !text.isEmpty()) {
                    if (text.length() > config.getMaxTextLength()) {
                        text = text.substring(0, config.getMaxTextLength()) + "\n[... truncated ...]";
                    }
                    prompt.append("Content:\n").append(text).append("\n");
                }
                prompt.append("\n");
            } catch (Exception e) {
                LOGGER.error("Error processing item {}", i, e);
            }
        }
        
        prompt.append("=== CLASSIFICATION REQUEST ===\n");
        prompt.append("Provide structured classification for forensic case management.\n");
        
        return provider.generate(prompt.toString());
    }
    
    /**
     * Extracts text content from an item
     */
    private String extractTextContent(IItem item) {
        try {
            // Try to read text using Reader
            try {
                LOGGER.info("Trying to read Text using getTextReader for: {}", item.getName());
                Reader textReader = item.getTextReader();
                if (textReader != null) {
                    StringBuilder sb = new StringBuilder();
                    char[] buffer = new char[8192];
                    int read;
                    int totalRead = 0;
                    int maxChars = config.getMaxTextLength();
                    
                    while ((read = textReader.read(buffer)) != -1 && totalRead < maxChars) {
                        int toAppend = Math.min(read, maxChars - totalRead);
                        sb.append(buffer, 0, toAppend);
                        totalRead += toAppend;
                    }
                    textReader.close();
                    
                    if (sb.length() > 0) {
                        String startContent = sb.toString().substring(0, Math.min(100, sb.length())).replace("\n", " ").replace("\r", " ");
                        LOGGER.info("Successfully read text using TextReader (see content): {}", startContent);
                        return sb.toString();
                    }
                    LOGGER.info("Could not read text using TextReader for: {}", item.getName());
                }
                else {
                    LOGGER.info("Trying to read content using getBufferedInputStream for: {}", item.getName());
                    // Try to read text using BufferedInputStream
                    try (BufferedInputStream inputStream = item.getBufferedInputStream()) {
                        byte[] rawBytes = inputStream.readNBytes(config.getMaxTextLength());
                        if (rawBytes.length > 0) {
                            String text = new String(rawBytes, StandardCharsets.UTF_8);
                            LOGGER.info("Read {} bytes from item: {}", rawBytes.length, item.getName());
                            LOGGER.info("First 50 chars: {}", text.substring(0, Math.min(50, text.length())));
                            return text;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.info("Could not read text using TextReader for: {}", item.getName());
            }

            LOGGER.info("Trying to read Metadata using for: {}", item.getName());
            // Try metadata fields if available (check if getMetadata exists)
            try {
                Metadata metadata = item.getMetadata();
                if (metadata != null) {
                    String content = metadata.get("Message-Body");
                    if (content != null && !content.isEmpty()) {
                        return content;
                    }
                    
                    content = metadata.get("text");
                    if (content != null && !content.isEmpty()) {
                        return content;
                    }
                }
            } catch (Exception e) {
                // getMetadata() might not be available in all implementations
                LOGGER.debug("Could not access metadata for: {}", item.getName());
            }
            
            LOGGER.info("[No text content available for: {}]", item.getName());
            // Return basic info if no text available
            return String.format("[No text content available for: %s]", item.getName());
            
        } catch (Exception e) {
            LOGGER.error("Error extracting text from item: {}", item.getName(), e);
            return "[Error extracting text]";
        }
    }
    
    /**
     * Formats file size for display
     */
    private String formatSize(Long size) {
        if (size == null) return "Unknown";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}

// ============================================================================
// Provider Interface
// ============================================================================

/**
 * Interface for local LLM providers
 */
interface LocalLLMProvider {
    /**
     * Generates a response from the LLM
     * @param prompt The prompt to send
     * @return The generated response
     * @throws IOException if connection or generation fails
     */
    String generate(String prompt) throws IOException;
}

// ============================================================================
// Ollama Provider
// ============================================================================

/**
 * Provider for Ollama (https://ollama.ai)
 * API endpoint: http://localhost:11434/api/generate
 */
class OllamaProvider implements LocalLLMProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaProvider.class);
    private AIAssistantConfig config;
    
    public OllamaProvider(AIAssistantConfig config) {
        this.config = config;
        LOGGER.info("Ollama provider initialized: model={}, url={}", 
                   config.getModelName(), config.getApiUrl());
    }
    
    @Override
    public String generate(String prompt) throws IOException {
        String apiUrl = config.getApiUrl();
        
        // Build Ollama request
        JSONObject request = new JSONObject();
        request.put("model", config.getModelName());
        request.put("prompt", prompt);
        request.put("stream", false);
        request.put("options", createOptions());
        
        // Send request
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(config.getConnectionTimeoutMs());
            conn.setReadTimeout(config.getReadTimeoutMs());
            
            // Write request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(request.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Ollama returned error code: " + responseCode);
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // Parse response
            JSONParser parser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());
            String result = (String) jsonResponse.get("response");
            
            if (result == null) {
                throw new IOException("No response field in Ollama output");
            }
            
            return result;
            
        } catch (ParseException e) {
            throw new IOException("Error parsing Ollama response", e);
        } catch (IOException e) {
            LOGGER.error("Error communicating with Ollama", e);
            throw new IOException("Ollama connection failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    private JSONObject createOptions() {
        JSONObject options = new JSONObject();
        options.put("temperature", config.getTemperature());
        options.put("num_predict", config.getMaxTokens());
        return options;
    }
}

// ============================================================================
// LM Studio Provider
// ============================================================================

/**
 * Provider for LM Studio (https://lmstudio.ai)
 * API endpoint: http://localhost:1234/v1/chat/completions
 * Uses OpenAI-compatible API
 */
class LMStudioProvider implements LocalLLMProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(LMStudioProvider.class);
    private AIAssistantConfig config;
    
    public LMStudioProvider(AIAssistantConfig config) {
        this.config = config;
        LOGGER.info("LM Studio provider initialized: model={}, url={}", 
                   config.getModelName(), config.getApiUrl());
    }
    
    @Override
    public String generate(String prompt) throws IOException {
        return new GenericOpenAIProvider(config).generate(prompt);
    }
}

// ============================================================================
// vLLM Provider
// ============================================================================

/**
 * Provider for vLLM (https://github.com/vllm-project/vllm)
 * API endpoint: http://localhost:8000/v1/chat/completions
 * Uses OpenAI-compatible API
 */
class VLLMProvider implements LocalLLMProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(VLLMProvider.class);
    private AIAssistantConfig config;
    
    public VLLMProvider(AIAssistantConfig config) {
        this.config = config;
        LOGGER.info("vLLM provider initialized: model={}, url={}", 
                   config.getModelName(), config.getApiUrl());
    }
    
    @Override
    public String generate(String prompt) throws IOException {
        return new GenericOpenAIProvider(config).generate(prompt);
    }
}

// ============================================================================
// Text Generation WebUI Provider
// ============================================================================

/**
 * Provider for Text Generation WebUI / oobabooga
 * (https://github.com/oobabooga/text-generation-webui)
 * API endpoint: http://localhost:5000/api/v1/generate
 */
class TextGenWebUIProvider implements LocalLLMProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextGenWebUIProvider.class);
    private AIAssistantConfig config;
    
    public TextGenWebUIProvider(AIAssistantConfig config) {
        this.config = config;
        LOGGER.info("Text Generation WebUI provider initialized: url={}", 
                   config.getApiUrl());
    }
    
    @Override
    public String generate(String prompt) throws IOException {
        String apiUrl = config.getApiUrl();
        
        // Build request
        JSONObject request = new JSONObject();
        request.put("prompt", prompt);
        request.put("max_new_tokens", config.getMaxTokens());
        request.put("temperature", config.getTemperature());
        request.put("top_p", 0.9);
        request.put("typical_p", 1.0);
        request.put("repetition_penalty", 1.1);
        
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(config.getConnectionTimeoutMs());
            conn.setReadTimeout(config.getReadTimeoutMs());
            
            // Write request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(request.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("TextGen WebUI returned error code: " + responseCode);
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // Parse response
            JSONParser parser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());
            JSONArray results = (JSONArray) jsonResponse.get("results");
            
            if (results == null || results.isEmpty()) {
                throw new IOException("No results in TextGen WebUI response");
            }
            
            JSONObject firstResult = (JSONObject) results.get(0);
            return (String) firstResult.get("text");
            
        } catch (ParseException e) {
            throw new IOException("Error parsing TextGen WebUI response", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

// ============================================================================
// Generic OpenAI-Compatible Provider
// ============================================================================

/**
 * Generic provider for OpenAI-compatible APIs
 * Works with LM Studio, vLLM, LocalAI, and other compatible services
 */
class GenericOpenAIProvider implements LocalLLMProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericOpenAIProvider.class);
    private AIAssistantConfig config;
    
    public GenericOpenAIProvider(AIAssistantConfig config) {
        this.config = config;
        LOGGER.info("Generic OpenAI provider initialized: model={}, url={}", 
                   config.getModelName(), config.getApiUrl());
    }
    
    @Override
    public String generate(String prompt) throws IOException {
        String apiUrl = config.getApiUrl();
        
        // Build OpenAI-style chat completion request
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        
        JSONObject request = new JSONObject();
        request.put("model", config.getModelName());
        request.put("messages", messages);
        request.put("temperature", config.getTemperature());
        request.put("max_tokens", config.getMaxTokens());
        
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(config.getConnectionTimeoutMs());
            conn.setReadTimeout(config.getReadTimeoutMs());
            
            // Write request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(request.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                // Try to read error message
                StringBuilder errorMsg = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorMsg.append(line);
                    }
                } catch (Exception e) {
                    // Ignore
                }
                throw new IOException("API returned error " + responseCode + ": " + errorMsg);
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // Parse response
            JSONParser parser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());
            JSONArray choices = (JSONArray) jsonResponse.get("choices");
            
            if (choices == null || choices.isEmpty()) {
                throw new IOException("No choices in API response");
            }
            
            JSONObject firstChoice = (JSONObject) choices.get(0);
            JSONObject messageObj = (JSONObject) firstChoice.get("message");
            
            if (messageObj == null) {
                throw new IOException("No message in choice");
            }
            
            return (String) messageObj.get("content");
            
        } catch (ParseException e) {
            throw new IOException("Error parsing API response", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}