package iped.app.bootstrap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class UnicodeArgsTest {

    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final List<String> MAIN = Collections.singletonList("iped.app.bootstrap.Bootstrap");

    @Test
    public void testApplicationArgsAfterJarOrMainClass() {
        String[] jar = { "javaw.exe", "-Xmx1G", "-jar", "C:\\iped\\iped.jar", "-d", "C:\\证据", "-o", "out" };
        assertArrayEquals(new String[] { "-d", "C:\\证据", "-o", "out" }, UnicodeArgs.applicationArgs(jar, MAIN));

        String[] cp = { "java.exe", "-cp", "a.jar;b.jar", "-Dx=y", "iped.app.bootstrap.Bootstrap", "-d", "x" };
        assertArrayEquals(new String[] { "-d", "x" }, UnicodeArgs.applicationArgs(cp, MAIN));

        assertNull(UnicodeArgs.applicationArgs(new String[] { "java.exe", "Other" }, MAIN));
    }

    @Test
    public void testOwnCommandLineIsUsedWhenIntact() {
        String[] argv = { "java.exe", "-jar", "iped.jar", "-d", "C:\\Привет.txt" };
        String[] recovered = UnicodeArgs.recover(argv, MAIN, () -> {
            throw new AssertionError("launcher must not be queried");
        }, CP1252);
        assertArrayEquals(new String[] { "-d", "C:\\Привет.txt" }, recovered);
    }

    @Test
    public void testArgsConvertedByNativeLauncherAreRecovered() {
        // iped.exe converted its arguments to the ANSI code page before starting java
        String[] own = { "javaw.exe", "-jar", "iped.jar", "-d", "C:\\??\\ação.E01", "-o", "out" };
        String[] launcher = { "iped.exe", "-d", "C:\\证据\\ação.E01", "-o", "out" };
        assertArrayEquals(new String[] { "-d", "C:\\证据\\ação.E01", "-o", "out" },
                UnicodeArgs.recover(own, MAIN, () -> launcher, CP1252));
    }

    @Test
    public void testLauncherArgsAreIgnoredIfTheyDoNotMatch() {
        // a real '?' typed by the user, parent is a shell
        String[] own = { "java.exe", "-jar", "iped.jar", "-p", "pass?word" };
        String[] shell = { "cmd.exe", "/c", "something", "else" };
        assertArrayEquals(new String[] { "-p", "pass?word" }, UnicodeArgs.recover(own, MAIN, () -> shell, CP1252));
        assertArrayEquals(new String[] { "-p", "pass?word" }, UnicodeArgs.recover(own, MAIN, () -> null, CP1252));
    }

    @Test
    public void testMatchesAfterConversion() {
        assertTrue(UnicodeArgs.matchesAfterConversion(new String[] { "C:\\证据" }, new String[] { "C:\\??" }, CP1252));
        // best fit conversions (ł -> l) and supplementary characters
        assertTrue(UnicodeArgs.matchesAfterConversion(new String[] { "Łódź" }, new String[] { "Lódz" }, CP1252));
        assertTrue(UnicodeArgs.matchesAfterConversion(new String[] { "a🎉b" }, new String[] { "a??b" }, CP1252));
        assertFalse(UnicodeArgs.matchesAfterConversion(new String[] { "C:\\证据" }, new String[] { "D:\\??" }, CP1252));
        assertFalse(UnicodeArgs.matchesAfterConversion(new String[] { "C:\\证据" }, new String[] { "C:\\???" }, CP1252));
        assertFalse(UnicodeArgs.matchesAfterConversion(new String[] { "abc" }, new String[] { "abd" }, CP1252));
        assertFalse(UnicodeArgs.matchesAfterConversion(new String[] { "a", "b" }, new String[] { "a" }, CP1252));
    }

    @Test
    public void testEncodingRoundTrip() {
        CharsetEncoder encoder = CP1252.newEncoder();
        assertFalse(UnicodeArgs.needsEncoding("C:\\ação\\out", encoder, true));
        assertTrue(UnicodeArgs.needsEncoding("C:\\证据", encoder, true));
        // wildcards would be expanded by the Windows java launcher
        assertTrue(UnicodeArgs.needsEncoding("pass?word*", encoder, true));
        assertFalse(UnicodeArgs.needsEncoding("pass?word*", encoder, false));
        assertTrue(UnicodeArgs.needsEncoding(UnicodeArgs.ENCODED_ARG_PREFIX + "x", encoder, true));

        String[] original = { "-d", "C:\\证据\\Привет 🎉.E01", "-p", "a?b*", UnicodeArgs.ENCODED_ARG_PREFIX + "x" };
        String[] encoded = new String[original.length];
        for (int i = 0; i < original.length; i++) {
            encoded[i] = UnicodeArgs.needsEncoding(original[i], encoder, true) ? UnicodeArgs.encode(original[i])
                    : original[i];
            for (char c : encoded[i].toCharArray()) {
                assertTrue("encoded args must be plain ASCII without wildcards", c < 128 && c != '?' && c != '*');
            }
        }
        assertEquals("-d", encoded[0]);
        assertArrayEquals(original, UnicodeArgs.decode(encoded));
        // args that just look encoded are kept
        assertArrayEquals(new String[] { UnicodeArgs.ENCODED_ARG_PREFIX + "%%%" },
                UnicodeArgs.decode(new String[] { UnicodeArgs.ENCODED_ARG_PREFIX + "%%%" }));
        assertEquals(Arrays.asList("plain"), Arrays.asList(UnicodeArgs.decode(new String[] { "plain" })));
    }
}
