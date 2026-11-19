/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.data.IItemReader;

public class IOUtil {

    private static Path tmpDir = null;

    private static final Set<String> DANGEROUS_EXTS = new HashSet<>(Arrays.asList("0XE", "73K", "89K", "8CK", "A6P",
            "A7R", "AC", "ACC", "ACR", "ACTC", "ACTION", "ACTM", "AHK", "AIR", "APK", "APP", "APPIMAGE", "APPLESCRIPT",
            "ARSCRIPT", "ASB", "AZW2", "BA_", "BAT", "BEAM", "BIN", "BTM", "CACTION", "CEL", "CELX", "CGI", "CMD",
            "COF", "COFFEE", "COM", "COMMAND", "CPL", "CSH", "CYW", "DEK", "DLD", "DMC", "DS", "DXL", "E_E", "EAR",
            "EBM", "EBS", "EBS2", "ECF", "EHAM", "ELF", "EPK", "ES", "ESH", "EX_", "EX4", "EX5", "EXE", "EXE1", "EXOPC",
            "EZS", "EZT", "FAS", "FKY", "FPI", "FRS", "FXP", "GADGET", "GPE", "GPU", "GS", "HAM", "HMS", "HPF", "HTA",
            "ICD", "IIM", "INF1", "INS", "INX", "IPA", "IPF", "ISU", "ITA", "JAR", "JOB", "JS", "JSE", "JSF", "JSX",
            "KIX", "KSH", "KX", "LNK", "LO", "LS", "M3G", "MAC", "MAM", "MCR", "MEL", "MEM", "MIO", "MLX", "MM", "MRC",
            "MRP", "MS", "MSC", "MSI", "MSL", "MSP", "MST", "MXE", "N", "NCL", "NEXE", "ORE", "OSX", "OTM", "OUT",
            "PAF", "PAFEXE", "PEX", "PHAR", "PIF", "PLSC", "PLX", "PRC", "PRG", "PS1", "PVD", "PWC", "PYC", "PYO",
            "QIT", "QPX", "RBF", "RBX", "REG", "RFU", "RGS", "ROX", "RPJ", "RUN", "RXE", "S2A", "SBS", "SCA", "SCAR",
            "SCB", "SCF", "SCPT", "SCPTD", "SCR", "SCRIPT", "SCT", "SEED", "SERVER", "SHB", "SHS", "SK", "SMM", "SNAP",
            "SPR", "STS", "TCP", "THM", "TIAPP", "TMS", "U3P", "UDF", "UPX", "VB", "VBE", "VBS", "VBSCRIPT", "VDO",
            "VEXE", "VLX", "VPM", "VXP", "WCM", "WIDGET", "WIZ", "WORKFLOW", "WPK", "WPM", "WS", "WSF", "WSH", "X86",
            "X86_64", "XAP", "XBAP", "XLM", "XQT", "XYS", "ZL9"));

    public static enum ExternalOpenEnum {
        NEVER, ASK_ALWAYS, ASK_IF_EXE, ALWAYS
    }

    private static ExternalOpenEnum externalOpenConfig = ExternalOpenEnum.ASK_IF_EXE;
    
    public static boolean isTemporaryFile(File file) {
        if (tmpDir == null) {
            tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        }
        Path filePath = Paths.get(file.getAbsolutePath()).getParent();
        return tmpDir.compareTo(filePath) == 0;
    }

    public static void setExternalOpenConfig(ExternalOpenEnum config) {
        externalOpenConfig = config;
    }

    public static boolean isToOpenExternally(String fileName, String trueExt) {
        return IOUtil.externalOpenConfig == ExternalOpenEnum.ALWAYS
                || (IOUtil.externalOpenConfig == ExternalOpenEnum.ASK_ALWAYS && IOUtil.confirmOpenDialog(fileName, trueExt))
                || (IOUtil.externalOpenConfig == ExternalOpenEnum.ASK_IF_EXE
                        && (!IOUtil.isDangerousExtension(trueExt) || IOUtil.confirmOpenDialog(fileName, trueExt)));
    }

    public static final boolean isDangerousExtension(String ext) {
        return ext != null && DANGEROUS_EXTS.contains(ext.toUpperCase());
    }

    public static final String getExtension(File file) {
        int idx = file.getName().lastIndexOf('.');
        if (idx == -1) {
            return "";
        } else {
            return file.getName().substring(idx + 1);
        }
    }

