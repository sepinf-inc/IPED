package iped.app.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import iped.data.IBookmarks;
import iped.data.IIPEDSource;

/**
 * Singleton class to check and cache bookmark query mismatches across multiple cases.
 */
public class BookmarksMismatchChecker {

    private static BookmarksMismatchChecker instance = new BookmarksMismatchChecker();

    private final Map<String, Boolean> mismatchMap = new HashMap<>();

    public static BookmarksMismatchChecker get() {
        return instance;
    }

    public void setMismatch(String bookmarkName, Boolean isMismatch) {
        if (isMismatch == null) {
            mismatchMap.remove(bookmarkName);
        }
        else {
            mismatchMap.put(bookmarkName, isMismatch.booleanValue());
        }
    }

    public boolean queryMismatch(String bookmarkName) {
        if (!App.get().isMultiCase()) {
            return false; // Single case: no mismatch
        }

        // Multi-case: check the cached value or compute it
        if (mismatchMap.containsKey(bookmarkName)) {
            return mismatchMap.get(bookmarkName);
        }
        return mismatchMap.computeIfAbsent(bookmarkName, k -> queryDiffers(k));
    }

    private boolean queryDiffers(String bookmarkName) {
        String firstQuery = null;
        boolean firstSet = false;
        
        for (IIPEDSource source : App.get().appCase.getAtomicSources()) {
            IBookmarks bookmarks = source.getBookmarks();

            // Get the bookmark ID for the bookmark in the case
            int bookmarkId = bookmarks.getBookmarkId(bookmarkName);            
            if (bookmarkId == -1) {
                continue; // the bookmark doesn't exist in the case
            }
            
            if (!firstSet) {
                firstQuery = bookmarks.getBookmarkQuery(bookmarkId);
                firstSet = true;
            } else {
                if (!Objects.equals(firstQuery, bookmarks.getBookmarkQuery(bookmarkId))) {
                    return true; // bookmark query differs between cases
                }
            }
        }

        return false; // bookmark query is the same across all cases
    }
}
