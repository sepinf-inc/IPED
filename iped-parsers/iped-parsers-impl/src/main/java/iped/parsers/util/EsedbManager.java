package iped.parsers.util;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.ShortByReference;

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


    public static EsedbLibrary getEsedbLibrary() {
        return esedbLibrary;
    }
    
    public static boolean loadFailed() {
        return loadFailed;
    }


    public static int getInt32Value(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerReference, String filePath, PointerByReference errorPointer) {
        IntByReference recordValueDataInt = new IntByReference();

        int result = esedbLibrary.libesedb_record_get_value_32bit(recordPointerReference.getValue(), value_entry, recordValueDataInt,
        errorPointer);
        if (result < 0)
            printError("Record Get 32-Bit Data", result, filePath, errorPointer);
        return  recordValueDataInt.getValue();
    }

    public static int getInt16Value(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerReference, String filePath, PointerByReference errorPointer) {
        IntByReference recordValueDataInt = new IntByReference();

        int result = esedbLibrary.libesedb_record_get_value_16bit(recordPointerReference.getValue(), value_entry, recordValueDataInt,
        errorPointer);
        if (result < 0)
            printError("Record Get 16-Bit Data", result, filePath, errorPointer);
        return  recordValueDataInt.getValue();
    }

    public static String getUnicodeValue(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerReference, String filePath, PointerByReference errorPointer) {
        IntByReference recordValueDataInt = new IntByReference();
        Memory recordValueData = new Memory(3072);

        int result = esedbLibrary.libesedb_record_get_value_utf16_string_size(recordPointerReference.getValue(), value_entry,
            recordValueDataInt, errorPointer);
        if (result < 0)
            printError("Record Get UTF16 String Size", result, filePath, errorPointer);
        if ((recordValueDataInt.getValue() > 0) && (result == 1)) {
            result = esedbLibrary.libesedb_record_get_value_utf8_string(recordPointerReference.getValue(), value_entry,
                    recordValueData, recordValueDataInt.getValue(), errorPointer);
            if (result < 0)
                printError("Record Get UTF8 String at " + value_entry, result, filePath, errorPointer);
            return recordValueData.getString(0);
        }
        return "";
    }

    public static Boolean getBooleanValue(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerReference, String filePath, PointerByReference errorPointer) {
        ShortByReference recordValueDataShort = new ShortByReference();

        int result = esedbLibrary.libesedb_record_get_value_boolean(recordPointerReference.getValue(), value_entry, recordValueDataShort,
            errorPointer);
        if (result < 0)
            printError("Record Get Boolean Data", result, filePath, errorPointer);
        return recordValueDataShort.getValue() == 1;
    }

    public static Date getFileTime(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerReference, String filePath, PointerByReference errorPointer) {
        LongByReference recordValueData = new LongByReference();

        int result = esedbLibrary.libesedb_record_get_value_64bit(recordPointerReference.getValue(), value_entry, recordValueData,
                errorPointer);
        if (result < 0)
            printError("Record Get FileTime Data", result, filePath, errorPointer);
        
        Date date = new Date((recordValueData.getValue() - 116444736000000000L)/10000);
        
        return date;
    }

    public static void printError(String function, int result, String path, PointerByReference errorPointer) {
        LOGGER.warn("Error decoding " + path + ": Function '" + function + "'. Function result number '"
                + result + "' Error value: " + errorPointer.getValue().getString(0)); //$NON-NLS-1$
        esedbLibrary.libesedb_error_free(errorPointer);
    }
}
