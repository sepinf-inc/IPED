package iped.engine.task.jumplist;

import java.util.ArrayList;
import java.util.List;

public class AppIDCalculator {

    private static final long POLY64 = 0x92C64265D32139A4L;
    private static final long[] CRC_TABLE = new long[256];

    static {
        initCrc64();
    }

    private static void initCrc64() {
        for (int b = 0; b < 256; b++) {
            long crc = (long) b;
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ POLY64;
                } else {
                    crc >>>= 1;
                }
            }
            CRC_TABLE[b] = crc;
        }
    }

    public static String envpath(String path) {

        String currentPath = path;

        // (?i) for case-insensitive.
        currentPath = currentPath.replaceAll("(?i).*/ProgramData", "%ALLUSERSPROFILE%");
        currentPath = currentPath.replaceAll("(?i).*/Windows", "%WINDIR%");
        currentPath = currentPath.replaceAll("(?i).*/Users/[^/]+?/AppData/Roaming", "%APPDATA%");
        currentPath = currentPath.replaceAll("(?i).*/Users/[^/]+?/AppData/Local", "%LOCALAPPDATA%");
        currentPath = currentPath.replaceAll("(?i).*/Users/[^/]+?", "%USERPROFILE%");
        currentPath = currentPath.replaceAll("(?i).*/Users/Public", "%PUBLIC%");
        currentPath = currentPath.replaceAll("(?i).*/Program Files \\(x86\\)/Common Files", "%COMMONPROGRAMFILES(X86)%");
        currentPath = currentPath.replaceAll("(?i).*/Program Files/Common Files", "%COMMONPROGRAMFILES%");
        currentPath = currentPath.replaceAll("(?i).*/Program Files \\(x86\\)", "%PROGRAMFILES(X86)%");
        currentPath = currentPath.replaceAll("(?i).*/Program Files", "%PROGRAMFILES%");
        currentPath = currentPath.replaceAll(".*/vol_vol[0-9]*", "%SYSTEMDRIVE%");

        return currentPath;
    }

    private static byte[] utf16le(String s) {
        if (s == null) {
            return new byte[0];
        }
        byte[] resultBytes = new byte[s.length() * 2];
        for (int i = 0; i < s.length(); i++) {
            resultBytes[2 * i] = (byte) s.charAt(i);
            resultBytes[2 * i + 1] = 0;
        }
        return resultBytes;
    }

    private static String crc64(byte[] data) {
        long crc = 0xFFFFFFFFFFFFFFFFL;

        for (byte b : data) {
            int index = ((int) (crc ^ b)) & 0xFF;
            crc = CRC_TABLE[index] ^ (crc >>> 8);
        }

        return String.format("%08x", crc);
    }

    public static List<String> calculateAppIDs(String path) {

        List<String> results = new ArrayList<>();

        if (path == null) {
            return results;
        }
        String envPath = envpath(path);

        // only handle paths in Windows known folders
        if (!envPath.startsWith("%")) {
            return results;
        }

        List<String> folderIdPaths = PathToGuidConverter.getInstance().pathsWithKnownFolderIds(envPath);
        for (String folderIdPath : folderIdPaths) {
            String normalizedPath = folderIdPath.replace('/', '\\').toUpperCase();
            results.add(crc64(utf16le(normalizedPath)));
        }

        return results;
    }
}