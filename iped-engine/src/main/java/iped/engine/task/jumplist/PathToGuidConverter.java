package iped.engine.task.jumplist;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

public class PathToGuidConverter {

    // Map of known path "keys" (which can be env variables or composite paths) to lists of GUIDs.
    // The comparator ensures that more specific keys (e.g., "%USERPROFILE%\DOWNLOADS") 
    // are checked before less specific keys (e.g., "%USERPROFILE%").
    private final Map<String, List<String>> guidsMap = new TreeMap<>((e1, e2) -> {
        int ret = Integer.compare(e2.length(), e1.length());
        if (ret == 0) ret = e2.compareTo(e1);
        return ret;
    });

    private static volatile PathToGuidConverter instance;

    public static PathToGuidConverter getInstance() {
        if (instance == null) {
            synchronized (PathToGuidConverter.class) {
                if (instance == null) {
                    instance = new PathToGuidConverter();
                }
            }
        }
        return instance;
    }

    private PathToGuidConverter() {

        // Documentation for Known Folder IDs:
        // https://learn.microsoft.com/en-us/windows/win32/shell/knownfolderid

        // For %PROGRAMFILES%
        List<String> programFilesGuids = new ArrayList<>();
        programFilesGuids.add("{905E63B6-C1BF-494E-B29C-65B732D3D21A}"); // FOLDERID_ProgramFiles
        programFilesGuids.add("{6D809377-6AF0-444b-8957-A3773F02200E}"); // FOLDERID_ProgramFilesX64
        programFilesGuids.add("{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}"); // FOLDERID_ProgramFilesX86
        guidsMap.put("%PROGRAMFILES%", unmodifiableList(programFilesGuids));

        // For %PROGRAMFILES(X86)%
        List<String> programFilesX86Guids = new ArrayList<>();
        programFilesX86Guids.add("{905E63B6-C1BF-494E-B29C-65B732D3D21A}"); // FOLDERID_ProgramFiles
        programFilesX86Guids.add("{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}"); // FOLDERID_ProgramFilesX86
        guidsMap.put("%PROGRAMFILES(X86)%", unmodifiableList(programFilesX86Guids));

        // For "%COMMONPROGRAMFILES%"
        List<String> commonProgramFilesGuids = new ArrayList<>();
        commonProgramFilesGuids.add("{F7F1ED05-9F6D-47A2-AAAE-29D317C6F066}"); // FOLDERID_ProgramFilesCommon
        commonProgramFilesGuids.add("{DE974D24-D9C6-4D3E-BF91-F4455120B917}"); // FOLDERID_ProgramFilesCommonX86
        commonProgramFilesGuids.add("{6365D5A7-0F0D-45E5-87F6-0DA56B6A4F7D}"); // FOLDERID_ProgramFilesCommonX64
        guidsMap.put("%COMMONPROGRAMFILES%", unmodifiableList(commonProgramFilesGuids));
        
        // For "%COMMONPROGRAMFILES(X86)%"
        List<String> commonProgramFilesX86Guids = new ArrayList<>();
        commonProgramFilesX86Guids.add("{F7F1ED05-9F6D-47A2-AAAE-29D317C6F066}"); // FOLDERID_ProgramFilesCommon
        commonProgramFilesX86Guids.add("{DE974D24-D9C6-4D3E-BF91-F4455120B917}"); // FOLDERID_ProgramFilesCommonX86
        guidsMap.put("%COMMONPROGRAMFILES(X86)%", unmodifiableList(commonProgramFilesX86Guids));

        // System Folders
        List<String> system32Guids = new ArrayList<>();
        system32Guids.add("{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}"); // FOLDERID_System
        system32Guids.add("{D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27}"); // FOLDERID_SystemX86
        guidsMap.put("%WINDIR%\\SYSTEM32", unmodifiableList(system32Guids));
        guidsMap.put("%WINDIR%\\SYSWOW64", singletonList("{D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27}")); // FOLDERID_SystemX86

        // ProgramData
        guidsMap.put("%PROGRAMDATA%", singletonList("{62AB5D82-FDC1-4DC3-A9DD-070D1D495D97}")); // FOLDERID_ProgramData
        guidsMap.put("%ALLUSERSPROFILE%", singletonList("{62AB5D82-FDC1-4DC3-A9DD-070D1D495D97}")); // FOLDERID_ProgramData

        // Common single environment variables
        guidsMap.put("%WINDIR%", singletonList("{F38BF404-1D43-42F2-9305-67DE0B28FC23}")); // FOLDERID_Windows
        guidsMap.put("%USERPROFILE%", singletonList("{5E6C858F-0E22-4760-9AFE-EA3317B67173}")); // FOLDERID_Profile
        guidsMap.put("%APPDATA%", singletonList("{3EB685DB-65F9-4CF6-A03A-E3EF65729F3D}")); // FOLDERID_RoamingAppData
        guidsMap.put("%LOCALAPPDATA%", singletonList("{F1B32785-6FBA-4FCF-9D55-7B8E7F157091}")); // FOLDERID_LocalAppData
        guidsMap.put("%PUBLIC%", singletonList("{DFDF76A2-C82A-4D63-906A-5644AC457385}")); // FOLDERID_Public

        // Paths that can contain executables (including composite paths)

        // Downloads
        guidsMap.put("%PUBLIC%\\DOWNLOADS", singletonList("{3D644C9B-1FB8-4f30-9B45-F670235F79C0}")); // FOLDERID_PublicDownloads
        guidsMap.put("%USERPROFILE%\\DOWNLOADS", singletonList("{374DE290-123F-4565-9164-39C4925E467B}")); // FOLDERID_Downloads

        // Desktop
        guidsMap.put("%USERPROFILE%\\DESKTOP", singletonList("{B4BFCC3A-DB2C-424C-B029-7FE99A87C641}")); // FOLDERID_Desktop
        guidsMap.put("%PUBLIC%\\DESKTOP", singletonList("{C4AA340D-F20F-4863-AFEF-F87EF2E6BA25}")); // FOLDERID_PublicDesktop

        // UserProgramFiles
        guidsMap.put("%LOCALAPPDATA%\\PROGRAMS", singletonList("{5CD7AEE2-2219-4A67-B85D-6C9CE15660CB}")); // FOLDERID_UserProgramFiles
        guidsMap.put("%LOCALAPPDATA%\\PROGRAMS\\COMMON", singletonList("{BCBD3057-CA5C-4622-B42D-BC56DB0AE516}")); // FOLDERID_UserProgramFilesCommon
    }

