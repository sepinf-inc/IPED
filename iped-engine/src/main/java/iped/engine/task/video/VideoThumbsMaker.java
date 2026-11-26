package iped.engine.task.video;

import iped.utils.CharsetUtil;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import iped.utils.ImageUtil;

import org.bytedeco.javacpp.Loader;

/**
 * Classe principal de geração de imagens com cenas extraídas de vídeos. Utiliza
 * o MPlayer para realização da extração de frames e na sequência monta uma
 * imagem única no layout especificado de linhas e colunas.
 *
 * @author Wladimir Leite
 */
public class VideoThumbsMaker {

    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

    private String mplayer = "mplayer.exe"; //$NON-NLS-1$
    private static final String ffmpeg = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
    private static final String ffprobe = Loader.load(org.bytedeco.ffmpeg.ffprobe.class);
    private Boolean videoThumbsOriginalDimension = false;
    private int maxDimensionSize = 1024;
    private int compression = 50;
    private String numFramesEquation;
    private ScriptEngine scriptEngine;
    private int timeoutProcess = 45000;
    private int timeoutInfo = 15000;
    private int timeoutFirstCall = 300000;
    private boolean verbose = false;
    private boolean firstCall = true;
    private static final String prefix = "_vtm"; //$NON-NLS-1$
    private int ignoreWaitKeyFrame;
    private static final int maxLines = 20000;
    // Windows limits a command line to 32,767 characters
    private static final int maxInlineListLength = 30000;
    // From this size on, only the first and the last packets of a video are read to get its time range
    private static final long tailScanMinLength = 32L << 20;
    // Number of inputs (and so of decoders working at the same time) the FFmpeg thumbs list is split into
    private static final int ffmpegInputs = 1;

    public String getVersion() {
        List<String> cmds = new ArrayList<String>(Arrays.asList(new String[] { mplayer }));

        ExecResult res = run(cmds.toArray(new String[0]), firstCall ? timeoutFirstCall : timeoutInfo, null);
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
        return createThumbs(inOrg, tmp, null, 0);
    }

