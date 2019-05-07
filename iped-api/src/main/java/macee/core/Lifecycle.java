package macee.core;

import macee.core.exceptions.SetupException;
import macee.core.exceptions.ShutdownException;

/**
 * Defines methods for setting up and shutting down objects.
 *
 * @author Bruno W. P. Hoelz
 */
public interface Lifecycle {

    /**
     * Method for setting up the component before execution.
     *
     * @throws SetupException
     *             if an error occurs during the setup of the component.
     */
    void setup() throws SetupException;

    /**
     * Method for shutting down the component.
     *
     * @throws ShutdownException
     *             if an error occurs during component shutdown.
     */
    void shutdown() throws ShutdownException;

}
