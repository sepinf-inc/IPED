package iped.engine.task.jumplist;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppIDCalculator {

    private static final long POLY64 = 0x92C64265D32139A4L;
    private static final long[] CRC_TABLE = new long[256];
    private static final Map<String, String> PG_GUIDS = new LinkedHashMap<>();

    static {
        initCrc64();
        initKnownGuids();
    }

    // https://learn.microsoft.com/pt-br/windows/win32/shell/knownfolderid?redirectedfrom=MSDN
    private static void initKnownGuids() {
        PG_GUIDS.put("%PROGRAMFILES(X86)%/COMMON FILES", "{DE974D24-D9C6-4D3E-BF91-F4455120B917}");
        PG_GUIDS.put("%PROGRAMFILES(X86)%", "{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}");
        PG_GUIDS.put("%PROGRAMFILES%/COMMON FILES", "{6365D5A7-0F0D-45E5-87F6-0DA56B6A4F7D}");
        PG_GUIDS.put("%PROGRAMFILES%/WINDOWS SIDEBAR\\GADGETS", "{7B396E54-9EC5-4300-BE0A-2482EBAE1A26}");
        PG_GUIDS.put("%PROGRAMFILES%", "{6D809377-6AF0-444b-8957-A3773F02200E}");
        PG_GUIDS.put("%WINDIR%/SYSTEM32", "{D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27}");
        PG_GUIDS.put("%WINDIR%/SYSWOW64", "{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}");
        PG_GUIDS.put("%WINDIR%/RESOURCES\\0409", "{2A00375E-224C-49DE-B8D1-440DF7EF3DDC}");
        PG_GUIDS.put("%WINDIR%/RESOURCES", "{8AD10C31-2ADB-4296-A8F7-E4701232C972}");
        PG_GUIDS.put("%WINDIR%/FONTS", "{FD228CB7-AE11-4AE3-864C-16F3910AB8FE}");
        PG_GUIDS.put("%WINDIR%", "{F38BF404-1D43-42F2-9305-67DE0B28FC23}");
        PG_GUIDS.put("%USERPROFILE%/SEARCHES", "{7D1D3A04-DEBB-4115-95CF-2F29DA2920DA}");
        PG_GUIDS.put("%USERPROFILE%/SAVED GAMES", "{4C5C32FF-BB9D-43B0-B5B4-2D72E54EAAA4}");
        PG_GUIDS.put("%USERPROFILE%/PICTURES\\SLIDE SHOWS", "{69D2CF90-FC33-4FB7-9A0C-EBB0F0FCB43C}");
        PG_GUIDS.put("%USERPROFILE%/PICTURES\\SCREENSHOTS", "{B7BEDE81-DF94-4682-A7D8-57A52620B86F}");
        PG_GUIDS.put("%USERPROFILE%/PICTURES", "{33E28130-4E1E-4676-835A-98395C3BC3BB}");
        PG_GUIDS.put("%USERPROFILE%/MUSIC\\PLAYLISTS", "{DE92C1C7-837F-4F69-A3BB-86E631204A23}");
        PG_GUIDS.put("%USERPROFILE%/MUSIC", "{4BD8D571-6D19-48D3-BE97-422220080E43}");
        PG_GUIDS.put("%USERPROFILE%/LINKS", "{BFB9D5E0-C6A9-404C-B2B2-AE6DB6AF4968}");
        PG_GUIDS.put("%USERPROFILE%/FAVORITES", "{1777F761-68AD-4D8A-87BD-30B759FA33DD}");
        PG_GUIDS.put("%USERPROFILE%/DOWNLOADS", "{374DE290-123F-4565-9164-39C4925E467B}");
        PG_GUIDS.put("%USERPROFILE%/DESKTOP", "{B4BFCC3A-DB2C-424C-B029-7FE99A87C641}");
        PG_GUIDS.put("%USERPROFILE%/CONTACTS", "{56784854-C6CB-462B-8169-88E350ACB882}");
        PG_GUIDS.put("%USERPROFILE%/APPDATA\\ROAMING\\MICROSOFT\\WINDOWS\\ACCOUNTCPICTURES", "{008CA0B1-55B4-4C56-B8A8-4DE4B299D3BE}");
        PG_GUIDS.put("%USERPROFILE%/APPDATA\\ROAMING", "{3EB685DB-65F9-4CF6-A03A-E3EF65729F3D}");
        PG_GUIDS.put("%USERPROFILE%/APPDATA\\LOCALLOW", "{A520A1A4-1780-4FF6-BD18-167343C5AF16}");
        PG_GUIDS.put("%USERPROFILE%/APPDATA\\LOCAL\\MICROSOFT\\WINDOWS\\ROAMINGTILES", "{00BCFC5A-ED94-4E48-96A1-3F6217F21990}");
        PG_GUIDS.put("%USERPROFILE%/APPDATA\\LOCAL\\MICROSOFT\\WINDOWS\\ROAMEDTILEIMAGES", "{AAA8D5A5-F1D6-4259-BAA8-78E7EF60835E}");
        PG_GUIDS.put("%USERPROFILE%/APPDATA\\LOCAL\\MICROSOFT\\WINDOWS\\APPLICATION SHORTCUTS", "{A3918781-E5F2-4890-B3D9-A7E54332328C}");
        PG_GUIDS.put("%USERPROFILE%/APPDATA\\LOCAL\\MICROSOFT\\WINDOWS SIDEBAR\\GADGETS", "{A75D362E-50FC-4FB7-AC2C-A8BEAA314493}");
        PG_GUIDS.put("%USERPROFILE%/APPDATA\\LOCAL", "{F1B32785-6FBA-4FCF-9D55-7B8E7F157091}");
        PG_GUIDS.put("%USERPROFILE%", "{5E6C858F-0E22-4760-9AFE-EA3317B67173}");
        PG_GUIDS.put("%SYSTEMDRIVE%/USERS\\PUBLIC", "{DFDF76A2-C82A-4D63-906A-5644AC457385}");
        PG_GUIDS.put("%SYSTEMDRIVE%/USERS\\%USERNAME%", "{5E6C858F-0E22-4760-9AFE-EA3317B67173}");
        PG_GUIDS.put("%SYSTEMDRIVE%/USERS", "{0762D272-C50A-4BB0-A382-697DCD729B80}");
        PG_GUIDS.put("%SYSTEMDRIVE%/PROGRAMDATA", "{62AB5D82-FDC1-4DC3-A9DD-070D1D495D97}");
        PG_GUIDS.put("%PUBLIC%/VIDEOS\\SAMPLE VIDEOS", "{859EAD94-2E85-48AD-A71A-0969CB56A6CD}");
        PG_GUIDS.put("%PUBLIC%/VIDEOS", "{2400183A-6185-49FB-A2D8-4A392A602BA3}");
        PG_GUIDS.put("%PUBLIC%/RECORDEDTV.LIBRARY-MS", "{1A6FDBA2-F42D-4358-A798-B74D745926C5}");
        PG_GUIDS.put("%PUBLIC%/PICTURES\\SAMPLE PICTURES", "{C4900540-2379-4C75-844B-64E6FAF8716B}");
        PG_GUIDS.put("%PUBLIC%/PICTURES", "{B6EBFB86-6907-413C-9AF7-4FC2ABF07CC5}");
        PG_GUIDS.put("%PUBLIC%/MUSIC\\SAMPLE PLAYLISTS", "{15CA69B3-30EE-49C1-ACE1-6B5EC372AFB5}");
        PG_GUIDS.put("%PUBLIC%/MUSIC\\SAMPLE MUSIC", "{B250C668-F57D-4EE1-A63C-290EE7D1AA1F}");
        PG_GUIDS.put("%PUBLIC%/MUSIC", "{3214FAB5-9757-4298-BB61-92A9DEAA44FF}");
        PG_GUIDS.put("%PUBLIC%/DOWNLOADS", "{3D644C9B-1FB8-4F30-9B45-F670235F79C0}");
        PG_GUIDS.put("%PUBLIC%/DOCUMENTS", "{ED4824AF-DCE4-45A8-81E2-FC7965083634}");
        PG_GUIDS.put("%PUBLIC%/DESKTOP", "{C4AA340D-F20F-4863-AFEF-F87EF2E6BA25}");
        PG_GUIDS.put("%PUBLIC%/ACCOUNTCPICTURES", "{0482AF6C-08F1-4C34-8C90-E17EC98B1E17}");
        PG_GUIDS.put("%PUBLIC%", "{DFDF76A2-C82A-4D63-906A-5644AC457385}");
        PG_GUIDS.put("%PROGRAMDATA%", "{62AB5D82-FDC1-4DC3-A9DD-070D1D495D97}");
        PG_GUIDS.put("%LOCALAPPDATA%", "{F1B32785-6FBA-4FCF-9D55-7B8E7F157091}");
        PG_GUIDS.put("%LOCALAPPDATA%/PROGRAMS", "{5CD7AEE2-2219-4A67-B85D-6C9CE15660CB}");
        PG_GUIDS.put("%LOCALAPPDATA%/PROGRAMS\\COMMON", "{BCBD3057-CA5C-4622-B42D-BC56DB0AE516}");
        PG_GUIDS.put("%LOCALAPPDATA%/MICROSOFT\\WINDOWS\\TEMPORARY INTERNET FILES", "{352481E8-33BE-4251-BA85-6007CAEDCF9D}");
        PG_GUIDS.put("%LOCALAPPDATA%/MICROSOFT\\WINDOWS\\RINGTONES", "{C870044B-F49E-4126-A9C3-B52A1FF411E8}");
        PG_GUIDS.put("%LOCALAPPDATA%/MICROSOFT\\WINDOWS\\HISTORY", "{D9DC8A3B-B784-432E-A781-5A1130A75963}");
        PG_GUIDS.put("%LOCALAPPDATA%/MICROSOFT\\WINDOWS\\GAMEEXPLORER", "{054FAE61-4DD8-4787-80B6-090220C4B700}");
        PG_GUIDS.put("%LOCALAPPDATA%/MICROSOFT\\WINDOWS\\BURN\\BURN", "{9E52AB10-F80D-49DF-ACB8-4330F5687855}");
        PG_GUIDS.put("%LOCALAPPDATA%/MICROSOFT\\WINDOWS PHOTO GALLERY\\ORIGINAL IMAGES", "{2C36C0AA-5812-4B87-BFD0-4CD0DFB19B39}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\TEMPLATES", "{A63293E8-664E-48DB-A079-DF759E0509F7}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\START MENU", "{625B53C3-AB48-4EC1-BA1F-A1EF4146FC19}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\START MENU\\PROGRAMS", "{A77F5D77-2E2B-44C3-A6A2-ABA601054A51}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\START MENU\\PROGRAMS\\STARTUP", "{B97D20BB-F46A-4C97-BA10-5E3608430854}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\START MENU\\PROGRAMS\\ADMINISTRATIVE TOOLS", "{724EF170-A42D-4FEF-9F26-B60E846FBA4F}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\SENDTO", "{8983036C-27C0-404B-8F08-102D10DCFD74}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\RECENT", "{AE50C081-EBD2-438A-8655-8A092E34987A}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\PRINTER SHORTCUTS", "{9274BD8D-CFD1-41C3-B35E-B13F55A758F4}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\NETWORK SHORTCUTS", "{C5ABBF53-E17F-4121-8900-86626FC2C973}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\LIBRARIES", "{1B3EA5DC-B587-4786-B4EF-BD1DC332AEAE}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\LIBRARIES\\VIDEOS.LIBRARY-MS", "{491E922F-5643-4AF4-A7EB-4E7A138D8174}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\LIBRARIES\\PICTURES.LIBRARY-MS", "{A990AE9F-A03B-4E80-94BC-9912D7504104}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\LIBRARIES\\MUSIC.LIBRARY-MS", "{2112AB0A-C86A-4FFE-A368-0DE96E47012E}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\LIBRARIES\\DOCUMENTS.LIBRARY-MS", "{7B0DB17D-9CD2-4A93-9733-46CC89022E7C}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\WINDOWS\\COOKIES", "{2B0F765D-C0E9-4171-908E-08A611B84FF6}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\INTERNET EXPLORER\\QUICK LAUNCH", "{52A4F021-7B75-48A9-9F6B-4B87A210BC8F}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\INTERNET EXPLORER\\QUICK LAUNCH\\USER", "{9E3995AB-1F9C-4F13-B827-48B24B6C7174}");
        PG_GUIDS.put("%APPDATA%/MICROSOFT\\INTERNET EXPLORER\\QUICK LAUNCH\\USER PINNED\\IMPLICITAPPSHORTCUTS",
                "{BCB5256F-79F6-4CEE-B725-DC34E402FD46}");
        PG_GUIDS.put("%APPDATA%", "{3EB685DB-65F9-4CF6-A03A-E3EF65729F3D}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/OEM LINKS", "{C1BAE2D0-10DF-4334-BEDD-7AA20B227A9D}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\TEMPLATES", "{B94237E7-57AC-4347-9151-B08C6C32D1F7}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\START MENU\\PROGRAMS\\ADMINISTRATIVE TOOLS", "{D0384E7D-BAC3-4797-8F14-CBA229B392B5}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\START MENU\\PROGRAMS\\STARTUP", "{82A5EA35-D9CD-47C5-9629-E15D2F714E6E}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\START MENU\\PROGRAMS", "{0139D44E-6AFE-49F2-8690-3DAFCAE6FFB8}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\START MENU", "{A4115719-D62E-491D-AA7C-E74B8BE3B067}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\RINGTONES", "{E555AB60-153B-4D17-9F04-A5FE99FC15EC}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\LIBRARIES", "{48DAF80B-E6CF-4F4E-B800-0E69D84EE384}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\GAMEEXPLORER", "{DEBF2536-E1A8-4C59-B6A2-414586476AEA}");
        PG_GUIDS.put("%ALLUSERSPROFILE%/MICROSOFT\\WINDOWS\\DEVICEMETADATASTORE", "{5CE4A5E9-E4EB-479D-B89F-130C02886155}");
        PG_GUIDS.put("%ALLUSERSPROFILE%", "{62AB5D82-FDC1-4DC3-A9DD-070D1D495D97}");
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
        currentPath = currentPath.replaceAll("(?i).*/Windows", "%windir%");
        currentPath = currentPath.replaceAll("(?i).*/Users/[^/]+?/AppData/Roaming", "%APPDATA%");
        currentPath = currentPath.replaceAll("(?i).*/Users/[^/]+?/AppData/Local", "%LOCALAPPDATA%");
        currentPath = currentPath.replaceAll("(?i).*/Users/[^/]+?", "%USERPROFILE%");
        currentPath = currentPath.replaceAll("(?i).*/Users/Public", "%PUBLIC%");
        currentPath = currentPath.replaceAll("(?i).*/ProgramData", "%ALLUSERSPROFILE%");
        currentPath = currentPath.replaceAll("(?i).*/ProgramData", "%ProgramData%");
        currentPath = currentPath.replaceAll("(?i).*/Program Files \\(x86\\)", "%ProgramFiles(x86)%");
        currentPath = currentPath.replaceAll("(?i).*/Program Files", "%ProgramFiles%");
        currentPath = currentPath.replaceAll(".*/vol_vol[0-9]*", "%SystemDrive%");
        return currentPath.toUpperCase();
    }

    public static String normalize(String path) {
        String currentPath = path;
        for (Map.Entry<String, String> entry : PG_GUIDS.entrySet()) {
            String onePathKey = entry.getKey();
            String guidValue = entry.getValue();
            Pattern pattern = Pattern.compile("^" + Pattern.quote(onePathKey) + "(.*)$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(currentPath);
            if (matcher.find()) {
                return guidValue + matcher.group(1);
            }
        }
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

    public static String calculateAppID(String path) {

        if (path == null) {
            return null;
        }

        path = envpath(path);

        // only handle paths in Windows known folders
        if (!path.startsWith("%")) {
            return null;
        }

        path = normalize(path);
        path = path.replace('/', '\\');
        path = path.toUpperCase();

        return crc64(utf16le(path));
    }
}