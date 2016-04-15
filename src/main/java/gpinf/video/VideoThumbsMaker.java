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
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import dpf.sp.gpinf.indexer.util.ImageUtil;

/**
 * Classe principal de geração de imagens com cenas extraídas de vídeos. Utiliza o MPlayer para
 * realização da extração de frames e na sequência monta uma imagem única no layout especificado de
 * linhas e colunas.
 *
 * @author Wladimir Leite
 */
public class VideoThumbsMaker {

  private String mplayer = "mplayer.exe";
  private int timeoutProcess = 15000;
  private int timeoutInfo = 10000;
  private int timeoutFirstCall = 180000;
  private boolean verbose = false;
  private int quality = 50;
  private boolean checkContent = false;
  private String escape = null;
  private boolean firstCall = true;
  private boolean isWindows = false;
  private static final String prefix = "_vtm";
  private int ignoreWaitKeyFrame;
  private int maxLines = 20000;

  public String getVersion() {
    List<String> cmds = new ArrayList<String>(Arrays.asList(new String[]{mplayer}));

    ExecResult res = run(cmds.toArray(new String[0]), firstCall ? timeoutFirstCall : timeoutInfo);
    if (res.exitCode != 0) {
      return null;
    }
    String info = res.output;
    if (info != null) {
      if (info.indexOf("\n") > 0) {
        info = info.substring(0, info.indexOf("\n"));
      }
      if (info.indexOf("MPlayer") < 0) {
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
        escape = "";
        if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
          isWindows = true;
          escape = "\\\"";
        }
      } catch (Exception e) {
      }
    }

    long start = System.currentTimeMillis();
    VideoProcessResult result = new VideoProcessResult();

    File in = inOrg;
    List<String> cmds = new ArrayList<String>(Arrays.asList(new String[]{mplayer, "-nosound", "-noautosub", "-noconsolecontrols", "-vo", "null", "-ao", "null", "-frames", "0", "-identify", in.getPath()}));

    File subTmp = new File(tmp, prefix + Thread.currentThread().getId() + "_" + System.currentTimeMillis());
    subTmp.mkdir();
    subTmp.deleteOnExit();

    boolean fixed = false;
    File lnk = null;
    String videoStream = null;
    for (int step = 0; step <= 1; step++) {
      ExecResult res = run(cmds.toArray(new String[0]), firstCall ? timeoutFirstCall : timeoutInfo);
      if (firstCall) {
        firstCall = false;
        maxLines = 2000;
      }

      String info = res.output;
      if (step == 0 && info != null && info.indexOf("File not found") >= 0 && !fixed) {
        fixed = true;
        String shortName = getShortName(inOrg);
        if (shortName != null) {
          if (verbose) {
            System.err.println("Usando nome curto = " + shortName);
          }
          in = new File(inOrg.getParentFile(), shortName);
          cmds.set(cmds.size() - 1, in.getPath());
          step--;
          continue;
        }
        lnk = makeLink(inOrg, subTmp);
        if (lnk != null) {
          if (verbose) {
            System.err.println("Usando link = " + lnk);
          }
          in = lnk;
          cmds.set(cmds.size() - 1, in.getPath());
          step--;
          continue;
        }
      }
      if (info != null) {
        long duration = getDuration(info);
        result.setVideoDuration(duration);

        Dimension dimension = getDimension(info);
        result.setDimension(dimension);

        String s = getVideoStream(info);
        if (s != null) {
          videoStream = s;
        }

        if (result.getVideoDuration() > 0 && result.getDimension() != null) {
          break;
        }
      }

      cmds.add(1, "-demuxer");
      cmds.add(2, "lavf");
    }
    if (outs == null) {
      result.setFile(in);
      result.setSubTemp(subTmp);
      return result;
    }

    if (result.getVideoDuration() == 0 || result.getDimension() == null || result.getDimension().width == 0 || result.getDimension().height == 0) {
      cleanTemp(subTmp);
      return result;
    }

    if (verbose) {
      System.err.println("DURATION: " + result.getVideoDuration());
    }

