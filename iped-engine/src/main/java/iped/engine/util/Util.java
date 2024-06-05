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
package iped.engine.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.lucene.util.IOUtils;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.detect.microsoft.POIFSContainerDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import com.sun.jna.Native;

import iped.data.IItem;
import iped.engine.data.CaseData;
import iped.engine.data.Item;
import iped.engine.localization.Messages;
import iped.engine.task.SkipCommitedTask;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.HashValue;
import iped.utils.IOUtil;

public class Util {

    public static final Integer MIN_JAVA_VER = 11;
    public static final Integer MAX_JAVA_VER = 14;

    // These java versions have a WebView bug that crashes the JVM: JDK-8196011
    private static final String[] buggedVersions = { "1.8.0_161", "1.8.0_162", "1.8.0_171" };

    private static CLibrary C_Library;

    public static void fsync(Path path) throws IOException {
        IOUtils.fsync(path, false);
    }

    public static String getJavaVersionWarn() {
        String versionStr = System.getProperty("java.version"); //$NON-NLS-1$
        if (versionStr.startsWith("1.")) //$NON-NLS-1$
            versionStr = versionStr.substring(2, 3);
        int dotIdx = versionStr.indexOf("."); //$NON-NLS-1$
        if (dotIdx > -1)
            versionStr = versionStr.substring(0, dotIdx);
        Integer version = Integer.valueOf(versionStr);

        if (version < MIN_JAVA_VER) {
            return Messages.getString("JavaVersion.Error").replace("{}", MIN_JAVA_VER.toString()); //$NON-NLS-1$
        }
        if (version > MAX_JAVA_VER) {
            return Messages.getString("JavaVersion.Warn").replace("{}", version.toString()); //$NON-NLS-1$
        }
        for (String ver : buggedVersions) {
            if (System.getProperty("java.version").equals(ver))
                return Messages.getString("JavaVersion.Bug").replace("{1}", ver).replace("{2}", //$NON-NLS-1$
                        MAX_JAVA_VER.toString());
        }

        if (!System.getProperty("os.arch").contains("64"))
            return Messages.getString("JavaVersion.Arch"); //$NON-NLS-1$

        return null;
    }

    public static boolean isJavaFXPresent() {
        try {
            Class.forName("javafx.application.Platform");
            return true;

        } catch (Throwable t) {
            return false;
        }
    }

    public static String getRootName(String path) {
        int fromIndex = path.charAt(0) == '/' || path.charAt(0) == '\\' ? 1 : 0;
        int slashIdx = path.indexOf('/', fromIndex);
        int backSlashIndx = path.indexOf('\\', fromIndex);
        int expanderIdx = path.indexOf(">>", fromIndex);
        if (slashIdx == -1) {
            slashIdx = path.length();
        }
        if (backSlashIndx == -1) {
            backSlashIndx = path.length();
        }
        if (expanderIdx == -1) {
            expanderIdx = path.length();
        }
        int endIndex = Math.min(slashIdx, Math.min(backSlashIndx, expanderIdx));
        return path.substring(fromIndex, endIndex);
    }

    public static String getTrackID(IItem item) {
        String id = (String) item.getExtraAttribute(IndexItem.TRACK_ID);
        if (id == null) {
            return generateTrackID(item);
        }
        return id;
    }
    
    /**
     * Computes trackID and reassign the item ID if it was mapped to a different ID
     * in a previous processing, being resumed or restarted.
     * 
     * @param item
     */
    public static void calctrackIDAndUpdateID(CaseData caseData, IItem item) {
        HashValue trackID = new HashValue(Util.getTrackID(item));
        Map<HashValue, Integer> globalToIdMap = (Map<HashValue, Integer>) caseData.getCaseObject(SkipCommitedTask.trackID_ID_MAP);
        // changes id to previous processing id if using --continue
        if (globalToIdMap != null) {
            Integer previousId = globalToIdMap.get(trackID);
            if (previousId != null) {
                item.setId(previousId.intValue());
            }
        }
        ((Item) item).setAllowGetId(true);
    }

    public static String generatetrackIDForTextFrag(String trackID, int fragNum) {
        if (fragNum != 0) {
            StringBuilder sb = new StringBuilder(trackID);
            sb.append("fragNum").append(fragNum);
            trackID = DigestUtils.md5Hex(sb.toString());
        }
        return trackID;
    }