    public static final boolean confirmOpenDialog(String fileName, String trueExt) {
        String fileType = !trueExt.isEmpty() ? "(." + trueExt + ")"
                : "(" + Messages.getString("IOUtil.ConfirmOpening.Unknown") + ")";
        int option = JOptionPane.showConfirmDialog(null,
                Messages.getString("IOUtil.ConfirmOpening") + " \"" + fileName + "\" " + fileType + " ?", "",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (option == JOptionPane.YES_OPTION)
            return true;
        else
            return false;
    }

    public static String getValidFilename(String filename) {
        // max NTFS file name size is 255, but since max path size is 256, we use a
        // much smaller value, which should handle most scenarios
        return getValidFilename(filename, 160);
    }

    public static String getValidFilename(String filename, int maxLength) {
        filename = filename.trim();

        String invalidChars = "\\/:;*?\"<>|"; //$NON-NLS-1$
        char[] chars = filename.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if ((invalidChars.indexOf(chars[i]) >= 0) || (chars[i] < '\u0020')) {
                filename = filename.replace(chars[i] + "", " "); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        String[] invalidNames = { "CON", "PRN", "AUX", "NUL", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
                "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$

        for (String name : invalidNames) {
            if (filename.equalsIgnoreCase(name) || filename.toUpperCase().startsWith(name + ".")) { //$NON-NLS-1$
                filename = "1" + filename; //$NON-NLS-1$
            }
        }

        if (filename.length() > maxLength) {
            int extIndex = filename.lastIndexOf('.');
            if (extIndex == -1) {
                filename = filename.substring(0, maxLength);
            } else {
                String ext = filename.substring(extIndex);
                int MAX_EXT_LEN = 20;
                if (ext.length() > MAX_EXT_LEN) {
                    ext = filename.substring(extIndex, extIndex + MAX_EXT_LEN);
                }
                filename = filename.substring(0, maxLength - ext.length()) + ext;
            }
        }

        char c;
        while (filename.length() > 0 && ((c = filename.charAt(filename.length() - 1)) == ' ' || c == '.')) {
            filename = filename.substring(0, filename.length() - 1);
        }

        return filename;
    }

    public static final boolean isDiskFull(IOException e) {
        if (e == null)
            return false;

        String msg = e.getMessage();
        if (msg != null) {
            msg = msg.toLowerCase();
            if (msg.startsWith("espaço insuficiente no disco") || //$NON-NLS-1$
                    msg.startsWith("não há espaço disponível no dispositivo") || //$NON-NLS-1$
                    msg.startsWith("there is not enough space") || //$NON-NLS-1$
                    msg.startsWith("not enough space") || //$NON-NLS-1$
                    msg.startsWith("no space left on")) //$NON-NLS-1$
                return true;
        }
        return false;
    }

    public static boolean canCreateFile(File dir) {

        try {
            File test = File.createTempFile("writeTest", null, dir); //$NON-NLS-1$
            test.deleteOnExit();
            test.delete();
            return true;

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * A more reliable method than File.canWrite() to test for write permissions.
     * It's slower because the implementation opens a file handler internally. See
     * more details on https://github.com/sepinf-inc/IPED/issues/996
     */
    public static boolean canWrite(File file) {
        if (!file.exists()) {
            return false;
        }
        if (file.isDirectory()) {
            return IOUtil.canCreateFile(file);
        }
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            return true;

        } catch (IOException e) {
            return false;
        }
    }

    public static void closeQuietly(Closeable in) {
        try {
            if (in != null)
                in.close();
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    public static List<File> getAllFileChildren(File file) {
        List<File> list = new ArrayList<>();
        String[] subFileName = file.list();
        if (subFileName != null) {
            for (int i = 0; i < subFileName.length; i++) {
                File subFile = new File(file, subFileName[i]);

                if (subFile.isDirectory())
                    list.addAll(getAllFileChildren(subFile));
                else
                    list.add(subFile);
            }
        }
        return list;
    }

    public static int countSubFiles(File file) {
        int result = 0;
        String[] subFileName = file.list();
        if (subFileName != null)
            for (int i = 0; i < subFileName.length; i++) {
                File subFile = new File(file, subFileName[i]);
                if (subFile.isDirectory())
                    result += countSubFiles(subFile);
                else
                    result++;
            }
        return result;
    }

    public static void deleteDirectory(File file) {
        try {
            deleteDirectory(file, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteDirectory(File file, boolean logError) throws IOException {
        if (file.isDirectory()) {
            String[] names = file.list();
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    File subFile = new File(file, names[i]);
                    deleteDirectory(subFile, logError);
                }
            }
        }
        try {
            Files.delete(file.toPath());

        } catch (Exception e) {
            // also catch InvalidPathException and others
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                // see https://learn.microsoft.com/en-us/troubleshoot/windows-server/backup-and-storage/cannot-delete-file-folder-on-ntfs-file-system
                new File("\\\\?\\" + file.getAbsolutePath()).delete();
                /*
                String[] cmd = { "powershell.exe", "rm", "-Recurse", "-Force", "\\\"\\\\?\\" + file.getAbsolutePath() + "\\\"" };
                try {
                    Runtime.getRuntime().exec(cmd).waitFor();
                } catch (Exception e2) {
                    e2.printStackTrace();
                }*/
            }
            if (file.exists()) {
                if (logError) {
                    LoggerFactory.getLogger(IOUtil.class).info("Delete failed on '" + file.getPath() + "' " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$
                } else {
                    throw e;
                }
            }
        }

    }

    public static void copyFile(File origem, File destino) throws IOException {
        copyFile(origem, destino, false);
    }

    public static void copyFile(File origem, File destino, boolean append) throws IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(origem));
        OutputStream out = new BufferedOutputStream(new FileOutputStream(destino, append));
        if (append)
            out.write(0x0A);
        byte[] buf = new byte[1024 * 1024];
        int len;
        while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted()) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
        if (len != -1)
            if (!destino.delete())
                throw new IOException("Fail to delete " + destino.getPath()); //$NON-NLS-1$
    }

    public static void copyInputToOutputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024 * 1024];
        int len;
        while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted()) {
            out.write(buf, 0, len);
        }
    }

    public static void copyDirectory(File source, File dest, boolean recursive) throws IOException {
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("Fail to create folder " + dest.getAbsolutePath()); //$NON-NLS-1$
        }
        String[] subdir = source.list();
        if (subdir == null) {
            throw new IOException("Source is not a directory: " + source.getAbsolutePath());
        }
        for (int i = 0; i < subdir.length; i++) {
            File subFile = new File(source, subdir[i]);
            if (subFile.isDirectory()) {
                if (recursive) {
                    copyDirectory(subFile, new File(dest, subdir[i]));
                }
            } else {
                File subDestino = new File(dest, subdir[i]);
                copyFile(subFile, subDestino);
            }
        }
    }

