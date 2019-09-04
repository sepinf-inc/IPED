package macee.core.util;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Interface for objects that keep their running time.
 *
 * @author Bruno Hoelz
 */
public interface Timer {

    /**
     * Sets the end time.
     */
    void stop();

    /**
     * Sets the start time.
     */
    void start();

    /**
     * Gets the instance's start time.
     *
     * @return the instance's start time.
     */
    LocalDateTime getStartTime();

    /**
     * Registers the time when the instance stopped execution. Returns -1, if end
     * time is not registered.
     *
     * @return the time the instance stopped execution.
     */
    LocalDateTime getEndTime();

    /**
     * Informs how long the object has been running in the platform.
     *
     * @return how long the object has been running in the platform.
     */
    Duration getRunningTime();
}
