package gpinf.led;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;

/* 
 * @author Wladimir Leite (GPINF/SP)
 */
public class ImageUtil {
	public static BufferedImage read(File imageFile) throws IOException {
		BufferedImage img = null;
		try {
			ImageInputStream imageStream = ImageIO.createImageInputStream(imageFile);
			Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);

			ImageReader reader = null;
			if (readers.hasNext()) {
				reader = readers.next();

				reader.setInput(imageStream, true, true);

				int w = reader.getWidth(0);
				int h = reader.getHeight(0);

				if (w * (long) h < 40 << 20) {
					// Evita imagens muito grandes
					try {
						img = reader.read(0);
					} catch (IllegalArgumentException ex2) {
						// Tenta contornar erro
						// "Numbers of source Raster bands and source color space components do not match"
						try {
							ImageReadParam param = reader.getDefaultReadParam();
							Iterator<ImageTypeSpecifier> imageTypes = reader.getImageTypes(0);
							while (imageTypes.hasNext()) {
								ImageTypeSpecifier imageTypeSpecifier = imageTypes.next();
								int bufferedImageType = imageTypeSpecifier.getBufferedImageType();
								if (bufferedImageType == BufferedImage.TYPE_BYTE_GRAY) {
									param.setDestinationType(imageTypeSpecifier);
									img = reader.read(0, param);
									break;
								}
							}
						}finally{
							
						}
					}
				}
				reader.dispose();
				imageStream.close();
			} else {
				imageStream.close();
			}
		}finally{
			if(img == null)
				throw new IOException();
		}
		return img;
	}
}