    public static String getParentPath(IItem item) {
        String path = item.getPath();
        int end = path.length() - item.getName().length() - 1;
        if (end <= 0)
            return "";
        if (path.charAt(end) == '>' && path.charAt(end - 1) == '>')
            end--;
        return path.substring(0, end);
    }

    private static String generateTrackID(IItem item) {
        StringBuilder sb = new StringBuilder();
        String notFoundIn = " not found in ";
        if (!item.isCarved() && !item.isSubItem() && item.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) == null) {
            if (item.getIdInDataSource() != null) {
                sb.append(IndexItem.ID_IN_SOURCE).append(item.getIdInDataSource());
            } else if (!item.isQueueEnd()) {
                throw new IllegalArgumentException(IndexItem.ID_IN_SOURCE + notFoundIn + item.getPath());
            }
        } else {
            String parenttrackID = (String) item.getExtraAttribute(IndexItem.PARENT_TRACK_ID);
            if (parenttrackID != null) {
                sb.append(IndexItem.PARENT_TRACK_ID).append(parenttrackID);
            } else {
                throw new IllegalArgumentException(IndexItem.PARENT_TRACK_ID + notFoundIn + item.getPath());
            }
        }
        if (item.isSubItem()) {
            if (item.getSubitemId() != null) {
                sb.append(IndexItem.SUBITEMID).append(item.getSubitemId());
            } else {
                throw new IllegalArgumentException(IndexItem.SUBITEMID + notFoundIn + item.getPath());
            }
        }
        if (item.isCarved()) {
            Object carvedId = item.getExtraAttribute(BaseCarveTask.CARVED_ID);
            if (carvedId != null) {
                sb.append(BaseCarveTask.CARVED_ID).append(carvedId.toString());
            } else {
                throw new IllegalArgumentException(BaseCarveTask.CARVED_ID + notFoundIn + item.getPath());
            }
        }
        if (item.getPath() != null) {
            sb.append(IndexItem.PATH).append(item.getPath());
        } else {
            throw new IllegalArgumentException(IndexItem.PATH + notFoundIn + item.getPath());
        }
        String trackId = DigestUtils.md5Hex(sb.toString());
        item.setExtraAttribute(IndexItem.TRACK_ID, trackId);

        // additionally compute a globalId see #784
        if (item.getDataSource() != null) {
            sb.append(BasicProps.EVIDENCE_UUID).append(item.getDataSource().getUUID());
            String globalId = DigestUtils.md5Hex(sb.toString());
            item.setExtraAttribute(ExtraProperties.GLOBAL_ID, globalId);
        } else if (!item.isQueueEnd()) {
            throw new RuntimeException(BasicProps.EVIDENCE_UUID + notFoundIn + item.getPath());
        }

