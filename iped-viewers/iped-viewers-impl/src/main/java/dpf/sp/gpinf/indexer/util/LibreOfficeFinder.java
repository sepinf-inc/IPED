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
    
    private static File winPathLO = new File(System.getProperty("user.home") + "/.indexador/libreoffice6"); //$NON-NLS-1$ //$NON-NLS-2$
    //private static File winPathLO = new File("C:\\Program Files\\LibreOffice");
    
    private static String libreofficeZip = "tools/libreoffice.zip";
    
    private static File baseDir;
    
    private static String detectedPath = null;
    
    public LibreOfficeFinder(File baseFolder) {
        baseDir = baseFolder;
    }
    
    public String getLOPath() {
        if(detectedPath == null) {
            detectedPath = System.getProperty("libreOfficePath");
            if(detectedPath != null)
                return detectedPath;
            
            if (!System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
                try {
                  IApplicationAssistant ass = new ApplicationAssistant(null); //$NON-NLS-1$
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
            }else {
                File compressedLO = new File(baseDir, libreofficeZip); //$NON-NLS-1$

                if (winPathLO.exists() || compressedLO.exists()) { //$NON-NLS-1$ //$NON-NLS-2$
                    LOExtractor extractor = new LOExtractor(compressedLO, winPathLO);
                    if(extractor.decompressLO())
                        detectedPath = winPathLO.getAbsolutePath();
                }
            }
            if(detectedPath != null)
                System.setProperty("libreOfficePath", detectedPath);
        }
        return detectedPath;
    }

}