    /**
     * Given a generic path starting with a known path key (like an environment
     * variable or a composite path such as "%PUBLIC%\Downloads"), returns a list of
     * possible paths with the key replaced by corresponding Known Folder GUIDs.
     *
     * @param genericPath The generic path string, e.g., "%PROGRAMFILES%\My App\app.exe" 
     * or "%PUBLIC%\Downloads\installer.exe".
     * @return A list of strings with GUID-based paths. Returns an empty list if no mapped path key is found at the beginning of the path, or if the
     *         input path is null/empty.
     */
    @Nonnull
    public List<String> pathsWithKnownFolderIds(String genericPath) {
        List<String> resultPaths = new ArrayList<>();

        if (StringUtils.isBlank(genericPath)) {
            return resultPaths;
        }

        for (Map.Entry<String, List<String>> entry : guidsMap.entrySet()) {

            String pathKey = entry.getKey();
            String pathKeyUpperCase = pathKey.toUpperCase();
            String genericPathUpperCase = genericPath.replace('/', '\\').toUpperCase();

            if (genericPathUpperCase.startsWith(pathKeyUpperCase)) {
                // Ensure that the match is for the whole key, either ending the string
                // or followed by a path separator.
                if (genericPath.length() == pathKey.length()
                        || (genericPath.length() > pathKey.length() && (genericPath.charAt(pathKey.length()) == '\\'
                                || genericPath.charAt(pathKey.length()) == '/'))) {

                    String remainingPath = genericPath.substring(pathKey.length());
                    List<String> guids = entry.getValue();

                    for (String guid : guids) {
                        resultPaths.add(guid + remainingPath);
                    }

                    return resultPaths;
                }
            }
        }

        // No mapped path key was found at the beginning of the generic path.
        return resultPaths;
    }
}