    public VideoProcessResult createThumbs(File inOrg, File tmp, List<VideoThumbsOutputConfig> outs, int numFrames) throws Exception {

        long start = System.currentTimeMillis();

        File in = inOrg;
        List<String> cmds = new ArrayList<String>(Arrays.asList(new String[] { mplayer, "-demuxer", "lavf", "-nosound", "-noautosub", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "-noconsolecontrols", "-vo", "null", "-ao", "null", "-frames", "0", "-identify", in.getPath() })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$

        File subTmp = new File(tmp, prefix + Thread.currentThread().threadId() + "_" + System.currentTimeMillis());
        subTmp.mkdir();
        subTmp.deleteOnExit();

        VideoProcessResult result = new VideoProcessResult(subTmp);

        boolean fixed = false;
        File lnk = null;
        File subTmpFile = null;
        String videoStream = null;
        for (int step = numFrames <= 0 ? 0 : 1; step <= 1; step++) {
            if (step == 1) {
                int pos = cmds.indexOf("-demuxer"); //$NON-NLS-1$
                if (pos < 0) {
                    continue;
                }
                cmds.remove(pos + 1);
                cmds.remove(pos);
            }
            ExecResult res = run(cmds.toArray(new String[0]), firstCall ? timeoutFirstCall : timeoutInfo, null);
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
                if (inOrg.getAbsolutePath().length() > 256) {
                    subTmpFile = File.createTempFile("_temp_video_", ".tmp", subTmp);
                    subTmpFile.deleteOnExit();
                    if (verbose) {
                        System.err.println("Using temp file = " + subTmpFile); //$NON-NLS-1$
                    }
                    try {
                        Files.copy(inOrg.toPath(), subTmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        in = subTmpFile;
                        cmds.set(cmds.size() - 1, in.getPath());
                        step--;
                        continue;
                    } catch (IOException e) {
                        if (verbose) {
                            System.err.println("Error copying to temp a file with long path: " + inOrg.toPath()); //$NON-NLS-1$
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (info != null) {
                result.setVideoInfo(info);
                videoStream = result.getVideoStream();
                if (numFrames > 0) {
                    result.setVideoDuration(1);
                }
                if (result.getVideoDuration() > 0 && result.getDimension() != null) {
                    break;
                }
            }
        }
        if (outs == null) {
            return result;
        }

        // Calls FFprobe as a fallback to get video info
        if ((result.getDimension() == null || result.getDimension().width == 0 || result.getDimension().height == 0
                || result.getVideoDuration() <= 0) && ffprobe != null) {
            cmds = new ArrayList<String>(Arrays.asList(new String[] { ffprobe, "-v", "error", "-select_streams", "v",
                    "-show_entries", "stream:format:program", "-of", "default=nw=1", "-i", in.getPath() }));

            ExecResult res = run(cmds.toArray(new String[0]), firstCall ? timeoutFirstCall : timeoutInfo, null);
            if (firstCall) {
                firstCall = false;
            }

            String info = res.output;
            if (info != null) {
                result.setVideoInfoFFprobe(info);
            }
        }

        if (result.getDimension() == null || result.getDimension().width == 0 || result.getDimension().height == 0) {
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
            if (maxSize < config.getThumbSize()) {
                maxSize = config.getThumbSize();
            }
        }

        int frequency = (int) ((result.getVideoDuration() - 1000) * 0.00095 / (maxThumbs + 2));
        if (frequency < 1) {
            frequency = 1;
        }
        
        if (numFramesEquation != null) {
            int newMaxThumbs = getNumFramesFromJSEquation(result.getVideoDuration() / 1000) + 1;
            if (newMaxThumbs > maxThumbs) {
                maxThumbs = newMaxThumbs;
                frequency = 1; // this will cause frameStep to be used below
            }
        }

        File[] files = null;

        Dimension targetDimension;
        if (videoThumbsOriginalDimension){
            targetDimension = result.getDimension();
        } else {
            targetDimension = getTargetDimension(maxSize, result.getDimension());
        }
        

        String scale = "scale=" + targetDimension.width + ":" + targetDimension.height; //$NON-NLS-1$ //$NON-NLS-2$

        cmds = new ArrayList<String>();
        cmds.add(mplayer);
        if (numFrames <= 0) {
            cmds.add("-demuxer"); //$NON-NLS-1$
            cmds.add("lavf"); //$NON-NLS-1$
        }
        cmds.add("-speed"); //$NON-NLS-1$
        cmds.add("100"); //$NON-NLS-1$
        cmds.add("-dr"); //$NON-NLS-1$
        cmds.add("-nosound"); //$NON-NLS-1$
        cmds.add("-noconsolecontrols"); //$NON-NLS-1$
        cmds.add("-noautosub"); //$NON-NLS-1$
        cmds.add("-noaspect"); //$NON-NLS-1$
        cmds.add("-sws"); //$NON-NLS-1$
        cmds.add("1"); //$NON-NLS-1$
        if (ignoreWaitKeyFrame != 1 && numFrames <= 0) {
            cmds.add("-lavdopts"); //$NON-NLS-1$
            cmds.add("wait_keyframe"); //$NON-NLS-1$
        }

        if (videoStream != null) {
            cmds.add("-vid"); //$NON-NLS-1$
            cmds.add(videoStream);
        }

        cmds.addAll(Arrays.asList(new String[] { "-vo", "jpeg:smooth=50:nobaseline:quality=" + compression}));

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

        cmds.addAll(
                Arrays.asList(new String[] { "-ao", "null", "-ss", "1", "-sstep", String.valueOf(frequency), "-frames", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                        String.valueOf(maxThumbs + 1), in.getPath() }));

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
                    int frameStep = 0;
                    if (numFrames <= 0) {
                        float fps = Math.min(240, result.getFPS());
                        frameStep = (int) (fps * (result.getVideoDuration() - 1) * 0.001 / (maxThumbs + 2));
                    } else {
                        frameStep = numFrames / (maxThumbs + 2);
                    }
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
                int pos = cmds.indexOf("-demuxer"); //$NON-NLS-1$
                if (pos < 0) {
                    continue;
                }
                cmds.remove(pos + 1);
                cmds.remove(pos);
                cmds.add(pos, "-forceidx"); //$NON-NLS-1$

                frameStepStr = null;
                pos = cmds.indexOf("-vf"); //$NON-NLS-1$
                if (pos > 0) {
                    cmds.remove(pos + 1);
                    cmds.remove(pos);
                }
                cmds.addAll(vfOptions(frameStepStr, scale, rot));
            }

            ExecResult res = run(cmds.toArray(new String[0]), timeoutProcess, subTmp);
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

        // Calls FFmpeg if no thumbs were generated by MPlayer
        if (files.length == 0 && ffmpeg != null) {
            result.setFFmpeg(true);
            // Time range of the video, in seconds, taken from its packets. If the end can't be read from
            // them, uses the duration already found (which is not the real one when numFrames was given).
            double[] range = getPacketsTimeRange(in);
            double videoStart = range[0];
            double duration = range[1] - range[0];
            if ((Double.isNaN(duration) || duration <= 0) && numFrames <= 0) {
                duration = result.getVideoDuration() / 1000.0;
            }

            if (duration > 0) {
                // FFmpeg applies the rotation metadata before the filters, so the frames reach the scale
                // filter already rotated
                transposed = Math.abs(result.getRotation()) % 180 == 90;
                scale = transposed ? "scale=" + targetDimension.height + ":" + targetDimension.width
                        : "scale=" + targetDimension.width + ":" + targetDimension.height;

                // The thumbs are decoded by a few decoders in parallel (see thumbsCommand). Each of them starts
                // afresh at its first entry, which in a broken or partial video may begin at a point a new
                // decoder cannot start from, while a single decoder carries on from the previous entry. So, if
                // thumbs are missing, a single input is tried too, and the result with more thumbs is kept.
                int inputs = Math.max(1, Math.min(ffmpegInputs, maxThumbs));
                List<File> listFiles = new ArrayList<File>();
                cmds = thumbsCommand(in, inputs, maxThumbs, videoStart, duration, scale, subTmp, "thumb_", listFiles);
                ExecResult res = run(cmds.toArray(new String[0]), timeoutProcess, subTmp);
                files = listImages(subTmp, "thumb_");
                if (inputs > 1 && !res.timeout && files.length < maxThumbs) {
                    cmds = thumbsCommand(in, 1, maxThumbs, videoStart, duration, scale, subTmp, "single_", listFiles);
                    ExecResult singleRes = run(cmds.toArray(new String[0]), timeoutProcess, subTmp);
                    File[] singleFiles = listImages(subTmp, "single_");
                    File[] discarded = singleFiles;
                    if (singleFiles.length > files.length) {
                        discarded = files;
                        files = singleFiles;
                        res = singleRes;
                    }
                    for (File discardedFile : discarded) {
                        discardedFile.delete();
                    }
                }
                for (File listFile : listFiles) {
                    listFile.delete();
                }
                if (res.timeout) {
                    result.setTimeout(true);
                } else if (result.isTimeout()) {
                    result.setTimeout(false);
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
        if (subTmpFile != null) {
            subTmpFile.delete();
        }
        if (images.size() == 0) {
            return result;
        }
        if (transposed) {
            transpose(result.getDimension());
        }
        for (VideoThumbsOutputConfig config : outs) {
            generateGridImage(config, images, result.getDimension());
            result.setFrames(images);
        }

        result.setTimeout(false);
        result.setSuccess(true);
        result.setProcessingTime(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Builds the FFmpeg command that extracts the thumbs into dir, as prefix + number + ".jpg".
     * <p>
     * The concat demuxer gets one entry per thumb: each entry seeks to its target time (inpoint) and reads
     * the next 2 seconds. The targets are the centers of maxThumbs equal slices of the video. The duration
     * gives each entry its own slot in the output timeline, keeping the timestamps increasing. The entries
     * are split among the given number of inputs, because each input has its own decoder and a decoder
     * handles its entries one after the other. Each input has its own output, which numbers its files from
     * the position of its first thumb.
     * <p>
     * The select filter keeps only the first frame at or after each inpoint: concatdec_select drops the
     * frames decoded before it (from the previous keyframe) and the prev_selected_t test drops the ones
     * after it. "-copyts" and "-fps_mode passthrough" are required to keep the timestamps set by the concat
     * demuxer and to write each selected frame exactly once. "discardcorrupt" drops the incomplete last
     * packet of truncated videos, which otherwise may abort the whole command (the concat demuxer fails to
     * convert it in H.264 videos). Only the decoders run multithreaded. "-q:v" sets the JPEG quality.
     *
     * @param listFiles
     *            receives the list files written to dir, used when the lists are too long to go inline.
     */
    private List<String> thumbsCommand(File in, int inputs, int maxThumbs, double videoStart, double duration,
            String scale, File dir, String prefix, List<File> listFiles) throws IOException {
        // The path is absolute because FFmpeg runs in dir
        String file = "file 'file:" + in.getAbsolutePath().replace("'", "'\\''") + "'\n";
        int[] firstThumb = new int[inputs + 1];
        String[] lists = new String[inputs];
        int listsLength = 0;
        for (int k = 0; k < inputs; k++) {
            firstThumb[k + 1] = (k + 1) * maxThumbs / inputs;
            StringBuilder list = new StringBuilder();
            for (int i = firstThumb[k]; i < firstThumb[k + 1]; i++) {
                double t = videoStart + duration * (i + 0.5) / maxThumbs;
                list.append(file).append(String.format(Locale.ROOT, "inpoint %.6f\noutpoint %.6f\nduration %.6f\n",
                        t, t + 2, duration + 10));
            }
            lists[k] = list.toString();
            listsLength += lists[k].length();
        }

        List<String> cmds = new ArrayList<String>(
                Arrays.asList(new String[] { ffmpeg, "-v", "error", "-filter_threads", "1", "-copyts" }));
        for (int k = 0; k < inputs; k++) {
            // The lists go inline in the command, unless they are too long for a command line
            String input = "data:text/plain," + lists[k];
            if (listsLength > maxInlineListLength) {
                File listFile = new File(dir, prefix + "list" + k + ".txt");
                Files.write(listFile.toPath(), lists[k].getBytes(StandardCharsets.UTF_8));
                listFiles.add(listFile);
                input = listFile.getName();
            }
            cmds.addAll(Arrays.asList(new String[] { "-fflags", "+discardcorrupt", "-f", "concat", "-safe", "0",
                    "-segment_time_metadata", "1", "-i", input }));
        }
        String filter = "select='concatdec_select*not(lt(t-prev_selected_t,5))'," + scale;
        for (int k = 0; k < inputs; k++) {
            cmds.addAll(Arrays.asList(new String[] { "-map", k + ":v:0", "-vf", filter, "-fps_mode", "passthrough",
                    "-frames:v", String.valueOf(firstThumb[k + 1] - firstThumb[k]), "-threads", "1", "-q:v", "2",
                    "-start_number", String.valueOf(firstThumb[k] + 1), prefix + "%05d.jpg" }));
        }
        return cmds;
    }

    private static File[] listImages(File dir, final String prefix) {
        File[] files = dir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                String name = pathname.getName().toLowerCase();
                return name.startsWith(prefix) && name.endsWith(".jpg");
            }
        });
        return files == null ? new File[0] : files;
    }

    /**
     * Gets the time range (in seconds) of the first video stream from its packets: the timestamp of the
     * first one and the end of the last one. The values stored in the file are not used because they may
     * be wrong (e.g. truncated or fragmented videos).
     *
     * @return the start time (0 if unknown) and the end time (NaN if unknown).
     */
    private double[] getPacketsTimeRange(File in) {
        double[] range = { 0, Double.NaN };
        if (ffprobe == null) {
            return range;
        }
        long begin = System.currentTimeMillis();
        PacketScan scan;
        if (in.length() < tailScanMinLength) {
            scan = scanPackets(in, false, timeoutProcess);
        } else {
            // Reading all the packets of a big file takes too long (about 1 second per GB, or much more
            // from a slow disk), so only its first and last packets are read. That is enough if they agree
            // with the duration stored in the file. If not, all the packets are read in the time left.
            scan = scanPackets(in, true, timeoutProcess);
            int timeLeft = timeoutProcess - (int) (System.currentTimeMillis() - begin);
            if (!scan.isComplete() && !scan.hasNoTimestamps() && timeLeft > 0) {
                scan = scanPackets(in, false, timeLeft);
            }
        }
        double start = scan.getStart();
        if (!Double.isNaN(start)) {
            range[0] = start;
        }
        if (scan.isComplete()) {
            range[1] = scan.getEnd();
        }
        return range;
    }

    /**
     * Reads the packets of the first video stream with FFprobe, which does not decode them.
     *
     * @param tailOnly
     *            if true, reads just the first packet and the ones from the last keyframe to the end,
     *            instead of the whole file.
     */
    private PacketScan scanPackets(File in, boolean tailOnly, int timeout) {
        // Corrupt packets are discarded here too, as FFmpeg will not decode them
        List<String> cmds = new ArrayList<String>(Arrays.asList(new String[] { ffprobe, "-v", "error", "-fflags",
                "+discardcorrupt", "-select_streams", "v:0" }));
        if (tailOnly) {
            // First interval: one packet from the start. Second one: seeks to a time after the end, which
            // goes to the last keyframe, and reads until the end. The second interval needs an explicit end
            // (a number of packets): left open, it sometimes stopped after a few packets (FFprobe 9.0.2).
            cmds.add("-read_intervals");
            cmds.add("%+#1,999999999%+#1000000000");
        }
        cmds.addAll(Arrays.asList(new String[] { "-show_entries",
                "packet=pts_time,duration_time:stream=duration:format=duration", "-of", "csv=p=1", "-i",
                in.getPath() }));
        PacketScan scan = new PacketScan(tailOnly);
        ExecResult res = run(cmds.toArray(new String[0]), timeout, null, scan);
        scan.setTimeout(res.timeout);
        return scan;
    }

    private static double parseTime(String time) {
        try {
            return Double.parseDouble(time);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
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
        String[] cdir = new String[] { "cmd", "/c", "dir", "/x", in.getPath() }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        ExecResult res = run(cdir, 1000, null);
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

        if (images.size() > config.getRows() * config.getColumns()) {
            images.remove(0);
        }
        if (images.size() > config.getRows() * config.getColumns()) {
            images.remove(images.size() - 1);
        }
        double rate = images.size() * 0.999 / (config.getRows() * config.getColumns());
        int border = config.getBorder();
        int w, h;
        // setting dimension for gallery thumbs
        int size = config.getThumbSize();
        if (dimension.width >= dimension.height) {
            w = size;
            h = dimension.height * w / dimension.width;
        } else {
            h = size;
            w = dimension.width * h / dimension.height;
        }
        if (w > maxDimensionSize) {
            w = maxDimensionSize;
        }
        if (h > maxDimensionSize) {
            h = maxDimensionSize;
        }

        BufferedImage img = new BufferedImage(2 + config.getColumns() * (w + border) + border,
                2 + config.getRows() * (h + border) + border, BufferedImage.TYPE_INT_BGR);
        Graphics2D g2 = (Graphics2D) img.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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
                "Frames=" + config.getRows() + "x" + config.getColumns(), compression);
    }

    private final ExecResult run(String[] cmds, int timeout, File currDir) {
        return run(cmds, timeout, currDir, null);
    }

    /**
     * @param lineConsumer
     *            if not null, receives each line written by the process to its standard output, with no
     *            limit on the number of lines. In this case nothing is stored in the result output and
     *            the error messages of the process are discarded.
     */
    private final ExecResult run(String[] cmds, int timeout, File currDir, Consumer<String> lineConsumer) {
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
            if (lineConsumer == null) {
                pb.redirectErrorStream(true);
            } else {
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            }
            if (currDir != null) {
                pb.directory(currDir);
            }
            process = pb.start();
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), sb, process, lineConsumer);
            outputGobbler.start();

            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (!finished) {
                if (verbose)
                    System.err.println("TIMEOUT!");
                isTimeout = true;
                process.destroy();
                process.waitFor(3, TimeUnit.SECONDS);
            } else {
                outputGobbler.join();
            }
            exitCode = process.exitValue();
            return new ExecResult(exitCode, sb.toString(), isTimeout);

        } catch (Exception e) {
            if (verbose) {
                System.err.print("Error running program '"); //$NON-NLS-1$
                e.printStackTrace();
            }
            if (!isTimeout && process != null && process.isAlive()) {
                process.destroy();
                try {
                    process.waitFor(3, TimeUnit.SECONDS);
                } catch (InterruptedException e1) {
                    // ignore
                }
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

    private int getNumFramesFromJSEquation(long duration) {
        if (scriptEngine == null) {
            ScriptEngineManager manager = new ScriptEngineManager();
            scriptEngine = manager.getEngineByExtension("js"); // $NON-NLS-1$
        }
        scriptEngine.put("duration", duration);
        try {
            Object result = scriptEngine.eval(numFramesEquation);
            return ((Number) result).intValue();

        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    public void setMPlayer(String mplayer) {
        this.mplayer = mplayer;
    }

    public String getMPlayer() {
        return mplayer;
    }

    public void setNumFramesEquation(String numFramesEquation) {
        this.numFramesEquation = numFramesEquation;
    }

    public void setVideoThumbsOriginalDimension(Boolean videoThumbsOriginalDimension) {
        this.videoThumbsOriginalDimension = videoThumbsOriginalDimension;
    }

    public void setMaxDimensionSize(int maxDimensionSize) {
        this.maxDimensionSize = maxDimensionSize;
    }

    public void setCompression(int compression) {
        this.compression = compression;
    }

    public Boolean getVideoThumbsOriginalDimension() {
        return videoThumbsOriginalDimension;
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
        private Consumer<String> lineConsumer;

        StreamGobbler(InputStream is, StringBuilder sb, Process process, Consumer<String> lineConsumer) {
            this.is = is;
            this.sb = sb;
            this.process = process;
            this.lineConsumer = lineConsumer;
            setDaemon(true);
        }

        public void run() {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(is, CharsetUtil.NATIVE), 4096);
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (lineConsumer != null) {
                        lineConsumer.accept(line);
                        continue;
                    }
                    counter++;
                    sb.append(line).append('\n');
                    if (verbose) {
                        System.err.println(line);
                    }
                    if (counter > maxLines) {
                        process.destroy();
                        if (!process.waitFor(3, TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                        }
                        break;
                    }
                }
            } catch (IOException | InterruptedException e) {
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

    /**
     * Output of a FFprobe packet scan: a "packet,pts_time,duration_time" line per packet read, followed by
     * the durations stored in the file ("stream,duration" and "format,duration"). Only the first and the
     * last packets are kept. The lines are received in another thread.
     */
    class PacketScan implements Consumer<String> {
        private final boolean tailOnly;
        private boolean timeout;
        private int packets;
        private int timedPackets;
        private boolean firstHasTime;
        private String first;
        private String last;
        private String streamDuration;
        private String formatDuration;

        PacketScan(boolean tailOnly) {
            this.tailOnly = tailOnly;
        }

        @Override
        public synchronized void accept(String line) {
            if (line.startsWith("packet,")) {
                // Some packets have no timestamp (N/A)
                boolean hasTime = !line.startsWith("N/A", 7);
                if (packets++ == 0) {
                    firstHasTime = hasTime;
                }
                if (hasTime) {
                    if (first == null) {
                        first = line;
                    }
                    last = line;
                    timedPackets++;
                }
            } else if (line.startsWith("stream,")) {
                if (streamDuration == null) {
                    streamDuration = line;
                }
            } else if (line.startsWith("format,")) {
                formatDuration = line;
            }
        }

        synchronized void setTimeout(boolean timeout) {
            this.timeout = timeout;
        }

        /**
         * @return the timestamp of the first packet of the video that has one, or NaN if unknown.
         */
        synchronized double getStart() {
            // In a tail scan only the first line read is a packet from the start of the video
            if (first == null || (tailOnly && !firstHasTime)) {
                return Double.NaN;
            }
            return parseTime(first.split(",", -1)[1]);
        }

        /**
         * @return the end of the last packet read that has a timestamp, or NaN if there is none.
         */
        synchronized double getEnd() {
            if (last == null) {
                return Double.NaN;
            }
            String[] s = last.split(",", -1);
            double end = parseTime(s[1]);
            double duration = s.length > 2 ? parseTime(s[2]) : Double.NaN;
            return Double.isNaN(duration) ? end : end + duration;
        }

        /**
         * @return true if the last packet read is the last one of the video.
         */
        synchronized boolean isComplete() {
            if (timeout) {
                return false;
            }
            if (!tailOnly) {
                return true;
            }
            // A tail scan is trusted only if what was read agrees with the duration stored in the file,
            // which is not the case of truncated videos, for example
            if (!firstHasTime || timedPackets < 2) {
                return false;
            }
            double stored = streamDuration == null ? Double.NaN : parseTime(streamDuration.split(",", -1)[1]);
            if (Double.isNaN(stored) && formatDuration != null) {
                stored = parseTime(formatDuration.split(",", -1)[1]);
            }
            return stored > 0 && Math.abs(getEnd() - getStart() - stored) <= Math.max(1, stored * 0.005);
        }

        /**
         * @return true if packets were read but none of them has a timestamp.
         */
        synchronized boolean hasNoTimestamps() {
            return !timeout && packets > 0 && first == null;
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