    int maxThumbs = 0;
    int maxWidth = 0;
    for (VideoThumbsOutputConfig config : outs) {
      int curr = config.getColumns() * config.getRows();
      if (maxThumbs < curr) {
        maxThumbs = curr;
      }
      if (maxWidth < config.getThumbWidth()) {
        maxWidth = config.getThumbWidth();
      }
    }
    int frequency = (int) ((result.getVideoDuration() - 1) * 0.001 / (maxThumbs + 2));
    if (frequency < 1) {
      frequency = 1;
    }

    String s1 = "VO: [jpeg] ";
    String s2 = " => ";
    File[] files = null;

    int maxHeight = result.getDimension().height * maxWidth / result.getDimension().width;
    String scale = "scale=" + maxWidth + ":" + maxHeight;

    boolean scaled = result.getDimension().width > maxWidth;
    cmds = new ArrayList<String>();
    cmds.add(mplayer);
    cmds.add("-demuxer");
    cmds.add("lavf");
    cmds.add("-nosound");
    cmds.add("-noconsolecontrols");
    cmds.add("-noautosub");
    if (ignoreWaitKeyFrame != 1) {
      cmds.add("-lavdopts");
      cmds.add("wait_keyframe");
    }
    if (scaled) {
      cmds.addAll(Arrays.asList(new String[]{"-sws", "0", "-vf", scale}));
    }

    if (videoStream != null) {
      cmds.add("-vid");
      cmds.add(videoStream);
    }

    String ssVal = String.valueOf(result.getVideoDuration() < 5000 ? 0 : Math.max(frequency / 2, 1));
    cmds.addAll(Arrays.asList(new String[]{"-vo", "jpeg:smooth=50:nobaseline:quality=" + quality + ":outdir=" + escape + subTmp.getPath().replace('\\', '/') + escape, "-ao", "null", "-ss", ssVal, "-sstep", String.valueOf(frequency), "-frames", String.valueOf(maxThumbs + 1), in.getPath()}));

