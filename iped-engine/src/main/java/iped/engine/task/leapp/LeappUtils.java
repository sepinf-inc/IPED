package iped.engine.task.leapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;

/**
 * A utility class containing helper methods for ALEAPP.
 */
public final class LeappUtils {

    // Cache to avoid recompiling the same glob patterns into Regex repeatedly
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private LeappUtils() {
    }

    /**
     * Builds the complete Lucene query used by FileSeeker: the glob-derived clauses from
     * {@link #globToLuceneQuery(String)}, anchored to the evidence root path and excluding carved file fragments.
     */
    public static String buildFileSearchQuery(String basePath, String globPattern) {
        StringBuilder query = new StringBuilder();
        query.append(BasicProps.PATH).append(":\"").append(basePath).append("\"");

        String globQuery = globToLuceneQuery(globPattern);
        if (StringUtils.isNotBlank(globQuery)) {
            query.append(" && (").append(globQuery).append(")");
        }

        query.append(" && -fileFragment:true");

        return query.toString();
    }

    /**
     * Converts a glob pattern to a Lucene query combining exact path logic and a robust name pre-filter.
     *
     * IMPORTANT ARCHITECTURAL NOTE: This method acts as a high-recall "pre-filter". Because Lucene tokenizes text and this
     * method strips certain wildcards/punctuation to maximize performance, the generated Lucene query WILL often match MORE
     * files than the original glob.
     *
     * To guarantee 100% precision without missing any results, the hits returned by this Lucene query MUST be checked
     * again in memory using a strict Java PathMatcher or Regex (e.g., via AleappUtils.matchesGlob, as FileSeeker.search
     * does).
     *
     * @param globPattern The glob pattern to convert.
     * @return A Lucene query string.
     */
    public static String globToLuceneQuery(String globPattern) {
        if (StringUtils.isBlank(globPattern)) {
            return "";
        }

        // Normalize separators and isolate path from filename
        String normalizedPath = globPattern.replace("\\", "/");
        int lastSlash = normalizedPath.lastIndexOf('/');

        String parentPathString = lastSlash >= 0 ? normalizedPath.substring(0, lastSlash) : "";
        String fileName = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;

        // 1. Build the exact path query logic provided
        String pathQuery = "";
        if (StringUtils.isNotBlank(parentPathString)) {
            String[] cleanedPaths = StringUtils.split(parentPathString, '*');
            pathQuery = Arrays
                    .stream(cleanedPaths)
                    .map(term -> StringUtils.strip(term, "/"))
                    .filter(StringUtils::isNotBlank)
                    .map(term -> String.format("%s:\"%s\"", BasicProps.PATH, term))
                    .collect(Collectors.joining(" && "));
        }

        // 2. Handle pure catch-all wildcards
        if (StringUtils.equalsAny(fileName, "*", "*.*", "**", "")) {
            return pathQuery;
        }

        // 3. Build the Name Query
        String nameQuery = "";

        // If there are absolutely no wildcards in the filename, we can safely quote it for an exact phrase match
        if (!fileName.contains("*") && !fileName.contains("?")) {
            nameQuery = String.format("%s:\"%s\"", BasicProps.NAME, fileName);
        } else {
            // Contains wildcards: Split by ALL punctuation (including hyphens) to ensure robust boolean queries.
            // Hyphens unquoted in Lucene act as the NOT operator (e.g. test-profile -> test AND NOT profile),
            // so we must split them into separate required terms to maximize recall.
            String normalizedName = fileName.replaceAll("[^a-zA-Z0-9*?]+", " ");
            String[] tokens = normalizedName.trim().split("\\s+");
            List<String> queryParts = new ArrayList<>();

            for (String token : tokens) {
                if (StringUtils.isBlank(token)) {
                    continue;
                }

                // Compress duplicate wildcards
                String term = token.replaceAll("\\*+", "*");
                term = term.replaceAll("\\?+", "?");

                // Skip standalone wildcards to avoid unoptimized match-all name clauses (e.g., from splitting "*.")
                if (term.equals("*") || term.equals("?")) {
                    continue;
                }

                queryParts.add(BasicProps.NAME + ":" + term);
            }
            nameQuery = String.join(" && ", queryParts);
        }

        // 4. Combine pathQuery and nameQuery
        if (StringUtils.isNoneBlank(pathQuery, nameQuery)) {
            return String.format("%s && %s", pathQuery, nameQuery);
        } else if (StringUtils.isNotBlank(nameQuery)) {
            return nameQuery;
        } else {
            return pathQuery;
        }
    }

