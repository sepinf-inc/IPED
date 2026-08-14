package iped.app.ui.ai.context;

import java.util.EventObject;

/**
 * Event representing a change in the AI context.
 * <p>
 * This event is fired whenever a context file is added, removed, or when
 * the entire context is cleared. It provides the type of change via
 * {@link #getChangeType()}.
 * </p>
 */
public class ContextChangeEvent extends EventObject {

    /** Change type constant for adding a file */
    public static final String FILE_ADDED = "fileAdded";

    /** Change type constant for removing a file */
    public static final String FILE_REMOVED = "fileRemoved";

    /** Change type constant for clearing all files */
    public static final String CLEARED = "cleared";
    
    /** The type of change that occurred */
    private final String changeType;
    
    /**
     * Constructs a new {@code ContextChangeEvent}.
     *
     * @param source the object that originated the event
     * @param changeType the type of change (one of the static constants)
     */
    public ContextChangeEvent(Object source, String changeType) {
        super(source);
        this.changeType = changeType;
    }
    
    /**
     * Returns the type of change that occurred.
     *
     * @return the change type string
     */
    public String getChangeType() {
        return changeType;
    }
}
