package macee.instance;

import java.io.File;
import macee.core.util.ObjectRef;

/**
 * Defines a platform (the place where the app is running). The platform must have an unique ID in
 * the environment.
 *
 * @author Bruno W. P. Hoelz
 */
public interface Platform extends ObjectRef {

    String OBJECT_REF_TYPE = "PLATFORM";

    @Override default String getRefType() {
        return OBJECT_REF_TYPE;
    }

    /**
     * Information about the underlying platform resources such as memory, number of cores, network
     * interfaces and storage.
     *
     * @return information about the underlying platform.
     */
    PlatformInfo getPlatformInfo();

    /**
     * Gets the type of platform.
     *
     * @return the type of platform.
     */
    PlatformType getPlatformType();

    /**
     * Sets the platform type.
     *
     * @param type the desired platform type.
     */
    void setPlatformType(PlatformType type);

    /**
     * Gets the platform's address (e.g. the IP address).
     *
     * @return the platform's address.
     */
    String getRemoteAddress();

    /**
     * Sets the platform's address.
     *
     * @param address the platform's address.
     */
    void setRemoteAddress(String address);

    File getTemporaryFolder();
}
