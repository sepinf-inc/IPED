package iped.app.ui.ai.context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;

import iped.app.ui.ai.model.ContextFileEntry;
import iped.app.ui.ai.util.AIWhatsappChatExtractor;
import iped.app.ui.ai.util.SummaryValueExtractor;
import iped.data.IItem;
import iped.engine.lucene.analysis.CategoryTokenizer;
import iped.properties.ExtraProperties;

/**
 * Singleton manager responsible for maintaining the AI context file list.
 * <p>
 * This class provides thread-safe operations for adding, removing, and clearing
 * context files used by the AI. It also implements an event-listener mechanism
 * to notify interested components whenever the context changes.
 * </p>
 * 
 * <p>
 * Internally, it uses a {@link CopyOnWriteArrayList} to ensure safe concurrent access
 * without explicit synchronization for read-heavy operations.
 * </p>
 * 
 * <p>
 * All UI-related notifications are dispatched on the Swing Event Dispatch Thread (EDT).
 * </p>
 */
public class AIContextManager {

    /** Singleton instance */
    private static AIContextManager instance;
    
    /** Thread-safe list holding context files */
    private final List<IItem> contextFiles;

    /** Invalid entries shown only in UI as feedback */
    private final List<ContextFileEntry> invalidEntries;
    
    /** Listener list for context change events */
    private final EventListenerList listeners;

    private AIWhatsappChatExtractor chatExtractor = new AIWhatsappChatExtractor();
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private AIContextManager() {
        this.contextFiles = new CopyOnWriteArrayList<>();
        this.invalidEntries = new CopyOnWriteArrayList<>();
        this.listeners = new EventListenerList();
    }
    
    /**
     * Returns the singleton instance of the manager.
     *
     * @return the single {@code AIContextManager} instance
     */
    public static synchronized AIContextManager getInstance() {
        if (instance == null) {
            instance = new AIContextManager();
        }
        return instance;
    }
    
    /**
     * Adds a file to the context if it is not already present.
     *
     * @param item the item to add; ignored if {@code null}
     */
    public void addContextFile(IItem item) {
        if (item == null) {
            return;
        }
        List<IItem> single = new ArrayList<>(1);
        single.add(item);
        addContextFiles(single);
    }
    
    /**
     * Removes a file from the context based on its ID.
     *
     * @param item the item to remove
     */
    public void removeContextFile(IItem item) {
        if (item == null) {
            return;
        }

        boolean removedValid = contextFiles.removeIf(existing -> existing.getId() == item.getId());
        boolean removedInvalid = invalidEntries.removeIf(entry -> entry.getItem().getId() == item.getId());

        if (removedValid || removedInvalid) {
            fireContextChanged(ContextChangeEvent.FILE_REMOVED);
        }
    }
    
    /**
     * Clears all context files.
     */
    public void clearContext() {
        if (!contextFiles.isEmpty() || !invalidEntries.isEmpty()) {
            contextFiles.clear();
            invalidEntries.clear();
            fireContextChanged(ContextChangeEvent.CLEARED);
        }
    }
    
    /**
     * Returns a copy of the current context file list.
     *
     * @return a new list containing all context files
     */
    public List<IItem> getContextFiles() {
        return new ArrayList<>(contextFiles);
    }

    /**
     * Returns entries to display in the UI: valid context files plus invalid items with reasons.
     */
    public List<ContextFileEntry> getContextEntriesForUI() {
        List<ContextFileEntry> result = new ArrayList<>();
        for (IItem file : contextFiles) {
            result.add(new ContextFileEntry(file));
        }
        result.addAll(invalidEntries);
        return result;
    }
    
    /**
     * Registers a listener to receive context change events.
     *
     * @param listener the listener to add
     */
    public void addContextChangeListener(ContextChangeListener listener) {
        listeners.add(ContextChangeListener.class, listener);
    }
    
    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeContextChangeListener(ContextChangeListener listener) {
        listeners.remove(ContextChangeListener.class, listener);
    }
    
