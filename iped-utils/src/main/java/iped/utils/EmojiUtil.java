package iped.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EmojiUtil {
    private static final Map<String, String> base64ImagesPerCode = new HashMap<String, String>();
    private static final Map<Integer, Character> specialCharsReplacement = new HashMap<Integer, Character>();

    static {
        initEmojiImages();
        initSpecialChars();
    }

    public static String replace(String s, char replacement) {
        int len = s.length();
        boolean found = false;
        for (int i = 0; i < len;) {
            int c = s.codePointAt(i);
            if (c > 0x2000 && base64ImagesPerCode.containsKey(format(c))) {
                found = true;
                break;
            }
            i += Character.charCount(c);
        }
        if (!found) {
            return s;
        }
        StringBuilder ret = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len;) {
            int c = s.codePointAt(i);
            Character rep = specialCharsReplacement.get(c);
            if (rep != null) {
                i += Character.charCount(c);
                ret.append(rep == 0 ? replacement : rep);
                continue;
            }
            if (c > 0x2000) {
                sb.delete(0, sb.length());
                sb.append(format(c));
                if (base64ImagesPerCode.containsKey(sb.toString())) {
                    i += Character.charCount(c);
                    int skip = i;
                    int j = i;
                    int cnt = 0;
                    while (j < len && ++cnt <= 5) {
                        int next = s.codePointAt(j);
                        j += Character.charCount(next);
                        sb.append('_').append(format(next));
                        if (base64ImagesPerCode.containsKey(sb.toString())) {
                            skip = j;
                        }
                    }
                    ret.append(replacement);
                    i = skip;
                    if (i < len) {
                        int next = s.codePointAt(i);
                        if (next == 0xFE0E || next == 0xFE0F) {
                            i += Character.charCount(next);
                        }
                    }
                    continue;
                }
            }
            ret.append(s.charAt(i));
            i++;
        }
        return ret.toString();
    }

    public static byte[] replaceByImages(byte[] inBytes) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(inBytes), StandardCharsets.UTF_8));
            in.mark(inBytes.length);
            String s = null;
            Set<String> usedEmojis = new HashSet<String>();
            Map<Integer, String> replacedLines = new HashMap<Integer, String>();
            int idx = 0;
            boolean insidePrev = false;
            while ((s = in.readLine()) != null) {
                final int len = s.length();
                boolean found = false;
                boolean inside = insidePrev;
                for (int i = 0; i < len;) {
                    int c = s.codePointAt(i);
                    if (c == '<') {
                        inside = true;
                    } else if (c == '>') {
                        inside = false;
                    } else if (!inside && c > 0x2000 && base64ImagesPerCode.containsKey(format(c))) {
                        found = true;
                        break;
                    }
                    i += Character.charCount(c);
                }
                if (found) {
                    StringBuilder line = new StringBuilder();
                    StringBuilder sb = new StringBuilder();
                    String key = "";
                    inside = insidePrev;
                    for (int i = 0; i < len;) {
                        int c = s.codePointAt(i);
                        if (c == '<') {
                            inside = true;
                        } else if (c == '>') {
                            inside = false;
                        } else if (!inside && c > 0x2000) {
                            sb.delete(0, sb.length());
                            sb.append(format(c));
                            if (base64ImagesPerCode.containsKey(sb.toString())) {
                                key = sb.toString();
                                i += Character.charCount(c);
                                int skip = i;
                                int j = i;
                                int cnt = 0;
                                while (j < len && ++cnt <= 5) {
                                    int next = s.codePointAt(j);
                                    j += Character.charCount(next);
                                    sb.append('_').append(format(next));
                                    if (base64ImagesPerCode.containsKey(sb.toString())) {
                                        key = sb.toString();
                                        skip = j;
                                    }
                                }
                                line.append("<img class=\"e").append(key).append("\"/>");
                                usedEmojis.add(key);
                                i = skip;
                                if (i < len) {
                                    int next = s.codePointAt(i);
                                    if (next == 0xFE0E || next == 0xFE0F) {
                                        i += Character.charCount(next);
                                    }
                                }
                                continue;
                            }
                        }
                        line.append(s.charAt(i));
                        i++;
                    }
                    replacedLines.put(idx, line.toString());
                }
                insidePrev = inside;
                idx++;
            }
            if (!replacedLines.isEmpty()) {
                in.reset();
                ByteArrayOutputStream outBytes = new ByteArrayOutputStream(inBytes.length + inBytes.length / 4);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(outBytes, StandardCharsets.UTF_8));
                boolean styleFound = false;
                boolean bodyFound = false;
                idx = 0;
                while ((s = in.readLine()) != null) {
                    if (!styleFound) {
                        out.println(s);
                        if (s.indexOf("<style>") >= 0) {
                            styleFound = true;
                            for (String c : usedEmojis) {
                                out.print("img.e");
                                out.print(c);
                                out.print("{width:18px;height:18px;margin-right:1px;margin-left:1px;");
                                out.print("vertical-align:middle;content:url('data:image/png;base64,");
                                out.print(base64ImagesPerCode.get(c));
                                out.println("');}");
                            }
                        }
                    } else if (!bodyFound) {
                        out.println(s);
                        if (s.indexOf("<body>") >= 0) {
                            bodyFound = true;
                        }
                    } else {
                        String line = replacedLines.get(idx);
                        out.println(line == null ? s : line);
                    }
                    idx++;
                }
                out.flush();
                return outBytes.toByteArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtil.closeQuietly(in);
        }
        return inBytes;
    }

    private static final String format(int c) {
        return String.format("%04x", c);
    }

    private static final void initEmojiImages() {
        try {
            String path = "iped/utils/emoji/";
            CodeSource src = EmojiUtil.class.getProtectionDomain().getCodeSource();
            if (src != null) {
                URL jar = src.getLocation();
                try (ZipInputStream zip = new ZipInputStream(jar.openStream())) {
                    while (true) {
                        ZipEntry e = zip.getNextEntry();
                        if (e == null) {
                            break;
                        }
                        String name = e.getName();
                        if (name.startsWith(path)) {
                            int pos = name.indexOf(".png");
                            if (pos > 0) {
                                String code = name.substring(path.length(), pos);
                                byte[] bytes = EmojiUtil.class.getResourceAsStream("/" + name).readAllBytes();
                                String base64 = Base64.getEncoder().encodeToString(bytes);
                                base64ImagesPerCode.put(code, base64);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initSpecialChars() {
        initRange(0x2200, 0x22FF); // Mathematical Operators
        initRange(0x2A00, 0x2AFF); // Supplemental Mathematical Operators
        initRange(0x2100, 0x214F); // Letterlike Symbols
        initRange(0x27C0, 0x27EF); // Miscellaneous Mathematical Symbols-A
        initRange(0x2980, 0x29FF); // Miscellaneous Mathematical Symbols-B
        initRange(0x2300, 0x23FF); // Miscellaneous Technical
        initRange(0x25A0, 0x25FF); // Geometric Shapes
        initRange(0x2190, 0x21FF); // Arrows
        initRange(0x27F0, 0x27FF); // Supplemental Arrows-A
        initRange(0x2900, 0x2900); // Supplemental Arrows-B
        initRange(0x2B00, 0x2BFF); // Miscellaneous Symbols and Arrows
        initRange(0x20D0, 0x20FF); // Combining Diacritical Marks for Symbols
        initRange(0x1EE00, 0x1EEFF); // Arabic Mathematical Alphabetic Symbols
        initRange(0x1D400, 0x1D7FF); // Mathematical Alphanumeric Symbols
        char c = 'A';
        for (int i = 0x1D400; i <= 0x1D6A3; i++) {
            specialCharsReplacement.put(i, c);
            c = c == 'Z' ? 'a' : c == 'z' ? 'A' : (char) (c + 1);
        }
        c = '0';
        for (int i = 0x1D7CE; i <= 0x1D7FF; i++) {
            specialCharsReplacement.put(i, c);
            c = c == '9' ? '0' : (char) (c + 1);
        }
    }

    private static void initRange(int start, int end) {
        for (int i = start; i <= end; i++) {
            specialCharsReplacement.put(i, (char) 0);
        }
    }
}
