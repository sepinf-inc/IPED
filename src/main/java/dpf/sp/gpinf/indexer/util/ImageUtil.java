/*
 * Copyright 2012-2014, Wladimir Luiz Caldas Leite
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.util;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.jpeg.JpegDirectory;

import dpf.sp.gpinf.indexer.search.GalleryValue;

public class ImageUtil {

	public static BufferedImage resizeImage(BufferedImage img, int maxW, int maxH) {
		int imgW = img.getWidth();
		int imgH = img.getHeight();
		if (imgW * maxH > imgH * maxW) {
			imgH = imgH * maxW / imgW;
			imgW = maxW;
		} else {
			imgW = imgW * maxH / imgH;
			imgH = maxH;
		}
		if (imgW <= 0)
			imgW = 1;
		if (imgH <= 0)
			imgH = 1;
		BufferedImage bufferedImage = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics2D = bufferedImage.createGraphics();
		// graphics2D.setComposite(AlphaComposite.Src);
		graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics2D.drawImage(img, 0, 0, imgW, imgH, null);
		graphics2D.dispose();
		return bufferedImage;
	}

	public static BufferedImage getThumb(InputStream stream, GalleryValue value) {
		try {
			Metadata metadata = JpegMetadataReader.readMetadata(stream, false);
			if (metadata != null) {
				ExifThumbnailDirectory dir = metadata.getDirectory(ExifThumbnailDirectory.class);
				if (dir != null) {
					byte[] imgBytes = dir.getThumbnailData();
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(imgBytes));
					try {
						JpegDirectory dj = metadata.getDirectory(JpegDirectory.class);
						if (dj != null) {
							int iw = dj.getImageWidth();
							value.originalW = iw;
							int ih = dj.getImageHeight();
							value.originalH = ih;
							int tw = img.getWidth();
							int th = img.getHeight();
							int x = 0;
							int y = 0;
							while (iw * th >= ih * tw && th > 20) {
								y++;
								th -= 2;
							}
							while (iw * th <= ih * tw && tw > 20) {
								x++;
								tw -= 2;
							}
							img = img.getSubimage(x, y, tw, th);
						}

					} catch (Exception e) {
					}

					return img;
				}
			}
		} catch (Exception e) {
			//e.printStackTrace();
		}
		return null;
	}

	// Contribuição do PCF Wladimir
	public static BufferedImage getSubSampledImage(InputStream source, int w, int h, GalleryValue value) {
		ImageInputStream iis = null;
		ImageReader reader = null;
		try {
			iis = ImageIO.createImageInputStream(source);
			Iterator<ImageReader> iter = ImageIO.getImageReaders(iis);
			reader = iter.next();
			ImageReadParam params = reader.getDefaultReadParam();
			reader.setInput(iis, false, true);
			int w0 = reader.getWidth(0);
			int h0 = reader.getHeight(0);
			value.originalW = w0;
			value.originalH = h0;
			int sampling = 1;
			if (w0 > w || h0 > h) {
				if (w * h0 < w0 * h)
					sampling = w0 / w;
				else
					sampling = h0 / h;
			}

			params.setSourceSubsampling(sampling, sampling, 0, 0);
			BufferedImage image = reader.read(0, params);

			return image;

		} catch (Exception e) {
			// e.printStackTrace();

		} finally {
			if (reader != null)
				reader.dispose();
			try {
				iis.close();
			} catch (IOException e) {
			}
		}
		return null;
	}

	public static BufferedImage trim(BufferedImage img) {

		double WHITE_TOLERANCE = 20;
		int[] pixels = new int[0];

		int w = img.getWidth();
		int h = img.getHeight();
		if (pixels.length < w * h)
			pixels = new int[w * h];
		img.getRGB(0, 0, w, h, pixels, 0, w);
		Rectangle rc = new Rectangle(1, 1, w - 2, h - 2);
		for (int dir = 0; dir <= 1; dir++) {
			while (rc.height > 0) {
				int off = (dir == 0 ? rc.y : rc.y + rc.height - 1) * w;
				int sum = 0;
				for (int x = rc.x; x < rc.width + rc.x; x++) {
					int pixel = pixels[off + x];
					sum += 255 - red(pixel) + 255 - green(pixel) + 255 - blue(pixel);
				}
				if (sum < WHITE_TOLERANCE * rc.width) {
					if (dir == 0)
						rc.y++;
					rc.height--;
				} else {
					break;
				}
			}
		}
		for (int dir = 0; dir <= 1; dir++) {
			while (rc.width > 0) {
				int off = dir == 0 ? rc.x : rc.x + rc.width - 1;
				int sum = 0;
				for (int y = rc.y; y < rc.height + rc.y; y++) {
					int pixel = pixels[off + y * w];
					sum += 255 - red(pixel) + 255 - green(pixel) + 255 - blue(pixel);
				}
				if (sum < WHITE_TOLERANCE * rc.height) {
					if (dir == 0)
						rc.x++;
					rc.width--;
				} else {
					break;
				}
			}
		}
		return img.getSubimage(rc.x, rc.y, rc.width, rc.height);
	}

	private static final int red(int color) {
		return color & 0xff;
	}

	private static final int green(int color) {
		return (color >>> 8) & 0xff;
	}

	private static final int blue(int color) {
		return (color >>> 16) & 0xff;
	}

}
