package macee.core.exceptions;

/**
 * An exception during the shutdown process.
 *
 * @author Bruno W. P. Hoelz
 */
public class ShutdownException extends Exception {

    /**
     * Creates an exception with the given message and the original exception
     * associated with it.
     *
     * @param message
     *            the error message.
     * @param cause
     *            the original cause of the exception.
     */
    public ShutdownException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with the given message.
     *
     * @param message
     *            the error message.
     */
    public ShutdownException(final String message) {
        super(message);
    }
}
