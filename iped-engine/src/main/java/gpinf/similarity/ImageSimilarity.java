package gpinf.similarity;

import java.awt.*;
import java.awt.image.*;
import java.util.*;

public class ImageSimilarity {
    public static final int numFeatures1 = 1040;
    public static final int numFeatures2 = 72;

    private static final int maxDim = 160;
    private static final int maxPixels = maxDim * maxDim;
    private static final short[] sqrt = new short[1 << 20];
    private static final double power = 0.3;
    private final int[][] hist1 = new int[2][512];
    private final int[][] histEdge1 = new int[2][8];
    private final int[] hist2 = new int[64];
    private final int[] histEdge2 = new int[8];
    private final int[] pixels = new int[maxPixels];
    private final byte[] gray = new byte[maxPixels];
    private final int[] edges = new int[maxPixels];
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

    public byte[][] extractFeatures(BufferedImage img) {
        if (img == null) return null;
        w = img.getWidth();
        h = img.getHeight();
        if (w <= 2 || h <= 2) return null;
        getPixels(img);
        calcEdges();
        for (int i = 0; i < 2; i++) {
            Arrays.fill(hist1[i], 0);
            Arrays.fill(histEdge1[i], 0);
        }
        Arrays.fill(hist2, 0);
        Arrays.fill(histEdge2, 0);
        int cut = w * h * 63 / 64;
        for (int y = 1; y < h - 1; y++) {
            int off = y * w + 1;
            int dy = Math.abs((y << 1) - (h - 1));
            for (int x = 1; x < w - 1; x++, off++) {
                int dx = Math.abs((x << 1) - (w - 1));
                int reg = dx * h + dy * w < cut ? 0 : 1;
                int color = pixels[off] >>> 5;
                int bb = (color >>> 16) & 7;
                int gg = (color >>> 8) & 7;
                int rr = color & 7;
                int[] hr1 = hist1[reg];
                for (int i = Math.max(0, rr - 1); i < 8 && i <= rr + 1; i++) {
                    int wi = i == rr ? 1 : 0;
                    int ii = i << 6;
                    for (int j = Math.max(0, gg - 1); j < 8 && j <= gg + 1; j++) {
                        int wij = j == gg ? wi + 1 : wi;
                        int ij = ii | (j << 3);
                        for (int k = Math.max(0, bb - 1); k < 8 && k <= bb + 1; k++) {
                            hr1[ij | k] += wij << (k == bb ? wij + 1 : wij);
                        }
                    }
                }
                rr >>= 1;
                gg >>= 1;
                bb >>= 1;
                for (int i = Math.max(0, rr - 1); i < 4 && i <= rr + 1; i++) {
                    int wi = i == rr ? 1 : 0;
                    int ii = i << 4;
                    for (int j = Math.max(0, gg - 1); j < 4 && j <= gg + 1; j++) {
                        int wij = j == gg ? wi + 1 : wi;
                        int ij = ii | (j << 2);
                        for (int k = Math.max(0, bb - 1); k < 4 && k <= bb + 1; k++) {
                            hist2[ij | k] += wij << (k == bb ? wij + 1 : wij);
                        }
                    }
                }
                int ee = edges[off] >>> 6;
                hr1 = histEdge1[reg];
                for (int i = Math.max(0, ee - 1); i < 8 && i < ee; i++) {
                    int wi = 2 - Math.abs(i - ee);
                    hr1[i] += wi;
                    histEdge2[i] += wi;
                }
            }
        }
        int idx1 = 0;
        int idx2 = 0;
        double m = 64 / Math.pow((w - 2) * (h - 2) * 4, power);
        byte[] features1 = new byte[numFeatures1];
        byte[] features2 = new byte[numFeatures2];
        for (int i = 0; i < hist1.length; i++) {
            int[] hi = hist1[i];
            for (int j = 0; j < hi.length; j++, idx1++) {
                int v = hi[j];
                if (v > 0) features1[idx1] = range(Math.pow(v, power) * m);
            }
        }
        for (int j = 0; j < hist2.length; j++, idx2++) {
            int v = hist2[j];
            if (v > 0) features2[idx2] = range(Math.pow(v, power) * m);
        }
        m = 96 / Math.pow((w - 2) * (h - 2), power);
        for (int i = 0; i < histEdge1.length; i++) {
            int[] hi = histEdge1[i];
            for (int j = 0; j < hi.length; j++, idx1++) {
                int v = hi[j];
                if (v > 0) features1[idx1] = range(Math.pow(v, power) * m);
            }
        }
        for (int i = 0; i < histEdge2.length; i++, idx2++) {
            int v = histEdge2[i];
            if (v > 0) features1[idx2] = range(Math.pow(v, power) * m);
        }
        return new byte[][] {features1,features2};
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
        int size = w * h;
        if (size > maxPixels) {
            if (w > h) {
                h = h * maxDim / w;
                w = maxDim;
            } else {
                w = w * maxDim / h;
                h = maxDim;
            }
            gColor.drawImage(img, 0, 0, w, h, null);
            gGray.drawImage(img, 0, 0, w, h, null);
            size = w * h;
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
        for (int i = 0; i < a.length; i++) {
            int d = (a[i] & 0xFF) - (b[i] & 0xFF);
            distance += d * d;
        }
        return distance;
    }
}