package dpf.sp.gpinf.indexer.util;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalImageConverter implements Closeable {
    private static Logger logger = LoggerFactory.getLogger(ExternalImageConverter.class);

    private static final String prefix = "extImgConv."; //$NON-NLS-1$

    public static final String enabledProp = prefix + "enabled"; //$NON-NLS-1$
    public static final String useGMProp = prefix + "useGM"; //$NON-NLS-1$
    public static final String winToolPathPrefixProp = prefix + "winToolPathPrefix"; //$NON-NLS-1$
    public static final String lowDensityProp = prefix + "lowDensity"; //$NON-NLS-1$
    public static final String highDensityProp = prefix + "highDensity"; //$NON-NLS-1$
    public static final String magickAreaLimitProp = prefix + "magickAreaLimit"; //$NON-NLS-1$
    public static final String minTimeoutProp = prefix + "minTimeout"; //$NON-NLS-1$
    public static final String timeoutPerMBProp = prefix + "timeoutPerMB"; //$NON-NLS-1$
    public static final String tmpDirProp = prefix + "tmpDir"; //$NON-NLS-1$

    private static final String SAMPLE_GEOMETRY = "SAMPLE_GEOMETRY"; //$NON-NLS-1$
    private static final String RESIZE_GEOMETRY = "RESIZE_GEOMETRY"; //$NON-NLS-1$
    private static final String THREAD = "thread"; //$NON-NLS-1$
    private static final String NUM_THREADS = "numThreads"; //$NON-NLS-1$
    private static final String IM_TEMP_PATH = "MAGICK_TEMPORARY_PATH"; //$NON-NLS-1$
    private static final String GM_TEMP_PATH = "MAGICK_TMPDIR"; //$NON-NLS-1$
    private static final String MAGICK_AREA_LIMIT = "MAGICK_AREA_LIMIT"; //$NON-NLS-1$
    private static final String DENSITY = "DENSITY"; //$NON-NLS-1$
    private static final String INPUT = "INPUT"; //$NON-NLS-1$

    private static final int sampleFactor = 2;

    private String[] CMD = { "magick", "convert", "-limit", THREAD, NUM_THREADS, "-density", DENSITY, "-sample", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            SAMPLE_GEOMETRY, "-resize", RESIZE_GEOMETRY, INPUT, "png:-" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private final String winToolPathPrefix;
    private final String toolPath;
    private final boolean enabled;
    private final boolean useGM;
    private final int lowDensity;
    private final int highDensity;
    private final String magickAreaLimit;
    private final int minTimeout;
    private final int timeoutPerMB;
    private final File tmpDir;

    private int numThreads = 1;

    private ExecutorService executorService = null;
    private boolean ownsExecutor;

    public ExternalImageConverter(ExecutorService executorService) {
        this(executorService, false);
    }

    public ExternalImageConverter() {
        this(Executors.newCachedThreadPool(), true);
    }

    private ExternalImageConverter(ExecutorService executorService, boolean ownsExecutor) {
        super();
        this.executorService = executorService;
        this.ownsExecutor = ownsExecutor;

        enabled = Boolean.valueOf(System.getProperty(enabledProp, "false").trim()); //$NON-NLS-1$
        useGM = Boolean.valueOf(System.getProperty(useGMProp, "false").trim()); //$NON-NLS-1$
        winToolPathPrefix = System.getProperty(winToolPathPrefixProp, "").trim(); //$NON-NLS-1$
        lowDensity = Integer.parseInt(System.getProperty(lowDensityProp, "96").trim()); //$NON-NLS-1$
        highDensity = Integer.parseInt(System.getProperty(highDensityProp, "250").trim()); //$NON-NLS-1$
        magickAreaLimit = System.getProperty(magickAreaLimitProp, "32").trim() + "MP"; //$NON-NLS-1$  //$NON-NLS-2$
        minTimeout = Integer.parseInt(System.getProperty(minTimeoutProp, "20").trim()); //$NON-NLS-1$
        timeoutPerMB = Integer.parseInt(System.getProperty(timeoutPerMBProp, "2").trim()); //$NON-NLS-1$
        tmpDir = new File(System.getProperty(tmpDirProp, System.getProperty("java.io.tmpdir"))); //$NON-NLS-1$
        toolPath = winToolPathPrefix.isEmpty() ? "" //$NON-NLS-1$
                : winToolPathPrefix + File.separatorChar + "tools" //$NON-NLS-1$
                        + File.separatorChar + (useGM ? "graphicsmagick" : "imagemagick"); //$NON-NLS-1$ //$NON-NLS-2$
        if (useGM) {
            CMD[0] = "gm";
            for (int i = 1; i < CMD.length; i++) {
                if (CMD[i].equals(THREAD)) {
                    CMD[i] = "threads";
                    break;
                }
            }
        }
        if (!toolPath.isEmpty())
            CMD[0] = toolPath + File.separatorChar + CMD[0];
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    private String[] getCmd(int maxDimension, boolean highRes, Object objIn) {
        String[] cmd = CMD.clone();
        for (int i = 0; i < cmd.length; i++) {
            String c = CMD[i];
            if (c.equals(SAMPLE_GEOMETRY))
                cmd[i] = String.format("%1$dx%1$d>", maxDimension * sampleFactor);
            else if (c.equals(RESIZE_GEOMETRY))
                cmd[i] = String.format("%1$dx%1$d>", maxDimension);
            else if (c.equals(NUM_THREADS))
                cmd[i] = String.valueOf(numThreads);
            else if (c.equals(DENSITY))
                cmd[i] = String.valueOf(highRes ? highDensity : lowDensity);
            else if (c.equals(INPUT))
                cmd[i] = objIn instanceof File ? ((File) objIn).getAbsolutePath() : "-";
        }
        return cmd;
    }

    public BufferedImage getImage(InputStream in, int maxDimension, boolean highRes, Long imageSize) {
        try {
            return doGetImage(in, maxDimension, highRes, imageSize, false);
        } catch (TimeoutException e) {
            return null;
        }
    }

    public BufferedImage getImage(File in, int maxDimension, boolean highRes, Long imageSize) {
        try {
            return doGetImage(in, maxDimension, highRes, imageSize, false);
        } catch (TimeoutException e) {
            return null;
        }
    }

    public Dimension getDimension(InputStream in) {
        if (!enabled)
            return null;

        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().put(MAGICK_AREA_LIMIT, magickAreaLimit);
        pb.environment().put(MAGICK_AREA_LIMIT, magickAreaLimit);

        String[] cmd = { CMD[0], "identify", "-ping", "-format", "%w %h", "-" };
        try {
            pb.command(cmd);
            Process p = pb.start();
            sendInputStream(in, p);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.ISO_8859_1))) {
                String line = reader.readLine();
                String[] d = line.split(" ");
                if (d.length >= 2)
                    return new Dimension(Integer.parseInt(d[0]), Integer.parseInt(d[1]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public BufferedImage getImage(InputStream in, int maxDim, boolean highRes, Long imageSize, boolean throwTimeout)
            throws TimeoutException {
        return doGetImage(in, maxDim, highRes, imageSize, throwTimeout);
    }

    private BufferedImage doGetImage(Object objIn, int maxDim, boolean highRes, Long imageSize, boolean throwTimeout)
            throws TimeoutException {

        if (!enabled)
            return null;

        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().put(useGM ? GM_TEMP_PATH : IM_TEMP_PATH, tmpDir.getAbsolutePath());
        pb.environment().put(MAGICK_AREA_LIMIT, magickAreaLimit);

        pb.command(getCmd(maxDim, highRes, objIn));
        Process p = null;
        try {
            p = pb.start();
        } catch (IOException e) {
            logger.error("Error executing " + (useGM ? "graphicsMagick" : "imageMagick") + ". " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + "Check if it is installed and if its path is configured!", e); //$NON-NLS-1$
        }
        BufferedImage result = null;
        if (p != null) {
            Future<?> sendFuture = objIn instanceof InputStream ? sendInputStream((InputStream) objIn, p) : null;
            ignoreErrorStream(p);
            Future<BufferedImage> resultFuture = getResultFuture(p);
            int timeout = getTotalTimeout(imageSize);
            if (highRes) 
                timeout *= 2;
            try {
                if (sendFuture != null)
                    sendFuture.get(timeout, TimeUnit.SECONDS);
                result = resultFuture.get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException | InterruptedException e) {
                if (throwTimeout) {
                    if (e instanceof TimeoutException) {
                        throw (TimeoutException) e;
                    } else {
                        TimeoutException te = new TimeoutException(e.getMessage());
                        te.initCause(e);
                        throw te;
                    }
                } else {
                    logger.warn("Timeout converting image to PNG, elapsed {} s.", timeout); //$NON-NLS-1$
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    p.destroy();
                    boolean exited = p.waitFor(2, TimeUnit.SECONDS);
                    if (!exited) {
                        p.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return result;
    }

    public int getTotalTimeout(Long imageSize) {
        if (imageSize == null)
            return minTimeout;
        return minTimeout + (int) (imageSize >> 20) * timeoutPerMB;
    }

    private Future<BufferedImage> getResultFuture(final Process p) {
        Callable<BufferedImage> callable = new ResultCallable(p);
        return submit(callable);
    }

    private void ignoreErrorStream(final Process p) {
        Runnable runnable = new IgnoreErrorStreamRunnable(p);
        submit(runnable);
    }

    private Future<?> sendInputStream(final InputStream in, final Process p) {
        Runnable runnable = new SendInputStreamRunnable(p, in);
        return submit(runnable);
    }

    private <V> Future<V> submit(Callable<V> callable) {
        return executorService.submit(callable);
    }

    private Future<?> submit(Runnable runnable) {
        return executorService.submit(runnable);
    }

    public void finish(long timeoutInMilis) {
        if (ownsExecutor && executorService != null) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(timeoutInMilis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        finish(10000);
    }

    private static class IgnoreErrorStreamRunnable implements Runnable {
        private final Process p;

        private IgnoreErrorStreamRunnable(Process p) {
            this.p = p;
        }

        @Override
        public void run() {
            InputStream in = p.getErrorStream();
            int i = 0;
            byte[] buf = new byte[8192];
            try {
                while (i != -1 && p.isAlive()) {
                    i = in.read(buf);
                }
            } catch (IOException e) {
            } finally {
                IOUtil.closeQuietly(in);
            }
        }
    }

    private class ResultCallable implements Callable<BufferedImage> {
        private final Process p;

        private ResultCallable(Process p) {
            this.p = p;
        }

        @Override
        public BufferedImage call() {
            InputStream in = p.getInputStream();
            try {
                return ImageIO.read(in);
            } catch (IOException e) {
                return null;
            } finally {
                IOUtil.closeQuietly(in);
            }
        }
    }

    private static class SendInputStreamRunnable implements Runnable {
        private final Process p;
        private final InputStream in;

        private SendInputStreamRunnable(Process p, InputStream in) {
            this.p = p;
            this.in = in;
        }

        @Override
        public void run() {
            OutputStream out = p.getOutputStream();
            int i = 0;
            byte[] buf = new byte[64 * 1024];
            try {
                while (i != -1 && p.isAlive()) {
                    out.write(buf, 0, i);
                    out.flush();
                    i = in.read(buf);
                }
            } catch (IOException e) {
            } finally {
                IOUtil.closeQuietly(out);
            }
        }
    }
}