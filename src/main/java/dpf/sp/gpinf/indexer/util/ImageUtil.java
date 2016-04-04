/*
 * Copyright 2012-2016, Wladimir Luiz Caldas Leite
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.jpeg.JpegDirectory;

import dpf.sp.gpinf.indexer.search.GalleryValue;

public class ImageUtil {

    /**
     * Redimensiona um imagem, mantendo sua proporção original. 
     */
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
        if (imgW <= 0) imgW = 1;
        if (imgH <= 0) imgH = 1;
        return resizeImageFixed(img, imgW, imgH);
    }

    /**
     * Redimensiona um imagem numa área determinada. 
     */
    public static BufferedImage resizeImageFixed(BufferedImage img, int imgW, int imgH) {
        BufferedImage bufferedImage = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
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
            Metadata metadata = JpegMetadataReader.readMetadata(stream);
            if (metadata != null) {
                ExifThumbnailDirectory dir = metadata.getFirstDirectoryOfType(ExifThumbnailDirectory.class);
                if (dir != null) {
                    byte[] imgBytes = dir.getThumbnailData();
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(imgBytes));
                    try {
                        JpegDirectory dj = metadata.getFirstDirectoryOfType(JpegDirectory.class);
                        if (dj != null) {
                            int iw = dj.getImageWidth();
                            value.originalW = iw;
                            int ih = dj.getImageHeight();
                            value.originalH = ih;
                            int tw = img.getWidth();
                            int th = img.getHeight();
                            int x = 0;
                            int y = 0;
                            while (iw * th > ih * tw && th > 20) {
                                y++;
                                th -= 2;
                            }
                            while (iw * th < ih * tw && tw > 20) {
                                x++;
                                tw -= 2;
                            }
                            if (x > 0 || y > 0) img = img.getSubimage(x, y, tw, th);
                        }

                    } catch (Exception e) {}

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
                if (w * h0 < w0 * h) sampling = w0 / w;
                else sampling = h0 / h;
            }

            params.setSourceSubsampling(sampling, sampling, 0, 0);
            BufferedImage image = reader.read(0, params);

            return image;

        } catch (Exception e) {
            //e.printStackTrace();

        } finally {
            if (reader != null) reader.dispose();
            try {
                iis.close();
            } catch (IOException e) {}
        }
        return null;
    }

    public static BufferedImage trim(BufferedImage img) {
    	if(img == null)
    		return null;

        double WHITE_TOLERANCE = 20;
        int[] pixels = new int[0];

        int w = img.getWidth();
        int h = img.getHeight();
        if(w <= 2 || h <= 2)
        	return img;
        if (pixels.length < w * h) pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        Rectangle rc = new Rectangle(1, 1, w - 2, h - 2);
        for (int dir = 0; dir <= 1; dir++) {
            while (rc.height > 1) {
                int off = (dir == 0 ? rc.y : rc.y + rc.height - 1) * w;
                int sum = 0;
                for (int x = rc.x; x < rc.width + rc.x; x++) {
                    int pixel = pixels[off + x];
                    sum += 255 - red(pixel) + 255 - green(pixel) + 255 - blue(pixel);
                }
                if (sum < WHITE_TOLERANCE * rc.width) {
                    if (dir == 0) rc.y++;
                    rc.height--;
                } else {
                    break;
                }
            }
        }
        for (int dir = 0; dir <= 1; dir++) {
            while (rc.width > 1) {
                int off = dir == 0 ? rc.x : rc.x + rc.width - 1;
                int sum = 0;
                for (int y = rc.y; y < rc.height + rc.y; y++) {
                    int pixel = pixels[off + y * w];
                    sum += 255 - red(pixel) + 255 - green(pixel) + 255 - blue(pixel);
                }
                if (sum < WHITE_TOLERANCE * rc.height) {
                    if (dir == 0) rc.x++;
                    rc.width--;
                } else {
                    break;
                }
            }
        }
        return img.getSubimage(rc.x, rc.y, rc.width, rc.height);
    }

    /**
     * Obtém as dimensões de uma imagem.
     * Este método visa evitar que a imagem seja lida/descompactada, já que apenas
     * necessita-se das dimensões. 
     */
    public static Dimension getImageFileDimension(Object img) {
    	ImageInputStream in = null;
        try {
            in = ImageIO.createImageInputStream(img);
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(in);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            }
        } catch (Exception e) {
        	//ignore
        }finally{
        	IOUtil.closeQuietly(in);
        }
        return null;
    }

    /**
     * Cria uma nova imagem das dimensões especificadas, com a imagem original centralizada na mesma.
     */
    public static BufferedImage getCenteredImage(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2 = (Graphics2D) out.getGraphics();
        int x = (w - img.getWidth()) / 2;
        int y = (h - img.getHeight()) / 2;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.drawImage(img, x, y, null);
        g2.dispose();
        return out;
    }
    
    public static BufferedImage getOpaqueImage(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_3BYTE_BGR) return img;
    	int w = img.getWidth();
    	int h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2 = (Graphics2D) out.getGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return out;
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

    /**
     * Lê um arquivo JPEG e retorna a imagem e um comentário, se este estiver presente nos
     * metadados da imagem.
     * @return Array com {BufferedImage, String}
     */
    public static Object[] readJpegWithMetaData(File inFile) throws IOException {
        ImageReader reader = ImageIO.getImageReadersBySuffix("jpeg").next();
        ImageInputStream is = ImageIO.createImageInputStream(inFile);
        reader.setInput(is);
        BufferedImage img = reader.read(0);
        IIOMetadata meta = reader.getImageMetadata(0);
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_jpeg_image_1.0");
        is.close();
        reader.dispose();
        return new Object[] {img,findAttribute(root, "comment")};
    }

    /**
     * Método auxiliar que percorre uma árvore buscando o valor de um nó com determinado nome.
     */
    private static String findAttribute(Node node, String name) {
        String ret = null;
        if (node.getNodeName().equals(name)) ret = node.getNodeValue();
        NamedNodeMap map = node.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            String s = findAttribute(map.item(i), name);
            if (ret == null) ret = s;
        }
        NodeList nl = node.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            String s = findAttribute(nl.item(i), name);
            if (ret == null) ret = s;
        }
        return ret;
    }

    /**
     * Grava uma imagem JPEG e insere um comentário nos metadados da imagem. 
     */
    public static void saveJpegWithMetadata(BufferedImage img, File outFile, String data) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersBySuffix("jpeg").next();
        JPEGImageWriteParam jpegParams = (JPEGImageWriteParam) writer.getDefaultWriteParam();
        IIOMetadata imageMetadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(img), jpegParams);

        Element tree = (Element) imageMetadata.getAsTree("javax_imageio_jpeg_image_1.0");
        NodeList comNL = tree.getElementsByTagName("com");
        IIOMetadataNode comNode;
        if (comNL.getLength() == 0) {
            comNode = new IIOMetadataNode("com");
            Node markerSequenceNode = tree.getElementsByTagName("markerSequence").item(0);
            markerSequenceNode.insertBefore(comNode, markerSequenceNode.getFirstChild());
        } else {
            comNode = (IIOMetadataNode) comNL.item(0);
        }
        comNode.setUserObject((data).getBytes("ISO-8859-1"));
        imageMetadata.setFromTree("javax_imageio_jpeg_image_1.0", tree);

        IIOImage iioimage = new IIOImage(img, null, imageMetadata);
        ImageOutputStream os = ImageIO.createImageOutputStream(outFile);
        writer.setOutput(os);
        writer.write(null, iioimage, jpegParams);
        os.close();
        writer.dispose();
    }
}
