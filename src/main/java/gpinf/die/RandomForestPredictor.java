package gpinf.die;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

public class RandomForestPredictor {
    private final int[] roots;
    private final int[] nodeLeft;
    private final short[] splitFeature;
    private final float[] value;
    private int trees, free;
    private static final int version = 6;

    public RandomForestPredictor(int maxTrees) {
        roots = new int[maxTrees];
        int maxNodes = maxTrees * 32768;
        nodeLeft = new int[maxNodes];
        splitFeature = new short[maxNodes];
        value = new float[maxNodes];
    }

    private RandomForestPredictor(int trees, int nodes) {
        this.trees = trees;
        this.free = nodes;
        roots = new int[trees];
        nodeLeft = new int[nodes];
        splitFeature = new short[nodes];
        value = new float[nodes];
    }

    public synchronized void add(ClassificationNode root) {
        int rt = roots[trees++] = free;
        free++;
        expand(root, rt);
    }

    public int size() {
        return trees;
    }

    private void expand(ClassificationNode node, int pos) {
        if (node.left == null) {
            nodeLeft[pos] = -1;
            splitFeature[pos] = -1;
            value[pos] = node.getValue();
        } else {
            int l = nodeLeft[pos] = free;
            free += 2;
            splitFeature[pos] = (short) node.splitFeature;
            value[pos] = node.splitVal;
            expand(node.left, l);
            expand(node.right, l + 1);
        }
    }

    public double predict(List<Float> lFeatures) {
        float[] features = toArr(lFeatures);
        double ret = 0;
        for (int root : roots) {
            ret += classify(root, features);
        }
        return ret / trees;
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
            if (sf < 0) return value[pos];
            if (features[sf] < value[pos]) pos = nodeLeft[pos];
            else pos = nodeLeft[pos] + 1;
        }
    }

    public void save(File file) throws Exception {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        out.writeInt(version);
        out.writeInt(trees);
        for (int i = 0; i < trees; i++) {
            out.writeInt(roots[i]);
        }
        out.writeInt(free);
        for (int i = 0; i < free; i++) {
            out.writeShort(splitFeature[i]);
            out.writeInt(nodeLeft[i]);
            out.writeFloat(value[i]);
        }
        out.close();
    }

    public static RandomForestPredictor load(File file, int maxTrees) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        int ver = in.readInt();
        if (ver != version) {
            in.close();
            throw new Exception("Versão incorreta do arquivo de dados (" + file.getAbsolutePath() + ").");
        }
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
        System.arraycopy(t, 0, predictor.roots, 0, trees);
        byte[] bytes = new byte[nodes * 10];
        in.read(bytes);

        int pos = 0;
        for (int i = 0; i < nodes; i++) {
            predictor.splitFeature[i] = (short) (((bytes[pos++] & 0xFF) << 8) + ((bytes[pos++] & 0xFF) << 0));
            predictor.nodeLeft[i] = (((bytes[pos++] & 0xFF) << 24) + ((bytes[pos++] & 0xFF) << 16) + ((bytes[pos++] & 0xFF) << 8) + ((bytes[pos++] & 0xFF) << 0));
            predictor.value[i] = Float.intBitsToFloat((((bytes[pos++] & 0xFF) << 24) + ((bytes[pos++] & 0xFF) << 16) + ((bytes[pos++] & 0xFF) << 8) + ((bytes[pos++] & 0xFF) << 0)));
        }
        in.close();
        return predictor;
    }
}