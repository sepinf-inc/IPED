package iped.engine.hashdb;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

public class PhotoDnaTree implements Serializable {
    private static final long serialVersionUID = -7030355598905120949L;
    private static final int maxItemsLeaf = 32;
    private transient int len;
    private transient SplittableRandom rnd;
    private transient PhotoDnaItem[] items, centerItems;
    private int[] rays, starts, ends, childs, hashId, centers;
    private byte[][] photoDnaArr;

    public PhotoDnaTree(PhotoDnaItem[] items) {
        this.items = items;
        int maxLen = (items.length / maxItemsLeaf + 1) * 4;
        rnd = new SplittableRandom(999);
        centerItems = new PhotoDnaItem[maxLen];
        rays = new int[maxLen];
        starts = new int[maxLen];
        ends = new int[maxLen];
        childs = new int[maxLen];
        Arrays.fill(starts, -1);

        split(0, items.length, len++);

        rays = Arrays.copyOf(rays, len);
        starts = Arrays.copyOf(starts, len);
        ends = Arrays.copyOf(ends, len);
        childs = Arrays.copyOf(childs, len);
        photoDnaArr = new byte[items[0].getBytes().length][items.length];
        hashId = new int[items.length];
        Map<Integer, Integer> posToId = new HashMap<Integer, Integer>();
        for (int i = 0; i < items.length; i++) {
            PhotoDnaItem item = items[i];
            int id = hashId[i] = item.getHashId();
            posToId.put(id, i);
            byte[] b = item.getBytes();
            for (int j = 0; j < b.length; j++) {
                photoDnaArr[j][i] = b[j];
            }
        }
        centers = new int[len];
        for (int i = 0; i < len; i++) {
            PhotoDnaItem item = centerItems[i];
            if (item != null) {
                centers[i] = posToId.get(item.getHashId());
            }
        }
        this.items = null;
        centerItems = null;
        rnd = null;
    }

    private void split(int from, int to, int idx) {
        if (to - from > maxItemsLeaf) {
            PhotoDnaItem center = items[rnd.nextInt(from, to)];
            for (int i = from; i < to; i++) {
                PhotoDnaItem item = items[i];
                item.setRefSqDist(item.sqDistance(center));
            }
            Arrays.parallelSort(items, from, to, new Comparator<PhotoDnaItem>() {
                public int compare(PhotoDnaItem a, PhotoDnaItem b) {
                    return Integer.compare(a.getRefSqDist(), b.getRefSqDist());
                }
            });
            int n = (to - from) / 2;
            int ray = items[from + n - 1].getRefSqDist();
            while (from + n < to && ray == items[from + n].getRefSqDist()) {
                n++;
            }
            if (n == to - from) {
                starts[idx] = from;
                ends[idx] = to;
            } else {
                centerItems[idx] = center;
                rays[idx] = ray;
                int c = childs[idx] = len;
                len += 2;
                split(from, from + n, c);
                split(from + n, to, c + 1);
            }
        } else {
            starts[idx] = from;
            ends[idx] = to;
        }
    }

    public PhotoDnaHit search(byte[] refBytes, int maxSqDist) {
        int[] ref = new int[refBytes.length];
        for (int i = 0; i < ref.length; i++) {
            ref[i] = refBytes[i] & 0xFF;
        }
        PhotoDnaHit hit = new PhotoDnaHit();
        hit.sqDist = maxSqDist;
        search(ref, hit, 0);
        return hit.nearest == null ? null : hit;
    }

    private void search(int[] ref, PhotoDnaHit hit, int node) {
        int start = starts[node];
        if (start == -1) {
            int center = centers[node];
            int ray = rays[node];
            int sqDist = 0;
            for (int j = 0; j < ref.length; j++) {
                int d = ref[j] - (photoDnaArr[j][center] & 0xFF);
                sqDist += d * d;
            }
            int c = childs[node];
            if (sqDist <= ray) {
                search(ref, hit, c);
                if (sqDist + hit.sqDist > ray) {
                    search(ref, hit, c + 1);
                }
            } else {
                search(ref, hit, c + 1);
                if (sqDist - hit.sqDist <= ray) {
                    search(ref, hit, c);
                }
            }
        } else {
            int end = ends[node];
            NEXT: for (int i = start; i < end; i++) {
                int sqDist = 0;
                for (int j = 0; j < ref.length; j++) {
                    int d = ref[j] - (photoDnaArr[j][i] & 0xFF);
                    sqDist += d * d;
                    if (sqDist >= hit.sqDist)
                        continue NEXT;
                }
                hit.sqDist = sqDist;
                byte[] bytes = new byte[ref.length];
                for (int j = 0; j < bytes.length; j++) {
                    bytes[j] = photoDnaArr[j][i];
                }
                hit.nearest = new PhotoDnaItem(hashId[i], bytes);
            }
        }
    }
}
