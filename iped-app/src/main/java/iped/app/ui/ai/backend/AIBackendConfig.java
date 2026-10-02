package iped.app.ui.ai.backend;

/**
 * An immutable configuration object that holds the necessary connection details 
 * for communicating with the AI Large Language Model (LLM) backend.
 * <p>
 * This class encapsulates the endpoint URL and authentication credentials. 
 * By passing this object to the backend services rather than individual string 
 * arguments, the system remains flexible and easier to maintain.
 * </p>
 */
public class AIBackendConfig {
    
    // The root URL of the AI API endpoint 
    private final String baseUrl;
    
    // The authentication key required to access the AI service. 
    private final String apiKey;

    public AIBackendConfig(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * Command-Line Configuration (Current)
     * <p>
     * Currently, this loads the configuration from Java System Properties 
     * (looks for terminal flags passed when IPED starts up)
     * </p>
     * <p>
     * If no flags are provided, it safely defaults to a local developer setup.
     * </p>
     * TODO: IPED Global Configuration Integration
     * <p>This method should eventually be replaced or updated to read directly
     * from IPED's configuration files </p>
     * @return A fully initialized AIBackendConfig.
     */
    public static AIBackendConfig loadFromSystemProperties() {
        // Default to local SARD backend port 8000
        String url = System.getProperty("iped.ai.url", "http://localhost:8000");
        String apiKey = System.getProperty("iped.ai.key", "change-me");
        return new AIBackendConfig(url, apiKey);
    }
}