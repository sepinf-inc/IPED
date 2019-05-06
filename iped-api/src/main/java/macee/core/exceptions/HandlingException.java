package macee.core.exceptions;

/**
 * An exception during the handling of an object.
 *
 * @author Bruno W. P. Hoelz
 */
public class HandlingException extends Exception {

    /**
     * Creates an exception with the given message and the original cause of the exception.
     *
     * @param message the error message.
     * @param cause   the original cause of the exception.
     */
    public HandlingException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with the given message.
     *
     * @param message the error message.
     */
    public HandlingException(final String message) {
        super(message);
    }
}
