package gpinf.video;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import dpf.sp.gpinf.indexer.util.ImageUtil;

/**
 * Classe principal de geração de imagens com cenas extraídas de vídeos. Utiliza
 * o MPlayer para realização da extração de frames e na sequência monta uma
 * imagem única no layout especificado de linhas e colunas.
 *
 * @author Wladimir Leite
 */
public class VideoThumbsMaker {

    private String mplayer = "mplayer.exe"; //$NON-NLS-1$
    private int timeoutProcess = 45000;
    private int timeoutInfo = 15000;
    private int timeoutFirstCall = 300000;
    private boolean verbose = false;
    private int quality = 50;
    private String escape = null;
    private boolean firstCall = true;
    private boolean isWindows = false;
    private static final String prefix = "_vtm"; //$NON-NLS-1$
    private int ignoreWaitKeyFrame;
    private static final int maxLines = 20000;

    public String getVersion() {
        List<String> cmds = new ArrayList<String>(Arrays.asList(new String[] {mplayer}));

        ExecResult res = run(cmds.toArray(new String[0]), firstCall ? timeoutFirstCall : timeoutInfo);
        if (res.exitCode != 0) {
            return null;
        }
        String info = res.output;
        if (info != null) {
            if (info.indexOf("\n") > 0) { //$NON-NLS-1$
                info = info.substring(0, info.indexOf("\n")); //$NON-NLS-1$
            }
            if (info.indexOf("MPlayer") < 0) { //$NON-NLS-1$
                return null;
            }
        }

        return info;
    }

    public VideoProcessResult getInfo(File inOrg, File tmp) throws Exception {
        return createThumbs(inOrg, tmp, null);
    }

    public VideoProcessResult createThumbs(File inOrg, File tmp, List<VideoThumbsOutputConfig> outs) throws Exception {
        if (escape == null) {
            try {
                escape = ""; //$NON-NLS-1$
                if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) { //$NON-NLS-1$ //$NON-NLS-2$
                    isWindows = true;
                    escape = "\\\""; //$NON-NLS-1$
                }
            } catch (Exception e) {
            }
        }

        long start = System.currentTimeMillis();
        VideoProcessResult result = new VideoProcessResult();

        File in = inOrg;
        List<String> cmds = new ArrayList<String>(Arrays.asList(new String[] {mplayer,"-nosound","-noautosub", //$NON-NLS-1$ //$NON-NLS-2$
                "-noconsolecontrols","-vo","null","-ao","null","-frames","0","-identify",in.getPath()})); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$

        File subTmp = new File(tmp, prefix + Thread.currentThread().getId() + "_" + System.currentTimeMillis()); //$NON-NLS-1$
        subTmp.mkdir();
        subTmp.deleteOnExit();

        boolean fixed = false;
        File lnk = null;
        String videoStream = null;
        for (int step = 0; step <= 1; step++) {
            if (step == 1) {
                cmds.add("-demuxer"); //$NON-NLS-1$
                cmds.add("lavf"); //$NON-NLS-1$
            }
            ExecResult res = run(cmds.toArray(new String[0]), firstCall ? timeoutFirstCall : timeoutInfo);
            if (firstCall) {
                firstCall = false;
            }

            String info = res.output;
            if (step == 0 && info != null && info.indexOf("File not found") >= 0 && !fixed) { //$NON-NLS-1$
                fixed = true;
                String shortName = getShortName(inOrg);
                if (shortName != null) {
                    if (verbose) {
                        System.err.println("Using short name = " + shortName); //$NON-NLS-1$
                    }
                    in = new File(inOrg.getParentFile(), shortName);
                    cmds.set(cmds.size() - 1, in.getPath());
                    step--;
                    continue;
                }
                lnk = makeLink(inOrg, subTmp);
                if (lnk != null) {
                    if (verbose) {
                        System.err.println("Using link = " + lnk); //$NON-NLS-1$
                    }
                    in = lnk;
                    cmds.set(cmds.size() - 1, in.getPath());
                    step--;
                    continue;
                }
            }
            if (info != null) {
                result.setVideoInfo(info);
                videoStream = result.getVideoStream();

                if (result.getVideoDuration() > 0 && result.getDimension() != null) {
                    break;
                }
            }
        }
        if (outs == null) {
            return result;
        }

        if (result.getVideoDuration() == 0 || result.getDimension() == null || result.getDimension().width == 0
                || result.getDimension().height == 0) {
            cleanTemp(subTmp);
            return result;
        }

        if (verbose) {
            System.err.println("DURATION: " + result.getVideoDuration()); //$NON-NLS-1$
        }

