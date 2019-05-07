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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.jpeg.JpegDirectory;

/* 
 * @author Wladimir Leite (GPINF/SP)
 */
public class ImageUtil {

    public static final Set<String> jdkImagesSupported = getjdkImageMimesSupported();

    private static final Set<String> getjdkImageMimesSupported() {
        HashSet<String> set = new HashSet<String>();
        set.add("image/bmp"); //$NON-NLS-1$
        set.add("image/jpeg"); //$NON-NLS-1$
        set.add("image/gif"); //$NON-NLS-1$
        set.add("image/png"); //$NON-NLS-1$
        return set;
    }

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
        if (imgW <= 0) {
            imgW = 1;
        }
        if (imgH <= 0) {
            imgH = 1;
        }
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

    public static BufferedImage getSubSampledImage(InputStream source, int w, int h) {
        return getSubSampledImage(source, w, h, null);
    }

    public static class BooleanWrapper {
        public boolean value;
    }

    // Contribuição do PCF Wladimir e Nassif
    public static BufferedImage getSubSampledImage(InputStream source, int w, int h, BooleanWrapper renderException) {
        ImageInputStream iis = null;
        ImageReader reader = null;
        BufferedImage image = null;
        try {
            iis = ImageIO.createImageInputStream(source);
            Iterator<ImageReader> iter = ImageIO.getImageReaders(iis);
            if (!iter.hasNext())
                return null;
            reader = iter.next();
            reader.setInput(iis, false, true);

            int w0 = reader.getWidth(0);
            int h0 = reader.getHeight(0);
            int sampling = 1;
            if (w0 > w || h0 > h)
                if (w * h0 < w0 * h) {
                    sampling = w0 / w;
                } else {
                    sampling = h0 / h;
                }
            int finalW = (int) Math.ceil((float) w0 / sampling);
            int finalH = (int) Math.ceil((float) h0 / sampling);

            ImageReadParam params = reader.getDefaultReadParam();
            image = reader.getImageTypes(0).next().createBufferedImage(finalW, finalH);
            params.setDestination(image);
            params.setSourceSubsampling(sampling, sampling, 0, 0);

            reader.read(0, params);

        } catch (Throwable e) {
            // e.printStackTrace();
            if (renderException != null)
                renderException.value = true;

            if (image != null && isMonoColorImage(image))
                image = null;

        } finally {
            if (reader != null) {
                reader.dispose();
            }
            try {
                iis.close();
            } catch (IOException e) {
            }
        }

        return image;
    }

    public static boolean isMonoColorImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        int color;
        if (pixels.length > 0) {
            color = pixels[0];
        } else
            return false;

        for (int p : pixels)
            if (p != color)
                return false;

