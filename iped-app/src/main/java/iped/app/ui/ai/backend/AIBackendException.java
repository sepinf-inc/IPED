package iped.app.ui.ai.backend;

/**
 * A custom checked exception representing errors that occur within the AI backend layer.
 * <p>
 * This exception serves as a generic wrapper for various lower-level failures that might 
 * happen during AI processing, such as network timeouts when reaching an LLM API, 
 * JSON parsing errors, or authentication failures. By catching low-level exceptions 
 * and wrapping them in an {@code AIBackendException}, the UI and presentation layers 
 * can handle AI-specific errors gracefully without needing to know the implementation 
 * details of the backend.
 * </p>
 */
public class AIBackendException extends Exception {
    
    /**
     * Constructs a new AIBackendException with the specified detail message.
     * * @param message The detail message explaining the specific failure (e.g., "Connection to LLM timed out").
     */
    public AIBackendException(String message) {
        super(message);
    }

    /**
     * Constructs a new AIBackendException with the specified detail message and root cause.
     * <p>
     * This constructor is used for "Exception Chaining," allowing you to preserve the original 
     * stack trace of the underlying error (like an IOException) while presenting a clean 
     * AIBackendException to the rest of the application.
     * </p>
     * @param message The detail message explaining the specific failure.
     * @param cause   The underlying cause of the failure (can be null).
     */
    public AIBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
