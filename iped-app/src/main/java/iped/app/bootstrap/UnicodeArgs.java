package iped.app.bootstrap;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Keeps command line arguments with any Unicode character intact.
 * <p>
 * On Windows, java.exe converts the arguments to the ANSI code page: characters
 * it cannot represent become '?', and unquoted arguments with '?' or '*' are
 * then expanded as file name wildcards. IPED's native launchers (iped.exe,
 * IPED-SearchApp.exe) also convert their arguments before starting Java. So
 * evidence paths, output folders, passwords... with, for instance, Chinese or
 * Cyrillic characters in a western Windows would be lost.
 * <p>
 * {@link #recoverWindowsArgs} reads the original arguments from the operating
 * system and {@link #encodeForChildProcess}/{@link #decode} pass them to the
 * processing JVM started by {@link Bootstrap} in an ASCII only form.
 */
public final class UnicodeArgs {

    public static final String ENCODED_ARG_PREFIX = "iped-b64:"; //$NON-NLS-1$

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT) //$NON-NLS-1$ //$NON-NLS-2$
            .startsWith("windows"); //$NON-NLS-1$

    private UnicodeArgs() {
    }

    /**
     * On Windows, returns the arguments of this process as given by the user,
     * otherwise (or if they cannot be read) returns args unchanged.
     *
     * @param mainClassNames
     *            names of the main class this process may have been started
     *            with, to find where the application arguments start (after the
     *            JVM options), "-jar file" is recognized too
     */
    public static String[] recoverWindowsArgs(String[] args, Collection<String> mainClassNames) {
        if (!IS_WINDOWS) {
            return args;
        }
        try {
            Supplier<String[]> launcherArgs = () -> ProcessHandle.current().parent()
                    .map(parent -> WindowsCommandLine.processArgs(parent.pid())).orElse(null);
            String[] recovered = recover(WindowsCommandLine.currentProcessArgs(), mainClassNames, launcherArgs,
                    commandLineCharset());
            return recovered != null ? recovered : args;
        } catch (Throwable t) {
            // JNA not available or an old Windows version: use the args as received
            return args;
        }
    }

    /**
     * @param ownArgv
     *            Unicode command line arguments of this process
     * @param launcherArgv
     *            Unicode arguments of the parent process, read only if needed
     * @param ansi
     *            charset the arguments may have been converted to
     * @return the application arguments, or null if they could not be found
     */
    static String[] recover(String[] ownArgv, Collection<String> mainClassNames, Supplier<String[]> launcherArgv,
            Charset ansi) {
        String[] ownArgs = applicationArgs(ownArgv, mainClassNames);
        if (ownArgs == null || !containsQuestionMark(ownArgs)) {
            return ownArgs;
        }
        // A launcher may have replaced characters by '?' before starting this process. If the last
        // arguments of the parent process convert to exactly what we received, they are the originals.
        String[] parentArgv = launcherArgv.get();
        if (parentArgv != null && parentArgv.length - 1 >= ownArgs.length) {
            String[] candidate = Arrays.copyOfRange(parentArgv, parentArgv.length - ownArgs.length, parentArgv.length);
            if (matchesAfterConversion(candidate, ownArgs, ansi)) {
                return candidate;
            }
        }
        return ownArgs;
    }

    /** @return the arguments after the main class or "-jar file", or null */
    static String[] applicationArgs(String[] argv, Collection<String> mainClassNames) {
        if (argv == null) {
            return null;
        }
        for (int i = 1; i < argv.length; i++) {
            if (mainClassNames.contains(argv[i])) {
                return Arrays.copyOfRange(argv, i + 1, argv.length);
            }
            if ("-jar".equals(argv[i])) { //$NON-NLS-1$
                return i + 1 < argv.length ? Arrays.copyOfRange(argv, i + 2, argv.length) : null;
            }
        }
        return null;
    }

    private static boolean containsQuestionMark(String[] args) {
        for (String arg : args) {
            if (arg.indexOf('?') >= 0) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesAfterConversion(String[] originals, String[] converted, Charset ansi) {
        if (originals.length != converted.length) {
            return false;
        }
        CharsetEncoder encoder = ansi.newEncoder();
        for (int i = 0; i < originals.length; i++) {
            if (!matchesAfterConversion(originals[i], converted[i], encoder)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if converted could result from converting original to the ANSI code
     * page: representable characters are kept, others become one replacement
     * character ('?' or a "best fit" one) per UTF-16 unit or per code point.
     */
    private static boolean matchesAfterConversion(String original, String converted, CharsetEncoder encoder) {
        int j = 0;
        for (int i = 0; i < original.length();) {
            int cp = original.codePointAt(i);
            int n = Character.charCount(cp);
            String chars = original.substring(i, i + n);
            if (j >= converted.length()) {
                return false;
            }
            if (encoder.canEncode(chars)) {
                if (!converted.startsWith(chars, j)) {
                    return false;
                }
                j += n;
            } else {
                j++;
                if (n == 2 && j < converted.length() && converted.charAt(j) == '?') {
                    j++;
                }
            }
            i += n;
        }
        return j == converted.length();
    }

    /**
     * Encodes the arguments that would not reach a child JVM intact through the
     * command line: characters not representable in the platform charset and, on
     * Windows, wildcards.
     */
    public static List<String> encodeForChildProcess(List<String> args) {
        CharsetEncoder encoder = commandLineCharset().newEncoder();
        List<String> result = new ArrayList<>(args.size());
        for (String arg : args) {
            result.add(needsEncoding(arg, encoder, IS_WINDOWS) ? encode(arg) : arg);
        }
        return result;
    }

    static boolean needsEncoding(String arg, CharsetEncoder encoder, boolean windows) {
        return arg.startsWith(ENCODED_ARG_PREFIX) || !encoder.canEncode(arg)
                || (windows && (arg.indexOf('*') >= 0 || arg.indexOf('?') >= 0));
    }

    static String encode(String arg) {
        return ENCODED_ARG_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(arg.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes the arguments encoded by {@link #encodeForChildProcess}. */
    public static String[] decode(String[] args) {
        String[] result = args.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i].startsWith(ENCODED_ARG_PREFIX)) {
                try {
                    byte[] bytes = Base64.getUrlDecoder().decode(result[i].substring(ENCODED_ARG_PREFIX.length()));
                    result[i] = new String(bytes, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    // not encoded by us, keep it
                }
            }
        }
        return result;
    }

    /**
     * @return true if the text can be passed to a child process in the command
     *         line (e.g. as a -D system property) without being changed
     */
    public static boolean canPassOnCommandLine(String text) {
        return commandLineCharset().newEncoder().canEncode(text);
    }

    /** Charset used by the JVM to decode command line arguments. */
    static Charset commandLineCharset() {
        String name = System.getProperty("sun.jnu.encoding"); //$NON-NLS-1$
        if (name != null) {
            try {
                return Charset.forName(name);
            } catch (IllegalArgumentException e) {
                // fall back to the default
            }
        }
        return Charset.defaultCharset();
    }
}
