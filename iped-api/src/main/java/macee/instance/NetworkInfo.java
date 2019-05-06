package macee.instance;

import java.io.Serializable;
import java.net.NetworkInterface;

/**
 * Keeps information about a network interface, including MAC address and display name.
 *
 * @author Bruno Hoelz
 */
public class NetworkInfo implements Serializable {

    private String macAddress;
    private String displayName;
    private transient NetworkInterface networkInterface;

    /**
     * Gets the MAC address.
     *
     * @return the MAC address.
     */
    public final String getMacAddress() {
        return macAddress;
    }

    /**
     * Set the MAC address of the interface.
     *
     * @param mac the MAC address.
     */
    public final void setMacAddress(final String mac) {
        this.macAddress = mac;
    }

    /**
     * Gets the display name of the network interface.
     *
     * @return the display name of the network interface.
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Sets the display name of the network interface.
     *
     * @param name the display name of the network interface.
     */
    public final void setDisplayName(final String name) {
        this.displayName = name;
    }

    NetworkInterface getNetworkInterface() {
        return this.networkInterface;
    }

    void setNetworkInterface(NetworkInterface ni) {
        this.networkInterface = ni;
    }
}