    for (int step = 0; step <= 2; step++) {
      ExecResult res = run(cmds.toArray(new String[0]), timeoutProcess);
      files = subTmp.listFiles(new FileFilter() {
        public boolean accept(File pathname) {
          return pathname.getName().toLowerCase().endsWith(".jpg");
        }
      });
      String ret = res.output;
      if (ret != null) {
        if (!scaled) {
          int p1 = ret.indexOf(s1);
          if (p1 > 0) {
            int p2 = ret.indexOf(s2, p1);
            if (p2 > 0) {
              int p3 = ret.indexOf(" ", p2 + s2.length());
              if (p3 > 0) {
                String[] s = ret.substring(p1 + s1.length(), p3).split(s2);
                if (s.length == 2) {
                  s = s[1].split("x");
                  scaled = true;
                  Dimension nd = new Dimension(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
                  if (!nd.equals(result.getDimension())) {
                    result.setDimension(nd);
                    int pos = cmds.indexOf(scale);
                    if (pos >= 0) {
                      maxHeight = result.getDimension().height * maxWidth / result.getDimension().width;
                      scale = "scale=" + maxWidth + ":" + maxHeight;
                      cmds.set(pos, scale);
                      step--;
                      continue;
                    }
                  }
                }
              }
            }
          }
        }
        if (ignoreWaitKeyFrame == 0 && step == 0) {
          String rlc = ret.toLowerCase();
          if ((rlc.indexOf("unknown") >= 0 || rlc.indexOf("suboption") >= 0 || rlc.indexOf("error") >= 0) && (rlc.indexOf("lavdopts") >= 0 || rlc.indexOf("wait_keyframe") >= 0)) {
            step = -1;
            ignoreWaitKeyFrame = 1;
            int pos = cmds.indexOf("-lavdopts");
            cmds.remove(pos + 1);
            cmds.remove(pos);
            System.err.println(">>>>> OPCAO '-lavdopts wait_keyframe' DESABILITADA.");
            continue;
          }
        }
        if (files.length > (maxThumbs - 1) / 3 && ret.indexOf("Error while decoding frame") < 0) {
          break;
        }
      }
      if (step == 0) {
        int pos = cmds.indexOf("-vid");
        if (pos < 0) {
          step++;
        } else {
          cmds.remove(pos + 1);
          cmds.remove(pos);
          continue;
        }
      }
      if (step == 1) {
        int pos = cmds.indexOf("-sstep");
        cmds.remove(pos + 1);
        cmds.remove(pos);
        pos = cmds.indexOf("-ss");
        cmds.remove(pos + 1);
        cmds.remove(pos);
      }
    }
    if (ignoreWaitKeyFrame == 0) {
      ignoreWaitKeyFrame = -1;
    }

    int[] rgbPrev = null;
    Arrays.sort(files);
    List<File> images = new ArrayList<File>();
    for (File file : files) {
      file.deleteOnExit();
      if (checkContent) {
        BufferedImage img = ImageIO.read(file);
        boolean empty = true;
        int[] rgb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        int base = rgb[img.getHeight() / 2 * img.getWidth() + img.getWidth() / 2];
        int cntm = 0;
        for (int i = 0; i < rgb.length; i += 4) {
          if (rgbDist(rgb[i], base) > 80) {
            cntm++;
            if (cntm > rgb.length / 50 + 1) {
              empty = false;
              break;
            }
          }
        }
        boolean repeated = false;
        if (!empty) {
          if (rgbPrev != null) {
            repeated = rgb.length == rgbPrev.length;
            if (repeated) {
              for (int i = 0; i < rgb.length; i += 4) {
                if (rgb[i] != rgbPrev[i]) {
                  repeated = false;
                  break;
                }
              }
            }
          }
          if (!repeated) {
            rgbPrev = rgb;
          }
        }
        if (!empty && !repeated) {
          images.add(file);
        }
      } else {
        images.add(file);
      }
    }
    if (lnk != null) {
      lnk.delete();
    }
    if (images.size() == 0) {
      cleanTemp(subTmp);
      return result;
    }
    for (VideoThumbsOutputConfig config : outs) {
      generateGridImage(config, images, result.getDimension());
    }
    cleanTemp(subTmp);

    result.setSuccess(true);
    result.setProcessingTime(System.currentTimeMillis() - start);
    return result;
  }

