package iped.utils;

import java.io.UnsupportedEncodingException;
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

    public static String decodeIfUtf8(String value) {
        if (isUtf8(value)) {
            try {
                byte[] buf16 = value.getBytes("UTF-16LE");
                byte[] buf8 = new byte[buf16.length / 2];
                for (int i = 0; i < buf8.length; i++) {
                    buf8[i] = buf16[i * 2];
                }
                value = new String(buf8, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return value;
    }

    public static boolean isUtf8(String value) {
        int idx = -1;
        for (char c : new char[] { 'Ã', 'Ä', 'Å', 'Ð', 'Ñ' }) {
            if ((idx = value.indexOf(c)) != -1) {
                break;
            }
        }
        if (idx > -1 && idx < value.length() - 1) {
            int c = value.codePointAt(idx + 1);
            if (c >= 0x0080 && c <= 0xFFFF) {
                return true;
            }
        }
        return false;
    }
}
