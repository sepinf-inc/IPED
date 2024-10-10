package iped.app.home.utils;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import iped.app.home.MainFrame;
import iped.data.IIPEDSource;
import iped.engine.config.LocalConfig;
import iped.io.URLUtil;

/*
 * @created 22/11/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */
public class CasePathManager {

    private static CasePathManager casePathManager;
    private final File testPath = null; // = new
                                        // File("/home/patrick.pdb/ipedtimeline-workspace/iped-parent/target/release/iped-4.1-snapshot");
                                        // //new File("");
    private File rootPath;
    private File libDir;
    static String IPED_MODULE_DIR = IIPEDSource.MODULE_DIR;

    private CasePathManager() {
        try {
            detectCasePath();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public static CasePathManager getInstance() {
        if (casePathManager == null)
            casePathManager = new CasePathManager();
        return casePathManager;
    }

    private void detectCasePath() throws URISyntaxException {
        if (testPath != null) {
            rootPath = testPath;
        } else {
            URL url = URLUtil.getURL(MainFrame.class);
            rootPath = new File(url.toURI()).getParentFile();
            if (rootPath.getName().equals("lib")) {
                rootPath = rootPath.getParentFile();
            }
        }
        libDir = new File(rootPath, "lib");
    }

    public File getCasePath() {
        return rootPath;
    }

    public File getLibDir() {
        return libDir;
    }

    public File getConfigPath() {
        return rootPath;
    }

    public File getLocalConfigFile() {
        return new File(rootPath, LocalConfig.CONFIG_FILE);
    }

}