    /**
     * Notifies all registered listeners about a context change.
     * <p>
     * Ensures that notifications are dispatched on the Swing EDT.
     * </p>
     *
     * @param changeType the type of change that occurred
     */
    private void fireContextChanged(String changeType) {
        ContextChangeEvent event = new ContextChangeEvent(this, changeType);
        Runnable notification = () -> {
            for (ContextChangeListener listener : listeners.getListeners(ContextChangeListener.class)) {
                listener.contextChanged(event);
            }
        };
        
        if (SwingUtilities.isEventDispatchThread()) {
            notification.run();
        } else {
            SwingUtilities.invokeLater(notification);
        }
    }

    /**
     * Batch adds multiple files to the context.
     * <p>
     * This method avoids UI thread starvation by processing all additions first
     * and firing a single change event at the end.
     * </p>
     *
     * @param items list of items to add; ignored if {@code null} or empty
     */
    public void addContextFiles(List<IItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        boolean changedAny = false;

        for (IItem item : items) {
            if (item == null) {
                continue;
            }

            String rejectionReason = getRejectionReason(item);
            if (rejectionReason != null) {
                if (invalidEntries.removeIf(entry -> entry.getItem().getId() == item.getId())) {
                    changedAny = true;
                }
                invalidEntries.add(ContextFileEntry.invalid(item, rejectionReason));
                changedAny = true;
                continue;
            }

            // Item became valid, ensure it is no longer flagged as invalid
            if (invalidEntries.removeIf(entry -> entry.getItem().getId() == item.getId())) {
                changedAny = true;
            }

            boolean alreadyExists = contextFiles.stream()
                    .anyMatch(existing -> existing.getId() == item.getId());

            if (!alreadyExists) {
                contextFiles.add(item);
                changedAny = true;
            }
        }

        if (changedAny) {
            fireContextChanged(ContextChangeEvent.FILE_ADDED);
        }
    }

    private String getRejectionReason(IItem item) {
        if (!chatExtractor.isPotentiallyValidChat(item)) {
            return "Rejected: Not a WhatsApp chat item.";
        }

        if (hasEmptyFilesCategory(item)) {
            return "Rejected: Category is Empty Files.";
        }

        if (SummaryValueExtractor.hasSummary(item)) {
            return null;
        }

        Boolean isEmpty = readCommunicationIsEmpty(item);
        if (Boolean.TRUE.equals(isEmpty)) {
            return "Rejected: Communication is empty.";
        }
        return null;
    }

    private boolean hasEmptyFilesCategory(IItem item) {
        if (item == null) {
            return false;
        }

        if (item.getCategorySet() != null) {
            for (String category : item.getCategorySet()) {
                if (isEmptyFilesCategoryValue(category)) {
                    return true;
                }
            }
        }

        String categories = item.getCategories();
        if (categories != null && !categories.isBlank()) {
            String[] splitCategories = categories.split(String.valueOf(CategoryTokenizer.SEPARATOR));
            for (String category : splitCategories) {
                if (isEmptyFilesCategoryValue(category)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isEmptyFilesCategoryValue(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase();
        return normalized.equals("empty files") || normalized.contains("empty files");
    }

    private Boolean readCommunicationIsEmpty(IItem item) {
        if (item == null) {
            return null;
        }

        String[] keys = {
            ExtraProperties.COMMUNICATION_PREFIX + "isEmpty"
        };

        for (String key : keys) {
            String value = readFirstValue(item, key);
            if (value != null) {
                return Boolean.parseBoolean(value.trim().toLowerCase());
            }
        }
        return null;
    }

    private String readFirstValue(IItem item, String key) {
        Object extra = item.getExtraAttribute(key);
        if (extra != null) {
            if (extra instanceof String) {
                return (String) extra;
            }
            if (extra instanceof Boolean) {
                return String.valueOf(extra);
            }
            if (extra instanceof String[] && ((String[]) extra).length > 0) {
                return ((String[]) extra)[0];
            }
            return String.valueOf(extra);
        }

        if (item.getMetadata() == null) {
            return null;
        }

        String value = item.getMetadata().get(key);
        if (value != null) {
            return value;
        }

        String[] values = item.getMetadata().getValues(key);
        if (values != null && values.length > 0) {
            return values[0];
        }

        return null;
    }
}