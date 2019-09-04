package dpf.sp.gpinf.indexer.process.task;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.dpf.sepinf.photodna.PhotoDNA;
import gpinf.dev.data.EvidenceFile;

public class PhotoDNATask extends AbstractTask{
    
    private Logger LOGGER = LoggerFactory.getLogger(PhotoDNATask.class);
    
    public static final int HASH_SIZE = 144;
    
    public static final String PHOTO_DNA = "photoDNA";
    
    private PhotoDNA photodna;
    
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        PhotoDNA.setLibPath(new File("E:\\3.3 master_PhotoDNAwithVideo\\PhotoDNA"));
        photodna = new PhotoDNA();
    }

    @Override
    public void finish() throws Exception {
        
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        
        if(!evidence.getMediaType().getType().equals("image"))
            return;
        
        try (InputStream is = evidence.getBufferedStream()){
        //try (InputStream is = new ByteArrayInputStream(evidence.getThumb())){
            
            BufferedImage img = ImageIO.read(is);
            
            byte[] data;
            if (img.getType() == BufferedImage.TYPE_3BYTE_BGR && img.getRaster() != null && img.getRaster().getDataBuffer() instanceof DataBufferByte) {
                data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            } else {
                //System.out.println("alternate");
                BufferedImage bgrImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                Graphics g = bgrImg.getGraphics();  
                g.drawImage(img, 0, 0, null);  
                g.dispose();  
                data = ((DataBufferByte) bgrImg.getRaster().getDataBuffer()).getData();
            }
            
            byte[] hash = new byte[HASH_SIZE];
            
            photodna.reset();
            int ret = photodna.ComputeHash(data, img.getWidth(), img.getHeight(), 0, hash);
            
            if(ret == 0) {
                String hashStr = new String(Hex.encodeHex(hash, false));
                evidence.setExtraAttribute(PHOTO_DNA, hashStr);
            }else
                throw new Exception("photodna returned error " + ret);
            
        }catch(Throwable e) {
            //e.printStackTrace();
            LOGGER.info("Error computing photoDNA for " + evidence.getPath() + ": " + e.toString());
            evidence.setExtraAttribute("photodna_exception", e.toString());
            return;
        }
        
    }
    
}
