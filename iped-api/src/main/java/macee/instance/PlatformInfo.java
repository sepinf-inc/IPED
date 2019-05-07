package macee.instance;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Describes the resources available in a platform.
 *
 * @author Bruno W. P. Hoelz
 */
public interface PlatformInfo extends Serializable {

    /**
     * Gets the number of CPU cores.
     *
     * @return the number of CPU cores.
     */
    int getProcessorNumber();

    /**
     * Gets information about the storage.
     *
     * @return the storage space available.
     */
    Set<StorageInfo> getStorageInfo();

    /**
     * Gets the last update to the resource information.
     *
     * @return the last update to the resource information.
     */
    ZonedDateTime getLastUpdate();

    /**
     * Sets the last update to the resource information.
     *
     * @param lastUpdate
     *            the last update to the resource information to set.
     */
    void setLastUpdate(ZonedDateTime lastUpdate);

    /**
     * Gets the available memory .
     *
     * @return the available memory .
     */
    long getMemory();

    /**
     * Gets a general description of the platform.
     *
     * @return a general description of the platform.
     */
    String getDescription();

    /**
     * Gets the operating system.
     *
     * @return the operating system.
     */
    String getOperatingSystem();

    /**
     * Gets the hostname.
     *
     * @return the hostname.
     */
    String getHostname();
}
