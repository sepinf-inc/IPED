package iped.app.ui.bookmarks;

import java.text.Collator;

import javax.swing.KeyStroke;

public class BookmarkAndKey implements Comparable<BookmarkAndKey> {
    private static final Collator collator;
    static {
        collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
    }

    public String bookmark;
    public KeyStroke key;

    public BookmarkAndKey(String bookmark) {
        this.bookmark = bookmark;
    }

    public boolean equals(Object obj) {
        if (obj instanceof BookmarkAndKey) {
            return ((BookmarkAndKey) obj).bookmark.equalsIgnoreCase(bookmark);
        } else if (obj instanceof String) {
            return ((String) obj).equalsIgnoreCase(bookmark);
        }
        return false;
    }

    public String toString() {
        return bookmark + (key != null ? " (" + key.toString().replace("released ", "") + ")" : "");
    }

    @Override
    public int compareTo(BookmarkAndKey other) {
        return collator.compare(bookmark, other.bookmark);
    }
}
