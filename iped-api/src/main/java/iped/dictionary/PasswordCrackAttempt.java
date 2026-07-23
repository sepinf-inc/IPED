package iped.dictionary;

/**
 * Represents a single try to crack a password using a candidate password.
 *
 * Implementations are supplied by the module that knows how to test a password
 * (e.g. a parser), while the actual (possibly parallel) execution and timeout
 * handling are performed by the {@link PasswordDictionaryFactory}.
 *
 * @param <T>
 *            the type returned on success (e.g. {@link String} with the cracked
 *            password, or the decrypted result).
 */
@FunctionalInterface
public interface PasswordCrackAttempt<T> {

    /**
     * Makes one try to crack using the given candidate password.
     *
     * @param password
     *            the candidate password to test.
     * @return a non-null result on success, or {@code null} on failure.
     */
    T tryPassword(String password) throws Exception;
}
