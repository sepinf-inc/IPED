package iped.app.home.utils;

import iped.app.home.MainFrame;
import iped.io.URLUtil;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * @created 22/11/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */
public class CasePathManager {

    private static CasePathManager casePathManager;
    private final File testPath = null; //new File("");
    private File casePath;
    private File libDir;

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
            URL url = URLUtil.getURL(MainFrame.class);
            String rootPath = new File(url.toURI()).getParent();
            // test for report generation from case folder
            if (rootPath.endsWith("iped" + File.separator + "lib")) { //$NON-NLS-1$ //$NON-NLS-2$
                rootPath = new File(url.toURI()).getParentFile().getParent();
            }
            if (rootPath.endsWith("iped")) { //$NON-NLS-1$ //$NON-NLS-2$
                rootPath = new File(url.toURI()).getParentFile().getParent();
            }
            casePath = new File(rootPath);
            libDir = new File(casePath + "/iped/lib");
        }

        System.out.println(new File(casePath, "iped").toPath());
        if (!new File(casePath, "iped").exists()) //$NON-NLS-1$
            casePath = null;
    }

    private static List<String> getSystemProperties() {
        List<String> props = new ArrayList<>();
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            String key = e.getKey().toString();
            if (!key.equals("java.class.path") && !key.equals("sun.boot.library.path")) {
                props.add("-D" + key + "=" + e.getValue().toString().replace("\"", "\\\""));
            }
        }
        return props;
    }

    private File detectLibDir() throws URISyntaxException {
        //URL url = MainFrame.class.getProtectionDomain().getCodeSource().getLocation();
        URL url = URLUtil.getURL(MainFrame.class);
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
        String LOCAL_CONFIG = "LocalConfig.txt";
        return Paths.get(casePath.getAbsolutePath(), "/iped", LOCAL_CONFIG).toFile();
    }

}