        return trackId;
    }

    public static String readUTF8Content(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        // BOM test
        if (bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            bytes[0] = bytes[1] = bytes[2] = 0;
        }
        String content = new String(bytes, "UTF-8"); //$NON-NLS-1$
        return content;
    }

    public static boolean isPhysicalDrive(File file) {
        return file.getName().toLowerCase().startsWith("physicaldrive") //$NON-NLS-1$
                || file.getAbsolutePath().toLowerCase().startsWith("/dev/"); //$NON-NLS-1$
    }

    public static Path getResolvedFile(String prefix, String suffix) {
        Path first = Paths.get(prefix);
        Path other = Paths.get(suffix);
        return first.resolve(other);
    }

    public static String getRelativePath(File baseFile, URI uri) {
        try {
            return Util.getRelativePath(baseFile, Paths.get(uri).toFile());
        } catch (FileSystemNotFoundException e) {
            return uri.toString();
        }
    }

    public static String getRelativePath(File baseFile, File file) {
        Path base = baseFile.getParentFile().toPath().normalize().toAbsolutePath();
        Path path = file.toPath().normalize().toAbsolutePath();
        if (!base.getRoot().equals(path.getRoot()))
            return file.getAbsolutePath();
        return base.relativize(path).toString();
    }

    public static void writeObject(Object obj, String filePath) throws IOException {
        FileOutputStream fileOut = new FileOutputStream(new File(filePath));
        BufferedOutputStream bufOut = new BufferedOutputStream(fileOut);
        ObjectOutputStream out = new ObjectOutputStream(bufOut);
        out.writeObject(obj);
        out.close();
    }

    public static Object readObject(String filePath) throws IOException, ClassNotFoundException {
        FileInputStream fileIn = new FileInputStream(new File(filePath));
        BufferedInputStream bufIn = new BufferedInputStream(fileIn);
        ObjectInputStream in = new ObjectInputStream(bufIn);
        Object result;
        try {
            result = in.readObject();
        } finally {
            in.close();
        }
        return result;
    }

    public static String concatStrings(List<String> strings) {
        if (strings == null) {
            return null;
        }
        if (strings.isEmpty()) {
            return "";
        }
        if (strings.size() == 1) {
            return strings.get(0);
        }
        return strings.stream().collect(Collectors.joining(" | "));
    }

    public static String getNameWithTrueExt(IItem item) {
        String ext = item.getType();
        String name = item.getName();
        if (ext == null)
            return name;
        ext = "." + ext.toLowerCase();
        if (name.toLowerCase().endsWith(ext))
            return name;
        else
            return name + ext;
    }

    public static String concat(String filename, int num) {
        int extIndex = filename.lastIndexOf('.');
        if (extIndex == -1) {
            return filename + " (" + num + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            String ext = filename.substring(extIndex);
            return filename.substring(0, filename.length() - ext.length()) + " (" + num + ")" + ext; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    public static String removeNonLatin1Chars(String filename) {
        StringBuilder str = new StringBuilder();
        for (char c : filename.toCharArray())
            if ((c >= '\u0020' && c <= '\u007E') || (c >= '\u00A0' && c <= '\u00FF'))
                str.append(c);
        return str.toString();
    }

    public static String getValidFilename(String filename) {
        return IOUtil.getValidFilename(filename);
    }

    public static void changeEncoding(File file) throws IOException {
        if (file.isDirectory()) {
            String[] names = file.list();
            for (int i = 0; i < names.length; i++) {
                File subFile = new File(file, names[i]);
                changeEncoding(subFile);
            }
        } else {
            Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "windows-1252")); //$NON-NLS-1$
            String contents = ""; //$NON-NLS-1$
            char[] buf = new char[(int) file.length()];
            int count;
            while ((count = reader.read(buf)) != -1) {
                contents += new String(buf, 0, count);
            }

            reader.close();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")); //$NON-NLS-1$
            writer.write(contents);
            writer.close();
        }

    }

    public static void readFile(File origem) throws IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(origem));
        byte[] buf = new byte[1024 * 1024];
        while (in.read(buf) != -1)
            ;
        in.close();
    }

    public static ArrayList<String> loadKeywords(String filePath, String encoding) throws IOException {
        ArrayList<String> array = new ArrayList<String>();
        File file = new File(filePath);
        if (!file.exists()) {
            return array;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                array.add(line.trim());
            }
        }
        reader.close();
        return array;
    }

    public static void saveKeywords(ArrayList<String> keywords, String filePath, String encoding) throws IOException {
        File file = new File(filePath);
        file.delete();
        file.createNewFile();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
        for (int i = 0; i < keywords.size(); i++) {
            writer.write(keywords.get(i) + "\r\n"); //$NON-NLS-1$
        }
        writer.close();
    }

    public static TreeSet<String> loadKeywordSet(String filePath, String encoding) throws IOException {
        TreeSet<String> set = new TreeSet<String>();
        File file = new File(filePath);
        if (!file.exists()) {
            return set;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                set.add(line.trim());
            }
        }
        reader.close();
        return set;
    }

    public static void saveKeywordSet(TreeSet<String> keywords, String filePath, String encoding) throws IOException {
        File file = new File(filePath);
        file.delete();
        file.createNewFile();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
        for (String keyword : keywords) {
            writer.write(keyword + "\r\n"); //$NON-NLS-1$
        }
        writer.close();
    }

    /**
     * Carrega bibliotecas nativas de uma pasta, tentando adivinhar a ordem correta
     * 
     * @param libDir
     */
    public static void loadNatLibs(File libDir) {

        if (System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
            LinkedList<File> libList = new LinkedList<File>();
            for (File file : libDir.listFiles())
                if (file.getName().endsWith(".dll")) //$NON-NLS-1$
                    libList.addFirst(file);

            int fail = 0;
            while (!libList.isEmpty()) {
                File lib = libList.removeLast();
                try {
                    System.load(lib.getAbsolutePath());
                    fail = 0;

                } catch (Throwable t) {
                    libList.addFirst(lib);
                    fail++;
                    if (fail == libList.size())
                        throw t;
                }
            }
        }
    }

    public static void loadLibs(File libDir) {
        File[] subFiles = libDir.listFiles();
        for (File subFile : subFiles) {
            if (subFile.isFile()) {
                System.load(subFile.getAbsolutePath());
            }
        }
    }

    public static void setEnvVar(String key, String value) {
        if (SystemUtils.IS_OS_WINDOWS) {
            if (C_Library == null) {
                C_Library = (CLibrary) Native.loadLibrary("ucrtbase", CLibrary.class);
            }
            C_Library._putenv(key + "=" + value);
        } else {
            if (C_Library == null) {
                C_Library = (CLibrary) Native.loadLibrary("c", CLibrary.class);
            }
            C_Library.setenv(key, value, true);
        }
    }

    /**
     * Cria caminho completo a partir da pasta base, hash e extensao, no formato:
     * "base/0/1/01hhhhhhh.ext".
     */
    public static File getFileFromHash(File baseDir, String hash, String ext) {
        StringBuilder path = new StringBuilder();
        hash = hash.toUpperCase();
        path.append(hash.charAt(0)).append('/');
        path.append(hash.charAt(1)).append('/');
        path.append(hash).append('.').append(ext);
        File result = new File(baseDir, path.toString());
        return result;
    }

    public static File findFileFromHash(File baseDir, String hash) {
        if (hash == null) {
            return null;
        }
        hash = hash.toUpperCase();
        File hashDir = new File(baseDir, hash.charAt(0) + "/" + hash.charAt(1)); //$NON-NLS-1$
        if (hashDir.exists()) {
            for (File file : hashDir.listFiles()) {
                if (file.getName().startsWith(hash)) {
                    return file;
                }
            }
        }
        return null;
    }

    public static InputStream getPOIFSInputStream(TikaInputStream tin) throws IOException {
        POIFSContainerDetector oleDetector = new POIFSContainerDetector();
        MediaType mime = oleDetector.detect(tin, new Metadata());
        if (!MediaType.OCTET_STREAM.equals(mime) && tin.getOpenContainer() != null
                && tin.getOpenContainer() instanceof DirectoryEntry) {
            try (POIFSFileSystem fs = new POIFSFileSystem()) {
                copy((DirectoryEntry) tin.getOpenContainer(), fs.getRoot());
                LimitedByteArrayOutputStream baos = new LimitedByteArrayOutputStream();
                fs.writeFilesystem(baos);
                return new ByteArrayInputStream(baos.toByteArray());
            }
        }
        return null;
    }

    private static class LimitedByteArrayOutputStream extends ByteArrayOutputStream {

        private void checkLimit(int len) {
            int limit = 1 << 27;
            if (this.size() + len > limit) {
                throw new RuntimeException("Reached max memory limit of " + limit + " bytes.");
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            checkLimit(len);
            super.write(b, off, len);
        }

        @Override
        public void write(byte[] b) {
            this.write(b, 0, b.length);
        }

        @Override
        public void write(int b) {
            checkLimit(1);
            super.write(b);
        }
    }

    protected static void copy(DirectoryEntry sourceDir, DirectoryEntry destDir) throws IOException {
        for (org.apache.poi.poifs.filesystem.Entry entry : sourceDir) {
            if (entry instanceof DirectoryEntry) {
                // Need to recurse
                DirectoryEntry newDir = destDir.createDirectory(entry.getName());
                copy((DirectoryEntry) entry, newDir);
            } else {
                // Copy entry
                try (InputStream contents = new DocumentInputStream((DocumentEntry) entry)) {
                    destDir.createDocument(entry.getName(), contents);
                }
            }
        }
    }

    public static long getPhysicalMemorySize() {
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            Object attribute = mBeanServer.getAttribute(new ObjectName("java.lang", "type", "OperatingSystem"),
                    "TotalPhysicalMemorySize");
            return Long.parseLong(attribute.toString());
        } catch (Exception e) {
        }
        return 0;
    }
}
