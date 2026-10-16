package iped.engine.task.yara;

import java.util.Objects;

/**
 * Byte slice that matched a specific string inside a YARA rule.
 *
 * <p>{@code id} is the string identifier as declared in the rule (e.g. {@code $s1},
 * {@code $re1_3}). {@code offset} is in bytes, relative to the start of the item stream.
 * {@code hex} is the lowercase hex representation of the raw bytes without spaces;
 * it may be truncated according to {@link YaraConfig#getMatchHexMaxBytes()}, with
 * {@code truncated == true} signalling the cut.</p>
 */
public final class MatchedString {

    private final String id;
    private final long offset;
    private final String hex;
    private final boolean truncated;

    public MatchedString(String id, long offset, String hex, boolean truncated) {
        this.id = Objects.requireNonNull(id, "id");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got " + offset);
        }
        this.offset = offset;
        this.hex = (hex == null) ? "" : hex;
        this.truncated = truncated;
    }

    public String getId() {
        return id;
    }

    public long getOffset() {
        return offset;
    }

    public String getHex() {
        return hex;
    }

    public boolean isTruncated() {
        return truncated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MatchedString)) {
            return false;
        }
        MatchedString other = (MatchedString) o;
        return offset == other.offset
                && truncated == other.truncated
                && id.equals(other.id)
                && hex.equals(other.hex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, offset, hex, truncated);
    }

    @Override
    public String toString() {
        return "MatchedString[" + id + "@" + offset + ", " + hex.length() / 2 + " bytes"
                + (truncated ? " (truncated)" : "") + "]";
    }
}
