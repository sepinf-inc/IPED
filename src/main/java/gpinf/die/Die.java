package gpinf.die;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class Die {
    public static final int size = 160;
    private static final float INF = -1e10f;
    private static final int numStripes1 = 3, numStripes2 = 5, grid1 = 6, grid2 = 9, grid3 = 3, grid4 = 5, grid5 = 5;

    public static List<Float> extractFeatures(BufferedImage img) {
        if (img == null) return null;
        ArrayList<Float> features = new ArrayList<Float>(8192);
        SortedMap<Integer, List<Float>> idxFeatures = new TreeMap<Integer, List<Float>>();
        int width = img.getWidth();
        int height = img.getHeight();
        features.add((float) (width == 0 || height == 0 ? 0 : width >= height ? 4 * width / height : -4 * height / width));
        img = resizeImage(img, size, size);

        int[][] channels = getChannels(img);
        int[] hue = channels[0];
        int[] saturation = channels[1];
        int[] lightness = channels[2];
        int[] gray = channels[3];

        addAll(features, null, -1, rectStatFeatures(hue, 0, 0, size, size, size));
        addAll(features, null, -1, rectStatFeatures(saturation, 0, 0, size, size, size));
        addAll(features, null, -1, rectStatFeatures(lightness, 0, 0, size, size, size));
        features.addAll(rectBinCount(hue, 0, 0, size, size, size));
        for (int step = 1; step <= 2; step++) {
            int numStripes = step == 1 ? numStripes1 : numStripes2;
            for (int i = 0; i < numStripes; i++) {
                addAll(features, idxFeatures, step + 0, rectStatFeatures(hue, 0, size * i / numStripes, size, size / numStripes, size));
                addAll(features, idxFeatures, step + 2, rectStatFeatures(hue, size * i / numStripes, 0, size / numStripes, size, size));
                addAll(features, idxFeatures, step + 4, rectStatFeatures(saturation, 0, size * i / numStripes, size, size / numStripes, size));
                addAll(features, idxFeatures, step + 6, rectStatFeatures(saturation, size * i / numStripes, 0, size / numStripes, size, size));
                addAll(features, idxFeatures, step + 8, rectStatFeatures(lightness, 0, size * i / numStripes, size, size / numStripes, size));
                addAll(features, idxFeatures, step + 10, rectStatFeatures(lightness, size * i / numStripes, 0, size / numStripes, size, size));
            }
            int grid = step == 1 ? grid1 : grid2;
            for (int i = 0; i < grid; i++) {
                for (int j = 0; j < grid; j++) {
                    addAll(features, idxFeatures, step + 12, rectStatFeatures(hue, size * i / grid, size * j / grid, size / grid, size / grid, size));
                    addAll(features, idxFeatures, step + 14, rectStatFeatures(lightness, size * i / grid, size * j / grid, size / grid, size / grid, size));
                    addAll(features, idxFeatures, step + 16, rectStatFeatures(saturation, size * i / grid, size * j / grid, size / grid, size / grid, size));
                }

                if (i > 0 && i < grid - 1) {
                    int dx = size * i / grid / 2;
                    int dy = size * i / grid / 2;
                    addAll(features, idxFeatures, step + 18, rectStatFeatures(hue, dx, dy, size - 2 * dx, size - 2 * dy, size));
                    addAll(features, idxFeatures, step + 20, rectStatFeatures(lightness, dx, dy, size - 2 * dx, size - 2 * dy, size));
                    addAll(features, idxFeatures, step + 22, rectStatFeatures(saturation, dx, dy, size - 2 * dx, size - 2 * dy, size));
                }
            }
            grid = step == 1 ? grid3 : grid4;
            for (int i = 0; i < grid; i++) {
                for (int j = 0; j < grid; j++) {
                    features.addAll(rectBinCount(hue, size * i / grid, size * j / grid, size / grid, size / grid, size));
                }
            }
            int m = size / grid;
            int[] dif1 = new int[m * size];
            int[] dif2 = new int[m * size];
            Arrays.fill(dif1, -1);
            Arrays.fill(dif2, -1);
            int center = grid / 2 * m;
            int pos = 0;
            for (int y = 0; y < size; y++) {
                int off = y * size;
                for (int x = 0; x < m; x++, off++, pos++) {
                    int h0 = hue[off + center];
                    if (h0 < 0) continue;
                    int h1 = hue[off + center + m];
                    int h2 = hue[off + center - m];
                    if (h1 >= 0) dif1[pos] = hDist(h0, h1);
                    if (h2 >= 0) dif2[pos] = hDist(h0, h2);
                }
            }
            features.addAll(rectStatFeatures(dif1, 0, 0, dif1.length, 1, dif1.length));
            features.addAll(rectStatFeatures(dif2, 0, 0, dif2.length, 1, dif2.length));
        }
        for (int idx : idxFeatures.keySet()) {
            features.addAll(stats(idxFeatures.get(idx)));
        }

        features.addAll(rectPosBin(hue, 0, 0, size, size, size));
        features.addAll(rectPosBin(saturation, 0, 0, size, size, size));
        for (int i = 0; i < grid5; i++) {
            for (int j = 0; j < grid5; j++) {
                features.addAll(rectPosBin(hue, size * i / grid5, size * j / grid5, size / grid5, size / grid5, size));
                features.addAll(rectPosBin(saturation, size * i / grid5, size * j / grid5, size / grid5, size / grid5, size));
            }
        }

        int[] eh = new int[size * size];
        int[] ev = new int[size * size];
        int[] edge = new int[size * size];
        Arrays.fill(eh, -1);
        Arrays.fill(ev, -1);
        Arrays.fill(edge, -1);
        for (int y = 1; y < size - 1; y++) {
            int off = y * size + 1;
            for (int x = 1; x < size - 1; x++, off++) {
                /* p1 p4 p6
                 * p2    p7
                 * p3 p5 p8 */
                int p1 = gray[off - 1 - size];
                int p2 = gray[off - 1];
                int p3 = gray[off - 1 + size];
                int p4 = gray[off - size];
                int p5 = gray[off + size];
                int p6 = gray[off + 1 - size];
                int p7 = gray[off + 1];
                int p8 = gray[off + 1 + size];
                if (p1 < 0 || p3 < 0 || p6 < 0 || p8 < 0) continue;
                int vert = ev[off] = Math.min(999, Math.abs(p1 + 2 * p5 + p6 - p3 - 2 * p4 - p8));
                int horiz = eh[off] = Math.min(999, Math.abs(p1 + 2 * p2 + p3 - p6 - 2 * p7 - p8));
                edge[off] = (int) Math.sqrt((vert * vert + horiz * horiz) / 2);
            }
        }

        int grid = grid5;
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                features.addAll(rectStatFeatures(ev, size * i / grid, size * j / grid, size / grid, size / grid, size));
                features.addAll(rectStatFeatures(eh, size * i / grid, size * j / grid, size / grid, size / grid, size));
                features.addAll(rectStatFeatures(edge, size * i / grid, size * j / grid, size / grid, size / grid, size));
            }
        }
        /*
                BufferedImage v = new BufferedImage(size, size, BufferedImage.TYPE_3BYTE_BGR);
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        int c = edge[i * size + j];
                        if (c < 0) {
                            v.setRGB(j, i, 256 * 128);
                        } else {
                            if (c > 255) c = 255;
                            v.setRGB(j, i, c * (256 * 256 + 256 + 1));
                        }
                    }
                }
                new ImgViewer(v, img);
         */
        return features;
    }

    private static void addAll(List<Float> features, Map<Integer, List<Float>> idxFeatures, int idx, List<Float> vals) {
        features.addAll(vals);
        if (idxFeatures != null) {
            for (int i = 0; i < vals.size(); i++) {
                int pos = vals.size() * idx + i;
                List<Float> l = idxFeatures.get(pos);
                if (l == null) idxFeatures.put(pos, l = new ArrayList<Float>());
                l.add(vals.get(i));
            }
        }
    }

    private static List<Float> stats(List<Float> a) {
        List<Float> l = new ArrayList<Float>(2);
        double sum = 0;
        double sumSquares = 0;
        int cnt = a.size();
        for (float p : a) {
            sum += p;
            sumSquares += p * p;
        }
        l.add((float) ((sumSquares - sum * sum / cnt) / cnt));
        l.add((float) (sum / cnt));
        return l;
    }

    private static List<Float> rectStatFeatures(int[] a, int rx, int ry, int rw, int rh, int width) {
        List<Float> l = new ArrayList<Float>(7);
        int binSize = 25;
        int[] bc = new int[1000 / binSize];
        double sum = 0;
        double sumSquares = 0;
        double sumCubes = 0;
        int cnt = 0;
        double sx = 0;
        double sy = 0;
        for (int y = ry; y < ry + rh; y++) {
            int off = y * width + rx;
            int px = a[off];
            for (int x = 0; x < rw; x++, off++) {
                int p = a[off];
                if (p < 0) continue;
                sx += Math.abs(p - px);
                px = p;
                if (y > ry) {
                    int pa = a[off - width];
                    if (pa >= 0) sy += Math.abs(p - pa);
                }
                bc[p / binSize]++;
                sum += p;
                int pp = p * p;
                sumSquares += pp;
                sumCubes += pp * p;
                cnt++;
            }
        }
        if (cnt == 0) {
            for (int i = 0; i < 7; i++) {
                l.add(INF);
            }
        } else {
            double k3 = (sumCubes - 3 * sumSquares * sum / cnt + 2 * sum * sum * sum / cnt / cnt) / cnt;
            double k2 = (sumSquares - sum * sum / cnt) / cnt;
            int max = 0;
            double mode = 0;
            for (int i = 0; i < bc.length; i++) {
                if (bc[i] > max) {
                    max = bc[i];
                    mode = i;
                }
            }
            l.add((float) (sum / cnt));
            l.add((float) (k2));
            l.add((float) (k3 / Math.pow(k2, 1.5)));
            l.add((float) mode);
            l.add((float) (max / cnt));
            l.add((float) (sx / cnt));
            l.add((float) (sy / cnt));
        }
        return l;
    }

    private static List<Float> rectBinCount(int[] a, int rx, int ry, int rw, int rh, int width) {
        int binSize = 33;
        int[] bc = new int[(1000 + binSize - 1) / binSize];
        List<Float> l = new ArrayList<Float>(bc.length);
        int cnt = 0;
        for (int y = ry; y < ry + rh; y++) {
            int off = y * width + rx;
            for (int x = 0; x < rw; x++, off++) {
                int p = a[off];
                if (p < 0) continue;
                bc[p / binSize]++;
                cnt++;
            }
        }
        if (cnt == 0) {
            for (int i = 0; i < bc.length; i++) {
                l.add(INF);
            }
        } else {
            for (int i = 0; i < bc.length; i++) {
                l.add(bc[i] / (float) cnt);
            }
        }
        return l;
    }

    private static List<Float> rectPosBin(int[] a, int rx, int ry, int rw, int rh, int width) {
        int binSize = 50;
        int[] xc = new int[(1000 + binSize - 1) / binSize];
        int[] yc = new int[(1000 + binSize - 1) / binSize];
        int[] bc = new int[(1000 + binSize - 1) / binSize];
        List<Float> l = new ArrayList<Float>(xc.length * 2);
        for (int y = ry; y < ry + rh; y++) {
            int off = y * width + rx;
            for (int x = 0; x < rw; x++, off++) {
                int p = a[off];
                if (p < 0) continue;
                int bin = p / binSize;
                bc[bin]++;
                xc[bin] += x + rx;
                yc[bin] += y + ry;
            }
        }
        for (int i = 0; i < bc.length; i++) {
            float cnt = bc[i];
            l.add(cnt == 0 ? INF : xc[i] / cnt);
            l.add(cnt == 0 ? INF : yc[i] / cnt);
        }
        return l;
    }

    private static int[][] getChannels(BufferedImage img) {
        int[] rgb = new int[img.getWidth() * img.getHeight()];
        int[] h = new int[rgb.length];
        int[] s = new int[rgb.length];
        int[] l = new int[rgb.length];
        int[] gray = new int[rgb.length];
        Arrays.fill(h, -1);
        Arrays.fill(s, -1);
        Arrays.fill(l, -1);
        Arrays.fill(gray, -1);

        img.getRGB(0, 0, img.getWidth(), img.getHeight(), rgb, 0, img.getWidth());
        float[] hsl = new float[3];
        for (int offset = 0; offset < rgb.length; offset++) {
            int p = rgb[offset];
            int aa = a(p);
            if (aa == 0) continue;
            int rr = r(p);
            int gg = g(p);
            int bb = b(p);
            gray[offset] = (rr * 299 + gg * 587 + bb * 114) / 1000;
            Color.RGBtoHSB(rr, gg, bb, hsl);
            h[offset] = (((int) Math.round(hsl[0] * 999)) + 500) % 1000;
            s[offset] = (int) Math.round(hsl[1] * 999);
            l[offset] = (int) Math.round(hsl[2] * 999);
        }
        return new int[][] {h,s,l,gray};
    }

    private static BufferedImage resizeImage(BufferedImage img, int width, int height) {
        final BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, width, height);

        int w = 0;
        int h = 0;
        if (img.getWidth() * (long) height > width * (long) img.getHeight()) {
            h = height * img.getHeight() / img.getWidth();
            w = width;
        } else {
            w = width * img.getWidth() / img.getHeight();
            h = height;
        }

        g2.setComposite(AlphaComposite.Src);
        g2.drawImage(img, (width - w) / 2, (height - h) / 2, w, h, null);
        g2.dispose();
        return resized;
    }

    private static final int hDist(int h1, int h2) {
        if (h2 > h1) return hDist(h2, h1);
        return Math.min(h1 - h2, 1000 + h2 - h1);
    }

    private static final int a(int rgb) {
        return (rgb >>> 24) & 255;
    }

    private static final int r(int rgb) {
        return (rgb >>> 16) & 255;
    }

    private static final int g(int rgb) {
        return (rgb >>> 8) & 255;
    }

    private static final int b(int rgb) {
        return rgb & 255;
    }
}