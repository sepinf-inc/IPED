package iped.app.ui.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable source text admitted to one request, with UTF-16 context offsets. */
final class RAGContextSnapshot {

    static final int MAX_BODY_CHARS = 262144;
    static final int MAX_SOURCES = 256;

    final List<Fragment> fragments;
    final List<Omission> omissions;
    final int bodyChars;

    private RAGContextSnapshot(List<Fragment> fragments, List<Omission> omissions, int bodyChars) {
        this.fragments = Collections.unmodifiableList(new ArrayList<>(fragments));
        this.omissions = Collections.unmodifiableList(new ArrayList<>(omissions));
        this.bodyChars = bodyChars;
    }

    static final class Fragment {
        final String itemId;
        final String contenttrackID;
        final String name;
        final String content;
        final float score;
        final int retrievedOrdinal;
        final int includedOrdinal;
        final int bodyStart;
        final int bodyEnd;

        Fragment(String itemId, String track, String name, String content, float score,
                int retrievedOrdinal, int includedOrdinal, int bodyStart) {
            this.itemId = itemId;
            this.contenttrackID = track;
            this.name = name;
            this.content = content;
            this.score = score;
            this.retrievedOrdinal = retrievedOrdinal;
            this.includedOrdinal = includedOrdinal;
            this.bodyStart = bodyStart;
            this.bodyEnd = bodyStart + content.length();
        }
    }

    static final class Omission {
        final int retrievedOrdinal;
        final String itemId;
        final String contenttrackID;
        final boolean emptyContent;

        Omission(int ordinal, String itemId, String track, boolean emptyContent) {
            this.retrievedOrdinal = ordinal;
            this.itemId = itemId;
            this.contenttrackID = track;
            this.emptyContent = emptyContent;
        }
    }

    static final class Builder {
        private final List<Fragment> fragments = new ArrayList<>();
        private final List<Omission> omissions = new ArrayList<>();
        private int bodyChars;
        private boolean available = true;

        void include(String itemId, String track, String name, String content, float score,
                int retrievedOrdinal, int bodyStart) {
            if (reserve(content.length())) {
                fragments.add(new Fragment(itemId, track, name, content, score,
                        retrievedOrdinal, fragments.size(), bodyStart));
                bodyChars += content.length();
            }
        }

        void omit(int retrievedOrdinal, String itemId, String track, boolean emptyContent) {
            if (reserve(0)) {
                omissions.add(new Omission(retrievedOrdinal, itemId, track, emptyContent));
            }
        }

        private boolean reserve(int chars) {
            if (available && ((long) bodyChars + chars > MAX_BODY_CHARS
                    || fragments.size() + omissions.size() >= MAX_SOURCES)) {
                available = false;
                fragments.clear();
                omissions.clear();
            }
            return available;
        }

        /** A null result means unavailable, never a truncated or empty context. */
        RAGContextSnapshot build() {
            return available ? new RAGContextSnapshot(fragments, omissions, bodyChars) : null;
        }
    }

    /** EDT-owned, transient retention; no provider, index or history-schema changes. */
    static final class Cache {
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final int maxEntries;
        private final int maxChars;
        private long bodyChars;

        Cache() {
            this(16, 1048576);
        }

        Cache(int maxEntries, int maxChars) {
            if (maxEntries < 1 || maxChars < 0) {
                throw new IllegalArgumentException("Invalid RAG snapshot retention limits");
            }
            this.maxEntries = maxEntries;
            this.maxChars = maxChars;
        }

        void put(String requestId, String sessionId, RAGContextSnapshot snapshot) {
            Entry previous = entries.remove(requestId);
            if (previous != null) {
                bodyChars -= previous.snapshot.bodyChars;
            }
            if (snapshot == null || snapshot.bodyChars > maxChars) {
                return;
            }
            entries.put(requestId, new Entry(sessionId, snapshot));
            bodyChars += snapshot.bodyChars;
            Iterator<Entry> oldest = entries.values().iterator();
            while (entries.size() > maxEntries || bodyChars > maxChars) {
                bodyChars -= oldest.next().snapshot.bodyChars;
                oldest.remove();
            }
        }

        RAGContextSnapshot get(String requestId) {
            Entry entry = entries.get(requestId);
            return entry == null ? null : entry.snapshot;
        }

        void removeSession(String sessionId) {
            Iterator<Entry> iterator = entries.values().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (entry.sessionId.equals(sessionId)) {
                    bodyChars -= entry.snapshot.bodyChars;
                    iterator.remove();
                }
            }
        }

        void clear() {
            entries.clear();
            bodyChars = 0;
        }

        private static final class Entry {
            final String sessionId;
            final RAGContextSnapshot snapshot;

            Entry(String sessionId, RAGContextSnapshot snapshot) {
                this.sessionId = sessionId;
                this.snapshot = snapshot;
            }
        }
    }
}
