package iped.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Checks emoji sequences listed in the official Unicode emoji-test.txt file
 * (https://www.unicode.org/Public/emoji/latest/emoji-test.txt).
 */
public class EmojiUtilTest {

    // Subgroups without images
    private static final Set<String> skippedSubgroups = Set.of("country-flag", "subdivision-flag", "keycap");

    private static final Pattern linePattern = Pattern.compile("^([0-9A-F ]+?)\\s*;\\s*fully-qualified\\s*#.*");
    private static final Pattern imgPattern = Pattern.compile("<img class=\"e([0-9a-f_]+)\">");

    private static final class Emoji {
        final String text;
        final String key;
        final String line;

        Emoji(String text, String key, String line) {
            this.text = text;
            this.key = key;
            this.line = line;
        }
    }

    private static final List<Emoji> emojis = new ArrayList<>();

    @BeforeClass
    public static void loadEmojiTestFile() throws Exception {
        try (InputStream is = EmojiUtilTest.class.getResourceAsStream("emoji-test.txt");
                BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String subgroup = null;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("# subgroup:")) {
                    subgroup = line.substring(line.indexOf(':') + 1).trim();
                    continue;
                }
                Matcher m = linePattern.matcher(line);
                if (!m.matches() || skippedSubgroups.contains(subgroup)) {
                    continue;
                }
                StringBuilder text = new StringBuilder();
                StringBuilder key = new StringBuilder();
                for (String hex : m.group(1).split(" ")) {
                    int c = Integer.parseInt(hex, 16);
                    text.appendCodePoint(c);
                    // Image names do not include variation selectors
                    if (c != 0xFE0E && c != 0xFE0F) {
                        if (key.length() > 0) {
                            key.append('_');
                        }
                        key.append(String.format("%04x", c));
                    }
                }
                // EmojiUtil only handles sequences starting after U+2000 (e.g. skips © and ®)
                if (text.codePointAt(0) > 0x2000) {
                    emojis.add(new Emoji(text.toString(), key.toString(), line));
                }
            }
        }
        assertTrue("Few emojis loaded: " + emojis.size(), emojis.size() > 3500);
    }

    @Test
    public void testReplaceByImages() {
        List<String> failures = new ArrayList<>();
        for (Emoji emoji : emojis) {
            String html = "<html><head><style>\n</style></head><body>\nA" + emoji.text + "B\n</body></html>";
            String out = EmojiUtil.replaceByImages(html);
            String body = out.substring(out.indexOf("<body>"));
            List<String> keys = new ArrayList<>();
            Matcher m = imgPattern.matcher(body);
            while (m.find()) {
                keys.add(m.group(1));
            }
            if (!body.contains("A<img class=\"e" + emoji.key + "\">B") || keys.size() != 1
                    || !out.contains("img.e" + emoji.key + "{")) {
                failures.add(emoji.line + " -> " + keys);
            }
        }
        assertEquals("Emojis not replaced by a single image:\n" + String.join("\n", failures), 0, failures.size());
    }

    @Test
    public void testClean() {
        List<String> failures = new ArrayList<>();
        for (Emoji emoji : emojis) {
            String out = EmojiUtil.clean("A" + emoji.text + "B", '?');
            if (!out.equals("A?B")) {
                failures.add(emoji.line + " -> " + out);
            }
        }
        assertEquals("Emojis not replaced by a single char:\n" + String.join("\n", failures), 0, failures.size());
    }
}
