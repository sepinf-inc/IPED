package iped.engine.task.aleapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
public final class AleappUtils {

    // Cache to avoid recompiling the same glob patterns into Regex repeatedly
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private AleappUtils() {
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
        Pattern pat = PATTERN_CACHE.computeIfAbsent(globPattern, AleappUtils::compileFnmatchPattern);

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

    public static IItemReader findItemByPath(IItemSearcher searcher, String itemPath) {
        String query = BasicProps.PATH + ":\"" + itemPath + "\"";
        return searcher.search(query).stream()
                .filter(item -> itemPath.equals(item.getPath()))
                .findFirst()
                .orElse(null);
    }

}