        int maxThumbs = 0;
        int maxSize = 0;
        for (VideoThumbsOutputConfig config : outs) {
            int curr = config.getColumns() * config.getRows();
            if (maxThumbs < curr) {
                maxThumbs = curr;
            }
            if (maxSize < config.getThumbWidth()) {
                maxSize = config.getThumbWidth();
            }
        }
        int frequency = (int) ((result.getVideoDuration() - 1000) * 0.00095 / (maxThumbs + 2));
        if (frequency < 1) {
            frequency = 1;
        }

        File[] files = null;

        Dimension targetDimension = getTargetDimension(maxSize, result.getDimension());

        String scale = "scale=" + targetDimension.width + ":" + targetDimension.height; //$NON-NLS-1$ //$NON-NLS-2$
        
        cmds = new ArrayList<String>();
        cmds.add(mplayer);
        cmds.add("-speed"); //$NON-NLS-1$
        cmds.add("100"); //$NON-NLS-1$
        cmds.add("-dr"); //$NON-NLS-1$
        cmds.add("-nosound"); //$NON-NLS-1$
        cmds.add("-noconsolecontrols"); //$NON-NLS-1$
        cmds.add("-noautosub"); //$NON-NLS-1$
        cmds.add("-noaspect"); //$NON-NLS-1$
        cmds.add("-sws"); //$NON-NLS-1$
        cmds.add("1"); //$NON-NLS-1$
        if (ignoreWaitKeyFrame != 1) {
            cmds.add("-lavdopts"); //$NON-NLS-1$
            cmds.add("wait_keyframe"); //$NON-NLS-1$
        }

        if (videoStream != null) {
            cmds.add("-vid"); //$NON-NLS-1$
            cmds.add(videoStream);
        }

        cmds.addAll(Arrays.asList(new String[] {"-vo", //$NON-NLS-1$
                "jpeg:smooth=50:nobaseline:quality=" + quality + ":outdir=" + escape //$NON-NLS-1$ //$NON-NLS-2$
                        + subTmp.getPath().replace('\\', '/') + escape}));

        String rot = null;
        boolean transposed = false;
        if (result.getRotation() == 90) {
            rot = "rotate=1"; //$NON-NLS-1$
            transposed = true;
        } else if (result.getRotation() == 180) {
            rot = "flip,mirror"; //$NON-NLS-1$ 
        } else if (result.getRotation() == 270) {
            transposed = true;
            rot = "rotate=2"; //$NON-NLS-1$ 
        }

        cmds.addAll(Arrays.asList(new String[] {"-ao","null","-ss","1","-sstep",String.valueOf(frequency),"-frames", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                String.valueOf(maxThumbs + 1),in.getPath()}));

        cmds.addAll(vfOptions(scale, rot));

