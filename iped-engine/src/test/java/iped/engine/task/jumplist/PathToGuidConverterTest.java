package iped.engine.task.jumplist;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

public class PathToGuidConverterTest {

    private static PathToGuidConverter converter;

    @BeforeClass
    public static void setUp() {
        converter = PathToGuidConverter.getInstance();
    }

    @Test
    public void testProgramFilesPath() {
        String path = "%PROGRAMFILES%\\My App\\app.exe";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 3 GUID paths for %PROGRAMFILES%", 3, result.size());
        assertThat("Missing FOLDERID_ProgramFiles", result, hasItem("{905E63B6-C1BF-494E-B29C-65B732D3D21A}\\My App\\app.exe"));
        assertThat("Missing FOLDERID_ProgramFilesX64", result, hasItem("{6D809377-6AF0-444b-8957-A3773F02200E}\\My App\\app.exe"));
        assertThat("Missing FOLDERID_ProgramFilesX86", result, hasItem("{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}\\My App\\app.exe"));
    }

    @Test
    public void testProgramFilesX86PathCaseInsensitive() {
        String path = "%programfiles(x86)%\\My App\\app.exe"; // Lowercase key in input
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 2 GUID paths for %PROGRAMFILES(X86)%", 2, result.size());
        assertThat("Missing FOLDERID_ProgramFiles (generic)", result, hasItem("{905E63B6-C1BF-494E-B29C-65B732D3D21A}\\My App\\app.exe"));
        assertThat("Missing FOLDERID_ProgramFilesX86", result, hasItem("{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}\\My App\\app.exe"));
    }

    @Test
    public void testPublicDownloadsPath() {
        String path = "%PUBLIC%\\Downloads\\installer.exe";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 1 GUID path for %PUBLIC%\\Downloads", 1, result.size());
        assertEquals("Incorrect GUID for FOLDERID_PublicDownloads", "{3D644C9B-1FB8-4f30-9B45-F670235F79C0}\\installer.exe", result.get(0));
    }

    @Test
    public void testUserProfileDesktopPath() {
        String path = "%USERPROFILE%\\Desktop\\shortcut.lnk";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        
        assertEquals("Should return 1 GUID path for %USERPROFILE%\\Desktop", 1, result.size());
        assertEquals("Incorrect GUID for FOLDERID_Desktop", "{B4BFCC3A-DB2C-424C-B029-7FE99A87C641}\\shortcut.lnk", result.get(0));
    }

    @Test
    public void testUserProfileGenericPath() {
        String path = "%USERPROFILE%\\Documents\\doc.txt";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 1 GUID path for %USERPROFILE%", 1, result.size());
        assertEquals("Incorrect GUID for FOLDERID_Profile", "{5E6C858F-0E22-4760-9AFE-EA3317B67173}\\Documents\\doc.txt", result.get(0));
    }

    @Test
    public void testWinDirSystem32Path() {
        String path = "%WINDIR%\\System32\\cmd.exe";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 1 GUID path for %WINDIR%\\System32", 2, result.size());
        assertThat("Missing FOLDERID_System", result, hasItem("{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\\cmd.exe"));
        assertThat("Missing FOLDERID_SystemX86", result, hasItem("{D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27}\\cmd.exe"));
    }

    @Test
    public void testWinDirGenericPath() {
        String path = "%WINDIR%\\notepad.exe";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 1 GUID path for %WINDIR%", 1, result.size());
        assertEquals("Incorrect GUID for FOLDERID_Windows", "{F38BF404-1D43-42F2-9305-67DE0B28FC23}\\notepad.exe", result.get(0));
    }

    @Test
    public void testLocalAppDataProgramsPath() {
        String path = "%LOCALAPPDATA%\\Programs\\SomeApp\\app.exe";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 1 GUID path for %LOCALAPPDATA%\\Programs", 1, result.size());
        assertEquals("Incorrect GUID for FOLDERID_UserProgramFiles", "{5CD7AEE2-2219-4A67-B85D-6C9CE15660CB}\\SomeApp\\app.exe", result.get(0));
    }

    @Test
    public void testPathWithNoMappedKey() {
        String path = "C:\\StandardPath\\app.exe";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertThat("Result list should be empty for unmapped path", result, is(empty()));
    }

    @Test
    public void testPathWithUnmappedEnvVar() {
        String path = "%TEMP%\\file.tmp";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertThat("Result list should be empty for unmapped environment variable", result, is(empty()));
    }

    @Test
    public void testPathWithKeyNotAtStart() {
        String path = "C:\\SomeFolder\\%PROGRAMFILES%";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertThat(result, is(empty()));
    }
    
    @Test
    public void testPathKeyIsEntirePath() {
        String path = "%USERPROFILE%";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 1 GUID path for %USERPROFILE% as entire path", 1, result.size());
        assertEquals("Incorrect GUID for FOLDERID_Profile", "{5E6C858F-0E22-4760-9AFE-EA3317B67173}", result.get(0));
    }

    @Test
    public void testNullInputPath() {
        List<String> result = converter.pathsWithKnownFolderIds(null);
        assertNotNull("Result list should not be null for null input", result);
        assertThat(result, is(empty()));
    }

    @Test
    public void testEmptyInputPath() {
        List<String> result = converter.pathsWithKnownFolderIds("");
        assertNotNull("Result list should not be null for empty input", result);
        assertThat(result, is(empty()));
    }

    @Test
    public void testPartialKeyMatchPrevention() {
        String path = "%PROGRAMFILES%EXTRA\\app.exe";
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertThat("Should not match partial key like %PROGRAMFILES%EXTRA", result, is(empty()));
    }

    @Test
    public void testPathKeyWithTrailingSlash() {
        String path = "%PROGRAMFILES%\\"; // Path key itself has a trailing slash
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should return 3 GUID paths for %PROGRAMFILES%\\", 3, result.size());
        assertThat("Missing FOLDERID_ProgramFiles with trailing slash", result, hasItem("{905E63B6-C1BF-494E-B29C-65B732D3D21A}\\"));
    }

    @Test
    public void testPathWithForwardSlashes() {
        String path = "%PROGRAMFILES%/My App/app.exe"; // Using forward slashes
        List<String> result = converter.pathsWithKnownFolderIds(path);
        assertEquals("Should handle forward slashes in input path", 3, result.size());
        assertThat("Missing FOLDERID_ProgramFiles with forward slash", result, hasItem("{905E63B6-C1BF-494E-B29C-65B732D3D21A}/My App/app.exe"));
    }
}
