package dpf.sp.gpinf.indexer.process.task.photodna;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Hex;

public class PhotoDNATest {
    
    private static final int HASH_SIZE = 144;

    public static void main(String[] args) {
        
        File file = new File("F:/teste-files/photo-dna/test-file.JPG");
        
        PhotoDNA.setLibPath(new File("E:\\3.3 master_PhotoDNAwithVideo\\PhotoDNA"));
        PhotoDNA photodna = new PhotoDNA();
        
        for(int i = 0; i < 1000; i++) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                InputStream is = new ByteArrayInputStream(bytes);
                
                BufferedImage img = ImageIO.read(is);
                
                if(img.getRaster() == null)
                    throw new IOException("No raster for image");
                
                byte[] data;
                if (img.getType() == BufferedImage.TYPE_3BYTE_BGR) {
                    // get DataBufferBytes from Raster
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
                    System.out.println(hashStr);
                }else
                    System.out.println("photodna returned error " + ret);
                
            }catch(Throwable e) {
                e.printStackTrace();
            }
        }

    }

}
