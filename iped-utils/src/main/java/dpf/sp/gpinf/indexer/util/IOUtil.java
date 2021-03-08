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
package dpf.sp.gpinf.indexer.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import org.slf4j.LoggerFactory;

public class IOUtil {

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

    public static void setExternalOpenConfig(ExternalOpenEnum config) {
        externalOpenConfig = config;
    }

    public static boolean isToOpenExternally(String fileName, String fileExt) {
        return IOUtil.externalOpenConfig == ExternalOpenEnum.ALWAYS
                || (IOUtil.externalOpenConfig == ExternalOpenEnum.ASK_ALWAYS && IOUtil.confirmOpenDialog(fileName))
                || (IOUtil.externalOpenConfig == ExternalOpenEnum.ASK_IF_EXE
                        && (!IOUtil.isDangerousExtension(fileExt) || IOUtil.confirmOpenDialog(fileName)));
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

    public static final boolean confirmOpenDialog(String fileName) {
        int option = JOptionPane.showConfirmDialog(null,
                Messages.getString("IOUtil.ConfirmOpening") + " \"" + fileName + "\" ?", "",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (option == JOptionPane.YES_OPTION)
            return true;
        else
            return false;
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

    public static void closeQuietly(Closeable in) {
        try {
            if (in != null)
                in.close();
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    public static void lerListaDeArquivos(File file, List<File> lista) {

        String[] subFileName = file.list();
        if (subFileName != null)
            for (int i = 0; i < subFileName.length; i++) {
                File subFile = new File(file, subFileName[i]);

                if (subFile.isDirectory())
                    lerListaDeArquivos(subFile, lista);
                else
                    lista.add(subFile);
            }
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

    public static void deletarDiretorio(File file) {
        try {
            deleteDirectory(file, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteDirectory(File file, boolean logError) throws IOException {
        if (file.isDirectory()) {
            String[] names = file.list();
            if (names != null)
                for (int i = 0; i < names.length; i++) {
                    File subFile = new File(file, names[i]);
                    deleteDirectory(subFile, logError);
                }
        }
        try {
            Files.delete(file.toPath());

        } catch (Exception e) {
            // also catch InvalidPathException and others
            if (logError) {
                LoggerFactory.getLogger(IOUtil.class).info("Delete failed on '" + file.getPath() + "' " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$
            } else
                throw e;
        }

    }

    public static void copiaArquivo(File origem, File destino) throws IOException {
        copiaArquivo(origem, destino, false);
    }

    public static void copiaArquivo(File origem, File destino, boolean append) throws IOException {
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

    public static void copiaArquivo(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024 * 1024];
        int len;
        while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted()) {
            out.write(buf, 0, len);
        }
    }

    public static void copiaDiretorio(File origem, File destino, boolean recursive) throws IOException {
        if (!destino.exists())
            if (!destino.mkdirs())
                throw new IOException("Fail to create folder " + destino.getAbsolutePath()); //$NON-NLS-1$
        String[] subdir = origem.list();
        for (int i = 0; i < subdir.length; i++) {
            File subFile = new File(origem, subdir[i]);
            if (subFile.isDirectory()) {
                if (recursive)
                    copiaDiretorio(subFile, new File(destino, subdir[i]));
            } else {
                File subDestino = new File(destino, subdir[i]);
                copiaArquivo(subFile, subDestino);
            }
        }
    }

    public static void copiaDiretorio(File origem, File destino) throws IOException {
        copiaDiretorio(origem, destino, true);
    }

    public static byte[] loadInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len = 0;
        while ((len = is.read(buf)) != -1)
            bos.write(buf, 0, len);

        return bos.toByteArray();
    }

    private static Path tmpDir = null;

    public static boolean isTemporaryFile(File file) {
        if (tmpDir == null) {
            tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        }
        Path filePath = Paths.get(file.getAbsolutePath()).getParent();
        return tmpDir.compareTo(filePath) == 0;
    }

}
