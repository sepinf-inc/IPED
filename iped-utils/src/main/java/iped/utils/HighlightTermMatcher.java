package iped.utils;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds the terms to highlight in a text in a single pass, ignoring case. Where
 * several terms start at the same position the longest one wins, and matches
 * don't overlap.
 */
public class HighlightTermMatcher {

    private static final int[] NO_MATCHES = new int[0];

    private static class TrieNode {
        private Map<Character, TrieNode> children;
        private boolean endsTerm;
    }

    private final TrieNode root = new TrieNode();

    private final BitSet firstChars = new BitSet();

    /**
     * @param terms
     *            the terms to find. Blank terms are ignored, they would match
     *            everywhere.
     */
    public HighlightTermMatcher(Collection<String> terms) {
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String lowerCase = toLowerCase(term);
            TrieNode node = root;
            for (int i = 0; i < lowerCase.length(); i++) {
                if (node.children == null) {
                    node.children = new HashMap<>();
                }
                node = node.children.computeIfAbsent(lowerCase.charAt(i), c -> new TrieNode());
            }
            node.endsTerm = true;
            firstChars.set(lowerCase.charAt(0));
        }
    }

    public boolean isEmpty() {
        return firstChars.isEmpty();
    }

    /**
     * @return the start and end offsets of the matches in the text, as {start0,
     *         end0, start1, end1, ...}
     */
    public int[] find(String text) {
        if (isEmpty() || text == null) {
            return NO_MATCHES;
        }
        String lowerCase = toLowerCase(text);
        int[] matches = NO_MATCHES;
        int count = 0;
        int start = 0;
        while (start < lowerCase.length()) {
            int end = firstChars.get(lowerCase.charAt(start)) ? findLongestTermEnd(lowerCase, start) : -1;
            if (end < 0) {
                start++;
                continue;
            }
            // keep combining marks with their base character, e.g. the vowel and tone mark of "กี่"
            while (end < text.length()) {
                int codePoint = text.codePointAt(end);
                if (!isCombiningMark(codePoint)) {
                    break;
                }
                end += Character.charCount(codePoint);
            }
            if (count == matches.length) {
                matches = Arrays.copyOf(matches, Math.max(8, count * 2));
            }
            matches[count++] = start;
            matches[count++] = end;
            start = end;
        }
        return count == matches.length ? matches : Arrays.copyOf(matches, count);
    }

    private int findLongestTermEnd(String text, int start) {
        int end = -1;
        TrieNode node = root;
        for (int i = start; i < text.length() && node.children != null; i++) {
            node = node.children.get(text.charAt(i));
            if (node == null) {
                break;
            }
            if (node.endsTerm) {
                end = i + 1;
            }
        }
        return end;
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    /**
     * Lower cases each code point on its own, so offsets in the result are valid
     * in the original text too: String.toLowerCase() may change the length, e.g.
     * of "İ".
     */
    static String toLowerCase(String text) {
        StringBuilder sb = null;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            int lowerCase = Character.toLowerCase(codePoint);
            if (lowerCase != codePoint && Character.charCount(lowerCase) == charCount) {
                if (sb == null) {
                    sb = new StringBuilder(text.length()).append(text, 0, i);
                }
                sb.appendCodePoint(lowerCase);
            } else if (sb != null) {
                sb.append(text, i, i + charCount);
            }
            i += charCount;
        }
        return sb == null ? text : sb.toString();
    }
}