        return true;
    }

    public static BufferedImage trim(BufferedImage img) {
        if (img == null) {
            return null;
        }

        double WHITE_TOLERANCE = 20;
        int[] pixels = new int[0];

        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 2 || h <= 2) {
            return img;
        }
        if (pixels.length < w * h) {
            pixels = new int[w * h];
        }
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
                    if (dir == 0) {
                        rc.y++;
                    }
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
                    if (dir == 0) {
                        rc.x++;
                    }
                    rc.width--;
                } else {
                    break;
                }
            }
        }
        return img.getSubimage(rc.x, rc.y, rc.width, rc.height);
    }

    /**
     * Obtém as dimensões de uma imagem. Este método visa evitar que a imagem seja
     * lida/descompactada, já que apenas necessita-se das dimensões.
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
            // ignore
        } finally {
            IOUtil.closeQuietly(in);
        }
        return null;
    }

    /**
     * Cria uma nova imagem das dimensões especificadas, com a imagem original
     * centralizada na mesma.
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
        if (img.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return img;
        }
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
     * Lê um arquivo JPEG e retorna a imagem e um comentário, se este estiver
     * presente nos metadados da imagem.
     *
     * @return Array com {BufferedImage, String}
     */
    public static Object[] readJpegWithMetaData(File inFile) throws IOException {
        ImageReader reader = ImageIO.getImageReadersBySuffix("jpeg").next(); //$NON-NLS-1$
        ImageInputStream is = ImageIO.createImageInputStream(inFile);
        reader.setInput(is);
        BufferedImage img = reader.read(0);
        IIOMetadata meta = reader.getImageMetadata(0);
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_jpeg_image_1.0"); //$NON-NLS-1$
        is.close();
        reader.dispose();
        return new Object[] { img, findAttribute(root, "comment") }; //$NON-NLS-1$
    }

    /**
     * Método auxiliar que percorre uma árvore buscando o valor de um nó com
     * determinado nome.
     */
    private static String findAttribute(Node node, String name) {
        String ret = null;
        if (node.getNodeName().equals(name)) {
            ret = node.getNodeValue();
        }
        NamedNodeMap map = node.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            String s = findAttribute(map.item(i), name);
            if (ret == null) {
                ret = s;
            }
        }
        NodeList nl = node.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            String s = findAttribute(nl.item(i), name);
            if (ret == null) {
                ret = s;
            }
        }
        return ret;
    }

    /**
     * Grava uma imagem JPEG e insere um comentário nos metadados da imagem.
     */
    public static void saveJpegWithMetadata(BufferedImage img, File outFile, String data) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersBySuffix("jpeg").next(); //$NON-NLS-1$
        JPEGImageWriteParam jpegParams = (JPEGImageWriteParam) writer.getDefaultWriteParam();
        IIOMetadata imageMetadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(img), jpegParams);

        Element tree = (Element) imageMetadata.getAsTree("javax_imageio_jpeg_image_1.0"); //$NON-NLS-1$
        NodeList comNL = tree.getElementsByTagName("com"); //$NON-NLS-1$
        IIOMetadataNode comNode;
        if (comNL.getLength() == 0) {
            comNode = new IIOMetadataNode("com"); //$NON-NLS-1$
            Node markerSequenceNode = tree.getElementsByTagName("markerSequence").item(0); //$NON-NLS-1$
            markerSequenceNode.insertBefore(comNode, markerSequenceNode.getFirstChild());
        } else {
            comNode = (IIOMetadataNode) comNL.item(0);
        }
        comNode.setUserObject((data).getBytes("ISO-8859-1")); //$NON-NLS-1$
        imageMetadata.setFromTree("javax_imageio_jpeg_image_1.0", tree); //$NON-NLS-1$

        IIOImage iioimage = new IIOImage(img, null, imageMetadata);
        ImageOutputStream os = ImageIO.createImageOutputStream(outFile);
        writer.setOutput(os);
        writer.write(null, iioimage, jpegParams);
        os.close();
        writer.dispose();
    }

    private static long scaleSubsamplingMaintainAspectRatio(Dimension d1, Dimension d2) {
        long subsampling = 1;
        if (d1.getWidth() > d2.getWidth() || d1.getHeight() > d2.getHeight()) {
            subsampling = (long) Math.max(Math.floor(d1.getWidth() / d2.getWidth()),
                    Math.floor(d1.getHeight() / d2.getHeight()));
        }
        return subsampling;
    }

    public static BufferedImage getThumb(InputStream stream) {
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
                            int ih = dj.getImageHeight();
                            int tw = img.getWidth();
                            int th = img.getHeight();
                            if ((tw > th && iw < ih) || (tw < th && iw > ih)) {
                                // Orientacao (retrato/paisagem) da miniatura EXIF inconsistente com a imagem.
                                // Melhor retornar e utilizar a geracao "normal", a partir da imagem.
                                return null;
                            }
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
                            if (x > 0 || y > 0) {
                                img = img.getSubimage(x, y, tw, th);
                            }
                        }

                    } catch (Exception e) {
                    }

                    return img;
                }
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return null;
    }

    public static int getOrientation(InputStream imageFile) {
        try {
            Metadata metadata = JpegMetadataReader.readMetadata(imageFile);
            if (metadata != null) {
                Collection<ExifIFD0Directory> dirs = metadata.getDirectoriesOfType(ExifIFD0Directory.class);
                if (dirs != null) {
                    for (ExifIFD0Directory dir : dirs) {
                        Integer tagOrientation = dir.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
                        if (tagOrientation != null)
                            return tagOrientation;
                    }
                }
            }
        } catch (Exception e) {
        }
        return -1;
    }

    public static BufferedImage rotate(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8)
            return src;
        int angle = (orientation - 1) / 2;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(angle == 1 ? w : h, angle == 1 ? h : w, src.getType());
        Graphics2D g = dest.createGraphics();
        double d = (h - w) / 2.0;
        if (angle == 2)
            g.translate(d, d);
        else if (angle == 3)
            g.translate(-d, -d);
        g.rotate((angle == 1 ? 2 : angle == 2 ? 1 : 3) * Math.PI / 2, dest.getWidth() / 2.0, dest.getHeight() / 2.0);
        g.drawRenderedImage(src, null);
        g.dispose();
        return dest;
    }
}
