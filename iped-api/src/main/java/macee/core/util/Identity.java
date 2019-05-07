package macee.core.util;

/**
 * Interface for objects with an unique identifier.
 *
 * @param <T>
 *            Type of identifier.
 * @author Bruno Hoelz
 */
public interface Identity<T> {

    /**
     * Gets the ID of the instance.
     *
     * @return the ID of the instance.
     */
    T getId();

    default void setId(T id) {
        throw new UnsupportedOperationException("Id cannot be changed.");
    }
}
