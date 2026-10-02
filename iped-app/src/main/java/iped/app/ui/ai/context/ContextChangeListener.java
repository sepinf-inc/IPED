package iped.app.ui.ai.context;

import java.util.EventListener;

/**
 * Listener interface for receiving AI context change events.
 * <p>
 * Implementations of this interface can be registered with
 * {@link AIContextManager} to receive notifications whenever the
 * context files are added, removed, or cleared.
 * </p>
 */
public interface ContextChangeListener extends EventListener {

    /**
     * Invoked when the AI context changes.
     *
     * @param event the context change event containing the change type
     */
    void contextChanged(ContextChangeEvent event);
}
