package iped.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

public class StringUtil {

    public static Comparator<String> getIgnoreCaseComparator() {
        return new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                if (o1 == null) {
                    return o2 == null ? 0 : -1;
                } else {
                    return o2 == null ? 1 : o1.trim().compareToIgnoreCase(o2.trim());
                }
            }
        };
    }

    public static String convertCamelCaseToSpaces(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return input.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    /**
     * Fixes a string that holds UTF-8 content wrongly decoded as ISO-8859-1 or
     * windows-1252 (e.g. "AÃ§Ã£o" becomes "Ação"). The original bytes are
     * recovered and strictly decoded as UTF-8; if they are not valid UTF-8, the
     * string is returned unchanged.
     */
    public static String decodeIfUtf8(String value) {
        if (value == null || !hasNonAscii(value)) {
            return value;
        }
        byte[] bytes = encodeStrict(value, StandardCharsets.ISO_8859_1);
        if (bytes == null) {
            bytes = encodeStrict(value, WINDOWS_1252);
        }
        if (bytes == null) {
            return value;
        }
        String decoded = decodeStrictUtf8(bytes);
        return decoded != null ? decoded : value;
    }

    /**
     * Checks if the given bytes contain non-ASCII content that is valid UTF-8.
     * 
     * @param partialEnd
     *            if true, an incomplete multi-byte sequence at the end is accepted
     *            (useful when checking just the beginning of a larger content).
     */
    public static boolean isValidUtf8(byte[] buf, int off, int len, boolean partialEnd) {
        boolean nonAscii = false;
        for (int i = off; i < off + len; i++) {
            if (buf[i] < 0) {
                nonAscii = true;
                break;
            }
        }
        if (!nonAscii) {
            return false;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(buf, off, len);
        CharBuffer out = CharBuffer.allocate(len);
        CoderResult result = decoder.decode(in, out, !partialEnd);
        if (result.isError()) {
            return false;
        }
        if (partialEnd) {
            // at most an incomplete sequence (up to 3 bytes) may be left
            return in.remaining() <= 3;
        }
        return !decoder.flush(out).isError();
    }

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static boolean hasNonAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) >= 0x80) {
                return true;
            }
        }
        return false;
    }

    private static byte[] encodeStrict(String value, Charset charset) {
        try {
            ByteBuffer bb = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[bb.remaining()];
            bb.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String decodeStrictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
