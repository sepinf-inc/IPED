package dpf.sp.gpinf.indexer.process.task;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.dpf.sepinf.photodna.PhotoDNA;
import iped3.IItem;

public class PhotoDNATask extends AbstractTask{
    
    private Logger LOGGER = LoggerFactory.getLogger(PhotoDNATask.class);
    
    public static final int HASH_SIZE = 144;
    
    public static final String PHOTO_DNA = "photoDNA";
    
    private static AtomicBoolean warned = new AtomicBoolean();
    
    private PhotoDNA photodna;
    
    private boolean inited = false;
    
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        try {
            photodna = new PhotoDNA();
            inited = true;
            
        }catch(NoClassDefFoundError e) {
            if(!warned.getAndSet(true))
                LOGGER.error("Optional photoDNA lib not loaded. If you have rights to use it, you should put it into plugin/optional_jars folder.");
        }
    }
    
    @Override
    public boolean isEnabled() {
        return inited;
    }
    
    @Override
    public void finish() throws Exception {
        
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        
        if(!inited || evidence.getThumb() == null || !evidence.getMediaType().getType().equals("image"))
            return;
                
        byte[] thumbHash;
        try (InputStream is = new ByteArrayInputStream(evidence.getThumb())){
            
            thumbHash = computePhotoDNA(is);
            String hashStr = new String(Hex.encodeHex(thumbHash, false));
            evidence.setExtraAttribute(PHOTO_DNA, hashStr);
            
        }catch(Throwable e) {
            //e.printStackTrace();
            LOGGER.info("Error computing photoDNA for " + evidence.getPath() + ": " + e.toString());
            evidence.setExtraAttribute("photodna_exception", e.toString());
            return;
        }
        /*
        byte[] fileHash;
        try (InputStream is = evidence.getBufferedStream()){
            
            fileHash = computePhotoDNA(is);
            //String hashStr = new String(Hex.encodeHex(fileHash, false));
            //evidence.setExtraAttribute(PHOTO_DNA, hashStr);
            
        }catch(Throwable e) {
            //e.printStackTrace();
            LOGGER.info("Error computing photoDNA for " + evidence.getPath() + ": " + e.toString());
            evidence.setExtraAttribute("photodna_exception", e.toString());
            return;
        }
        
        int distance = new br.dpf.sepinf.photodna.PhotoDNAComparator().compare(thumbHash, fileHash);
        evidence.setExtraAttribute("photodna_diff", distance);
        */
    }
    
    private byte[] computePhotoDNA(InputStream is) throws Exception {
        BufferedImage img = ImageIO.read(is);
        
        byte[] data;
        if (img.getType() == BufferedImage.TYPE_3BYTE_BGR && img.getRaster() != null && img.getRaster().getDataBuffer() instanceof DataBufferByte) {
            data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        } else {
            BufferedImage bgrImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics g = bgrImg.getGraphics();  
            g.drawImage(img, 0, 0, null);  
            g.dispose();  
            data = ((DataBufferByte) bgrImg.getRaster().getDataBuffer()).getData();
        }
        
        byte[] hash = new byte[HASH_SIZE];
        
        photodna.reset();
        int ret = photodna.ComputeHash(data, img.getWidth(), img.getHeight(), 0, hash);
        
        if(ret == 0)
            return hash;
        else
            throw new Exception("photodna returned error " + ret);
    }
    
}
