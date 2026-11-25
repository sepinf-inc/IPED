package iped.engine.task.leapp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;

/**
 * A utility class containing helper methods shared by the LEAPP tools.
 */
public final class LeappUtils {

    // Cache to avoid recompiling the same glob patterns into Regex repeatedly
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private LeappUtils() {
    }

    // Prefix ALEAPP seekers prepend to every extraction-relative path before matching it against a glob
    // (see FileSeekerDir/Tar/Zip.search: pat(root + normcase(item))). A few patterns rely on it to anchor
    // at the extraction root (e.g. "root/2[0-9][0-9][0-9]-*.txt").
    public static final String GLOB_ROOT_PREFIX = "root/";

    /**
     * Builds the complete Lucene query used by FileSeeker: the glob-derived clauses from
     * {@link #globToLuceneQuery(String)}, anchored to the evidence root path and excluding carved file fragments.
     */
    public static String buildFileSearchQuery(String basePath, String globPattern) {
        StringBuilder query = new StringBuilder();
        query.append(phrase(BasicProps.PATH, basePath));

        String globQuery = globToLuceneQuery(globPattern);
        if (StringUtils.isNotBlank(globQuery)) {
            query.append(" && (").append(globQuery).append(")");
        }

        query.append(" && -fileFragment:true");

        return query.toString();
    }

    /**
     * Converts a glob pattern to a Lucene query to be used as a high-recall PRE-FILTER: it must never miss an item the
     * glob matches, but it is allowed to match more. The hits MUST be checked again with
     * {@link #matchesGlob(String, String)}, as FileSeeker.searchItems does.
     *
     * The glob has fnmatch semantics: '*' also matches '/', so no part of the pattern is bound to a path level, except
     * what comes after the last wildcard. The pattern is therefore split at the wildcards into literal chunks, each one
     * a text that must exist somewhere in the item path, and every chunk becomes a clause over the (tokenized) path
     * field:
     * <ul>
     * <li>tokens delimited by punctuation on both sides are whole index tokens, so they are searched as a phrase;</li>
     * <li>a token touching a wildcard may be just part of an index token, so it is searched with a wildcard on that
     * side.</li>
     * </ul>
     * The text after the last '/' of the last chunk belongs to the item name, and is searched in the name field.
     *
     * Character classes are reduced first: "[Cc]" becomes "c" (the index is case insensitive) and any other class, as
     * well as '?', becomes a wildcard.
     *
     * NOTE: tokens are split the same way the default index tokenizer does (letters and digits). Indexing extra chars
     * ("extraCharsToIndex" option) is not supported.
     *
     * @param globPattern The glob pattern to convert.
     * @return A Lucene query string, empty when the pattern has no literal text to search for.
     */
    public static String globToLuceneQuery(String globPattern) {
        if (StringUtils.isBlank(globPattern)) {
            return "";
        }

        String glob = reduceGlobForQuery(globPattern);

        // a pattern anchored at the extraction root: the prefix is not part of any item path
        boolean anchoredAtStart = !glob.startsWith("*");
        if (glob.startsWith(GLOB_ROOT_PREFIX)) {
            glob = glob.substring(GLOB_ROOT_PREFIX.length() - 1);
        }
        boolean anchoredAtEnd = !glob.endsWith("*");

        String[] chunks = StringUtils.split(glob, '*');
        Set<String> clauses = new LinkedHashSet<>();

        for (int i = 0; i < chunks.length; i++) {
            String chunk = chunks[i];
            boolean first = i == 0;
            boolean last = i == chunks.length - 1;

            // a chunk edge is closed when the text cannot continue there: start/end of the path
            boolean closedLeft = first && anchoredAtStart;
            boolean closedRight = last && anchoredAtEnd;

            if (closedRight) {
                // what comes after the last '/' is in the item name (all of it, when the '/' is in this chunk)
                int lastSlash = chunk.lastIndexOf('/');
                String namePart = chunk.substring(lastSlash + 1);
                if (lastSlash >= 0) {
                    addChunkClauses(clauses, BasicProps.PATH, chunk.substring(0, lastSlash + 1), closedLeft, true);
                }
                addChunkClauses(clauses, BasicProps.NAME, namePart, lastSlash >= 0 || closedLeft, true);
            } else {
                addChunkClauses(clauses, BasicProps.PATH, chunk, closedLeft, false);
            }
        }

        return String.join(" && ", clauses);
    }

