package iped.engine.lucene.analysis;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versions of the text analysis used to build a case index.
 * <p>
 * Changing how text is tokenized makes an existing index unsearchable with the
 * new rules, so the version is stored in the commit user data of each index:
 * a case keeps being processed (--append, --continue) and searched with the
 * analysis it was created with. Indexes without the entry were created before
 * versioning existed and use {@link #LEGACY}.
 */
public final class AnalyzerVersion {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerVersion.class);

    /** Analysis used by all IPED versions before the analyzer version was recorded. */
    public static final int LEGACY = 1;

    /**
     * Unicode aware analysis: combining marks and joiners are part of words,
     * scripts written without spaces are indexed one grapheme cluster per token,
     * emoji are indexed and tokens are Unicode normalized.
     */
    public static final int UNICODE = 2;

    public static final int LATEST = UNICODE;

    public static final String COMMIT_DATA_KEY = "iped.analyzerVersion";

    private AnalyzerVersion() {
    }

    public static int fromCommitData(Map<String, String> userData) {
        if (userData == null) {
            return LEGACY;
        }
        return parse(userData.get(COMMIT_DATA_KEY));
    }

    public static int fromCommitData(Iterable<Entry<String, String>> userData) {
        if (userData != null) {
            for (Entry<String, String> entry : userData) {
                if (COMMIT_DATA_KEY.equals(entry.getKey())) {
                    return parse(entry.getValue());
                }
            }
        }
        return LEGACY;
    }

    /**
     * Reads the analyzer version of the latest commit of an index.
     *
     * @return the version, or {@code defaultIfNoIndex} if the directory has no
     *         index commit yet
     */
    public static int readFromIndex(Directory directory, int defaultIfNoIndex) throws IOException {
        try {
            return fromCommitData(SegmentInfos.readLatestCommit(directory).getUserData());
        } catch (IndexNotFoundException e) {
            return defaultIfNoIndex;
        }
    }

    /**
     * Returns the given commit user data with the analyzer version set, keeping
     * any other entries.
     */
    public static Map<String, String> withVersion(Iterable<Entry<String, String>> userData, int version) {
        Map<String, String> result = new HashMap<>();
        if (userData != null) {
            for (Entry<String, String> entry : userData) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        result.put(COMMIT_DATA_KEY, Integer.toString(version));
        return Collections.unmodifiableMap(result);
    }

    private static int parse(String value) {
        if (value == null) {
            return LEGACY;
        }
        try {
            int version = Integer.parseInt(value.trim());
            if (version > LATEST) {
                LOGGER.warn("Index analyzer version {} is newer than the supported {}. Searches may miss results.",
                        version, LATEST);
                return LATEST;
            }
            return Math.max(version, LEGACY);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid index analyzer version '{}', using {}", value, LEGACY);
            return LEGACY;
        }
    }
}
