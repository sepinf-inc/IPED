package macee.core.exceptions;

/**
 * An exception during the setup process.
 *
 * @author Bruno W. P. Hoelz
 */
public class SetupException extends Exception {

    /**
     * Creates an exception with the given message and the original exception associated with it.
     *
     * @param message the error message.
     * @param cause   the original cause of the exception.
     */
    public SetupException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with the given message.
     *
     * @param message the error message.
     */
    public SetupException(final String message) {
        super(message);
    }
}
