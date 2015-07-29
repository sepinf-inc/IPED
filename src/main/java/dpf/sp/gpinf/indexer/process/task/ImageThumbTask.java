package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.search.GalleryValue;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.Util;

public class ImageThumbTask extends AbstractTask{
    
    public ImageThumbTask(Worker worker) {
		super(worker);
		// TODO Auto-generated constructor stub
	}

	private static String thumbsFolder = "thumbs";
    private static String hasThumbProp = "hasThumb";
    private static int thumbSize = 112;
    
    private boolean taskEnabled = false;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        taskEnabled = Boolean.valueOf(confParams.getProperty("enableImageThumbs"));
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        
        if(!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase() || evidence.getHash() == null)
            return;
        
        File thumbFile = Util.getFileFromHash(new File(output, thumbsFolder), evidence.getHash(), "jpg");
        if (!thumbFile.getParentFile().exists())
            thumbFile.getParentFile().mkdirs();

        //Já está pasta? Então não é necessário gerar.
        if (thumbFile.exists()){
            evidence.setExtraAttribute(hasThumbProp, true);
            return;
        }
        
        createImageThumb(evidence, thumbFile);
        
    }
    
    /**
     * Verifica se é imagem.
     */
    public static boolean isImageType(MediaType mediaType) {
        return  mediaType.getType().equals("image") || 
                mediaType.toString().endsWith("msmetafile") || 
                mediaType.toString().endsWith("x-emf");
    }
    
    private void createImageThumb(EvidenceFile evidence, File thumbFile) {

        try {
            GalleryValue value = new GalleryValue(null, null, -1);
            BufferedImage img = null;
            if (evidence.getMediaType().getSubtype().startsWith("jpeg")) {
                BufferedInputStream stream = evidence.getBufferedStream();
                try{
                    img = ImageUtil.getThumb(stream, value);
                }finally{
                    IOUtil.closeQuietly(stream);
                }
            }
            if (img == null) {
                final int sampleFactor = 3;
                BufferedInputStream stream = evidence.getBufferedStream();
                try{
                    img = ImageUtil.getSubSampledImage(stream, thumbSize * sampleFactor, thumbSize * sampleFactor, value);
                }finally{
                    IOUtil.closeQuietly(stream);
                }
                if (img == null) {
                    stream = evidence.getBufferedStream();
                    try{
                        img = new GraphicsMagicConverter().getImage(stream, thumbSize * sampleFactor);
                    }finally{
                        IOUtil.closeQuietly(stream);
                    }
                }
            }
            if (img != null) {
                if (img.getWidth() > thumbSize || img.getHeight() > thumbSize) {
                    img = resizeThumb(img);
                }
                img = ImageUtil.getCenteredImage(img, thumbSize, thumbSize);
                ImageIO.write(img, "jpeg", thumbFile);
                evidence.setExtraAttribute(hasThumbProp, true);
                
            }else
                evidence.setExtraAttribute(hasThumbProp, false);
                
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private BufferedImage resizeThumb(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        if (width > height) {
            height = height * thumbSize / width;
            width = thumbSize;
        } else {
            width = width * thumbSize / height;
            height = thumbSize;
        }
        return ImageUtil.resizeImage(img, width, height);
    }

}
