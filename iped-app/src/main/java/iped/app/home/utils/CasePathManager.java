package iped.app.home.utils;

import iped.app.home.MainFrame;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

/*
 * @created 22/11/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */
public class CasePathManager {

    private static CasePathManager casePathManager;
    private File testPath = new File("");
    private File casePath;
    private File libDir;

    private final String CONFIG_FILE = "IPEDConfig.txt";
    private final String LOCAL_CONFIG = "LocalConfig.txt";
    private final String CONF_DIR = "conf";
    private final String PROFILES_DIR = "profiles";
    private final String CASE_PROFILE_DIR = "profile";

    private CasePathManager() {
        try {
            detectCasePath();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public static CasePathManager getInstance(){
        if( casePathManager == null )
            casePathManager = new CasePathManager();
        return casePathManager;
    }


    private void detectCasePath() throws URISyntaxException {
        if (testPath != null) {
            casePath = testPath;
            libDir = new File(casePath + "/iped/lib");
        }else {
            libDir = detectLibDir();
            casePath = libDir.getParentFile().getParentFile();
        }


        if (!new File(casePath, "iped").exists()) //$NON-NLS-1$
            casePath = null;
    }

    private File detectLibDir() throws URISyntaxException {
        URL url = MainFrame.class.getProtectionDomain().getCodeSource().getLocation();
        File jarFile = null;
        if (url.toURI().getAuthority() == null)
            jarFile = new File(url.toURI());
        else
            jarFile = new File(url.toURI().getSchemeSpecificPart());

        return jarFile.getParentFile();
    }

    public File getCasePath() {
        return casePath;
    }

    public File getLibDir() {
        return libDir;
    }

    public File getConfigPath() {
        return Paths.get(casePath.getAbsolutePath(), "/iped").toFile();
    }

    public File getLocalConfigFile(){
        return Paths.get(casePath.getAbsolutePath(), "/iped", LOCAL_CONFIG).toFile();
    }

}
