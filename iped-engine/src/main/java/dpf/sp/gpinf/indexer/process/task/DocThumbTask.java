package dpf.sp.gpinf.indexer.process.task;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import gpinf.util.PDFToThumb;
import iped3.IItem;

public class DocThumbTask extends ThumbTask {

    private static final String enableProperty = "enableDocThumbs";
    private static final String thumbTimeout = "thumbTimeout";
    private static final String taskConfigFile = "DocThumbsConfig.txt";

    private static boolean taskEnabled;

    private static int pdfTimeout = 30;
    private static int loTimeout = 60;
    private static int timeoutIncPerMB = 1;
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
                        logger.info("LibreOffice Conversion: " + (loEnabled ? "enabled" : "disabled") + ".");
                        if (loEnabled) {
                            URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
                            File jarDir = new File(url.toURI()).getParentFile();
                            LibreOfficeFinder loFinder = new LibreOfficeFinder(jarDir);
                            loPath = loFinder.getLOPath();
                            logger.info("LibreOffice Path: " + loPath);
                        }

                        logger.info("PDF Conversion: " + (pdfEnabled ? "enabled" : "disabled") + ".");
                        if (pdfEnabled) {
                            logger.info("External PDF Conversion: " + externalPdfConversion);
                        }
                    }
                }
                logger.info("Task" + (taskEnabled ? "enabled" : "disabled") + ".");
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
    protected void process(IItem evidence) throws Exception {
        if (!taskEnabled || evidence.getHash() == null || evidence.getThumb() != null || !evidence.isToAddToCase()
                || ((!pdfEnabled || !isPdfType(evidence.getMediaType())) &&
                        (!loEnabled || !isLibreOfficeType(evidence.getMediaType())))) {
            return;
        }
        File thumbFile = getThumbFile(evidence);
        if (hasThumb(evidence, thumbFile)) {
            return;
        }

        Future<?> future = executor.submit(new ThumbCreator(evidence, thumbFile));
        try {
            int timeout = isPdfType(evidence.getMediaType()) ? pdfTimeout : loTimeout;
            timeout += (int) ((evidence.getLength() * timeoutIncPerMB) >>> 20);
            future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            stats.incTimeouts();
            evidence.setExtraAttribute(thumbTimeout, "true");
            logger.warn("Timeout creating thumb: " + evidence);
            if (isPdfType(evidence.getMediaType())) {
                totalPdfTimeout.incrementAndGet();
            } else {
                totalLoTimeout.incrementAndGet();
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

    private class ThumbCreator implements Runnable {
        private IItem evidence;
        private File thumbFile;

        public ThumbCreator(IItem evidence, File thumbFile) {
            this.evidence = evidence;
            this.thumbFile = thumbFile;
        }

        @Override
        public void run() {
            createImageThumb(evidence, thumbFile);
        }
    }

    private void createImageThumb(IItem evidence, File thumbFile) {
        long t = System.currentTimeMillis();
        boolean success = false;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
        boolean isPdf = false;
        try {
            if (isPdfType(evidence.getMediaType())) {
                isPdf = true;
                if (externalPdfConversion) {
                    URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
                    String jarDir = new File(url.toURI()).getParent();
                    String classpath = jarDir + "/*";
                    File file = evidence.getTempFile();
                    String[] cmd = {"java","-cp",classpath,"-Xmx" + maxPdfExternalMemory + "M",PDFToThumb.class.getCanonicalName(),file.getAbsolutePath(),
                            String.valueOf(thumbSize)};

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    Process process = pb.start();
                    Util.ignoreStream(process.getErrorStream());
                    byte[] buf = new byte[65536];
                    try {
                        int read = 0;
                        BufferedInputStream pis = new BufferedInputStream(process.getInputStream());
                        while ((read = pis.read(buf)) >= 0) {
                            baos.write(buf, 0, read);
                        }
                        pis.close();
                        process.waitFor();
                        success = process.exitValue() == 0;
                    } catch (InterruptedException e) {
                        process.destroyForcibly();
                        Thread.currentThread().interrupt();
                    }
                } else {
                    File file = evidence.getTempFile();
                    BufferedImage img = PDFToThumb.getPdfThumb(file, thumbSize);
                    if (img != null) {
                        ImageIO.write(img, "jpg", baos);
                        success = true;
                    }
                }
            } else {
                if (loEnvCreateProcess != null) {
                    try {
                        loEnvCreateProcess.waitFor(30, TimeUnit.SECONDS);
                    } catch (Exception e) {}
                    if (loEnvCreateProcess.isAlive()) {
                        loEnvCreateProcess.destroyForcibly();
                    }
                    loEnvCreateProcess = null;
                }

                File inFile = evidence.getTempFile();
                String[] cmd = {loPath + "/program/soffice.bin",
                        "--convert-to",
                        "png",
                        inFile.getAbsolutePath(),
                        "--quickstart",
                        "--norestore",
                        "--nolockcheck",
                        "-env:UserInstallation=file://" + loOutPath,
                        "-outdir",
                        loOutDir.getAbsolutePath()};
                ProcessBuilder pb = new ProcessBuilder(cmd);
                Process process = pb.start();
                pb.redirectErrorStream();
                Util.ignoreStream(process.getInputStream());
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
                String name = inFile.getName();
                int pos = name.lastIndexOf('.');
                if (pos >= 0) name = name.substring(0, pos);
                name += ".png";
                File dest = new File(loOutDir, name);
                if (dest.exists()) {
                    BufferedImage img = ImageIO.read(dest);
                    dest.delete();
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
            }
            if (success && baos.size() > 0) {
                evidence.setThumb(baos.toByteArray());
            }
            saveThumb(evidence, thumbFile);
        } catch (Throwable e) {
            logger.warn(evidence.toString(), e);
        } finally {
            boolean hasThumb = updateHasThumb(evidence);
            if (isPdf) {
                (hasThumb ? totalPdfProcessed : totalPdfFailed).incrementAndGet();
            } else {
                (hasThumb ? totalLoProcessed : totalLoFailed).incrementAndGet();
            }
        }
        t = System.currentTimeMillis() - t;
        (isPdf ? totalPdfTime : totalLoTime).addAndGet(t);
    }
}