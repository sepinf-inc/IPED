package iped.utils;

import java.nio.charset.Charset;

public final class CharsetUtil {

    /**
     * The operating system charset, e.g. windows-1252 on most Windows installs
     * and UTF-8 on Linux.
     * <p>
     * Up to Java 17 this was the default charset. Since Java 18 (JEP 400) the
     * default charset is always UTF-8 and the OS one is in the "native.encoding"
     * property (Java 17+). Use it explicitly for data whose encoding follows the
     * OS: output of external tools, text files provided by the user and text
     * from evidence whose format does not define a charset. This keeps the
     * behavior IPED had with the default charset on Java 11.
     */
    public static final Charset NATIVE = nativeCharset();

    private CharsetUtil() {
    }

    private static Charset nativeCharset() {
        String name = System.getProperty("native.encoding"); //$NON-NLS-1$
        if (name != null) {
            try {
                return Charset.forName(name);
            } catch (IllegalArgumentException e) {
                // unsupported or invalid name, fall back below
            }
        }
        return Charset.defaultCharset();
    }
}