    public static void copyDirectory(File origem, File destino) throws IOException {
        copyDirectory(origem, destino, true);
    }

    /**
     * Use this method with CAUTION, it buffers all input stream data on memory and
     * could cause OOME.
     * 
     */
    public static byte[] loadInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len = 0;
        while ((len = is.read(buf)) != -1)
            bos.write(buf, 0, len);

        return bos.toByteArray();
    }

    public static byte[] readByteArray(DataInputStream is, int len) throws Exception {
        byte[] arr = new byte[len];
        int read = 0;
        while (read < len) {
            int r = is.read(arr, read, len - read);
            if (r < 0) break;
            read += r;
        }
        return len == read ? arr : null;
    }

    public static int[] readIntArray(DataInputStream is, int len) throws Exception {
        len <<= 2;
        byte[] arr = new byte[len];
        int read = 0;
        while (read < len) {
            int r = is.read(arr, read, len - read);
            if (r < 0) break;
            read += r;
        }
        if (len != read) return null;
        int[] ret = new int[len >>> 2];
        for (int i = 0; i < arr.length; i += 4) {
            ret[i >>> 2] = ((arr[i] & 0xFF) << 24) + ((arr[i + 1] & 0xFF) << 16) + ((arr[i + 2] & 0xFF) << 8) + ((arr[i + 3] & 0xFF) << 0);
        }
        return ret;
    }

    public static void writeIntArray(DataOutputStream os, int[] arr) throws Exception {
        byte[] buf = new byte[arr.length << 2];
        for (int i = 0; i < buf.length; i += 4) {
            int v = arr[i >>> 2];
            buf[i] = (byte) (v >>> 24);
            buf[i + 1] = (byte) (v >>> 16);
            buf[i + 2] = (byte) (v >>> 8);
            buf[i + 3] = (byte) (v >>> 0);
        }
        os.write(buf);
    }

    public static boolean hasFile(IItemReader item) {
        return item.getInputStreamFactory() instanceof FileInputStreamFactory
                && (!(item instanceof IItem) || ((IItem) item).getFileOffset() == -1);
    }

    public static File getFile(IItemReader item) {
        if (hasFile(item)) {
            FileInputStreamFactory fisf = ((FileInputStreamFactory) item.getInputStreamFactory());
            return fisf.getPath(item.getIdInDataSource()).toFile();
        }
        return null;
    }

    public static class ContainerVolatile {
        public volatile boolean progress = false;
    }

    public static void ignoreInputStream(final InputStream stream) {
        ignoreInputStream0(stream);
    }

    public static void ignoreInputStream(final Process p) {
        ignoreInputStream0(p.getInputStream());
    }

    public static void ignoreErrorStream(Process p) {
        ignoreInputStream0(p.getErrorStream());
    }

    @Deprecated
    public static void ignoreInputStream(final InputStream stream, final ContainerVolatile msg) {
        ignoreInputStream0(stream);
    }

    private static void ignoreInputStream0(final InputStream stream) {
        Thread t = new Thread() {
            @Override
            public void run() {
                byte[] out = new byte[1024];
                int read = 0;
                try {
                    while (read != -1) {
                        read = stream.read(out);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }
}