    /**
     * Adds the clauses for one literal chunk of the glob. closedLeft/closedRight tell that the chunk text cannot be
     * preceded/followed by more token chars, so its first/last token is a whole index token even without punctuation.
     */
    private static void addChunkClauses(Set<String> clauses, String field, String chunk, boolean closedLeft,
            boolean closedRight) {

        // token boundaries (start inclusive, end exclusive), split like the index tokenizer
        List<int[]> tokens = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= chunk.length(); i++) {
            boolean tokenChar = i < chunk.length() && Character.isLetterOrDigit(chunk.charAt(i));
            if (tokenChar && start < 0) {
                start = i;
            } else if (!tokenChar && start >= 0) {
                tokens.add(new int[] { start, i });
                start = -1;
            }
        }
        if (tokens.isEmpty()) {
            return;
        }

        int firstWhole = 0;
        int lastWhole = tokens.size() - 1;

        boolean openLeft = !closedLeft && tokens.get(0)[0] == 0;
        boolean openRight = !closedRight && tokens.get(lastWhole)[1] == chunk.length();

        if (tokens.size() == 1 && (openLeft || openRight)) {
            String token = chunk.substring(tokens.get(0)[0], tokens.get(0)[1]);
            clauses.add(field + ":" + (openLeft ? "*" : "") + token + (openRight ? "*" : ""));
            return;
        }
        if (openLeft) {
            clauses.add(field + ":*" + chunk.substring(tokens.get(0)[0], tokens.get(0)[1]));
            firstWhole++;
        }
        String openRightClause = null;
        if (openRight) {
            openRightClause = field + ":" + chunk.substring(tokens.get(lastWhole)[0], tokens.get(lastWhole)[1]) + "*";
            lastWhole--;
        }
        if (firstWhole <= lastWhole) {
            clauses.add(phrase(field, chunk.substring(tokens.get(firstWhole)[0], tokens.get(lastWhole)[1])));
        }
        if (openRightClause != null) {
            clauses.add(openRightClause);
        }
    }

    /**
     * The extension of the last segment of a path, without the dot, or an empty string when there is none.
     *
     * Used instead of {@code FilenameUtils.getExtension}, which on Windows throws for any name with a colon (taken as
     * a NTFS alternate data stream): colons are common in the file names of an Android extraction.
     */
    public static String getExtension(String path) {
        String name = StringUtils.substringAfterLast("/" + StringUtils.replaceChars(StringUtils.defaultString(path), '\\', '/'), "/");
        return StringUtils.contains(name, '.') ? StringUtils.substringAfterLast(name, ".") : "";
    }

    /** A phrase clause; only the two chars that are special inside a quoted phrase need escaping. */
    static String phrase(String field, String text) {
        return field + ":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Reduces a glob to literal text and '*' only, never narrowing what it matches: separators are normalized, a
     * character class matching a single letter in both cases is replaced by that letter, and '?' and any other class
     * are replaced by '*'.
     */
    private static String reduceGlobForQuery(String globPattern) {
        String glob = globPattern.trim().replace('\\', '/');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '?') {
                sb.append('*');
            } else if (c == '[') {
                int end = findClassEnd(glob, i);
                if (end < 0) {
                    // no closing bracket: fnmatch takes it as a literal '['
                    sb.append(c);
                    continue;
                }
                String stuff = glob.substring(i + 1, end);
                if (stuff.length() == 2 && Character.isLetter(stuff.charAt(0))
                        && Character.toLowerCase(stuff.charAt(0)) == Character.toLowerCase(stuff.charAt(1))) {
                    sb.append(stuff.charAt(0));
                } else {
                    sb.append('*');
                }
                i = end;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("\\*+", "*");
    }

    /**
     * Index of the ']' closing the character class opened at {@code open}, or -1 when there is none. Mirrors Python's
     * fnmatch: a ']' right after the '[' (or after "[!") is a member of the class, not its end.
     */
    private static int findClassEnd(String glob, int open) {
        int j = open + 1;
        if (j < glob.length() && glob.charAt(j) == '!') {
            j++;
        }
        if (j < glob.length() && glob.charAt(j) == ']') {
            j++;
        }
        while (j < glob.length() && glob.charAt(j) != ']') {
            j++;
        }
        return j < glob.length() ? j : -1;
    }

    /**
     * Replicates the matching done by ALEAPP seekers, to guarantee 100% precision over the hits returned by the
     * high-recall Lucene pre-filter built by {@link #globToLuceneQuery(String)}: the glob is matched, with Python's
     * fnmatch semantics, against {@link #GLOB_ROOT_PREFIX} + the path of the item relative to the extraction root.
     *
     * Matching is case insensitive (as ALEAPP is on Windows), since the same extraction is expected to give the same
     * results on every platform.
     *
     * See: https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/scripts/search_files.py#L489
     *
     * @param relativePath the item path relative to the extraction root, without a leading separator
     * @param globPattern the glob pattern
     */
    public static boolean matchesGlob(String relativePath, String globPattern) {
        if (globPattern == null || relativePath == null) {
            return false;
        }

        // 1. Fetch or compile the fnmatch-equivalent regex pattern
        Pattern pat = PATTERN_CACHE.computeIfAbsent(globPattern, LeappUtils::compileFnmatchPattern);

        // 2. Normalize the path to use forward slashes (cross-platform safety)
        String pathToCheck = GLOB_ROOT_PREFIX + relativePath.replace('\\', '/');

        // 3. Match against the full string exactly like Python
        return pat.matcher(pathToCheck).matches();
    }

    /**
     * Maps a shell-style glob pattern to a Java Regex like Python's fnmatch.translate does. Unlike Java's PathMatcher,
     * '*' and '?' also match directory separators. Supports character classes ("[seq]", "[!seq]").
     */
    private static Pattern compileFnmatchPattern(String globPattern) {
        // patterns built with pathlib get backslashes on Windows
        String glob = globPattern.replace('\\', '/');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
            case '*':
                sb.append(".*"); // fnmatch treats * as .* (crosses slashes)
                break;
            case '?':
                sb.append("."); // fnmatch treats ? as .
                break;
            case '[':
                int end = findClassEnd(glob, i);
                if (end < 0) {
                    sb.append("\\[");
                    break;
                }
                String stuff = glob.substring(i + 1, end);
                boolean negated = stuff.startsWith("!");
                if (negated) {
                    stuff = stuff.substring(1);
                }
                sb.append(negated ? "[^" : "[");
                for (int k = 0; k < stuff.length(); k++) {
                    char s = stuff.charAt(k);
                    // chars that are special inside a Java character class, but literal in fnmatch
                    if (s == '\\' || s == '[' || s == ']' || s == '&' || s == '^') {
                        sb.append('\\');
                    }
                    sb.append(s);
                }
                sb.append(']');
                i = end;
                break;
            // Escape all standard regex metacharacters
            case '.':
            case '+':
            case '^':
            case '$':
            case '|':
            case '{':
            case '}':
            case '(':
            case ')':
            case ']':
                sb.append('\\').append(c);
                break;
            default:
                sb.append(c);
                break;
            }
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    }

    /**
     * Locates the case item that corresponds to a device file path stored in plugin data (e.g. the "File Path" column of
     * chromeOfflinePages). The item path is expected to be {@code pathRoot + pathValue}; since Android exposes the same
     * storage under several symbolic links, the given path aliases of the value are tried too. The
     * item whose in-case path exactly equals one of the candidate paths is returned, or {@code null} when none matches.
     *
     * @param searcher the case searcher
     * @param pathRoot the evidence root path (the in-case prefix of every item under this extraction)
     * @param pathValue the device file path stored in the plugin data cell
     * @param pathAliases app-visible path prefixes and the extraction location each one resolves to (see
     *        {@link LeappTool#getPathAliases()})
     * @return the corresponding case item, or {@code null} when nothing matches
     */
    public static IItemReader findItemByPath(IItemSearcher searcher, String pathRoot, String pathValue,
            Map<String, String> pathAliases) {
        if (searcher == null || StringUtils.isBlank(pathValue)) {
            return null;
        }

        String path = pathValue.trim();

        // first try the path exactly as stored by the app
        IItemReader item = searchItemByPath(searcher, pathRoot + path);
        if (item != null) {
            return item;
        }

        // then try the known path aliases (e.g. storage symlinks), one at a time
        for (Map.Entry<String, String> symlink : pathAliases.entrySet()) {
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
        // the path query matches whole terms in any position, so it can hit the whole subtree of itemPath:
        // iterating lazily loads only the items up to the exact one, instead of all the hits at once
        for (IItemReader item : searcher.searchIterable(phrase(BasicProps.PATH, itemPath))) {
            if (itemPath.equals(item.getPath())) {
                return item;
            }
        }
        return null;
    }

}
