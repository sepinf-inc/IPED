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


    public static int getInt32Value(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerRef, String filePath, PointerByReference errorPointer) {
        if (value_entry >= 0) {
            IntByReference recordValueDataInt = new IntByReference();

            int result = esedbLibrary.libesedb_record_get_value_32bit(recordPointerRef.getValue(), value_entry, recordValueDataInt,
                errorPointer);
            if (result < 0)
                printError("Record Get 32-Bit Data", result, filePath, errorPointer);
            return  recordValueDataInt.getValue();
        }
        return -1;
    }

    public static int getInt16Value(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerRef, String filePath, PointerByReference errorPointer) {
        if (value_entry >= 0) {
            IntByReference recordValueDataInt = new IntByReference();

            int result = esedbLibrary.libesedb_record_get_value_16bit(recordPointerRef.getValue(), value_entry, recordValueDataInt,
            errorPointer);
            if (result < 0)
                printError("Record Get 16-Bit Data", result, filePath, errorPointer);
            return recordValueDataInt.getValue();
        }
        return -1;
    }

    public static String getUnicodeValue(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerRef, String filePath, PointerByReference errorPointer) {
        if (value_entry >= 0) {
            IntByReference recordValueDataInt = new IntByReference();
            Memory recordValueData = new Memory(3072);

            int result = esedbLibrary.libesedb_record_get_value_utf8_string_size(recordPointerRef.getValue(), value_entry,
                recordValueDataInt, errorPointer);
            if (result < 0)
                printError("Record Get UTF8 String Size", result, filePath, errorPointer);
            if ((recordValueDataInt.getValue() > 0) && (result == 1)) {
                result = esedbLibrary.libesedb_record_get_value_utf8_string(recordPointerRef.getValue(), value_entry,
                        recordValueData, recordValueDataInt.getValue(), errorPointer);
                if (result < 0)
                    printError("Record Get UTF8 String at " + value_entry, result, filePath, errorPointer);
                return recordValueData.getString(0, "UTF-8");
            }
        }
        return "";
    }

    public static byte[] getBinaryValue(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerRef, String filePath, PointerByReference errorPointer) {
        if (value_entry >= 0) {
            IntByReference recordValueDataInt = new IntByReference();
            Memory recordValueData = new Memory(3072);

            int result = esedbLibrary.libesedb_record_get_value_binary_data_size(recordPointerRef.getValue(), value_entry,
                recordValueDataInt, errorPointer);
            if (result < 0)
                printError("Record Get Binary Data Size", result, filePath, errorPointer);
            if ((recordValueDataInt.getValue() > 0) && (result == 1)) {
                result = esedbLibrary.libesedb_record_get_value_binary_data(recordPointerRef.getValue(), value_entry,
                        recordValueData, recordValueDataInt.getValue(), errorPointer);
                if (result < 0)
                    printError("Record Get Binary Data at " + value_entry, result, filePath, errorPointer);
                return recordValueData.getByteArray(0, recordValueDataInt.getValue());
           }
        }
        return null;
    }

    public static Boolean getBooleanValue(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerRef, String filePath, PointerByReference errorPointer) {
        if (value_entry >= 0) {
            ShortByReference recordValueDataShort = new ShortByReference();
            int result = esedbLibrary.libesedb_record_get_value_boolean(recordPointerRef.getValue(), value_entry, recordValueDataShort,
                errorPointer);
            if (result < 0)
                printError("Record Get Boolean Data", result, filePath, errorPointer);
            return recordValueDataShort.getValue() == 1;
        }
        return null;
    }


    public static Date getFileTime(EsedbLibrary esedbLibrary, int value_entry, PointerByReference recordPointerRef, String filePath, PointerByReference errorPointer) {
        if (value_entry >= 0) {
            LongByReference recordValueData = new LongByReference();

            int result = esedbLibrary.libesedb_record_get_value_64bit(recordPointerRef.getValue(), value_entry, recordValueData,
                    errorPointer);
            if (result < 0)
                printError("Record Get FileTime Data", result, filePath, errorPointer);
            
            Date date = new Date((recordValueData.getValue() - 116444736000000000L)/10000);
            
            return date;
        }
        return null;
    }

    public static void printError(String function, int result, String path, PointerByReference errorPointer) {
        LOGGER.warn("Error decoding " + path + ": Function '" + function + "'. Function result number '"
                + result + "' Error value: " + errorPointer.getValue().getString(0)); //$NON-NLS-1$
        esedbLibrary.libesedb_error_free(errorPointer);
    }


    public static int getColumnPosition(EsedbLibrary esedbLibrary, String columnCode, PointerByReference errorPointer, PointerByReference tablePointerRef, String filePath) {
        int position = -1;

        PointerByReference recordPointerRef = new PointerByReference();
        IntByReference numberOfColumns = new IntByReference();
        int columnFlags = 1;

        // use first record
        int result = esedbLibrary.libesedb_table_get_record(tablePointerRef.getValue(), 0, recordPointerRef,
            errorPointer);
        if (result < 0) {   // no records
            EsedbManager.printError("Table Get Record", result, filePath, errorPointer);
            return -1;
        }

        result = esedbLibrary.libesedb_table_get_number_of_columns(tablePointerRef.getValue(),
            numberOfColumns, columnFlags, errorPointer);
        if (result < 0) { // no columns
            EsedbManager.printError("Table Get Number of Columns", result, filePath, errorPointer);
            return -1;
        }

        for (int col = 0; col < numberOfColumns.getValue(); col++) {
            IntByReference columnNameSize =  new IntByReference();
            Memory columnName =  new Memory(3072);

            result = esedbLibrary.libesedb_record_get_utf8_column_name_size(recordPointerRef.getValue(), col,
                columnNameSize, errorPointer);
            if (result < 0) continue;

            result = esedbLibrary.libesedb_record_get_utf8_column_name(recordPointerRef.getValue(),
                col, columnName, columnNameSize.getValue(), errorPointer);
            if (result < 0) continue;

            if (columnName.getString(0).equals(columnCode)) {
                position = col;
                break;
            }
        }

        if (position == -1) {
            IntByReference tableNameSize = new IntByReference();
            Memory tableNameRef = new Memory(256);

            result = esedbLibrary.libesedb_table_get_utf8_name_size(tablePointerRef.getValue(), tableNameSize,
                errorPointer);
            if (result < 0)
                EsedbManager.printError("Table Get UTF8 Name Size", result, filePath, errorPointer);

            result = esedbLibrary.libesedb_table_get_utf8_name(tablePointerRef.getValue(), tableNameRef,
                tableNameSize.getValue(), errorPointer);
            if (result < 0)
                EsedbManager.printError("Table Get UTF8 Name", result, filePath, errorPointer);
    
            String tableName = tableNameRef.getString(0);
            LOGGER.warn("While decoding '" + filePath + "': Column '" + columnCode + "' not found in table '" + tableName + "'");
        }

        return position;
    }
}