        String frameStepStr = null;
        int initialStep = frequency > 1 ? 0 : 1;
        for (int step = initialStep; step <= 3; step++) {
            if (step == 1) {
                int pos = cmds.indexOf("-sstep"); //$NON-NLS-1$
                if (pos >= 0) {
                    cmds.remove(pos + 1);
                    cmds.remove(pos);
                    pos = cmds.indexOf("-ss"); //$NON-NLS-1$
                    cmds.remove(pos + 1);
                    cmds.remove(pos);
                    float fps = result.getFPS();
                    if (fps > 240)
                        fps = 1;
                    int frameStep = (int) (fps * (result.getVideoDuration() - 1) * 0.001 / (maxThumbs + 2));
                    if (frameStep < 1) {
                        frameStep = 1;
                    } else if (frameStep > 600) {
                        frameStep = 600;
                    }
                    frameStepStr = "framestep=" + frameStep; //$NON-NLS-1$
                    pos = cmds.indexOf("-vf");
                    if (pos > 0) {
                        cmds.remove(pos + 1);
                        cmds.remove(pos);
                    }
                    cmds.addAll(vfOptions(frameStepStr, scale, rot));
                }
            } else if (step == 2) {
                int pos = cmds.indexOf("-vid"); //$NON-NLS-1$
                if (pos < 0) {
                    continue;
                }

                cmds.remove(pos + 1);
                cmds.remove(pos);

            } else if (step == 3) {
                cmds.add("-demuxer"); //$NON-NLS-1$
                cmds.add("lavf"); //$NON-NLS-1$
                frameStepStr = null;
                int pos = cmds.indexOf("-vf");
                if (pos > 0) {
                    cmds.remove(pos + 1);
                    cmds.remove(pos);
                }
                cmds.addAll(vfOptions(frameStepStr, scale, rot));
            }

            ExecResult res = run(cmds.toArray(new String[0]), timeoutProcess);
            if (res.timeout) {
                result.setTimeout(true);
            } else if (result.isTimeout()) {
                result.setTimeout(false);
            }
            files = subTmp.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.getName().toLowerCase().endsWith(".jpg"); //$NON-NLS-1$
                }
            });
            String ret = res.output;
            if (ret != null) {
                if (ignoreWaitKeyFrame == 0 && step == initialStep) {
                    String rlc = ret.toLowerCase();
                    if ((rlc.indexOf("unknown") >= 0 || rlc.indexOf("suboption") >= 0 || rlc.indexOf("error") >= 0) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            && (rlc.indexOf("lavdopts") >= 0 || rlc.indexOf("wait_keyframe") >= 0)) { //$NON-NLS-1$ //$NON-NLS-2$
                        step = initialStep - 1;
                        ignoreWaitKeyFrame = 1;
                        int pos = cmds.indexOf("-lavdopts"); //$NON-NLS-1$
                        cmds.remove(pos + 1);
                        cmds.remove(pos);
                        System.err.println(">>>>> OPTION '-lavdopts wait_keyframe' DISABLED."); //$NON-NLS-1$
                        continue;
                    }
                }
                if (files.length >= maxThumbs - 2 && ((ret.indexOf("Error while decoding frame") < 0 //$NON-NLS-1$
                        && ret.indexOf("first frame is no keyframe") < 0) || (step > 0))) { //$NON-NLS-1$
                    break;
                }
            }
        }
        if (ignoreWaitKeyFrame == 0) {
            ignoreWaitKeyFrame = -1;
        }

        Arrays.sort(files);
        List<File> images = new ArrayList<File>();
        for (File file : files) {
            file.deleteOnExit();
            images.add(file);
        }
        if (lnk != null) {
            lnk.delete();
        }
        if (images.size() == 0) {
            cleanTemp(subTmp);
            return result;
        }
        if (transposed) {
            transpose(result.getDimension());
        }
        for (VideoThumbsOutputConfig config : outs) {
            generateGridImage(config, images, result.getDimension());
        }
        cleanTemp(subTmp);

        result.setTimeout(false);
        result.setSuccess(true);
        result.setProcessingTime(System.currentTimeMillis() - start);
        return result;
    }

    private void transpose(Dimension d) {
        int aux = d.height;
        d.height = d.width;
        d.width = aux;
    }
    
    private Dimension getTargetDimension(int maxSize, Dimension srcDimension) {
        double zoom = maxSize / (double) Math.max(1, Math.max(srcDimension.height, srcDimension.width));
        int w = (int) Math.round(srcDimension.width * zoom);
        int h = (int) Math.round(srcDimension.height * zoom);
        return new Dimension(w, h);
    }

    private List<String> vfOptions(String... options) {
        List<String> l = new ArrayList<String>(2);
        for (String opt : options) {
            if (opt != null) {
                if (l.isEmpty()) {
                    l.add("-vf");
                    l.add(opt);
                } else {
                    l.set(1, l.get(1) + "," + opt);
                }
            }
        }
        return l;
    }

    private File makeLink(File in, File dir) {
        try {
            String ext = getExtensao(in.getName());
            if (ext == null) {
                ext = ""; //$NON-NLS-1$
            }
            File lnk = new File(dir, "vtc" + System.currentTimeMillis() + ext); //$NON-NLS-1$
            lnk.deleteOnExit();
            Path link = Files.createSymbolicLink(lnk.toPath(), in.toPath());
            return link.toFile();
        } catch (Exception e) {
        }
        return null;
    }

    private String getShortName(File in) {
        if (!isWindows) {
            return null;
        }
        String[] cdir = new String[] {"cmd","/c","dir","/x",in.getPath()}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        ExecResult res = run(cdir, 1000);
        String sdir = res.output;
        if (sdir != null && res.exitCode == 0) {
            String ext = getExtensao(in.getName());
            if (ext != null) {
                String[] lines = sdir.split("\n"); //$NON-NLS-1$
                for (String line : lines) {
                    if (line.endsWith(ext)) {
                        String[] s = line.split(" +"); //$NON-NLS-1$
                        if (s.length > 4) {
                            String shortName = s[3];
                            if (shortName.indexOf('~') > 0) {
                                return shortName;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void generateGridImage(VideoThumbsOutputConfig config, List<File> images, Dimension dimension)
            throws IOException {
        int w = config.getThumbWidth();
        if (images.size() > config.getRows() * config.getColumns()) {
            images.remove(0);
        }
        if (images.size() > config.getRows() * config.getColumns()) {
            images.remove(images.size() - 1);
        }
        double rate = images.size() * 0.999 / (config.getRows() * config.getColumns());
        int h = dimension.height * w / dimension.width;
        int border = config.getBorder();
        if (w > 1024)
            w = 1024;
        if (h > 1024)
            h = 1024;

        BufferedImage img = new BufferedImage(2 + config.getColumns() * (w + border) + border,
                2 + config.getRows() * (h + border) + border, BufferedImage.TYPE_INT_BGR);
        Graphics2D g2 = (Graphics2D) img.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        g2.setColor(new Color(222, 222, 222));
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.setColor(new Color(22, 22, 22));
        g2.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);

        double pos = rate * 0.4;
        for (int i = 0; i < config.getRows(); i++) {
            int y = 1 + i * (h + border) + border;
            for (int j = 0; j < config.getColumns(); j++) {
                int x = 1 + j * (w + border) + border;
                BufferedImage in = ImageIO.read(images.get(Math.min(images.size() - 1, (int) pos)));
                g2.drawImage(in, x, y, w, h, null);
                pos += rate;
            }
        }
        g2.dispose();
        ImageUtil.saveJpegWithMetadata(img, config.getOutFile(),
                "Frames=" + config.getRows() + "x" + config.getColumns()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void cleanTemp(File subTmp) {
        File[] files = subTmp.listFiles();
        for (File file : files) {
            file.delete();
        }
        subTmp.delete();
    }

    private final ExecResult run(String[] cmds, int timeout) {
        if (verbose) {
            System.err.print("CMD = "); //$NON-NLS-1$
            for (int i = 0; i < cmds.length; i++) {
                System.err.print(cmds[i] + " "); //$NON-NLS-1$
            }
            System.err.println();
            System.err.print("TIMEOUT = " + timeout); //$NON-NLS-1$
            System.err.println();
        }

        StringBuilder sb = new StringBuilder();
        int exitCode = -1000;
        boolean isTimeout = false;
        Process process = null;
        try {
            final ProcessBuilder pb = new ProcessBuilder(cmds);
            pb.redirectErrorStream(true);
            process = pb.start();
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), sb, process);
            outputGobbler.start();

            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (!finished) {
                if (verbose)
                    System.err.println("TIMEOUT!");
                isTimeout = true;
                process.destroyForcibly();
            }
            outputGobbler.join();
            exitCode = process.exitValue();

            return new ExecResult(exitCode, sb.toString(), isTimeout);
        } catch (Exception e) {
            if (verbose) {
                System.err.print("Error running program '"); //$NON-NLS-1$
                e.printStackTrace();
            }
        } finally {
            if (process != null && process.isAlive())
                process.destroyForcibly();
        }
        return new ExecResult(exitCode, null, isTimeout);
    }

    private String getExtensao(String nome) {
        int pos = nome.lastIndexOf('.');
        if (pos >= 0) {
            return nome.substring(pos + 1);
        }
        return null;
    }

    public void setMPlayer(String mplayer) {
        this.mplayer = mplayer;
    }

    public String getMPlayer() {
        return mplayer;
    }

    public void setTimeoutProcess(int timeoutProcess) {
        this.timeoutProcess = timeoutProcess;
    }

    public void setTimeoutInfo(int timeoutInfo) {
        this.timeoutInfo = timeoutInfo;
    }

    public void setTimeoutFirstCall(int timeoutFirstCall) {
        this.timeoutFirstCall = timeoutFirstCall;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    class StreamGobbler extends Thread {
        private InputStream is;
        private StringBuilder sb;
        private int counter;
        private Process process;

        StreamGobbler(InputStream is, StringBuilder sb, Process process) {
            this.is = is;
            this.sb = sb;
            this.process = process;
            setDaemon(true);
        }

        public void run() {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(is), 4096);
                String line = null;
                while ((line = br.readLine()) != null) {
                    counter++;
                    sb.append(line).append('\n');
                    if (verbose) {
                        System.err.println(line);
                    }
                    if (counter > maxLines) {
                        process.destroyForcibly();
                        break;
                    }
                }
            } catch (IOException ioe) {
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    class ExecResult {
        final int exitCode;
        final String output;
        boolean timeout;

        public ExecResult(int exitCode, String output, boolean timeout) {
            this.exitCode = exitCode;
            this.output = output;
            this.timeout = timeout;
        }
    }
}
