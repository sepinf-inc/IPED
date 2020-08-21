package dpf.sp.gpinf.indexer.process.task;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.nd4j.linalg.api.ndarray.INDArray;

import dpf.sp.gpinf.indexer.Configuration;
import iped3.IItem;

public class NSFWNudityDetector extends AbstractTask {

    private static final String MODEL_PATH = "models/nsfw-keras-1.0.0.h5";

    private static final int WIDTH = 224;
    private static final int HEIGHT = 224;

    private static ComputationGraph model;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        if (model != null)
            return;

        try (InputStream is = Files.newInputStream(Paths.get(Configuration.getInstance().appRoot, MODEL_PATH))) {
            model = KerasModelImport.importKerasModelAndWeights(is, false);
        }

    }

    @Override
    public void finish() throws Exception {
        if (model != null) {
            model.close();
            model = null;
        }
    }

    public INDArray centerPixelMean(INDArray input) {
        return input.sub(127.0);
    }

    @Override
    protected void process(IItem item) throws Exception {

        if (item.getThumb() == null)
            return;

        byte[] thumb = item.getThumb();
        BufferedImage bimg = ImageIO.read(new ByteArrayInputStream(thumb));

        if (bimg == null)
            return;

        NativeImageLoader loader = new NativeImageLoader(WIDTH, HEIGHT, 3);

        INDArray input = centerPixelMean(loader.asMatrix(new ByteArrayInputStream(thumb), false));
        // input = Nd4j.stack(0, input);

        INDArray output = model.outputSingle(input);
        // model.fit(input, output);

        item.setExtraAttribute("nsfw_nudityScore", output.getDouble(1));

    }

}