  private File makeLink(File in, File dir) {
    try {
      String ext = getExtensao(in.getName());
      if (ext == null) {
        ext = "";
      }
      File lnk = new File(dir, "vtc" + System.currentTimeMillis() + ext);
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
    String[] cdir = new String[]{"cmd", "/c", "dir", "/x", in.getPath()};
    ExecResult res = run(cdir, 1000);
    String sdir = res.output;
    if (sdir != null && res.exitCode == 0) {
      String ext = getExtensao(in.getName());
      if (ext != null) {
        String[] lines = sdir.split("\n");
        for (String line : lines) {
          if (line.endsWith(ext)) {
            String[] s = line.split(" +");
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

  private static final int rgbDist(int a, int b) {
    return Math.abs(s(a, 16) - s(b, 16)) + Math.abs(s(a, 8) - s(b, 8)) + Math.abs(s(a, 0) - s(b, 0));
  }

  private static final int s(int a, int n) {
    return (a >>> n) & 0xFF;
  }

  private void generateGridImage(VideoThumbsOutputConfig config, List<File> images, Dimension dimension) throws IOException {
    int w = config.getThumbWidth();
    double rate = images.size() * 0.999 / (config.getRows() * config.getColumns());
    int h = dimension.height * w / dimension.width;
    int border = config.getBorder();

    BufferedImage img = new BufferedImage(2 + config.getColumns() * (w + border) + border, 2 + config.getRows() * (h + border) + border, BufferedImage.TYPE_INT_BGR);
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
    //ImageIO.write(img, "jpeg", config.getOutFile());
    ImageUtil.saveJpegWithMetadata(img, config.getOutFile(), "Frames=" + config.getRows() + "x" + config.getColumns());
  }

  public void cleanTemp(File subTmp) {
    File[] files = subTmp.listFiles();
    for (File file : files) {
      file.delete();
    }
    subTmp.delete();
  }

  private long getDuration(String info) throws Exception {
    String s1 = "ID_LENGTH=";
    int p1 = info.indexOf(s1);
    if (p1 < 0) {
      return -1;
    }
    int p2 = info.indexOf('\n', p1);
    String s = info.substring(p1 + s1.length(), p2);
    if (s.isEmpty() || !Character.isDigit(s.charAt(0))) {
      return -1;
    }
    return (long) (1000 * Double.parseDouble(s));
  }

  private String getVideoStream(String info) throws Exception {
    String s1 = "Video stream found, -vid ";
    int p1 = info.indexOf(s1);
    if (p1 < 0) {
      return null;
    }
    int p2 = info.indexOf('\n', p1);
    if (p2 < 0) {
      return null;
    }
    String s = info.substring(p1 + s1.length(), p2);
    if (s.length() != 1) {
      return null;
    }
    if (!Character.isDigit(s.charAt(0))) {
      return null;
    }
    return s;
  }

  private Dimension getDimension(String info) throws Exception {
    String s1 = "ID_VIDEO_WIDTH=";
    int p1 = info.indexOf(s1);
    if (p1 < 0) {
      return null;
    }
    int p2 = info.indexOf('\n', p1);
    if (p2 < 0) {
      return null;
    }
    String s3 = "ID_VIDEO_HEIGHT=";
    int p3 = info.indexOf(s3);
    if (p3 < 0) {
      return null;
    }
    int p4 = info.indexOf('\n', p3);
    if (p4 < 0) {
      return null;
    }
    return new Dimension(Integer.parseInt(info.substring(p1 + s1.length(), p2)), Integer.parseInt(info.substring(p3 + s3.length(), p4)));
  }

  private final ExecResult run(String[] cmds, int timeout) {
    if (verbose) {
      System.err.print("CMD = ");
      for (int i = 0; i < cmds.length; i++) {
        System.err.print(cmds[i] + " ");
      }
      System.err.println();
      System.err.print("TIMEOUT = " + timeout);
      System.err.println();
    }

    StringBuilder sb = new StringBuilder();
    AtomicInteger counter = new AtomicInteger();
    int exitCode = -1000;
    try {
      final Process process = Runtime.getRuntime().exec(cmds);

      StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), sb, counter, process);
      StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), sb, counter, process);

      errorGobbler.start();
      outputGobbler.start();

      long t = System.currentTimeMillis() + timeout;
      while (true) {
        Thread.sleep(10);
        try {
          exitCode = process.exitValue();
          break;
        } catch (IllegalThreadStateException threadStateException) {
          if (System.currentTimeMillis() > t) {
            if (verbose) {
              System.err.println("TIMEOUT");
            }
            process.destroy();
            break;
          }
        }
      }
      return new ExecResult(exitCode, sb.toString());
    } catch (Exception e) {
      if (verbose) {
        System.err.print("Erro executando comando '");
        e.printStackTrace();
      }
    }
    return new ExecResult(exitCode, null);
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

    InputStream is;
    StringBuilder sb;
    AtomicInteger counter;
    Process process;

    StreamGobbler(InputStream is, StringBuilder sb, AtomicInteger counter, Process process) {
      this.is = is;
      this.sb = sb;
      this.counter = counter;
      this.process = process;
      setDaemon(true);
    }

    public void run() {
      BufferedReader br = null;
      try {
        br = new BufferedReader(new InputStreamReader(is), 256);
        String line = null;
        while ((line = br.readLine()) != null) {
          synchronized (sb) {
            counter.incrementAndGet();
            sb.append(line).append('\n');
            if (verbose) {
              System.err.println(line);
            }
            if (line.indexOf("Error while decoding frame!") >= 0 || counter.intValue() > maxLines) {
              process.destroy();
              break;
            }
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

    public ExecResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }
  }
}
