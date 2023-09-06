package iped.app.ui.bookmarks;

import java.text.Collator;

import javax.swing.KeyStroke;

public class BookmarkAndKey implements Comparable<BookmarkAndKey> {
    private static final Collator collator;
    static {
        collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
    }

    public String name;
    public KeyStroke key;

    public BookmarkAndKey(String name) {
        this.name = name;
    }

    public boolean equals(Object obj) {
        if (obj instanceof BookmarkAndKey) {
            return ((BookmarkAndKey) obj).name.equalsIgnoreCase(name);
        } else if (obj instanceof String) {
            return ((String) obj).equalsIgnoreCase(name);
        }
        return false;
    }

    public String toString() {
        return name + (key != null ? " (" + key.toString().replace("released ", "") + ")" : "");
    }

    @Override
    public int compareTo(BookmarkAndKey other) {
        return collator.compare(name, other.name);
    }
}
