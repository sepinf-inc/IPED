package iped.parsers.util;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.ptr.PointerByReference;

import iped.parsers.browsers.edge.EdgeWebCacheParser;
import iped.parsers.browsers.edge.EsedbLibrary;
import iped.parsers.mail.win10.Win10MailParser;

public class EsedbManager {
    private static EsedbLibrary esedbLibrary;
    private static boolean loadFailed = false;    

    private static Logger LOGGER = LoggerFactory.getLogger(EsedbManager.class);

    static {
        if (Platform.isWindows()) {
            String osArch = System.getProperty("os.arch");
            String arch = osArch.equals("amd64") || osArch.equals("x86_64") ? "x64" : "x86";

            try (InputStream is = EdgeWebCacheParser.class
                    .getResourceAsStream("/nativelibs/libesedb/" + arch + "/libesedb.dll")) {
                File file = new File(System.getProperty("java.io.tmpdir") + "/libesedb.dll");
                if (!file.exists())
                    Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.load(file.getAbsolutePath());

            } catch (Throwable e) {
                LOGGER.error("Libesedb dll not loaded properly. " + Win10MailParser.class.getSimpleName()
                        + " will be disabled.", e);
                loadFailed = true;
            }
        }
        if (!loadFailed) {
            try {
                esedbLibrary = (EsedbLibrary) Native.load("esedb", EsedbLibrary.class);
                LOGGER.info("Libesedb library version: " + esedbLibrary.libesedb_get_version());

            } catch (Throwable e) {
                LOGGER.error("Libesedb JNA not loaded properly. " + EdgeWebCacheParser.class.getSimpleName()
                        + " will be disabled.");
                e.printStackTrace();
                loadFailed = true;
            }
        }
    }


    public static void printError(String function, int result, String path, PointerByReference errorPointer) {
        LOGGER.warn("Error decoding " + path + ": Function '" + function + "'. Function result number '"
                + result + "' Error value: " + errorPointer.getValue().getString(0)); //$NON-NLS-1$
        esedbLibrary.libesedb_error_free(errorPointer);
    }

    public static EsedbLibrary getEsedbLibrary() {
        return esedbLibrary;
    }
    
    public static boolean loadFailed() {
        return loadFailed;
    }
}
