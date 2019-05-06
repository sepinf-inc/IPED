package macee.core;

/**
 * Defines a basic component interface that can be loaded by a {@link Module}. It uses the same
 * event bus from the parent module.
 *
 * @param <T> the type of module associated with this component.
 * @author Bruno W. P. Hoelz
 */
public interface Component<T extends Module> extends Lifecycle {

    /**
     * Get the module associated with this component.
     *
     * @return the module associated with this component.
     */
    T getModule();

    /**
     * Sets the module associated with this component.
     *
     * @param module the module to be associated with this component.
     */
    void setModule(T module);

    default void post(Object event) {
        getModule().post(event);
    }

    default void register(Object obj) {
        getModule().register(obj);
    }
}
