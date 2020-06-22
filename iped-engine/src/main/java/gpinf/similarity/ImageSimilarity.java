package gpinf.similarity;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

public class ImageSimilarity {
    public static final int maxDim = 160;
    
    private static final int numFeatures = 1044;
    private static final int maxPixels = maxDim * maxDim;
    private static final int trimTolerance = 16;
    private static final short[] sqrt = new short[1 << 20];
    private static final double power = 0.3;
    private final int[][] hist = new int[2][512];
    private final int[][] histEdge = new int[2][8];
    private final int[] pixels = new int[maxPixels];
    private final byte[] gray = new byte[maxPixels];
    private final int[] edges = new int[maxPixels];
    private final int[][] channelCount = new int[4][256];
    private final int[] cc0 = channelCount[0];
    private final int[] cc1 = channelCount[1];
    private final int[] cc2 = channelCount[2];
    private final int[] cc3 = channelCount[3];
    private final BufferedImage auxColorImg = new BufferedImage(maxDim, maxDim, BufferedImage.TYPE_INT_BGR);
    private final BufferedImage auxGrayImg = new BufferedImage(maxDim, maxDim, BufferedImage.TYPE_BYTE_GRAY);
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
        if (img == null) return null;
        w = img.getWidth();
        h = img.getHeight();
        if (w <= 2 || h <= 2) return null;
        getPixels(img);
        trimBorders();
        if (w <= 2 || h <= 2) return null;
        calcEdges();
        for (int i = 0; i < 2; i++) {
            Arrays.fill(hist[i], 0);
            Arrays.fill(histEdge[i], 0);
        }
        for (int i = 0; i < 4; i++) {
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
                cc3[gray[off] & 255]++;
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
                int ee = edges[off] >>> 6;
                hr = histEdge[reg];
                for (int i = Math.max(0, ee - 1); i < 8 && i <= ee + 1; i++) {
                    hr[i] += 2 - Math.abs(i - ee);
                }
            }
        }
        byte[] features = new byte[numFeatures];
        int total = (w - 2) * (h - 2);
        for (int i = 0; i < 4; i++) {
            int[] cci = channelCount[i];
            int sum = 0;
            for (int j = 0; j < 256; j++) {
                if (((sum += cci[j]) << 1) >= total) {
                    features[i] = (byte) (j - 128);
                    break;
                }
            }
        }
        int idx = 4;
        double m = 64 / Math.pow(total * 4, power);
        for (int i = 0; i < hist.length; i++) {
            int[] hi = hist[i];
            for (int j = 0; j < hi.length; j++, idx++) {
                int v = hi[j];
                if (v > 0) features[idx] = range(Math.pow(v, power) * m);
            }
        }
        m = 64 / Math.pow(total, power);
        for (int i = 0; i < histEdge.length; i++) {
            int[] hi = histEdge[i];
            for (int j = 0; j < hi.length; j++, idx++) {
                int v = hi[j];
                if (v > 0) features[idx] = range(Math.pow(v, power) * m);
            }
        }
        return features;
    }

    private static final byte range(double v) {
        int i = (int) Math.round(v);
        if (i < 0) return (byte) 0;
        if (i > 63) return (byte) 63;
        return (byte) i;
    }

    private void getPixels(BufferedImage img) {
        Graphics2D gColor = auxColorImg.createGraphics();
        Graphics2D gGray = auxGrayImg.createGraphics();
        gColor.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gGray.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (w * h > maxPixels) {
            if (w > h) {
                h = h * maxDim / w;
                w = maxDim;
            } else {
                w = w * maxDim / h;
                h = maxDim;
            }
            gColor.drawImage(img, 0, 0, w, h, null);
            gGray.drawImage(img, 0, 0, w, h, null);
        } else {
            gColor.drawImage(img, 0, 0, null);
            gGray.drawImage(img, 0, 0, null);
        }
        gColor.dispose();
        int[] dataColor = ((DataBufferInt) auxColorImg.getRaster().getDataBuffer()).getData();
        byte[] dataGray = ((DataBufferByte) auxGrayImg.getRaster().getDataBuffer()).getData();
        if (w == maxDim) {
            System.arraycopy(dataColor, 0, pixels, 0, w * h);
            System.arraycopy(dataGray, 0, gray, 0, w * h);
        } else {
            int y0 = 0;
            int y1 = 0;
            for (int y = 0; y < h; y++, y0 += maxDim, y1 += w) {
                System.arraycopy(dataColor, y0, pixels, y1, w);
                System.arraycopy(dataGray, y0, gray, y1, w);
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
            if (s0 < trimTolerance && s1 < trimTolerance) detect = 0;
            else if (s0 > 255 - trimTolerance && s1 > 255 - trimTolerance) detect = 1;
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
        if (tw <= 2) return;
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
            if (s0 < trimTolerance && s1 < trimTolerance) detect = 0;
            else if (s0 > 255 - trimTolerance && s1 > 255 - trimTolerance) detect = 1;
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
                    gray[pos] = gray[off];
                    pos++;
                }
            }
            w = tw;
            h = th;
        }
    }

    private void calcEdges() {
        for (int y = 1; y < h - 1; y++) {
            int off = y * w + 1;
            int p1 = gray[off - 1 - w] & 255;
            int p2 = gray[off - 1] & 255;
            int p3 = gray[off - 1 + w] & 255;
            int p4 = gray[off - w] & 255;
            int p5 = gray[off] & 255;
            int p6 = gray[off + w] & 255;
            for (int x = 1; x < w - 1; x++, off++) {
                int p7 = gray[off + 1 - w] & 255;
                int p8 = gray[off + 1] & 255;
                int p9 = gray[off + 1 + w] & 255;
                int vv = p1 + (p4 << 1) + p7 - p3 - (p6 << 1) - p9;
                int hh = p1 + (p2 << 1) + p3 - p7 - (p8 << 1) - p9;
                int vert = Math.min(999, Math.abs(vv));
                int horiz = Math.min(999, Math.abs(hh));
                edges[off] = sqrt[(vert << 10) | horiz];
                p1 = p4;
                p2 = p5;
                p3 = p6;
                p4 = p7;
                p5 = p8;
                p6 = p9;
            }
        }
    }

    public static int distance(byte[] a, byte[] b) {
        int distance = 0;
        for (int i = 4; i < a.length; i++) {
            int d = a[i] - b[i];
            distance += d * d;
        }
        return distance;
    }
}
