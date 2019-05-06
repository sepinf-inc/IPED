package macee.core;

import macee.core.exceptions.HandlingException;

/**
 * A simple interface for handling objects of a given type.
 *
 * @param <T> the type of object to handle.
 * @author Bruno W. P. Hoelz
 */
@FunctionalInterface public interface Handler<T> {

    /**
     * Check if the given object can be handled by this Handler.
     *
     * @param obj the object to be handled.
     * @return true if the handler is capable of handling this object, false otherwise.
     */
    default boolean canHandle(T obj) {
        return true;
    }

    /**
     * Handles the given object.
     *
     * @param obj the object to be handled.
     * @throws HandlingException if any other exception occurs during the handling of the object.
     */
    void handle(T obj) throws HandlingException;
}
