package iped.app.ui.ai.model;

import iped.app.ui.ai.util.SummaryValueExtractor;
import iped.data.IItem;

/**
 * Wrapper class for {@link IItem} to represent a file entry in the AI context.
 * <p>
 * Provides convenient methods for displaying file names, paths, and labels
 * in the UI, as well as proper {@code equals} and {@code hashCode} implementations
 * based on the unique item ID.
 * </p>
 */
public class ContextFileEntry {

    /** The underlying file item */
    private final IItem item;
    
    /** The AI-generated summary of the item (if available) */
    private final String summary;

    /** Whether this entry is valid to be sent to AI context payload */
    private final boolean validForContext;

    /** Validation reason shown in UI when entry is invalid */
    private final String validationReason;
    
    /**
     * Constructs a new {@code ContextFileEntry} wrapping the given {@link IItem}.
     *
     * @param item the item to wrap
     */
    public ContextFileEntry(IItem item) {
        this(item, SummaryValueExtractor.extractSummary(item), true, null);
    }
    
    /**
     * Constructs a new {@code ContextFileEntry} wrapping the given {@link IItem} with explicit summary.
     *
     * @param item the item to wrap
     * @param summary the AI-generated summary (can be null)
     */
    public ContextFileEntry(IItem item, String summary) {
        this(item, summary, true, null);
    }

    private ContextFileEntry(IItem item, String summary, boolean validForContext, String validationReason) {
        this.item = item;
        this.summary = summary;
        this.validForContext = validForContext;
        this.validationReason = validationReason;
    }

    /**
     * Creates an invalid entry used only for UI feedback.
     *
     * @param item   the item that failed validation
     * @param reason the reason shown to the user
     * @return invalid context entry
     */
    public static ContextFileEntry invalid(IItem item, String reason) {
        return new ContextFileEntry(item, SummaryValueExtractor.extractSummary(item), false, reason);
    }
    
    /**
     * Returns the underlying {@link IItem}.
     *
     * @return the wrapped item
     */
    public IItem getItem() {
        return item;
    }
    
    /**
     * Returns the AI-generated summary if available.
     *
     * @return the summary, or null if not available
     */
    public String getSummary() {
        return summary;
    }
    
    /**
     * Returns true if this entry has an AI-generated summary.
     *
     * @return true if summary exists
     */
    public boolean hasSummary() {
        return summary != null && !summary.trim().isEmpty();
    }

    /**
     * Returns true when the entry is valid for AI context payload.
     */
    public boolean isValidForContext() {
        return validForContext;
    }

    /**
     * Returns UI validation reason for invalid entries.
     */
    public String getValidationReason() {
        return validationReason;
    }
    
    /**
     * Returns the displayable file name, or "Unknown File" if {@code null}.
     *
     * @return file name
     */
    public String getFileName() {
        return item.getName() != null ? item.getName() : "Unknown File";
    }
    
    /**
     * Returns the full file path, or an empty string if {@code null}.
     *
     * @return full path
     */
    public String getFullPath() {
        return item.getPath() != null ? item.getPath() : "";
    }
    
    @Override
    public String toString() {
        if (!validForContext && validationReason != null && !validationReason.trim().isEmpty()) {
            return getFileName() + " - " + validationReason;
        }
        if (hasSummary()) {
            return getFileName() + " [Summary]";
        }
        return getFileName();
    }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ContextFileEntry)) return false;
        return this.item.getId() == ((ContextFileEntry) o).item.getId();
    }
    
    @Override
    public int hashCode() {
        // Use Integer wrapper to get the hashcode of a primitive int
        return Integer.hashCode(item.getId());
    }
}