package gpinf.die;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RandomForestPredictor {
    private static final double borderSize = 0.15;
    private final int[] roots;
    private final int[] nodeLeft;
    private final short[] splitFeature;
    private final float[] value;
    private int trees;
    private int version;

    private RandomForestPredictor(int trees, int nodes) {
        this.trees = trees;
        roots = new int[trees];
        nodeLeft = new int[nodes];
        splitFeature = new short[nodes];
        value = new float[nodes];
    }

    public int size() {
        return trees;
    }

    public int getVersion() {
        return version;
    }

    public double predict(List<Float> lFeatures) {
        float[] features = toArr(lFeatures);
        double ret = 0;
        List<Double> l = new ArrayList<Double>();
        for (int root : roots) {
            l.add(classify(root, features));
        }
        Collections.sort(l);
        int cnt = 0;
        int border = (int) (l.size() * borderSize);
        for (int i = border; i < l.size() - border; i++) {
            ret += l.get(i);
            cnt++;
        }
        return ret / cnt;
    }

    private static float[] toArr(List<Float> l) {
        float[] arr = new float[l.size()];
        for (int i = 0; i < l.size(); i++) {
            arr[i] = l.get(i);
        }
        return arr;
    }

    private double classify(int pos, float[] features) {
        while (true) {
            int sf = splitFeature[pos];
            if (sf < 0) {
                return value[pos];
            }
            if (features[sf] < value[pos]) {
                pos = nodeLeft[pos];
            } else {
                pos = nodeLeft[pos] + 1;
            }
        }
    }

    public static RandomForestPredictor load(File file, int maxTrees) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        int skip = in.readInt();
        in.skipBytes(skip);
        int ver = in.readInt();
        int trees = in.readInt();
        int[] t = new int[trees];
        for (int i = 0; i < trees; i++) {
            t[i] = in.readInt();
        }
        int nodes = in.readInt();
        if (maxTrees > 0 && trees > maxTrees) {
            trees = maxTrees;
            nodes = t[trees + 1];
        }
        RandomForestPredictor predictor = new RandomForestPredictor(trees, nodes);
        predictor.version = ver;
        System.arraycopy(t, 0, predictor.roots, 0, trees);
        byte[] bytes = new byte[nodes * 10];
        in.read(bytes);

        int pos = 0;
        for (int i = 0; i < nodes; i++) {
            predictor.splitFeature[i] = (short) (((bytes[pos++] & 0xFF) << 8) + ((bytes[pos++] & 0xFF) << 0));
            predictor.nodeLeft[i] = (((bytes[pos++] & 0xFF) << 24) + ((bytes[pos++] & 0xFF) << 16)
                    + ((bytes[pos++] & 0xFF) << 8) + ((bytes[pos++] & 0xFF) << 0));
            predictor.value[i] = Float.intBitsToFloat((((bytes[pos++] & 0xFF) << 24) + ((bytes[pos++] & 0xFF) << 16)
                    + ((bytes[pos++] & 0xFF) << 8) + ((bytes[pos++] & 0xFF) << 0)));
        }
        in.close();
        return predictor;
    }
}
