package dpf.sp.gpinf.indexer.util;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ag.ion.bion.officelayer.application.IApplicationAssistant;
import ag.ion.bion.officelayer.application.ILazyApplicationInfo;
import ag.ion.bion.officelayer.internal.application.ApplicationAssistant;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.LOExtractor;

public class LibreOfficeFinder {

    private static Logger LOGGER = LoggerFactory.getLogger(LibreOfficeFinder.class);

    private static final String targetName = "libreoffice6";

    private static File winPathLO = getWinLOTarget();

    private static String libreofficeZip = "tools/libreoffice.zip";

    private static File baseDir;

    private static String detectedPath = null;

    private static File getWinLOTarget() {
        String userHome = System.getProperty("user.home"); //$NON-NLS-1$
        File path = new File(userHome + "/.indexador/" + targetName); //$NON-NLS-1$
        if (!path.exists()) {
            path = new File(userHome + "/AppData/Local/iped/" + targetName); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return path;
    }

    public LibreOfficeFinder(File baseFolder) {
        baseDir = baseFolder;
    }

    public String getLOPath() {
        if (detectedPath == null) {
            detectedPath = System.getProperty("libreOfficePath");
            if (detectedPath != null)
                return detectedPath;

            if (!System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
                try {
                    IApplicationAssistant ass = new ApplicationAssistant(null); // $NON-NLS-1$
                    ILazyApplicationInfo[] ila = ass.getLocalApplications();
                    if (ila.length != 0) {
                        LOGGER.info("Detected LibreOffice {} {}", ila[0].getMajorVersion(), ila[0].getHome()); //$NON-NLS-1$
                        if (ila[0].getMajorVersion() > 6) {
                            LOGGER.error("LibreOffice {} not tested!", ila[0].getMajorVersion()); //$NON-NLS-1$
                        }
                        detectedPath = ila[0].getHome();
                    }

                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            } else {
                File compressedLO = new File(baseDir, libreofficeZip); // $NON-NLS-1$

                if (winPathLO.exists() || compressedLO.exists()) { // $NON-NLS-1$ //$NON-NLS-2$
                    LOExtractor extractor = new LOExtractor(compressedLO, winPathLO);
                    if (extractor.decompressLO())
                        detectedPath = winPathLO.getAbsolutePath();
                }
            }
            if (detectedPath != null)
                System.setProperty("libreOfficePath", detectedPath);
        }
        return detectedPath;
    }

}
