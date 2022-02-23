package gpinf.similarity;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

public class ImageSimilarity {
    public static final int maxDim = 160;

    private static final int numFeatures = 1024;
    private static final int maxPixels = maxDim * maxDim;
    private static final int trimTolerance = 16;
    private static final short[] sqrt = new short[1 << 20];
    private static final double power = 0.3;
    private final int[][] hist = new int[2][512];
    private final int[] pixels = new int[maxPixels];
    private final int[][] channelCount = new int[3][256];
    private final int[] cc0 = channelCount[0];
    private final int[] cc1 = channelCount[1];
    private final int[] cc2 = channelCount[2];
    private final BufferedImage auxColorImg = new BufferedImage(maxDim, maxDim, BufferedImage.TYPE_INT_BGR);
    private int w, h;

    static {
        for (int i = 0; i < 1000; i++) {
            int i10 = i << 10;
            for (int j = 0; j < 1000; j++) {
                sqrt[i10 | j] = (short) Math.sqrt((i * i + j * j) / 4);
            }
        }
    }

    public byte[] extractFeatures(BufferedImage img) {
        if (img == null)
            return null;
        w = img.getWidth();
        h = img.getHeight();
        if (w <= 2 || h <= 2)
            return null;
        getPixels(img);
        trimBorders();
        if (w <= 2 || h <= 2)
            return null;
        for (int i = 0; i < 2; i++) {
            Arrays.fill(hist[i], 0);
        }
        for (int i = 0; i < 3; i++) {
            Arrays.fill(channelCount[i], 0);
        }
        int cut = w * h * 63 / 64;
        for (int y = 1; y < h - 1; y++) {
            int off = y * w + 1;
            int dy = Math.abs((y << 1) - (h - 1));
            for (int x = 1; x < w - 1; x++, off++) {
                int dx = Math.abs((x << 1) - (w - 1));
                int reg = dx * h + dy * w < cut ? 0 : 1;
                int color = pixels[off];
                int bb = (color >>> 16) & 255;
                int gg = (color >>> 8) & 255;
                int rr = color & 255;
                cc0[bb]++;
                cc1[gg]++;
                cc2[rr]++;
                bb >>>= 5;
                rr >>>= 5;
                gg >>>= 5;
                int[] hr = hist[reg];
                for (int i = Math.max(0, rr - 1); i < 8 && i <= rr + 1; i++) {
                    int wi = i == rr ? 1 : 0;
                    int ii = i << 6;
                    for (int j = Math.max(0, gg - 1); j < 8 && j <= gg + 1; j++) {
                        int wij = j == gg ? wi + 1 : wi;
                        int ij = ii | (j << 3);
                        for (int k = Math.max(0, bb - 1); k < 8 && k <= bb + 1; k++) {
                            hr[ij | k] += 1 << (k == bb ? wij + 1 : wij);
                        }
                    }
                }
            }
        }
        byte[] features = new byte[numFeatures];
        int total = (w - 2) * (h - 2);
        int idx = 0;
        double m = 64 / Math.pow(total * 4, power);
        for (int i = 0; i < hist.length; i++) {
            int[] hi = hist[i];
            for (int j = 0; j < hi.length; j++, idx++) {
                int v = hi[j];
                if (v > 0)
                    features[idx] = range(Math.pow(v, power) * m);
            }
        }
        return features;
    }

    private static final byte range(double v) {
        int i = (int) Math.round(v);
        if (i < 0)
            return (byte) 0;
        if (i > 63)
            return (byte) 63;
        return (byte) i;
    }

    private void getPixels(BufferedImage img) {
        Graphics2D gColor = auxColorImg.createGraphics();
        gColor.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (w > maxDim || h > maxDim) {
            if (w > h) {
                h = h * maxDim / w;
                w = maxDim;
            } else {
                w = w * maxDim / h;
                h = maxDim;
            }
            gColor.drawImage(img, 0, 0, w, h, null);
        } else {
            gColor.drawImage(img, 0, 0, null);
        }
        gColor.dispose();
        int[] dataColor = ((DataBufferInt) auxColorImg.getRaster().getDataBuffer()).getData();
        if (w == maxDim) {
            System.arraycopy(dataColor, 0, pixels, 0, w * h);
        } else {
            int y0 = 0;
            int y1 = 0;
            for (int y = 0; y < h; y++, y0 += maxDim, y1 += w) {
                System.arraycopy(dataColor, y0, pixels, y1, w);
            }
        }
    }

    private void trimBorders() {
        int tw = w;
        int th = h;
        int mode = -1;
        for (int x = 0; x * 3 < w; x++) {
            int s0 = 0;
            int s1 = 0;
            for (int y = 0; y < h; y++) {
                int off = y * w + x;
                int color = pixels[off];
                int bb = (color >>> 16) & 255;
                int gg = (color >>> 8) & 255;
                int rr = color & 255;
                s0 += rr + gg + bb;
                off = y * w + (w - x - 1);
                color = pixels[off];
                bb = (color >>> 16) & 255;
                gg = (color >>> 8) & 255;
                rr = color & 255;
                s1 += rr + gg + bb;
            }
            s0 /= h * 3;
            s1 /= h * 3;
            int detect = -1;
            if (s0 < trimTolerance && s1 < trimTolerance)
                detect = 0;
            else if (s0 > 255 - trimTolerance && s1 > 255 - trimTolerance)
                detect = 1;
            if (detect == -1) {
                break;
            }
            if (mode == -1) {
                mode = detect;
            } else if (mode != detect) {
                break;
            }
            tw -= 2;
        }
        if (tw <= 2)
            return;
        int x0 = (w - tw) / 2;
        int x1 = x0 + tw;
        for (int y = 0; y * 3 < h; y++) {
            int s0 = 0;
            int s1 = 0;
            for (int x = x0; x < x1; x++) {
                int off = y * w + x;
                int color = pixels[off];
                int bb = (color >>> 16) & 255;
                int gg = (color >>> 8) & 255;
                int rr = color & 255;
                s0 += rr + gg + bb;
                off = (h - y - 1) * w + x;
                color = pixels[off];
                bb = (color >>> 16) & 255;
                gg = (color >>> 8) & 255;
                rr = color & 255;
                s1 += rr + gg + bb;
            }
            s0 /= tw * 3;
            s1 /= tw * 3;
            int detect = -1;
            if (s0 < trimTolerance && s1 < trimTolerance)
                detect = 0;
            else if (s0 > 255 - trimTolerance && s1 > 255 - trimTolerance)
                detect = 1;
            if (detect == -1) {
                break;
            }
            if (mode == -1) {
                mode = detect;
            } else if (mode != detect) {
                break;
            }
            th -= 2;
        }
        if (tw != w || th != h) {
            int y0 = (h - th) / 2;
            int y1 = y0 + th;
            int pos = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int off = y * w + x;
                    pixels[pos] = pixels[off];
                    pos++;
                }
            }
            w = tw;
            h = th;
        }
    }

    public static int distance(byte[] a, byte[] b) {
        int distance = 0;
        for (int i = 0; i < a.length; i++) {
            int d = a[i] - b[i];
            distance += d * d;
        }
        return distance;
    }

    public static int distance(byte[] a, byte[] b, int cut) {
        int distance = 0;
        for (int i = 0; i < a.length && distance < cut;) {
            int d = a[i] - b[i++];
            distance += d * d + (d = a[i] - b[i++]) * d + (d = a[i] - b[i++]) * d + (d = a[i] - b[i++]) * d;
        }
        return distance;
    }
}
