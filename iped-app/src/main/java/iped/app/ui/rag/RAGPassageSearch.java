package iped.app.ui.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Bounded literal lookup over admitted source bodies, never semantic verification. */
final class RAGPassageSearch {

    static final int MAX_QUERY_CHARS = 1024;
    static final int MAX_HITS = 64;
    private static final long TIME_BUDGET_NANOS = 250000000L;

    enum State {
        COMPLETE, EMPTY_QUERY, INVALID_QUERY, UNAVAILABLE, CANCELLED, TIME_LIMIT, MATCH_LIMIT
    }

    static final class Hit {
        final int fragmentOrdinal;
        final int start;
        final int end;

        Hit(int fragmentOrdinal, int start, int end) {
            this.fragmentOrdinal = fragmentOrdinal;
            this.start = start;
            this.end = end;
        }
    }

    static final class Result {
        final State state;
        final List<Hit> hits;

        Result(State state, List<Hit> hits) {
            this.state = state;
            this.hits = Collections.unmodifiableList(new ArrayList<>(hits));
        }
    }

    private static final class Stopped extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final State state;

        Stopped(State state) {
            this.state = state;
        }
    }

    private static void check(BooleanSupplier cancelled, long deadline) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new Stopped(State.CANCELLED);
        }
        if (System.nanoTime() - deadline >= 0) {
            throw new Stopped(State.TIME_LIMIT);
        }
    }

    private static final class MappedText {
        final String text;
        final int[] starts;
        final int[] ends;

        MappedText(String raw, BooleanSupplier cancelled, long deadline) {
            starts = new int[raw.length()];
            ends = new int[raw.length()];
            StringBuilder normalized = new StringBuilder(raw.length());
            int whitespace = -1;
            for (int i = 0; i < raw.length(); i++) {
                if ((i & 1023) == 0) {
                    check(cancelled, deadline);
                }
                char c = raw.charAt(i);
                if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                    if (whitespace < 0) {
                        whitespace = i;
                    }
                    continue;
                }
                if (whitespace >= 0 && normalized.length() > 0) {
                    starts[normalized.length()] = whitespace;
                    ends[normalized.length()] = i;
                    normalized.append(' ');
                }
                whitespace = -1;
                starts[normalized.length()] = i;
                ends[normalized.length()] = i + 1;
                normalized.append(c);
            }
            text = normalized.toString();
        }
    }

    static Result find(RAGContextSnapshot snapshot, String query, BooleanSupplier cancelled) {
        return find(snapshot, query, cancelled, TIME_BUDGET_NANOS);
    }

    static Result find(RAGContextSnapshot snapshot, String query, BooleanSupplier cancelled, long budgetNanos) {
        List<Hit> hits = new ArrayList<>();
        if (snapshot == null) {
            return new Result(State.UNAVAILABLE, hits);
        }
        if (query == null || query.length() > MAX_QUERY_CHARS || !validSurrogates(query)) {
            return new Result(State.INVALID_QUERY, hits);
        }
        long deadline = System.nanoTime() + budgetNanos;
        try {
            check(cancelled, deadline);
            String normalizedQuery = new MappedText(query, cancelled, deadline).text;
            if (normalizedQuery.isEmpty()) {
                return new Result(State.EMPTY_QUERY, hits);
            }
            for (RAGContextSnapshot.Fragment fragment : snapshot.fragments) {
                check(cancelled, deadline);
                MappedText body = new MappedText(fragment.content, cancelled, deadline);
                int from = 0;
                while (true) {
                    check(cancelled, deadline);
                    int position = body.text.indexOf(normalizedQuery, from);
                    if (position < 0) {
                        break;
                    }
                    if (hits.size() == MAX_HITS) {
                        return new Result(State.MATCH_LIMIT, hits);
                    }
                    hits.add(new Hit(fragment.includedOrdinal, body.starts[position],
                            body.ends[position + normalizedQuery.length() - 1]));
                    from = position + 1;
                }
            }
            check(cancelled, deadline);
            return new Result(State.COMPLETE, hits);
        } catch (Stopped stopped) {
            return new Result(stopped.state, hits);
        }
    }

    private static boolean validSurrogates(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }
}