    /**
     * Replicates the exact behavior of Python's fnmatch logic. Evaluates a file path against a glob pattern to guarantee
     * 100% precision. Used as the strict in-memory re-check for hits returned by the high-recall Lucene pre-filter built
     * by {@link #globToLuceneQuery(String)}.
     *
     * See: https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/search_files.py#L111
     */
    public static boolean matchesGlob(IItemReader item, String globPattern) {
        if (globPattern == null || item == null) {
            return false;
        }

        // 1. Fetch or compile the fnmatch-equivalent regex pattern
        Pattern pat = PATTERN_CACHE.computeIfAbsent(globPattern, LeappUtils::compileFnmatchPattern);

        // 2. Normalize the path to use forward slashes (cross-platform safety)
        String pathToCheck = item.getPath().replace('\\', '/');

        // 3. Match against the full string exactly like Python
        return pat.matcher(pathToCheck).matches();
    }

    /**
     * Maps a shell-style glob pattern to a Java Regex exactly like Python's fnmatch. Unlike Java's PathMatcher, this treats
     * '*' as matching across directory boundaries.
     */
    private static Pattern compileFnmatchPattern(String glob) {
        StringBuilder sb = new StringBuilder("(?i)^"); // (?i) for case-insensitive, ^ for start
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
            case '*':
                sb.append(".*"); // fnmatch treats * as .* (crosses slashes)
                break;
            case '?':
                sb.append("."); // fnmatch treats ? as .
                break;
            // Escape all standard regex metacharacters
            case '.':
            case '+':
            case '^':
            case '$':
            case '\\':
            case '|':
            case '{':
            case '}':
            case '(':
            case ')':
            case '[':
            case ']':
                sb.append('\\').append(c);
                break;
            default:
                sb.append(c);
                break;
            }
        }
        sb.append("$"); // $ for end of string
        return Pattern.compile(sb.toString());
    }

    // Android exposes the same storage under several symbolic links / bind mounts, so the file path an app
    // records often differs from where the file physically lives in the extraction tree. Each entry maps an
    // app-visible prefix (key) to the underlying extraction location (value). More specific keys come first.
    private static final Map<String, String> SYMLINK_PREFIXES = new LinkedHashMap<>();
    static {
        SYMLINK_PREFIXES.put("/storage/emulated", "/data/media");
        SYMLINK_PREFIXES.put("/storage/self/primary", "/data/media/0");
        SYMLINK_PREFIXES.put("/mnt/sdcard", "/data/media/0");
        SYMLINK_PREFIXES.put("/mnt/user/0/primary", "/data/media/0");
        SYMLINK_PREFIXES.put("/sdcard", "/data/media/0");
        SYMLINK_PREFIXES.put("/data/user/0", "/data/data");
    }

    /**
     * Locates the case item that corresponds to a device file path stored in plugin data (e.g. the "File Path" column of
     * chromeOfflinePages). The item path is expected to be {@code pathRoot + pathValue}; since Android exposes the same
     * storage under several symbolic links, the known {@link #SYMLINK_PREFIXES} variants of the value are tried too. The
     * item whose in-case path exactly equals one of the candidate paths is returned, or {@code null} when none matches.
     *
     * @param searcher the case searcher
     * @param pathRoot the evidence root path (the in-case prefix of every item under this extraction)
     * @param pathValue the device file path stored in the plugin data cell
     * @return the corresponding case item, or {@code null} when nothing matches
     */
    public static IItemReader findItemByPath(IItemSearcher searcher, String pathRoot, String pathValue) {
        if (searcher == null || StringUtils.isBlank(pathValue)) {
            return null;
        }

        String path = pathValue.trim();

        // first try the path exactly as stored by the app
        IItemReader item = searchItemByPath(searcher, pathRoot + path);
        if (item != null) {
            return item;
        }

        // then try the known Android storage symlink resolutions, one at a time
        for (Map.Entry<String, String> symlink : SYMLINK_PREFIXES.entrySet()) {
            String prefix = symlink.getKey();
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                String resolved = symlink.getValue() + path.substring(prefix.length());
                item = searchItemByPath(searcher, pathRoot + resolved);
                if (item != null) {
                    return item;
                }
            }
        }

        return null;
    }

    /** Returns the case item whose path exactly equals {@code itemPath}, or {@code null} when there is none. */
    private static IItemReader searchItemByPath(IItemSearcher searcher, String itemPath) {
        return searcher.search(BasicProps.PATH + ":\"" + itemPath + "\"").stream()
                .filter(item -> itemPath.equals(item.getPath()))
                .findFirst()
                .orElse(null);
    }

}
