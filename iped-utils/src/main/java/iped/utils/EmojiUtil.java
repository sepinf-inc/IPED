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
    private static final Map<Integer, String> base64ImagesPerCode = new HashMap<Integer, String>();
    private static final AtomicBoolean init = new AtomicBoolean();

    public static byte[] replaceByImages(byte[] inBytes) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(inBytes), StandardCharsets.UTF_8));
            in.mark(inBytes.length);
            String s = null;
            Set<Integer> found = new HashSet<Integer>();
            while ((s = in.readLine()) != null) {
                for (int i = 0; i < s.length() - 1; i++) {
                    int c = s.codePointAt(i);
                    if (c > 0x2000) {
                        found.add(c);
                    }
                }
            }
            if (!found.isEmpty()) {
                synchronized (init) {
                    if (!init.get()) {
                        init();
                        init.set(true);
                    }
                }
                found.retainAll(base64ImagesPerCode.keySet());
                if (!found.isEmpty()) {
                    in.reset();
                    ByteArrayOutputStream outBytes = new ByteArrayOutputStream(inBytes.length + inBytes.length / 4);
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(outBytes, StandardCharsets.UTF_8));
                    boolean styleFound = false;
                    boolean bodyFound = false;
                    while ((s = in.readLine()) != null) {
                        if (!styleFound) {
                            out.println(s);
                            if (s.indexOf("<style>") >= 0) {
                                styleFound = true;
                                for (int c : found) {
                                    out.print("img.e");
                                    out.print(Integer.toHexString(c));
                                    out.print("{width:18px;height:18px;margin-right:1px;margin-left:1px;");
                                    out.print("content:url('data:image/png;base64,");
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
                            boolean hasEmoji = false;
                            if (s.length() > 1) {
                                for (int i = 0; i < s.length() - 1; i++) {
                                    int c = s.codePointAt(i);
                                    if (found.contains(c)) {
                                        hasEmoji = true;
                                        break;
                                    }
                                }
                            }
                            if (hasEmoji) {
                                for (int i = 0; i < s.length(); i++) {
                                    int c = s.codePointAt(i);
                                    if (found.contains(c)) {
                                        out.print("<img class=\"e");
                                        out.print(Integer.toHexString(c));
                                        out.print("\"/>");
                                        if (Character.isHighSurrogate(s.charAt(i))) {
                                            i++;
                                        }
                                    } else {
                                        out.print(s.charAt(i));
                                    }
                                }
                                out.println();
                            } else {
                                out.println(s);
                            }
                        }
                    }
                    out.flush();
                    return outBytes.toByteArray();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (Exception e) {
            }
        }
        return inBytes;
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
                        if (e.getName().startsWith(path) && e.getName().endsWith(".png")) {
                            byte[] bytes = EmojiUtil.class.getResourceAsStream("/" + e.getName()).readAllBytes();
                            String base64 = Base64.getEncoder().encodeToString(bytes);
                            int code = Integer.parseInt(e.getName().substring(path.length(), e.getName().length() - 4),
                                    16);
                            base64ImagesPerCode.put(code, base64);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
