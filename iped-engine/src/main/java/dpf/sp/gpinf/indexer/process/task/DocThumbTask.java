package dpf.sp.gpinf.indexer.process.task;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import gpinf.util.PDFToThumb;
import iped3.IEvidenceFileType;
import iped3.IItem;

public class DocThumbTask extends ThumbTask {

    private static final String enableProperty = "enableDocThumbs";
    private static final String thumbTimeout = "thumbTimeout";
    private static final String taskConfigFile = "DocThumbsConfig.txt";

    private static boolean taskEnabled;

    private static int pdfTimeout = 45;
    private static int loTimeout = 90;
    private static int loBatchSize = 10;
    private static int timeoutIncPerMB = 2;
    private static int thumbSize = 480;
    private static boolean externalPdfConversion;
    private static int maxPdfExternalMemory = 256;
    private static boolean pdfEnabled;
    private static boolean loEnabled;

    private static String loPath;

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);
    private static final Logger logger = LoggerFactory.getLogger(DocThumbTask.class);

    private static final AtomicLong totalPdfProcessed = new AtomicLong();
    private static final AtomicLong totalPdfFailed = new AtomicLong();
    private static final AtomicLong totalPdfTime = new AtomicLong();
    private static final AtomicLong totalPdfTimeout = new AtomicLong();

    private static final AtomicLong totalLoProcessed = new AtomicLong();
    private static final AtomicLong totalLoFailed = new AtomicLong();
    private static final AtomicLong totalLoTime = new AtomicLong();
    private static final AtomicLong totalLoTimeout = new AtomicLong();

    private File loOutDir;
    private String loOutPath;
    private Process loEnvCreateProcess;
    private Process convertProcess;
    private boolean tempSet;

    private boolean queued;
    private final List<IItem> itemList = Collections.synchronizedList(new ArrayList<IItem>());
    private final TemporaryResources tmpResources = new TemporaryResources();

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                taskEnabled = Boolean.valueOf(confParams.getProperty(enableProperty));
                if (taskEnabled) {
                    UTF8Properties properties = new UTF8Properties();
                    File confFile = new File(confDir, taskConfigFile);
                    properties.load(confFile);

                    String value = properties.getProperty("pdfThumbs");
                    if (value != null) {
                        value = value.trim();
                        if ("external".equalsIgnoreCase(value)) {
                            externalPdfConversion = true;
                            pdfEnabled = true;
                        } else if ("internal".equalsIgnoreCase(value)) {
                            externalPdfConversion = false;
                            pdfEnabled = true;
                        }
                    }

                    value = properties.getProperty("libreOfficeThumbs");
                    if (value != null) {
                        value = value.trim();
                        if ("external".equalsIgnoreCase(value)) {
                            loEnabled = true;
                        }
                    }
                    if (!loEnabled && !pdfEnabled) {
                        logger.warn("Both PDF and LibreOffice thumb generation disabled!");
                        taskEnabled = false;
                    }
                    if (taskEnabled) {
                        value = properties.getProperty("pdfTimeout");
                        if (value != null && !value.trim().isEmpty()) {
                            pdfTimeout = Integer.parseInt(value);
                        }

                        value = properties.getProperty("libreOfficeBatchSize");
                        if (value != null && !value.trim().isEmpty()) {
                            loBatchSize = Integer.parseInt(value);
                        }

                        value = properties.getProperty("libreOfficeTimeout");
                        if (value != null && !value.trim().isEmpty()) {
                            loTimeout = Integer.parseInt(value);
                        }

                        value = properties.getProperty("timeoutIncPerMB");
                        if (value != null && !value.trim().isEmpty()) {
                            timeoutIncPerMB = Integer.parseInt(value);
                        }

                        value = properties.getProperty("maxPdfExternalMemory");
                        if (value != null && !value.trim().isEmpty()) {
                            maxPdfExternalMemory = Integer.parseInt(value);
                        }

                        value = properties.getProperty("thumbSize");
                        if (value != null && !value.trim().isEmpty()) {
                            thumbSize = Integer.valueOf(value.trim());
                        }

                        logger.info("Thumb Size: " + thumbSize);
                        logger.info("LibreOffice Conversion: " + (loEnabled ? "enabled" : "disabled"));
                        if (loEnabled) {
                            URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
                            File jarDir = new File(url.toURI()).getParentFile();
                            LibreOfficeFinder loFinder = new LibreOfficeFinder(jarDir);
                            loPath = loFinder.getLOPath();
                            logger.info("LibreOffice Path: " + loPath);
                            logger.info("LibreOffice Batch Size: " + loBatchSize);
                        }

                        logger.info("PDF Conversion: " + (pdfEnabled ? "enabled" : "disabled"));
                        if (pdfEnabled) {
                            logger.info("External PDF Conversion: " + externalPdfConversion);
                        }
                    }
                }
                logger.info("Task " + (taskEnabled ? "enabled" : "disabled"));
                init.set(true);
            }
        }
        if (loEnabled) {
            loOutDir = Files.createTempDirectory("doc-thumb").toFile();
            loOutPath = loOutDir.getAbsolutePath().replace('\\', '/');
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                loOutPath = '/' + loOutPath;
            }
            String[] cmd = {loPath + "/program/soffice.bin",
                    "--headless",
                    "--quickstart",
                    "--norestore",
                    "--nolockcheck",
                    "-env:UserInstallation=file://" + loOutPath};
            ProcessBuilder pb = new ProcessBuilder(cmd);
            loEnvCreateProcess = pb.start();
            pb.redirectErrorStream();
            Util.ignoreStream(loEnvCreateProcess.getInputStream());
        }
    }

    @Override
    protected boolean processQueueEnd() {
        return true;
    }

    @Override
    protected void sendToNextTask(IItem item) throws Exception {
        if (!item.isQueueEnd() && !queued) {
            super.sendToNextTask(item);
            return;
        }
        if (isToProcessBatch(item)) {
            for (IItem it : itemList) {
                super.sendToNextTask(it);
            }
            itemList.clear();
            return;
        }
        if (item.isQueueEnd()) {
            super.sendToNextTask(item);
        }
    }

    private boolean isToProcessBatch(IItem item) {
        int size = itemList.size();
        return size >= loBatchSize || (size > 0 && item.isQueueEnd());
    }

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    @Override
    public void finish() throws Exception {
        if (loOutDir != null && loOutDir.exists()) {
            try {
                IOUtil.deleteDirectory(loOutDir, false);
            } catch (IOException e) {}
        }
        synchronized (finished) {
            if (!finished.get()) {
                if (!executor.isShutdown()) {
                    executor.shutdownNow();
                }
                if (taskEnabled) {
                    logger.info("Total PDF processed: " + totalPdfProcessed);
                    logger.info("Total PDF not processed: " + totalPdfFailed);
                    logger.info("Total PDF timeout: " + totalPdfTimeout);
                    long totalPdf = totalPdfProcessed.longValue() + totalPdfFailed.longValue();
                    if (totalPdf != 0) {
                        logger.info("Average PDF processing time (ms/item): " + (totalPdfTime.longValue() / totalPdf));
                    }
                    logger.info("Total LibreOffice processed: " + totalLoProcessed);
                    logger.info("Total LibreOffice not procesed: " + totalLoFailed);
                    logger.info("Total LibreOffice timeout: " + totalLoTimeout);
                    long totalLO = totalLoProcessed.longValue() + totalLoFailed.longValue();
                    if (totalLO != 0) {
                        logger.info("Average LibreOffice processing time (ms/item): " + (totalLoTime.longValue() / totalLO));
                    }
                    logger.info("Task finished.");
                }
                finished.set(true);
            }
        }
    }

    @Override
    protected void process(IItem item) throws Exception {
        queued = false;
        if (!item.isQueueEnd() && (!taskEnabled || !item.isToAddToCase()
                || ((!pdfEnabled || !isPdfType(item.getMediaType())) && (!loEnabled || !isLibreOfficeType(item.getMediaType())))
                || item.getHash() == null || item.getThumb() != null
                || item.getExtraAttribute("fileFragment") != null)) {
            return;
        }
        if (!item.isQueueEnd()) {
            File thumbFile = getThumbFile(item);
            if (hasThumb(item, thumbFile)) {
                return;
            }
            if (isPdfType(item.getMediaType())) {
                Future<?> future = executor.submit(new PDFThumbCreator(item, thumbFile));
                try {
                    int timeout = pdfTimeout + (int) ((item.getLength() * timeoutIncPerMB) >>> 20);
                    future.get(timeout, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    stats.incTimeouts();
                    item.setExtraAttribute(thumbTimeout, "true");
                    logger.warn("Timeout creating thumb: " + item);
                    totalPdfTimeout.incrementAndGet();
                }
                return;
            }
            Metadata metadata = item.getMetadata();
            if (metadata != null) {
                String pe = metadata.get("parserException");
                if (pe != null && pe.equalsIgnoreCase("true")) {
                    return;
                }
            }
            itemList.add(item);
            queued = true;
        }
        if (isToProcessBatch(item)) {
            processBatch();
        }
    }

    protected void processBatch() throws Exception {
        Future<?> future = executor.submit(new LOThumbCreator(itemList));
        boolean hasTimeout = false;
        try {
            int timeout = loTimeout * itemList.size();
            for (IItem it : itemList) {
                timeout += (int) ((it.getLength() * timeoutIncPerMB) >>> 20);
            }
            future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            hasTimeout = true;
        }
        for (IItem item : itemList) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
                File inFile = getHashTempFile(loOutDir, item);
                String name = inFile.getName();
                int pos = name.lastIndexOf('.');
                if (pos >= 0) name = name.substring(0, pos);
                name += ".png";
                File outDir = new File(loOutDir, name);
                boolean success = false;
                if (outDir.exists()) {
                    BufferedImage img = ImageIO.read(outDir);
                    if (img != null) {
                        if (img.getWidth() > thumbSize || img.getHeight() > thumbSize) {
                            img = ImageUtil.resizeImage(img, thumbSize, thumbSize, BufferedImage.TYPE_INT_BGR);
                        }
                        Graphics2D g = img.createGraphics();
                        g.setColor(Color.black);
                        g.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);
                        g.dispose();
                        ImageIO.write(img, "jpg", baos);
                        success = true;
                    }
                }
                if (success && baos.size() > 0) {
                    item.setThumb(baos.toByteArray());
                }
                File thumbFile = getThumbFile(item);
                saveThumb(item, thumbFile);
            } catch (Throwable e) {
                logger.warn(item.toString(), e);
            } finally {
                boolean hasThumb = updateHasThumb(item);
                (hasThumb ? totalLoProcessed : totalLoFailed).incrementAndGet();
                if (!hasThumb && hasTimeout) {
                    item.setExtraAttribute(thumbTimeout, "true");
                    logger.warn("Timeout creating thumb: " + item);
                    totalLoTimeout.incrementAndGet();
                    stats.incTimeouts();
                }
            }
        }
        for (IItem item : itemList) {
            File inFile = getHashTempFile(loOutDir, item);
            String name = inFile.getName();
            int pos = name.lastIndexOf('.');
            if (pos >= 0) name = name.substring(0, pos);
            name += ".png";
            File outFile = new File(loOutDir, name);
            if (inFile.exists()) {
                inFile.delete();
            }
            if (outFile.exists()) {
                outFile.delete();
            }
        }
    }

    private static boolean isPdfType(MediaType mediaType) {
        return mediaType.toString().equals("application/pdf");
    }

    private boolean isLibreOfficeType(MediaType mediaType) {
        String m = mediaType.toString();
        return m.startsWith("application/msword")
                || m.equals("application/rtf")
                || m.startsWith("application/vnd.ms-word")
                || m.startsWith("application/vnd.openxmlformats-officedocument")
                || m.startsWith("application/vnd.oasis.opendocument")
                || m.startsWith("application/vnd.sun.xml")
                || m.startsWith("application/vnd.stardivision")
                || m.equals("application/vnd.visio")
                || m.equals("application/x-mspublisher")
                || m.equals("application/postscript")
                || m.equals("image/wmf")
                || m.equals("image/x-portable-bitmap")
                || m.equals("image/svg+xml")
                || m.equals("image/x-pcx")
                || m.equals("image/vnd.dxf")
                || m.equals("image/cdr")
                || m.equals("application/coreldraw")
                || m.equals("application/x-vnd.corel.zcf.draw.document+zip")
                || m.startsWith("application/vnd.ms-powerpoint")
                || m.startsWith("application/vnd.openxmlformats-officedocument.presentationml")
                || m.startsWith("application/vnd.ms-excel")
                || m.startsWith("application/x-tika-msworks-spreadsheet")
                || m.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml")
                || m.startsWith("application/vnd.oasis.opendocument.spreadsheet");
    }

    private class PDFThumbCreator implements Runnable {
        private IItem item;
        private File thumbFile;

        public PDFThumbCreator(IItem item, File thumbFile) {
            this.item = item;
            this.thumbFile = thumbFile;
        }

        @Override
        public void run() {
            createPDFThumb(item, thumbFile);
        }
    }

    private class LOThumbCreator implements Runnable {
        private List<IItem> items;

        public LOThumbCreator(List<IItem> items) {
            this.items = items;
        }

        @Override
        public void run() {
            createLOThumb(items);
        }
    }

    private void createPDFThumb(IItem item, File thumbFile) {
        long t = System.currentTimeMillis();
        boolean success = false;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
        try {
            if (externalPdfConversion) {
                URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
                String jarDir = new File(url.toURI()).getParent();
                String classpath = jarDir + "/*";
                File file = item.getTempFile();
                String[] cmd = {"java","-cp",classpath,"-Xmx" + maxPdfExternalMemory + "M",PDFToThumb.class.getCanonicalName(),file.getAbsolutePath(),
                        String.valueOf(thumbSize)};

                ProcessBuilder pb = new ProcessBuilder(cmd);
                convertProcess = pb.start();
                Util.ignoreStream(convertProcess.getErrorStream());
                byte[] buf = new byte[65536];
                try {
                    int read = 0;
                    BufferedInputStream pis = new BufferedInputStream(convertProcess.getInputStream());
                    while ((read = pis.read(buf)) >= 0) {
                        baos.write(buf, 0, read);
                    }
                    pis.close();
                    convertProcess.waitFor();
                    success = convertProcess.exitValue() == 0;
                } catch (InterruptedException e) {
                    convertProcess.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            } else {
                File file = item.getTempFile();
                BufferedImage img = PDFToThumb.getPdfThumb(file, thumbSize);
                if (img != null) {
                    ImageIO.write(img, "jpg", baos);
                    success = true;
                }
            }
            if (success && baos.size() > 0) {
                item.setThumb(baos.toByteArray());
            }
            saveThumb(item, thumbFile);
        } catch (Throwable e) {
            logger.warn(item.toString(), e);
        } finally {
            finishProcess(convertProcess);
            convertProcess = null;
            boolean hasThumb = updateHasThumb(item);
            (hasThumb ? totalPdfProcessed : totalPdfFailed).incrementAndGet();
        }
        totalPdfTime.addAndGet(System.currentTimeMillis() - t);
    }

    private void createLOThumb(List<IItem> items) {
        long t = System.currentTimeMillis();
        try {
            if (loEnvCreateProcess != null) {
                try {
                    loEnvCreateProcess.waitFor(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                }
                finishProcess(loEnvCreateProcess);
                loEnvCreateProcess = null;
            }
            if (!tempSet) {
                setLOTemp();
                tempSet = true;
            }
            Set<String> hashes = new HashSet<String>();
            List<String> cmd = new ArrayList<String>();
            cmd.add(loPath + "/program/soffice.bin");
            cmd.add("--convert-to");
            cmd.add("png");
            for (IItem item : items) {
                if (hashes.add(item.getHash())) {
                    File inFile = createHashTempFile(loOutDir, item);
                    cmd.add(inFile.getAbsolutePath());
                }
            }
            cmd.add("--headless");
            cmd.add("--quickstart");
            cmd.add("--norestore");
            cmd.add("--nolockcheck");
            cmd.add("-env:UserInstallation=file://" + loOutPath);
            cmd.add("--outdir");
            cmd.add(loOutDir.getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(cmd.toArray(new String[0]));
            convertProcess = pb.start();
            pb.redirectErrorStream();
            Util.ignoreStream(convertProcess.getInputStream());
            try {
                convertProcess.waitFor();
            } catch (InterruptedException e) {
                convertProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        } catch (Throwable e) {
            logger.warn(items.toString(), e);
        } finally {
            finishProcess(convertProcess);
            convertProcess = null;
        }
        totalLoTime.addAndGet(System.currentTimeMillis() - t);
    }

    private File getHashTempFile(File dir, IItem item) throws IOException {
        String ext = ".tmp";
        IEvidenceFileType type = item.getType();
        if (type != null && !type.toString().isEmpty()) {
            ext = dpf.sp.gpinf.indexer.util.Util.getValidFilename("." + type.toString());
        }
        return new File(dir, item.getHash() + ext);
    }

    private File createHashTempFile(File dir, IItem item) throws IOException {
        File file = getHashTempFile(dir, item);
        final Path path = Files.createFile(file.toPath());
        tmpResources.addResource(new Closeable() {
            public void close() throws IOException {
                Files.delete(path);
            }
        });
        try (InputStream in = item.getBufferedStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    private void finishProcess(Process process) {
        try {
            if (process != null) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (Throwable e) {}
    }

    @Override
    public void interrupted() {
        finishProcess(loEnvCreateProcess);
        finishProcess(convertProcess);
    }

    private void setLOTemp() {
        try {
            File cfgIn = new File(loOutDir, "user/registrymodifications.xcu");
            File cfgOut = new File(loOutDir, "user/registrymodifications.tmp");
            if (cfgIn.exists()) {
                BufferedReader in = new BufferedReader(new FileReader(cfgIn));
                BufferedWriter out = new BufferedWriter(new FileWriter(cfgOut));
                String line = null;
                int cnt = 0;
                while ((line = in.readLine()) != null) {
                    if (line.contains("UseOpenCL")) {
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Misc\"><prop oor:name=\"UseOpenCL\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        continue;
                    }
                    out.write(line);
                    out.newLine();
                    if (++cnt == 2) {
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Drawinglayer\"><prop oor:name=\"AntiAliasing\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Misc\"><prop oor:name=\"FirstRun\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Misc\"><prop oor:name=\"UseLocking\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Save/Document\"><prop oor:name=\"AutoSave\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Save/Document\"><prop oor:name=\"LoadPrinter\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Save/Document\"><prop oor:name=\"CreateBackup\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Impress/Filter/Import/VBA\"><prop oor:name=\"Load\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Writer/Filter/Import/VBA\"><prop oor:name=\"Load\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Calc/Filter/Import/VBA\"><prop oor:name=\"Load\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Path/Current\"><prop oor:name=\"Temp\" oor:op=\"fuse\"><value xsi:nil=\"true\"/></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Common/Path/Info\"><prop oor:name=\"WorkPathChanged\" oor:op=\"fuse\"><value>false</value></prop></item>");
                        out.newLine();
                        out.write("<item oor:path=\"/org.openoffice.Office.Paths/Paths/org.openoffice.Office.Paths:NamedPath['Temp']\"><prop oor:name=\"WritePath\" oor:op=\"fuse\"><value>file://"
                                + loOutPath + "</value></prop></item>");
                        out.newLine();
                    }
                }
                in.close();
                out.close();
            }
            cfgIn.delete();
            cfgOut.renameTo(cfgIn);
        } catch (Exception e) {
            logger.warn("Error setting LibreOffice temp directory!", e);
        }
    }
}