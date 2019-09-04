package macee.core.util;

/**
 * Defines simple objects with name and description.
 *
 * @author Bruno Hoelz
 */
public interface ObjectDescription {

    /**
     * Gets the name of the instance.
     *
     * @return the name of the instance.
     */
    String getName();

    void setName(String name);

    /**
     * Gets the description of the instance.
     *
     * @return the description of the instance.
     */
    String getDescription();

    void setDescription(String description);
}
