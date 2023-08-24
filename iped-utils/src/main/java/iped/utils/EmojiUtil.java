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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EmojiUtil {
    private static final Map<String, String> base64ImagesPerCode = new HashMap<String, String>();
    private static final AtomicBoolean init = new AtomicBoolean();

    public static byte[] replaceByImages(byte[] inBytes) {
        synchronized (init) {
            if (!init.get()) {
                init();
                init.set(true);
            }
        }
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

    private static final void init() {
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
}
